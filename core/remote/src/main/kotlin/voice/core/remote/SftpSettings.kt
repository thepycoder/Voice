package voice.core.remote

import kotlinx.serialization.Serializable

@Serializable
public data class SftpSettings(
  val host: String = "192.168.0.201",
  val port: Int = 22,
  val user: String = "victor",
  val password: String = "",
  val remotePath: String = "/victor_backup/abtest",
)
