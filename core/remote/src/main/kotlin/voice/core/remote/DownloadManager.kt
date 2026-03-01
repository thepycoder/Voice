package voice.core.remote

import kotlinx.coroutines.flow.StateFlow

public interface DownloadManager {

  public val downloadState: StateFlow<DownloadState>

  public suspend fun downloadBook(book: RemoteBook): Result<Unit>

  public suspend fun removeBook(book: RemoteBook): Result<Unit>

  public fun cancelDownload()
}
