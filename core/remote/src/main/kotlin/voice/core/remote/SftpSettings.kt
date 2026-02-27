package voice.core.remote

import kotlinx.serialization.Serializable

@Serializable
public data class SftpSettings(
  val host: String = "",
  val port: Int = 22,
  val user: String = "",
  val password: String = "",
  val remotePath: String = "",
)
