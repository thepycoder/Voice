package voice.core.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Serializable
public data class ListeningStats(
  val dailySeconds: Map<String, Int> = emptyMap(),
) {

  public fun secondsForDate(date: LocalDate): Int {
    return dailySeconds[date.format(DateTimeFormatter.ISO_LOCAL_DATE)] ?: 0
  }

  public fun secondsForMonth(year: Int, month: Int): Int {
    return dailySeconds.entries
      .filter { entry ->
        val date = LocalDate.parse(entry.key, DateTimeFormatter.ISO_LOCAL_DATE)
        date.year == year && date.monthValue == month
      }
      .sumOf { it.value }
  }

  public fun secondsForYear(year: Int): Int {
    return dailySeconds.entries
      .filter { entry ->
        val date = LocalDate.parse(entry.key, DateTimeFormatter.ISO_LOCAL_DATE)
        date.year == year
      }
      .sumOf { it.value }
  }

  public fun addListeningTime(
    seconds: Int,
    date: LocalDate = LocalDate.now(),
  ): ListeningStats {
    val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val updatedSeconds = dailySeconds.toMutableMap()
    updatedSeconds[dateKey] = (updatedSeconds[dateKey] ?: 0) + seconds
    return copy(dailySeconds = updatedSeconds)
  }
}
