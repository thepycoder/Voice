package voice.core.remote

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import voice.core.data.store.SftpSettingsStore

@Inject
@ContributesBinding(AppScope::class)
public class SftpSettingsProviderImpl(
  @SftpSettingsStore
  private val dataStore: DataStore<SftpSettings>,
) : SftpSettingsProvider {

  override suspend fun get(): SftpSettings = dataStore.data.first()

  override fun flow(): Flow<SftpSettings> = dataStore.data

  override suspend fun update(block: (SftpSettings) -> SftpSettings) {
    dataStore.updateData(block)
  }
}
