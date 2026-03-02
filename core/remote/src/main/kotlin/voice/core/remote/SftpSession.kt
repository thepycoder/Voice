package voice.core.remote

import java.io.File
import net.schmizz.sshj.sftp.SFTPClient

/**
 * A persistent SFTP connection for batch operations. Reuse a single connection
 * instead of reconnecting for each operation.
 */
public interface SftpSession {

  public fun listDirectories(remotePath: String): List<String>

  public fun listFiles(remotePath: String): List<SftpFile>

  public fun partialRead(
    remotePath: String,
    offset: Long,
    length: Int,
  ): ByteArray

  public fun downloadFile(
    remotePath: String,
    localFile: File,
    onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
  )
}

internal class SftpSessionImpl(
  private val sftp: SFTPClient,
) : SftpSession {

  override fun listDirectories(remotePath: String): List<String> =
    sftp.ls(remotePath)
      .filter { it.isDirectory }
      .map { it.name }
      .filter { it != "." && it != ".." }

  override fun listFiles(remotePath: String): List<SftpFile> =
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

  override fun partialRead(
    remotePath: String,
    offset: Long,
    length: Int,
  ): ByteArray {
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

  override fun downloadFile(
    remotePath: String,
    localFile: File,
    onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)?,
  ) {
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
    } finally {
      remoteFile.close()
    }
  }
}
