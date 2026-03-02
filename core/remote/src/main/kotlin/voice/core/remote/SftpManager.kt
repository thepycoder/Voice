@file:Suppress("DEPRECATION")

package voice.core.remote

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import voice.core.logging.api.Logger
import java.io.File

@Inject
public class SftpManager(
  private val sftpSettings: SftpSettingsProvider,
) {

  private fun createSshClient(): SSHClient {
    // Ensure security providers are set up before creating SSHClient,
    // as DefaultConfig checks for BouncyCastle during construction.
    SshSecurityProviderInitializer.setupBouncyCastle()
    return SSHClient(DefaultConfig()).apply {
      addHostKeyVerifier(PromiscuousVerifier())
    }
  }

  public suspend fun testConnection() {
    withContext(Dispatchers.IO) {
      val settings = sftpSettings.get()

      if (settings.host.isBlank() || settings.user.isBlank()) {
        throw IllegalStateException("SFTP settings not configured")
      }

      val ssh = createSshClient()

      try {
        ssh.connect(settings.host, settings.port)
        ssh.authPassword(settings.user, settings.password)
        val sftp = ssh.newSFTPClient()
        sftp.close()
        Logger.i("SFTP connection test successful")
      } finally {
        try {
          ssh.disconnect()
        } catch (e: Exception) {
          Logger.w(e, "Error disconnecting SSH")
        }
      }
    }
  }

  public suspend fun listDirectories(remotePath: String): List<String> {
    return withContext(Dispatchers.IO) {
      try {
        withSftpClient { sftp ->
          sftp.ls(remotePath)
            .filter { it.isDirectory }
            .map { it.name }
            .filter { it != "." && it != ".." }
        }
      } catch (e: Exception) {
        Logger.e(e, "Failed to list directories in $remotePath")
        throw RuntimeException("Failed to access directory '$remotePath'. Original error: ${e.message}", e)
      }
    }
  }

  public suspend fun listFiles(remotePath: String): List<SftpFile> {
    return withContext(Dispatchers.IO) {
      try {
        withSftpClient { sftp ->
          sftp.ls(remotePath)
            .filter { it.isRegularFile }
            .map { SftpFile(it.name, it.attributes.size) }
        }
      } catch (e: Exception) {
        Logger.e(e, "Failed to list files in $remotePath")
        throw RuntimeException("Failed to list files in '$remotePath'. Original error: ${e.message}", e)
      }
    }
  }

  public suspend fun downloadFile(
    remotePath: String,
    localFile: File,
    onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
  ) {
    withContext(Dispatchers.IO) {
      try {
        withSftpClient { sftp ->
          localFile.parentFile?.mkdirs()
          val attributes = sftp.stat(remotePath)
          val fileSize = attributes.size
          val remoteFile = sftp.open(remotePath)
          try {
            localFile.outputStream().use { output ->
              val buffer = ByteArray(32 * 1024)
              var totalRead = 0L
              var bytesRead: Int
              var offset = 0L

              while (remoteFile.read(offset, buffer, 0, buffer.size).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                offset += bytesRead
                onProgress?.invoke(totalRead, fileSize)
              }
            }
            Logger.i("Successfully downloaded $remotePath to ${localFile.absolutePath}")
          } finally {
            remoteFile.close()
          }
        }
      } catch (e: java.util.concurrent.CancellationException) {
        throw e
      } catch (e: Exception) {
        Logger.e(e, "Failed to download $remotePath")
        throw RuntimeException("Failed to download file '$remotePath'. Original error: ${e.message}", e)
      }
    }
  }

  private suspend fun <T> withSftpClient(block: (SFTPClient) -> T): T {
    val settings = sftpSettings.get()

    if (settings.host.isBlank() || settings.user.isBlank()) {
      throw IllegalStateException("SFTP settings not configured")
    }

    val ssh = createSshClient()
    try {
      ssh.connect(settings.host, settings.port)
      ssh.authPassword(settings.user, settings.password)
      val sftp = ssh.newSFTPClient()
      try {
        return block(sftp)
      } finally {
        sftp.close()
      }
    } finally {
      ssh.disconnect()
    }
  }
}
