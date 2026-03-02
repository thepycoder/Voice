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

  private val MOOV_TYPE = "moov".toByteArray(Charsets.ISO_8859_1)

  /**
   * Scans the tail of the file for the moov box. [tailData] should be the last
   * N bytes of the file (e.g. [MAX_MOOV_READ]). [tailStart] is the file offset
   * at which [tailData] begins. Returns the offset and size of the moov box if found.
   * If the tail does not start at a box boundary (e.g. we're inside mdat), searches
   * for the "moov" type signature and infers the box from the size field before it.
   */
  public fun findMoovInTail(tailData: ByteArray, fileSize: Long): MoovLocation? {
    val tailStart = fileSize - tailData.size
    val buffer = ByteBuffer.wrap(tailData).order(ByteOrder.BIG_ENDIAN)
    var fileOffset = tailStart
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
    return findMoovBySignature(tailData, tailStart)
  }

  /**
   * Searches for the "moov" box type in [tailData] when the tail does not start
   * at a box boundary (e.g. we're inside mdat). Uses the last occurrence so we
   * find the top-level moov.
   */
  private fun findMoovBySignature(tailData: ByteArray, tailStart: Long): MoovLocation? {
    var lastMatch = -1
    var i = 0
    while (i <= tailData.size - 4) {
      if (tailData[i] == MOOV_TYPE[0] &&
        tailData[i + 1] == MOOV_TYPE[1] &&
        tailData[i + 2] == MOOV_TYPE[2] &&
        tailData[i + 3] == MOOV_TYPE[3]
      ) {
        lastMatch = i
      }
      i++
    }
    if (lastMatch < 4) return null
    val sizeBuf = ByteBuffer.wrap(tailData, lastMatch - 4, 4).order(ByteOrder.BIG_ENDIAN)
    val size = sizeBuf.int.toLong() and 0xFFFF_FFFFL
    if (size < 8 || size > MAX_MOOV_READ + 8L) return null
    return MoovLocation(
      offset = tailStart + lastMatch + 4,
      size = size - 8,
    )
  }
}
