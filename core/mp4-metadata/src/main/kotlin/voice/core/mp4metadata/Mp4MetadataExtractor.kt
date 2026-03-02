package voice.core.mp4metadata

public object Mp4MetadataExtractor {

  public fun extractMetadata(
    fileSize: Long,
    readBlock: (offset: Long, length: Int) -> ByteArray,
  ): Mp4MetadataReader.Metadata {
    return try {
      extractMetadataOrThrow(fileSize, readBlock)
    } catch (e: Exception) {
      Mp4MetadataReader.Metadata()
    }
  }

  public fun extractMetadataOrThrow(
    fileSize: Long,
    readBlock: (offset: Long, length: Int) -> ByteArray,
  ): Mp4MetadataReader.Metadata {
    if (fileSize == 0L) return Mp4MetadataReader.Metadata()

    val initialSize = MoovScanner.INITIAL_READ_SIZE.toLong().coerceAtMost(fileSize).toInt()
    val initialData = readBlock(0L, initialSize)
    if (initialData.isEmpty()) return Mp4MetadataReader.Metadata()

    var moovOffset: Long
    var moovSize: Long
    var locationInHead: MoovScanner.MoovLocation? = MoovScanner.findMoovInHead(initialData)

    if (locationInHead != null) {
      moovOffset = locationInHead.offset
      moovSize = locationInHead.size
    } else {
      val tailSize = MoovScanner.MAX_MOOV_READ.toLong().coerceAtMost(fileSize).toInt()
      val tailStart = (fileSize - tailSize).coerceAtLeast(0L)
      val tailData = readBlock(tailStart, tailSize)
      if (tailData.isEmpty()) return Mp4MetadataReader.Metadata()

      val locationInTail = MoovScanner.findMoovInTail(tailData, fileSize)
        ?: return Mp4MetadataReader.Metadata()
      moovOffset = locationInTail.offset
      moovSize = locationInTail.size
      locationInHead = null
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
    if (moovData.isEmpty()) return Mp4MetadataReader.Metadata()

    val reader = Mp4MetadataReader()
    return reader.parseMetadata(moovData)
  }
}
