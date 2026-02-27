package voice.core.remote

import kotlinx.coroutines.flow.Flow

public interface SftpSettingsProvider {
  public suspend fun get(): SftpSettings

  public fun flow(): Flow<SftpSettings>

  public suspend fun update(block: (SftpSettings) -> SftpSettings)
}
