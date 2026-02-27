package voice.core.data.folders

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.FileBasedDocumentFile
import voice.core.remote.RemoteDownloadsRootProvider
import voice.core.remote.RemotePaths
import java.io.File

@Inject
@ContributesBinding(AppScope::class)
public class RemoteDownloadsRootProviderImpl(
  private val application: Application,
) : RemoteDownloadsRootProvider {

  override fun get(): CachedDocumentFile? {
    val dir = File(application.filesDir, RemotePaths.DOWNLOADS_DIR)
    return if (dir.exists() && dir.isDirectory) {
      FileBasedDocumentFile(dir)
    } else {
      null
    }
  }
}
