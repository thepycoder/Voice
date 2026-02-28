package voice.core.remote

import android.app.Application
import dev.zacsweers.metro.Inject
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

@SingleIn(AppScope::class)
@Inject
public class LibrarySyncManager(
  private val application: Application,
  private val sftpManager: SftpManager,
  private val settingsProvider: SftpSettingsProvider,
  private val catalogRepo: RemoteCatalogRepo,
) {

  private val mp4MetadataReader = Mp4MetadataReader()
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

      val currentBooks = catalogRepo.flow().first().associateBy { it.id }.toMutableMap()
      var newBooksCount = 0
      val coversDir = File(application.filesDir, RemotePaths.COVERS_DIR).apply { mkdirs() }

      for (folder in remoteFolders) {
        emitProgress("Processing: $folder")

        if (currentBooks.containsKey(folder)) continue

        try {
          val remotePath = "${settings.remotePath}/$folder"
          val files = sftpManager.listFiles(remotePath)

          val m4bFile = files.find { it.name.endsWith(".m4b", ignoreCase = true) }
          val coverFile = files.find {
            it.name.endsWith(".jpg", ignoreCase = true) || it.name.endsWith(".jpeg", ignoreCase = true)
          }
          val pdfFile = files.find { it.name.endsWith(".pdf", ignoreCase = true) }

          if (m4bFile == null) {
            emitProgress("Skipping $folder: no m4b")
            continue
          }

          if (coverFile != null) {
            val localCoverFile = File(coversDir, "$folder.jpg")
            sftpManager.downloadFile("$remotePath/${coverFile.name}", localCoverFile)
          }

          emitProgress("Extracting metadata: $folder")
          val metadata = try {
            val m4bData = sftpManager.partialRead(
              "$remotePath/${m4bFile.name}",
              0,
              Mp4MetadataReader.DEFAULT_READ_SIZE,
            )
            mp4MetadataReader.parseMetadata(m4bData)
          } catch (e: Exception) {
            Logger.w(e, "Failed to read m4b metadata for $folder")
            Mp4MetadataReader.Metadata()
          }

          val book = RemoteBook(
            id = folder,
            folder = folder,
            title = metadata.title ?: folder,
            author = metadata.author,
            durationMs = metadata.durationMs,
            hasPdf = pdfFile != null,
            dateAdded = LocalDateTime.now().format(dateFormatter),
            coverFileName = coverFile?.name,
            m4bFileName = m4bFile.name,
          )
          currentBooks[folder] = book
          newBooksCount++
        } catch (e: Exception) {
          Logger.e(e, "Error processing folder $folder")
          emitProgress("Error: $folder – ${e.message}")
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
