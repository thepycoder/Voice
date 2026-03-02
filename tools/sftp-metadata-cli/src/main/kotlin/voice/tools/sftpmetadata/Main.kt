package voice.tools.sftpmetadata

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import voice.core.mp4metadata.Mp4MetadataExtractor
import voice.core.mp4metadata.Mp4MetadataReader
import java.io.File
import java.io.RandomAccessFile

fun main(args: Array<String>) = SftpMetadataCli().main(args)

class SftpMetadataCli : CliktCommand(
  name = "sftp-metadata-cli",
) {

  private val localPath by option("--local", help = "Path to a local .m4b file")

  private val host by option("--host", help = "SFTP host")
  private val portStr by option("--port", help = "SFTP port").default("22")
  private val port: Int get() = portStr.toIntOrNull() ?: 22
  private val user by option("--user", help = "SFTP user")
  private val password by option("--password", help = "SFTP password (or set SFTP_PASSWORD)")
  private val remotePath by option("--path", help = "Remote path to .m4b file")
  private val verbose by option("-v", "--verbose", help = "Verbose debug output").flag(default = true)

  override fun run() {
    val log = VerboseLogger(verbose = verbose)

    val local = localPath
    if (local != null) {
      val f = File(local)
      if (!f.exists()) {
        log.error("Local file not found: $local")
        throw RuntimeException("File not found: $local")
      }
      if (!f.isFile) {
        log.error("Not a file: $local")
        throw RuntimeException("Not a file: $local")
      }
    }

    val useLocal = local != null
    val useRemote = host != null && user != null && remotePath != null

    when {
      useLocal && useRemote -> {
        log.error("Use either --local or (--host + --user + --path), not both")
        throw RuntimeException("Invalid arguments")
      }
      useLocal -> runLocal(local, log)
      useRemote -> {
        val pass = password ?: System.getenv("SFTP_PASSWORD")
          ?: System.console()?.readPassword("Password: ")?.let { String(it) }
          ?: run {
            log.error("No password: set SFTP_PASSWORD or pass --password")
            throw RuntimeException("Missing SFTP password")
          }
        runRemote(host!!, port, user!!, pass, remotePath!!, log)
      }
      else -> {
        log.error("Specify either --local PATH or --host, --user, and --path for SFTP")
        throw RuntimeException("Missing arguments")
      }
    }
  }

  private fun runLocal(path: String, log: VerboseLogger) {
    val file = File(path)
    log.sftp("Reading local file: ${file.absolutePath} (${file.length()} bytes)")
    RandomAccessFile(file, "r").use { raf ->
      val fileSize = raf.length()
      if (fileSize == 0L) {
        log.error("File is empty: $path")
        throw RuntimeException("Empty file")
      }
      val metadata = Mp4MetadataExtractor.extractMetadata(fileSize) { offset, length ->
        raf.seek(offset)
        ByteArray(length).also { raf.readFully(it) }
      }
      printResult(metadata)
    }
  }

  private fun runRemote(
    host: String,
    port: Int,
    user: String,
    password: String,
    remotePath: String,
    log: VerboseLogger,
  ) {
    val client = SftpClient(host, port, user, password, log)
    client.withSftp { sftp ->
      val info = client.stat(sftp, remotePath) ?: run {
        log.error("Cannot stat remote file: $remotePath")
        throw RuntimeException("File not found or not accessible")
      }
      val fileSize = info.size
      log.sftp("Remote file size: $fileSize bytes")

      val metadata = Mp4MetadataExtractor.extractMetadata(fileSize) { offset, length ->
        client.partialRead(sftp, remotePath, offset, length)
      }
      printResult(metadata)
    }
  }

  private fun printResult(metadata: voice.core.mp4metadata.Mp4MetadataReader.Metadata) {
    val out = System.out
    out.println("title: ${metadata.title ?: ""}")
    out.println("author: ${metadata.author ?: ""}")
    out.println("duration_ms: ${metadata.durationMs}")
    out.println("duration_human: ${formatDuration(metadata.durationMs)}")
  }

  private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
      "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
      "%d:%02d".format(minutes, seconds)
    }
  }
}
