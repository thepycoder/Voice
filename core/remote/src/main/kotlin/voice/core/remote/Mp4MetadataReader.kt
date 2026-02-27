package voice.core.remote

import java.nio.ByteBuffer
import java.nio.ByteOrder

public class Mp4MetadataReader {

  public data class Metadata(
    val title: String? = null,
    val author: String? = null,
    val durationMs: Long = 0,
  )

  public fun parseMetadata(data: ByteArray): Metadata {
    val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
    var title: String? = null
    var author: String? = null
    var durationMs: Long = 0

    val moovAtom = findAtom(buffer, "moov") ?: return Metadata()

    moovAtom.rewind()
    val mvhdAtom = findAtom(moovAtom, "mvhd")
    if (mvhdAtom != null) {
      durationMs = parseMvhd(mvhdAtom)
    }

    moovAtom.rewind()
    val udtaAtom = findAtom(moovAtom, "udta")
    if (udtaAtom != null) {
      val metaAtom = findAtom(udtaAtom, "meta")
      if (metaAtom != null) {
        metaAtom.position(metaAtom.position() + 4)
        val ilstAtom = findAtom(metaAtom, "ilst")
        if (ilstAtom != null) {
          title = findStringValue(ilstAtom, "©nam")
          ilstAtom.rewind()
          author = findStringValue(ilstAtom, "©ART")
        }
      }
    }

    return Metadata(title, author, durationMs)
  }

  private fun findAtom(
    buffer: ByteBuffer,
    atomType: String,
  ): ByteBuffer? {
    val startPos = buffer.position()

    while (buffer.remaining() >= 8) {
      val size = buffer.int
      val type = String(ByteArray(4) { buffer.get() }, Charsets.ISO_8859_1)

      if (size < 8) break

      val contentSize = size - 8

      if (type == atomType) {
        val atomData = ByteArray(contentSize)
        buffer.get(atomData)
        return ByteBuffer.wrap(atomData).order(ByteOrder.BIG_ENDIAN)
      } else {
        val newPos = buffer.position() + contentSize
        if (newPos > buffer.limit()) break
        buffer.position(newPos)
      }
    }

    buffer.position(startPos)
    return null
  }

  private fun parseMvhd(buffer: ByteBuffer): Long {
    val version = buffer.get()

    buffer.position(buffer.position() + 3)

    return if (version.toInt() == 1) {
      buffer.position(buffer.position() + 16)
      val timeScale = buffer.int.toLong()
      val duration = buffer.long
      (duration * 1000 / timeScale)
    } else {
      buffer.position(buffer.position() + 8)
      val timeScale = buffer.int.toLong()
      val duration = buffer.int.toLong()
      (duration * 1000 / timeScale)
    }
  }

  private fun findStringValue(
    buffer: ByteBuffer,
    key: String,
  ): String? {
    val startPos = buffer.position()

    while (buffer.remaining() >= 8) {
      val size = buffer.int
      val type = String(ByteArray(4) { buffer.get() }, Charsets.ISO_8859_1)

      if (size < 8) break

      val contentSize = size - 8

      if (type == key) {
        val dataAtom = ByteArray(contentSize)
        buffer.get(dataAtom)
        val dataBuffer = ByteBuffer.wrap(dataAtom).order(ByteOrder.BIG_ENDIAN)

        if (dataBuffer.remaining() >= 8) {
          val dataSize = dataBuffer.int
          val dataType = String(ByteArray(4) { dataBuffer.get() }, Charsets.ISO_8859_1)

          if (dataType == "data" && dataBuffer.remaining() >= 8) {
            dataBuffer.position(dataBuffer.position() + 8)
            val textData = ByteArray(dataBuffer.remaining())
            dataBuffer.get(textData)
            return String(textData, Charsets.UTF_8)
          }
        }
        break
      } else {
        val newPos = buffer.position() + contentSize
        if (newPos > buffer.limit()) break
        buffer.position(newPos)
      }
    }

    buffer.position(startPos)
    return null
  }

  public companion object {
    public const val DEFAULT_READ_SIZE: Int = 512 * 1024
  }
}
