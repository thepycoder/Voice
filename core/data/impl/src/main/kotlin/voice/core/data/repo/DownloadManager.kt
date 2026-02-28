package voice.core.data.repo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import voice.core.logging.api.Logger
import voice.core.remote.DownloadManager
import voice.core.remote.DownloadState
import voice.core.remote.RemoteBook
import voice.core.remote.RemotePaths
import voice.core.remote.SftpManager
import voice.core.remote.SftpSettingsProvider
import voice.core.scanner.MediaScanTrigger
import java.io.File

@Inject
@ContributesBinding(AppScope::class)
public class DownloadManagerImpl(
  private val application: Application,
  private val sftpManager: SftpManager,
  private val settingsProvider: SftpSettingsProvider,
  private val mediaScanTrigger: MediaScanTrigger,
  private val bookContentRepo: BookContentRepo,
) : DownloadManager {

  private val notificationManager =
    application.getSystemService(NotificationManager::class.java)

  private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
  override val downloadState: StateFlow<DownloadState> = _downloadState

  init {
    createNotificationChannel()
  }

  override suspend fun downloadBook(book: RemoteBook): Result<Unit> = withContext(Dispatchers.IO) {
    val m4bFileName = book.m4bFileName
      ?: return@withContext Result.failure(IllegalArgumentException("No m4b file specified"))

    try {
      _downloadState.value = DownloadState.Downloading(book.id, 0f)
      val settings = settingsProvider.get()
      val remotePath = "${settings.remotePath}/${book.folder}/$m4bFileName"
      val downloadsDir = File(application.filesDir, RemotePaths.DOWNLOADS_DIR).apply { mkdirs() }
      val bookDir = File(downloadsDir, book.id).apply { mkdirs() }
      val localFile = File(bookDir, m4bFileName)

      val notificationId = book.id.hashCode()
      sftpManager.downloadFile(
        remotePath = remotePath,
        localFile = localFile,
        onProgress = { bytesRead, totalBytes ->
          val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
          _downloadState.value = DownloadState.Downloading(book.id, progress)
          notificationManager.notify(
            notificationId,
            createProgressNotification(book.title, (progress * 100).toInt()),
          )
        },
      )

      if (book.hasPdf) {
        try {
          val pdfRemote = "${settings.remotePath}/${book.folder}/companion.pdf"
          val pdfLocal = File(bookDir, "companion.pdf")
          sftpManager.downloadFile(pdfRemote, pdfLocal)
        } catch (e: Exception) {
          Logger.w(e, "Optional PDF download failed")
        }
      }

      mediaScanTrigger.scan(restartIfScanning = true)

      setRemoteBookIdAfterDownload(book.id)

      _downloadState.value = DownloadState.Complete(book.id)
      notificationManager.notify(notificationId, createCompleteNotification(book.title))
      Result.success(Unit)
    } catch (e: Exception) {
      Logger.e(e, "Download failed for ${book.id}")
      _downloadState.value = DownloadState.Error(book.id, e.message ?: "Unknown error")
      Result.failure(e)
    }
  }

  override suspend fun removeBook(book: RemoteBook): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val bookDir = File(application.filesDir, RemotePaths.DOWNLOADS_DIR).resolve(book.id)
      if (bookDir.exists()) {
        bookDir.deleteRecursively()
      }
      mediaScanTrigger.scan(restartIfScanning = true)
      Result.success(Unit)
    } catch (e: Exception) {
      Logger.e(e, "Remove download failed for ${book.id}")
      Result.failure(e)
    }
  }

  private suspend fun setRemoteBookIdAfterDownload(remoteBookId: String) {
    val downloadsPath = File(application.filesDir, RemotePaths.DOWNLOADS_DIR).absolutePath
    val allContent = bookContentRepo.all()
    val match = allContent.find { content ->
      val path = content.id.toUri().path ?: return@find false
      path.contains(remoteBookId) && path.startsWith(downloadsPath)
    }
    if (match != null) {
      val updated = match.copy(remoteBookId = remoteBookId)
      bookContentRepo.put(updated)
      Logger.i("Set remoteBookId=$remoteBookId on book ${match.id}")
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Downloads",
        NotificationManager.IMPORTANCE_LOW,
      ).apply { description = "Audiobook downloads" }
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun createProgressNotification(title: String, progress: Int): android.app.Notification {
    return NotificationCompat.Builder(application, CHANNEL_ID)
      .setContentTitle("Downloading: $title")
      .setContentText("$progress%")
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setProgress(100, progress, false)
      .setOngoing(true)
      .build()
  }

  private fun createCompleteNotification(title: String): android.app.Notification {
    return NotificationCompat.Builder(application, CHANNEL_ID)
      .setContentTitle("Download complete")
      .setContentText(title)
      .setSmallIcon(android.R.drawable.stat_sys_download_done)
      .setAutoCancel(true)
      .build()
  }

  private companion object {
    const val CHANNEL_ID = "voice_remote_downloads"
  }
}
