package voice.core.playback.playstate

import androidx.media3.common.Player
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import voice.core.data.repo.ListeningStatsRepo
import voice.core.logging.api.Logger
import kotlin.time.Duration.Companion.seconds

@Inject
class PlaybackStatsTracker(
  private val statsRepo: ListeningStatsRepo,
  private val scope: CoroutineScope,
) {

  private var trackingJob: Job? = null

  fun attachTo(player: Player) {
    player.addListener(
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (isPlaying) {
            startTracking()
          } else {
            stopTracking()
          }
        }
      },
    )
  }

  private fun startTracking() {
    if (trackingJob?.isActive == true) return
    
    Logger.d("Starting listening time tracking")
    trackingJob = scope.launch {
      while (isActive) {
        delay(TRACKING_INTERVAL.inWholeMilliseconds)
        statsRepo.addListeningTime(TRACKING_INTERVAL.inWholeSeconds.toInt())
        Logger.v("Added ${TRACKING_INTERVAL.inWholeSeconds}s to listening stats")
      }
    }
  }

  private fun stopTracking() {
    Logger.d("Stopping listening time tracking")
    trackingJob?.cancel()
    trackingJob = null
  }

  companion object {
    private val TRACKING_INTERVAL = 30.seconds
  }
}
