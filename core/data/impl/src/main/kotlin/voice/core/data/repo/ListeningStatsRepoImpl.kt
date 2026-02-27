package voice.core.data.repo

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import voice.core.data.ListeningStats
import voice.core.data.store.ListeningStatsStore
import java.time.LocalDate

@Inject
@ContributesBinding(AppScope::class)
public class ListeningStatsRepoImpl(
  @ListeningStatsStore
  private val dataStore: DataStore<ListeningStats>,
) : ListeningStatsRepo {

  override fun flow(): Flow<ListeningStats> = dataStore.data

  override suspend fun addListeningTime(
    seconds: Int,
    date: LocalDate,
  ) {
    dataStore.updateData { stats ->
      stats.addListeningTime(seconds, date)
    }
  }
}
