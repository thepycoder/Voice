package voice.tools.sftpmetadata

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import voice.core.mp4metadata.MoovScanner
import voice.core.mp4metadata.Mp4MetadataReader
import java.io.File

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
    val data = file.readBytes()
    if (data.isEmpty()) {
      log.error("File is empty: $path")
      throw RuntimeException("Empty file")
    }
    runMetadataExtraction(
      initialData = data,
      fileSize = file.length(),
      readBlock = { offset, length ->
        val start = offset.toInt()
        val end = (offset + length).toInt().coerceAtMost(data.size)
        data.copyOfRange(start, end)
      },
      log = log,
    )
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
    val info = client.stat(remotePath)
    if (info == null) {
      log.error("Cannot stat remote file: $remotePath")
      throw RuntimeException("File not found or not accessible")
    }
    val fileSize = info.size
    log.sftp("Remote file size: $fileSize bytes")

    val initialSize = MoovScanner.INITIAL_READ_SIZE.toLong().coerceAtMost(fileSize)
    val initialData = client.partialRead(remotePath, 0L, initialSize.toInt())
    if (initialData == null || initialData.isEmpty()) {
      log.error("Failed to read initial $initialSize bytes from $remotePath")
      throw RuntimeException("Partial read failed")
    }
    log.scan("Read ${initialData.size} bytes from start of file")

    runMetadataExtraction(
      initialData = initialData,
      fileSize = fileSize,
      readBlock = { offset, length ->
        client.partialRead(remotePath, offset, length) ?: ByteArray(0)
      },
      log = log,
    )
  }

  private fun runMetadataExtraction(
    initialData: ByteArray,
    fileSize: Long,
    readBlock: (offset: Long, length: Int) -> ByteArray,
    log: VerboseLogger,
  ) {
    var moovOffset: Long
    var moovSize: Long

    val locationInHead = MoovScanner.findMoovInHead(initialData)
    if (locationInHead != null) {
      log.scan("Found moov at start of file (faststart): offset=${locationInHead.offset}, size=${locationInHead.size}")
      moovOffset = locationInHead.offset
      moovSize = locationInHead.size
    } else {
      log.scan("moov not found in first ${initialData.size} bytes, scanning from end of file (size=$fileSize)")
      val tailSize = MoovScanner.INITIAL_READ_SIZE.toLong().coerceAtMost(fileSize)
      val tailStart = (fileSize - tailSize).coerceAtLeast(0L)
      val tailData = readBlock(tailStart, tailSize.toInt())
      if (tailData.isEmpty()) {
        log.error("Failed to read tail of file (offset=$tailStart, length=$tailSize)")
        throw RuntimeException("Cannot read file tail")
      }
      val locationInTail = MoovScanner.findMoovInTail(tailData, fileSize)
      if (locationInTail == null) {
        log.error("moov atom not found in head or tail of file. File may be corrupt or not a valid MP4/M4B.")
        throw RuntimeException("moov not found")
      }
      log.scan("Found moov at end of file: offset=${locationInTail.offset}, size=${locationInTail.size}")
      moovOffset = locationInTail.offset
      moovSize = locationInTail.size
    }

    val readSize = moovSize.toInt().coerceAtMost(MoovScanner.MAX_MOOV_READ)
    val boxStart = moovOffset - 8
    val needLen = 8 + readSize
    val moovData = if (locationInHead != null && boxStart >= 0 && boxStart + needLen <= initialData.size) {
      val start = boxStart.toInt()
      initialData.copyOfRange(start, start + needLen)
    } else {
      readBlock(boxStart, needLen)
    }
    if (moovData.isEmpty()) {
      log.error("Failed to read moov data (offset=${moovOffset - 8}, length=${readSize + 8})")
      throw RuntimeException("Cannot read moov")
    }
    log.parse("Read ${moovData.size} bytes of moov data")

    val reader = Mp4MetadataReader()
    val metadata = reader.parseMetadata(moovData)

    if (metadata.title == null && metadata.author == null && metadata.durationMs == 0L) {
      log.parse("udta atom not found in moov (or meta/ilst empty)")
      log.parse("meta→ilst not found (legacy udta or no iTunes metadata)")
    } else {
      log.ok("title=\"${metadata.title}\", author=\"${metadata.author}\", duration=${metadata.durationMs}ms")
    }

    printResult(metadata)
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
