package voice.core.remote

public sealed class DownloadState {
  public data object Idle : DownloadState()
  public data class Downloading(val remoteBookId: String, val progress: Float) : DownloadState()
  public data class Complete(val remoteBookId: String) : DownloadState()
  public data class Error(val remoteBookId: String, val message: String) : DownloadState()
}
