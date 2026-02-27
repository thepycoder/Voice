package voice.features.settings.sftp

data class SftpSettingsViewState(
  val host: String,
  val port: String,
  val user: String,
  val password: String,
  val remotePath: String,
  val testMessage: String?,
  val testInProgress: Boolean,
)
