package voice.core.mp4metadata

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Scans MP4 top-level atoms to find the moov box offset and size.
 * Handles faststart (moov at start) and non-faststart (moov at end).
 */
public object MoovScanner {

  public const val INITIAL_READ_SIZE: Int = 64 * 1024
  public const val MAX_MOOV_READ: Int = 8 * 1024 * 1024

  public data class MoovLocation(
    val offset: Long,
    val size: Long,
  )

  /**
   * Scans [data] for a top-level "moov" atom. [data] should be the first
   * [INITIAL_READ_SIZE] bytes of the file (or the full file if smaller).
   * Returns null if moov was not found in this chunk (caller should then
   * read from end of file).
   */
  public fun findMoovInHead(data: ByteArray): MoovLocation? {
    val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
    var fileOffset = 0L
    while (buffer.remaining() >= 8) {
      val size = buffer.int.toLong() and 0xFFFF_FFFFL
      if (size < 8) break
      val type = String(ByteArray(4) { buffer.get() }, Charsets.ISO_8859_1)
      var atomTotalSize = size
      var skip = size - 8
      if (size == 1L && buffer.remaining() >= 8) {
        atomTotalSize = buffer.long
        skip = atomTotalSize - 8 - 8
      }
      if (type == "moov") {
        return MoovLocation(
          offset = fileOffset + 8,
          size = atomTotalSize - 8,
        )
      }
      val newPos = buffer.position() + skip.toInt().coerceAtLeast(0)
      if (newPos > buffer.limit()) break
      buffer.position(newPos)
      fileOffset += atomTotalSize
    }
    return null
  }

  /**
   * Scans the tail of the file for the last top-level box, which is typically
   * "moov" when the file is not faststart. [tailData] should be the last
   * [INITIAL_READ_SIZE] bytes (or less) of the file. [fileSize] is total file size.
   * Returns the offset and size of the moov box if found.
   */
  public fun findMoovInTail(tailData: ByteArray, fileSize: Long): MoovLocation? {
    val buffer = ByteBuffer.wrap(tailData).order(ByteOrder.BIG_ENDIAN)
    var fileOffset = fileSize - tailData.size
    while (buffer.remaining() >= 8) {
      val size = buffer.int.toLong() and 0xFFFF_FFFFL
      if (size < 8) break
      val type = String(ByteArray(4) { buffer.get() }, Charsets.ISO_8859_1)
      var atomTotalSize = size
      var skip = size - 8
      if (size == 1L && buffer.remaining() >= 8) {
        atomTotalSize = buffer.long
        skip = atomTotalSize - 8 - 8
      }
      if (type == "moov") {
        return MoovLocation(
          offset = fileOffset + 8,
          size = atomTotalSize - 8,
        )
      }
      val newPos = buffer.position() + skip.toInt().coerceAtLeast(0)
      if (newPos > buffer.limit()) break
      buffer.position(newPos)
      fileOffset += atomTotalSize
    }
    return null
  }
}
