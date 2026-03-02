package voice.app.sync

import android.app.Application
import android.content.Intent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import voice.core.remote.SyncRunner

@Inject
@ContributesBinding(AppScope::class)
class SyncRunnerImpl(
  private val application: Application,
) : SyncRunner {

  override fun startSync() {
    application.startForegroundService(
      Intent(application, SyncForegroundService::class.java),
    )
  }
}
