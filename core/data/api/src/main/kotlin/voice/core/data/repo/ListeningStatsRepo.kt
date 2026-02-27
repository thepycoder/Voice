package voice.core.data.repo

import kotlinx.coroutines.flow.Flow
import voice.core.data.ListeningStats
import java.time.LocalDate

public interface ListeningStatsRepo {

  public fun flow(): Flow<ListeningStats>

  public suspend fun addListeningTime(
    seconds: Int,
    date: LocalDate = LocalDate.now(),
  )
}
