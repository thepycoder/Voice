@file:Suppress("DEPRECATION")

package voice.core.remote

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import voice.core.logging.api.Logger
import java.io.File
import java.net.ConnectException
import java.net.SocketException

private const val CONNECT_TIMEOUT_MS = 30_000
private const val SOCKET_TIMEOUT_MS = 60_000
private const val RETRY_DELAY_MS = 500L
private const val MAX_ATTEMPTS = 3

private fun isConnectionError(e: Throwable): Boolean {
  if (e is SocketException || e is ConnectException) return true
  val msg = e.message?.uppercase() ?: return false
  return "ECONNABORTED" in msg || "ECONNRESET" in msg
}

@Inject
public class SftpManager(
  private val sftpSettings: SftpSettingsProvider,
) {

  private fun createSshClient(): SSHClient {
    SshSecurityProviderInitializer.setupBouncyCastle()
    return SSHClient(DefaultConfig()).apply {
      addHostKeyVerifier(PromiscuousVerifier())
      setConnectTimeout(CONNECT_TIMEOUT_MS)
      setTimeout(SOCKET_TIMEOUT_MS)
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
            .map {
              val attrs = it.attributes
              val mtime = try {
                attrs.mtime
              } catch (_: Exception) {
                0L
              }
              SftpFile(it.name, attrs.size, mtime)
            }
        }
      } catch (e: Exception) {
        Logger.e(e, "Failed to list files in $remotePath")
        throw RuntimeException("Failed to list files in '$remotePath'. Original error: ${e.message}", e)
      }
    }
  }

  public suspend fun partialRead(
    remotePath: String,
    offset: Long,
    length: Int,
  ): ByteArray = withContext(Dispatchers.IO) {
    try {
      withSftpClient { sftp ->
        val remoteFile = sftp.open(remotePath)
        try {
          val result = ByteArray(length)
          var totalRead = 0
          var currentOffset = offset
          val chunkSize = 512 * 1024
          while (totalRead < length) {
            val toRead = (length - totalRead).coerceAtMost(chunkSize)
            val buffer = ByteArray(toRead)
            val read = remoteFile.read(currentOffset, buffer, 0, toRead)
            if (read <= 0) break
            buffer.copyInto(result, totalRead, 0, read)
            totalRead += read
            currentOffset += read
          }
          result.copyOf(totalRead)
        } finally {
          remoteFile.close()
        }
      }
    } catch (e: java.util.concurrent.CancellationException) {
      throw e
    } catch (e: Exception) {
      Logger.e(e, "Failed to partial read $remotePath at offset $offset length $length")
      throw RuntimeException("Failed to read file '$remotePath'. Original error: ${e.message}", e)
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
    var lastException: Throwable? = null
    repeat(MAX_ATTEMPTS) { attempt ->
      try {
        return withSftpClientOnce(settings, block)
      } catch (e: java.util.concurrent.CancellationException) {
        throw e
      } catch (e: Throwable) {
        lastException = e
        if (!isConnectionError(e) || attempt == MAX_ATTEMPTS - 1) {
          throw e
        }
        Logger.w(e, "SFTP connection error (attempt ${attempt + 1}/$MAX_ATTEMPTS), retrying…")
        delay(RETRY_DELAY_MS)
      }
    }
    throw lastException!!
  }

  private fun <T> withSftpClientOnce(settings: SftpSettings, block: (SFTPClient) -> T): T {
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

  /**
   * Keeps a single SFTP connection open for the duration of the block.
   * Use this for batch operations (e.g. sync) to avoid connection overhead.
   */
  public suspend fun <T> withSftpSession(block: suspend (SftpSession) -> T): T =
    withContext(Dispatchers.IO) {
      val settings = sftpSettings.get()
      if (settings.host.isBlank() || settings.user.isBlank()) {
        throw IllegalStateException("SFTP settings not configured")
      }
      var lastException: Throwable? = null
      repeat(MAX_ATTEMPTS) { attempt ->
        try {
          val ssh = createSshClient()
          ssh.connect(settings.host, settings.port)
          ssh.authPassword(settings.user, settings.password)
          val sftp = ssh.newSFTPClient()
          try {
            val session = SftpSessionImpl(sftp)
            return@withContext block(session)
          } finally {
            try {
              sftp.close()
            } catch (e: Exception) {
              Logger.w(e, "Error closing SFTP client")
            }
            try {
              ssh.disconnect()
            } catch (e: Exception) {
              Logger.w(e, "Error disconnecting SSH")
            }
          }
        } catch (e: java.util.concurrent.CancellationException) {
          throw e
        } catch (e: Throwable) {
          lastException = e
          if (!isConnectionError(e) || attempt == MAX_ATTEMPTS - 1) {
            throw e
          }
          Logger.w(e, "SFTP connection error (attempt ${attempt + 1}/$MAX_ATTEMPTS), retrying…")
          delay(RETRY_DELAY_MS)
        }
      }
      throw lastException!!
    }
}
