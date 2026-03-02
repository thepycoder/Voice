package voice.features.settings.sftp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.remote.LibrarySyncManager
import voice.core.remote.SftpManager
import voice.core.remote.SftpSettings
import voice.core.remote.SftpSettingsProvider
import voice.core.remote.SyncRunner

@Inject
class SftpSettingsViewModel(
  private val sftpSettingsProvider: SftpSettingsProvider,
  private val sftpManager: SftpManager,
  private val librarySyncManager: LibrarySyncManager,
  private val syncRunner: SyncRunner,
  dispatcherProvider: DispatcherProvider,
) {
  private val scope = MainScope(dispatcherProvider)

  private var host by mutableStateOf("")
  private var port by mutableStateOf("")
  private var user by mutableStateOf("")
  private var password by mutableStateOf("")
  private var remotePath by mutableStateOf("")
  private var initialSettings: SftpSettings? = null

  init {
    scope.launch {
      val settings = sftpSettingsProvider.get()
      initialSettings = settings
      host = settings.host
      port = settings.port.toString()
      user = settings.user
      password = settings.password
      remotePath = settings.remotePath
    }
  }

  var testMessage by mutableStateOf<String?>(null)
    private set
  var testInProgress by mutableStateOf(false)
    private set

  @Composable
  fun viewState(): SftpSettingsViewState {
    return SftpSettingsViewState(
      host = host,
      port = port,
      user = user,
      password = password,
      remotePath = remotePath,
      testMessage = testMessage,
      testInProgress = testInProgress,
    )
  }

  fun onHostChange(value: String) {
    host = value
    scope.launch {
      sftpSettingsProvider.update { it.copy(host = value) }
    }
  }

  fun onPortChange(value: String) {
    port = value
    scope.launch {
      val portInt = value.toIntOrNull() ?: 22
      sftpSettingsProvider.update { it.copy(port = portInt) }
    }
  }

  fun onUserChange(value: String) {
    user = value
    scope.launch {
      sftpSettingsProvider.update { it.copy(user = value) }
    }
  }

  fun onPasswordChange(value: String) {
    password = value
    scope.launch {
      sftpSettingsProvider.update { it.copy(password = value) }
    }
  }

  fun onRemotePathChange(value: String) {
    remotePath = value
    scope.launch {
      sftpSettingsProvider.update { it.copy(remotePath = value) }
    }
  }

  fun onSave() {
    scope.launch {
      val current = sftpSettingsProvider.get()
      val hostChanged = current.host != initialSettings?.host
      val remotePathChanged = current.remotePath != initialSettings?.remotePath
      if (hostChanged || remotePathChanged) {
        librarySyncManager.clearAll()
      }
      if (current.remotePath.isNotBlank() && current.host.isNotBlank()) {
        syncRunner.startSync()
      }
      testMessage = null
    }
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
