package voice.core.remote

import voice.core.documentfile.CachedDocumentFile

public interface RemoteDownloadsRootProvider {
  public fun get(): CachedDocumentFile?
}
