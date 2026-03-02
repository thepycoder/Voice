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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val m4Extensions = setOf("m4b", "m4a", "mp4")

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

      val emitProgress: (String) -> Unit = { msg ->
        _syncState.value = SyncState.Syncing(msg)
        onProgress(msg)
      }
      emitProgress("Connecting…")

      val remoteFolders = try {
        sftpManager.listDirectories(settings.remotePath)
      } catch (e: Exception) {
        try {
          val rootFolders = sftpManager.listDirectories("/")
          val msg = "Path '${settings.remotePath}' not found. Root folders: ${rootFolders.joinToString(", ")}"
          _syncState.value = SyncState.Error(msg)
          return@withContext SyncResult.Error(msg)
        } catch (rootError: Exception) {
          Logger.e(e, "SFTP list directories failed")
          val msg = "Failed to access '${settings.remotePath}': ${e.message}"
          _syncState.value = SyncState.Error(msg)
          return@withContext SyncResult.Error(msg)
        }
      }

      val existingBooks = catalogRepo.flow().first().associateBy { it.id }
      val remoteFolderSet = remoteFolders.toSet()
      val currentBooks = existingBooks.filterKeys { it in remoteFolderSet }.toMutableMap()
      var newBooksCount = 0
      val coversDir = File(application.filesDir, RemotePaths.COVERS_DIR).apply { mkdirs() }

      for (folder in remoteFolders) {
        emitProgress("Processing: $folder")

        if (existingBooks.containsKey(folder)) {
          currentBooks[folder] = existingBooks[folder]!!
          continue
        }

        try {
          val remotePath = "${settings.remotePath}/$folder"
          val files = sftpManager.listFiles(remotePath)

          val audioFile = files.find { it.name.isSupportedAudioFile() }
          val coverFile = files.find {
            it.name.endsWith(".jpg", ignoreCase = true) || it.name.endsWith(".jpeg", ignoreCase = true)
          }
          val pdfFile = files.find { it.name.endsWith(".pdf", ignoreCase = true) }

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
            )
            currentBooks[folder] = book
            newBooksCount++
            continue
          }

          var coverError: String? = null
          if (coverFile != null) {
            try {
              val localCoverFile = File(coversDir, "$folder.jpg")
              sftpManager.downloadFile("$remotePath/${coverFile.name}", localCoverFile)
            } catch (e: Exception) {
              Logger.w(e, "Cover download failed for $folder")
              coverError = "Couldn't download cover"
            }
          }

          val (title, author, durationMs) = if (audioFile.name.substringAfterLast(".", "").lowercase() in m4Extensions) {
            try {
              val metadata = metadataExtractor.extractMetadata(
                "$remotePath/${audioFile.name}",
                audioFile.size,
              )
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
          )
          currentBooks[folder] = book
          newBooksCount++
        } catch (e: Exception) {
          Logger.e(e, "Error processing folder $folder")
          emitProgress("Error: $folder – ${e.message}")
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
          )
          currentBooks[folder] = book
          newBooksCount++
        }
      }

      catalogRepo.setBooks(currentBooks.values.toList())
      Logger.i("Remote sync done: $newBooksCount new books")
      _syncState.value = SyncState.Success(newBooksCount)
      SyncResult.Success(newBooksCount)
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
