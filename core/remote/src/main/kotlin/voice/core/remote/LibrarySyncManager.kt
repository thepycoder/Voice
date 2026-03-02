package voice.core.remote

import android.app.Application
import dev.zacsweers.metro.Inject
import voice.core.data.isSupportedAudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import voice.core.logging.api.Logger
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val m4Extensions = setOf("m4b", "m4a", "mp4")

private fun computeContentHash(files: List<SftpFile>): String {
  val input = files
    .sortedBy { it.name }
    .joinToString("|") { "${it.name}:${it.size}:${it.mtime}" }
  val digest = MessageDigest.getInstance("SHA-256")
  val hash = digest.digest(input.encodeToByteArray())
  return hash.joinToString("") { "%02x".format(it) }
}

private fun metadataComplete(book: RemoteBook): Boolean =
  book.error == null && book.audioFileName != null

@SingleIn(AppScope::class)
@Inject
public class LibrarySyncManager(
  private val application: Application,
  private val sftpManager: SftpManager,
  private val settingsProvider: SftpSettingsProvider,
  private val catalogRepo: RemoteCatalogRepo,
  private val metadataExtractor: RemoteMetadataExtractor,
) {

  private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
  public val syncState: StateFlow<SyncState> = _syncState

  public suspend fun sync(onProgress: (String) -> Unit = {}): SyncResult = withContext(Dispatchers.IO) {
    try {
      val settings = settingsProvider.get()
      if (settings.remotePath.isBlank()) {
        _syncState.value = SyncState.Error("Remote path not configured")
        return@withContext SyncResult.Error("Remote path not configured")
      }

      sftpManager.withSftpSession { session ->
        val remoteFolders = try {
          session.listDirectories(settings.remotePath)
        } catch (e: Exception) {
          try {
            val rootFolders = session.listDirectories("/")
            val msg = "Path '${settings.remotePath}' not found. Root folders: ${rootFolders.joinToString(", ")}"
            _syncState.value = SyncState.Error(msg)
            return@withSftpSession SyncResult.Error(msg)
          } catch (rootError: Exception) {
            Logger.e(e, "SFTP list directories failed")
            val msg = "Failed to access '${settings.remotePath}': ${e.message}"
            _syncState.value = SyncState.Error(msg)
            return@withSftpSession SyncResult.Error(msg)
          }
        }

        val total = remoteFolders.size
        val emitProgress: (String, Int) -> Unit = { msg, current ->
          _syncState.value = SyncState.Syncing(msg, current, total)
          onProgress(msg)
        }
        emitProgress("Connecting…", 0)

        val existingBooks = catalogRepo.flow().first().associateBy { it.id }
        val remoteFolderSet = remoteFolders.toSet()
        val currentBooks = existingBooks.filterKeys { it in remoteFolderSet }.toMutableMap()
        var newBooksCount = 0
        val coversDir = File(application.filesDir, RemotePaths.COVERS_DIR).apply { mkdirs() }

        suspend fun persistCatalog() {
          catalogRepo.setBooks(currentBooks.values.toList())
        }

        for ((index, folder) in remoteFolders.withIndex()) {
            val processedCount = index + 1
            emitProgress("Processing: $folder", index)

            val remotePath = "${settings.remotePath}/$folder"
            val files = try {
              session.listFiles(remotePath)
            } catch (e: Exception) {
              Logger.e(e, "Error listing files for $folder")
              emitProgress("Error: $folder – ${e.message}", processedCount)
              val book = RemoteBook(
                id = folder,
                folder = folder,
                title = folder,
                author = null,
                durationMs = 0L,
                hasPdf = false,
                dateAdded = LocalDateTime.now().format(dateFormatter),
                coverFileName = null,
                audioFileName = null,
                error = e.message ?: "Unknown error",
                contentHash = null,
              )
              currentBooks[folder] = book
              newBooksCount++
              persistCatalog()
              continue
            }

            val currentHash = computeContentHash(files)
            val existingBook = existingBooks[folder]

            if (existingBook != null && existingBook.contentHash == currentHash && metadataComplete(existingBook)) {
              currentBooks[folder] = existingBook
              emitProgress("Processing: $folder", processedCount)
              persistCatalog()
              continue
            }

            val audioFile = files.find { it.name.isSupportedAudioFile() }
            val coverFile = files.find {
              it.name.endsWith(".jpg", ignoreCase = true) || it.name.endsWith(".jpeg", ignoreCase = true)
            }
            val pdfFile = files.find { it.name.endsWith(".pdf", ignoreCase = true) }

            try {
              if (audioFile == null) {
                val book = RemoteBook(
                  id = folder,
                  folder = folder,
                  title = folder,
                  author = null,
                  durationMs = 0L,
                  hasPdf = pdfFile != null,
                  dateAdded = LocalDateTime.now().format(dateFormatter),
                  coverFileName = coverFile?.name,
                  audioFileName = null,
                  error = "No audio file found",
                  contentHash = currentHash,
                )
                currentBooks[folder] = book
                newBooksCount++
                persistCatalog()
                emitProgress("Processing: $folder", processedCount)
                continue
              }

              var coverError: String? = null
              if (coverFile != null) {
                try {
                  val localCoverFile = File(coversDir, "$folder.jpg")
                  session.downloadFile("$remotePath/${coverFile.name}", localCoverFile)
                } catch (e: Exception) {
                  Logger.w(e, "Cover download failed for $folder")
                  coverError = "Couldn't download cover"
                }
              }

              val (title, author, durationMs) = if (audioFile.name.substringAfterLast(".", "").lowercase() in m4Extensions) {
                try {
                  val audioPath = "$remotePath/${audioFile.name}"
                  val metadata = metadataExtractor.extractMetadata(audioFile.size) { offset, length ->
                    session.partialRead(audioPath, offset, length)
                  }
                  Triple(
                    metadata.title?.takeIf { it.isNotBlank() } ?: folder,
                    metadata.author?.takeIf { it.isNotBlank() },
                    metadata.durationMs,
                  )
                } catch (e: Exception) {
                  Logger.w(e, "Metadata extraction failed for $folder")
                  Triple(folder, null, 0L)
                }
              } else {
                Triple(folder, null, 0L)
              }

              val book = RemoteBook(
                id = folder,
                folder = folder,
                title = title,
                author = author,
                durationMs = durationMs,
                hasPdf = pdfFile != null,
                dateAdded = LocalDateTime.now().format(dateFormatter),
                coverFileName = if (coverError != null) null else coverFile?.name,
                audioFileName = audioFile.name,
                error = coverError,
                contentHash = currentHash,
              )
              currentBooks[folder] = book
              newBooksCount++
              persistCatalog()
            } catch (e: Exception) {
              Logger.e(e, "Error processing folder $folder")
              emitProgress("Error: $folder – ${e.message}", processedCount)
              val book = RemoteBook(
                id = folder,
                folder = folder,
                title = folder,
                author = null,
                durationMs = 0L,
                hasPdf = false,
                dateAdded = LocalDateTime.now().format(dateFormatter),
                coverFileName = null,
                audioFileName = null,
                error = e.message ?: "Unknown error",
                contentHash = currentHash,
              )
              currentBooks[folder] = book
              newBooksCount++
              persistCatalog()
            }
            emitProgress("Processing: $folder", processedCount)
          }
        Logger.i("Remote sync done: $newBooksCount new books")
        _syncState.value = SyncState.Success(newBooksCount)
        SyncResult.Success(newBooksCount)
      }
    } catch (e: Exception) {
      Logger.e(e, "Sync failed")
      _syncState.value = SyncState.Error(e.message ?: "Unknown error")
      SyncResult.Error(e.message ?: "Unknown error")
    }
  }

  public fun clearSyncState() {
    _syncState.value = SyncState.Idle
  }

  public suspend fun clearAll(): Unit = withContext(Dispatchers.IO) {
    Logger.i("Clearing all remote data")
    catalogRepo.setBooks(emptyList())
    File(application.filesDir, RemotePaths.DOWNLOADS_DIR).deleteRecursively()
    File(application.filesDir, RemotePaths.COVERS_DIR).deleteRecursively()
  }

  public sealed class SyncResult {
    public data class Success(val newBooks: Int) : SyncResult()
    public data class Error(val message: String) : SyncResult()
  }

}
