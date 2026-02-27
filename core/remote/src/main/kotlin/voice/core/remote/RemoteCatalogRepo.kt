package voice.core.remote

import kotlinx.coroutines.flow.Flow

public interface RemoteCatalogRepo {

  public fun flow(): Flow<List<RemoteBook>>

  public suspend fun get(id: String): RemoteBook?

  public suspend fun setBooks(books: List<RemoteBook>)

  public suspend fun isDownloaded(remoteBookId: String): Boolean
}
