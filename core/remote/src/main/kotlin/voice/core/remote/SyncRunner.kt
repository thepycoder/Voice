package voice.core.remote

/**
 * Starts the remote library sync as a long-running foreground task (e.g. with notification).
 * Use this instead of calling [LibrarySyncManager.sync] directly so sync continues when the
 * app is backgrounded or the screen is off.
 */
public interface SyncRunner {

  public fun startSync()
}
