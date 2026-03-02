package voice.core.remote

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.runBlocking
import voice.core.logging.api.Logger
import voice.core.mp4metadata.Mp4MetadataExtractor
import voice.core.mp4metadata.Mp4MetadataReader

@Inject
public class RemoteMetadataExtractor(
  private val sftpManager: SftpManager,
) {

  public suspend fun extractMetadata(
    remotePath: String,
    fileSize: Long,
  ): Mp4MetadataReader.Metadata {
    return try {
      Mp4MetadataExtractor.extractMetadata(fileSize) { offset, length ->
        runBlocking { sftpManager.partialRead(remotePath, offset, length) }
      }
    } catch (e: Exception) {
      Logger.w(e, "Remote metadata extraction failed for $remotePath")
      Mp4MetadataReader.Metadata()
    }
  }
}
