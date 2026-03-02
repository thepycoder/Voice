package voice.features.settings.sftp

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.ui.rememberScoped
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Navigator
import voice.navigation.Origin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@ContributesTo(AppScope::class)
interface SftpSettingsGraph {
  val sftpSettingsViewModel: SftpSettingsViewModel
  val navigator: Navigator
}

@ContributesTo(AppScope::class)
interface SftpSettingsNavEntryProviderModule {

  @Provides
  @IntoSet
  fun sftpSettingsNavEntryProvider(): NavEntryProvider<*> =
    NavEntryProvider<Destination.SftpSettings> { key ->
      NavEntry(key) {
        SftpSettingsScreen(origin = key.origin)
      }
    }
}

@Composable
private fun SftpSettingsScreen(origin: Origin? = null) {
  val graph = rootGraphAs<SftpSettingsGraph>()
  val viewModel = rememberScoped { graph.sftpSettingsViewModel }
  val navigator = graph.navigator
  SftpSettingsScreen(viewModel = viewModel, navigator = navigator, origin = origin)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpSettingsScreen(
  viewModel: SftpSettingsViewModel,
  navigator: Navigator,
  origin: Origin? = null,
) {
  val state = viewModel.viewState()
  var passwordVisible by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Remote library (SFTP)") },
        navigationIcon = {
          IconButton(onClick = { navigator.goBack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
    ) {
      OutlinedTextField(
        value = state.host,
        onValueChange = viewModel::onHostChange,
        label = { Text("Host") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = state.port,
        onValueChange = viewModel::onPortChange,
        label = { Text("Port") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = state.user,
        onValueChange = viewModel::onUserChange,
        label = { Text("Username") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text("Password") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
          IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(
              imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
              contentDescription = if (passwordVisible) "Hide password" else "Show password",
            )
          }
        },
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = state.remotePath,
        onValueChange = viewModel::onRemotePathChange,
        label = { Text("Remote path") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
      Spacer(modifier = Modifier.height(16.dp))
      TextButton(
        onClick = viewModel::onTestConnection,
        enabled = !state.testInProgress,
      ) {
        Text(if (state.testInProgress) "Testing…" else "Test connection")
      }
      state.testMessage?.let { message ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = if (message == "Connection successful") {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.error
          },
        )
      }
      Spacer(modifier = Modifier.height(24.dp))
      TextButton(
        onClick = {
          viewModel.onSave()
          navigator.goBack()
        },
      ) {
        Text(if (origin != null) "Add" else "Save")
      }
    }
  }
}
