package voice.core.data.repo

import android.app.Application
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import voice.core.data.store.RemoteCatalogStore
import voice.core.remote.RemoteBook
import voice.core.remote.RemoteCatalogRepo
import voice.core.remote.RemoteCatalogStorage
import java.io.File

@Inject
@ContributesBinding(AppScope::class)
public class RemoteCatalogRepoImpl(
  private val application: Application,
  @RemoteCatalogStore
  private val dataStore: DataStore<RemoteCatalogStorage>,
) : RemoteCatalogRepo {

  override fun flow(): Flow<List<RemoteBook>> =
    dataStore.data.map { it.books }

  override suspend fun get(id: String): RemoteBook? =
    dataStore.data.first().books.find { it.id == id }

  override suspend fun setBooks(books: List<RemoteBook>) {
    dataStore.updateData { RemoteCatalogStorage(books = books) }
  }

  override suspend fun isDownloaded(remoteBookId: String): Boolean {
    val dir = File(application.filesDir, voice.core.remote.RemotePaths.DOWNLOADS_DIR).resolve(remoteBookId)
    return dir.exists() && dir.isDirectory &&
      (dir.listFiles()?.any { it.name.endsWith(".m4b", ignoreCase = true) } == true)
  }
}
