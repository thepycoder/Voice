package voice.features.settings.sftp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.remote.SftpManager
import voice.core.remote.SftpSettings
import voice.core.remote.SftpSettingsProvider

@Inject
class SftpSettingsViewModel(
  private val sftpSettingsProvider: SftpSettingsProvider,
  private val sftpManager: SftpManager,
  dispatcherProvider: DispatcherProvider,
) {
  private val scope = MainScope(dispatcherProvider)

  var testMessage by mutableStateOf<String?>(null)
    private set
  var testInProgress by mutableStateOf(false)
    private set

  @Composable
  fun viewState(): SftpSettingsViewState {
    val settings by remember { sftpSettingsProvider.flow() }.collectAsState(initial = SftpSettings())
    return SftpSettingsViewState(
      host = settings.host,
      port = settings.port.toString(),
      user = settings.user,
      password = settings.password,
      remotePath = settings.remotePath,
      testMessage = testMessage,
      testInProgress = testInProgress,
    )
  }

  fun onHostChange(value: String) = scope.launch {
    sftpSettingsProvider.update { it.copy(host = value) }
  }

  fun onPortChange(value: String) = scope.launch {
    val port = value.toIntOrNull() ?: 22
    sftpSettingsProvider.update { it.copy(port = port) }
  }

  fun onUserChange(value: String) = scope.launch {
    sftpSettingsProvider.update { it.copy(user = value) }
  }

  fun onPasswordChange(value: String) = scope.launch {
    sftpSettingsProvider.update { it.copy(password = value) }
  }

  fun onRemotePathChange(value: String) = scope.launch {
    sftpSettingsProvider.update { it.copy(remotePath = value) }
  }

  fun onSave() = scope.launch {
    testMessage = null
  }

  fun onTestConnection() {
    scope.launch {
      testMessage = null
      testInProgress = true
      try {
        sftpManager.testConnection()
        testMessage = "Connection successful"
      } catch (e: Exception) {
        testMessage = e.message ?: "Connection failed"
      } finally {
        testInProgress = false
      }
    }
  }

  fun clearTestMessage() {
    testMessage = null
  }
}
