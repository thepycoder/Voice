package voice.app.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import voice.core.common.rootGraphAs
import voice.core.remote.LibrarySyncManager
import voice.core.remote.SyncState
import voice.app.di.AppGraph

class SyncForegroundService : Service() {

  @Inject
  lateinit var librarySyncManager: LibrarySyncManager

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    rootGraphAs<AppGraph>().inject(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val notification = buildNotification("Syncing…", null)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ServiceCompat.startForeground(
        this,
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
    } else {
      @Suppress("DEPRECATION")
      startForeground(NOTIFICATION_ID, notification)
    }

    serviceScope.launch {
      val syncJob = launch {
        librarySyncManager.sync().let { }
      }
      launch {
        librarySyncManager.syncState
          .map { state -> notificationText(state) }
          .collect { (title, text) ->
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
              NOTIFICATION_ID,
              buildNotification(title, text),
            )
          }
      }
      syncJob.join()
      stopSelf()
    }

    return START_NOT_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun notificationText(state: SyncState): Pair<String, String?> {
    return when (state) {
      is SyncState.Idle -> "Syncing…" to null
      is SyncState.Syncing -> {
        val progress = if (state.total > 0) " (${state.current} / ${state.total})" else ""
        state.message + progress to null
      }
      is SyncState.Success -> "Sync complete" to "${state.newBooks} new books"
      is SyncState.Error -> "Sync failed" to state.message
    }
  }

  private fun buildNotification(title: String, text: String?): android.app.Notification {
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(title)
      .setContentText(text ?: " ")
      .setSmallIcon(android.R.drawable.stat_sys_upload)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Remote sync",
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply { description = "Syncing remote audiobook library" }
      (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
  }

  private companion object {
    const val NOTIFICATION_ID = 2000
    const val CHANNEL_ID = "voice_remote_sync"
  }
}
