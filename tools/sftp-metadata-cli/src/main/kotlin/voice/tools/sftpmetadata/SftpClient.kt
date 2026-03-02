package voice.tools.sftpmetadata

import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

public class SftpClient(
  private val host: String,
  private val port: Int,
  private val user: String,
  private val password: String,
  private val log: VerboseLogger,
) {

  public data class FileInfo(val size: Long)

  init {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(BouncyCastleProvider())
    }
  }

  public fun <T> withSftp(block: (SFTPClient) -> T): T {
    log.sftp("Connecting to $host:$port as $user...")
    val ssh = SSHClient(DefaultConfig()).apply {
      addHostKeyVerifier(PromiscuousVerifier())
    }
    try {
      ssh.connect(host, port)
      ssh.authPassword(user, password)
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

  public fun stat(sftp: SFTPClient, remotePath: String): FileInfo? {
    return try {
      val attrs = sftp.stat(remotePath)
      val size = attrs.size
      if (size < 0) {
        log.error("Invalid file size or not a regular file: $remotePath")
        null
      } else {
        FileInfo(size = size)
      }
    } catch (e: Exception) {
      log.error("File not found or not accessible: $remotePath", e)
      null
    }
  }

  public fun partialRead(sftp: SFTPClient, remotePath: String, offset: Long, length: Int): ByteArray {
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
      return result.copyOf(totalRead)
    } finally {
      remoteFile.close()
    }
  }

}
