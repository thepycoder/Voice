package voice.core.remote

public sealed class SyncState {
  public data object Idle : SyncState()
  public data class Syncing(val message: String) : SyncState()
  public data class Success(val newBooks: Int) : SyncState()
  public data class Error(val message: String) : SyncState()
}
