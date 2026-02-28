package voice.core.remote

import android.app.Application
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import voice.core.logging.api.Logger
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Inject
public class LibrarySyncManager(
  private val application: Application,
  private val sftpManager: SftpManager,
  private val settingsProvider: SftpSettingsProvider,
  private val catalogRepo: RemoteCatalogRepo,
) {

  private val mp4MetadataReader = Mp4MetadataReader()
  private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  public suspend fun sync(onProgress: (String) -> Unit = {}): SyncResult = withContext(Dispatchers.IO) {
    try {
      val settings = settingsProvider.get()
      if (settings.remotePath.isBlank()) {
        return@withContext SyncResult.Error("Remote path not configured")
      }

      onProgress("Connecting…")

      val remoteFolders = try {
        sftpManager.listDirectories(settings.remotePath)
      } catch (e: Exception) {
        try {
          val rootFolders = sftpManager.listDirectories("/")
          return@withContext SyncResult.Error(
            "Path '${settings.remotePath}' not found. Root folders: ${rootFolders.joinToString(", ")}",
          )
        } catch (rootError: Exception) {
          Logger.e(e, "SFTP list directories failed")
          return@withContext SyncResult.Error(
            "Failed to access '${settings.remotePath}': ${e.message}",
          )
        }
      }

      val currentBooks = catalogRepo.flow().first().associateBy { it.id }.toMutableMap()
      var newBooksCount = 0
      val coversDir = File(application.filesDir, RemotePaths.COVERS_DIR).apply { mkdirs() }

      for (folder in remoteFolders) {
        onProgress("Processing: $folder")

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
            onProgress("Skipping $folder: no m4b")
            continue
          }

          if (coverFile != null) {
            val localCoverFile = File(coversDir, "$folder.jpg")
            sftpManager.downloadFile("$remotePath/${coverFile.name}", localCoverFile)
          }

          onProgress("Extracting metadata: $folder")
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
          onProgress("Error: $folder – ${e.message}")
        }
      }

      catalogRepo.setBooks(currentBooks.values.toList())
      Logger.i("Remote sync done: $newBooksCount new books")
      SyncResult.Success(newBooksCount)
    } catch (e: Exception) {
      Logger.e(e, "Sync failed")
      SyncResult.Error(e.message ?: "Unknown error")
    }
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
