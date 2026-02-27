package voice.features.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import voice.core.data.repo.ListeningStatsRepo
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

@Inject
class ListeningStatsViewModel(
  private val statsRepo: ListeningStatsRepo,
) {

  @Composable
  fun viewState(): ListeningStatsViewState {
    val stats by statsRepo.flow().collectAsState(initial = voice.core.data.ListeningStats())
    val today = LocalDate.now()
    val currentYear = today.year
    val currentMonth = today.monthValue

    return ListeningStatsViewState(
      todaySeconds = stats.secondsForDate(today),
      monthSeconds = stats.secondsForMonth(currentYear, currentMonth),
      yearSeconds = stats.secondsForYear(currentYear),
      heatmapData = generateHeatmapData(stats, today),
    )
  }

  private fun generateHeatmapData(
    stats: voice.core.data.ListeningStats,
    today: LocalDate,
  ): List<DayData> {
    val result = mutableListOf<DayData>()
    
    // Get last 365 days
    for (daysAgo in 364 downTo 0) {
      val date = today.minusDays(daysAgo.toLong())
      val seconds = stats.secondsForDate(date)
      result.add(
        DayData(
          date = date,
          seconds = seconds,
          intensity = calculateIntensity(seconds),
        ),
      )
    }
    
    return result
  }

  private fun calculateIntensity(seconds: Int): DayData.Intensity {
    return when {
      seconds == 0 -> DayData.Intensity.NONE
      seconds < 30 * 60 -> DayData.Intensity.LOW // < 30 min
      seconds < 60 * 60 -> DayData.Intensity.MEDIUM // < 1 hour
      seconds < 2 * 60 * 60 -> DayData.Intensity.HIGH // < 2 hours
      else -> DayData.Intensity.VERY_HIGH // >= 2 hours
    }
  }
}
