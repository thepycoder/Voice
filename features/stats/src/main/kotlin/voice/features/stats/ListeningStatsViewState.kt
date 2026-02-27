package voice.features.stats

import java.time.LocalDate

data class ListeningStatsViewState(
  val todaySeconds: Int = 0,
  val monthSeconds: Int = 0,
  val yearSeconds: Int = 0,
  val heatmapData: List<DayData> = emptyList(),
)

data class DayData(
  val date: LocalDate,
  val seconds: Int,
  val intensity: Intensity,
) {
  enum class Intensity {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH,
  }
}
