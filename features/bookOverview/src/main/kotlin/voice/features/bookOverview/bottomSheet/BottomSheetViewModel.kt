package voice.features.bookOverview.bottomSheet

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.repo.BookContentRepo
import voice.core.remote.DownloadManager
import voice.core.remote.RemoteBook
import voice.core.remote.RemoteCatalogRepo
import voice.core.remote.RemotePaths
import voice.features.bookOverview.di.BookOverviewScope
import java.io.File

@BookOverviewScope
@Inject
class BottomSheetViewModel(
  private val application: Application,
  private val viewModels: Set<@JvmSuppressWildcards BottomSheetItemViewModel>,
  private val remoteCatalogRepo: RemoteCatalogRepo,
  private val contentRepo: BookContentRepo,
  private val downloadManager: DownloadManager,
) {

  private val scope = MainScope()

  private val _state: MutableState<EditBookBottomSheetState> = mutableStateOf(EditBookBottomSheetState(emptyList()))
  internal val state: State<EditBookBottomSheetState> get() = _state
  var bookId: BookId? = null
    private set

  private var selectedRemoteBook: RemoteBook? = null

  internal fun bookSelected(bookId: BookId) {
    this.bookId = bookId
    if (bookId.value.startsWith("remote://")) {
      val remoteId = bookId.value.removePrefix("remote://")
      scope.launch {
        val books = remoteCatalogRepo.flow().first()
        selectedRemoteBook = books.find { it.id == remoteId }
        updateStateForRemoteBook()
      }
      return
    }

    scope.launch {
      val contentList = contentRepo.flow().first()
      val content = contentList.find { it.id == bookId }
      val downloadsPath = File(application.filesDir, RemotePaths.DOWNLOADS_DIR).absolutePath

      val uri = content?.id?.toUri()
      val path = uri?.path
      val remoteIdFromPath = if (path != null && path.startsWith(downloadsPath)) {
        path.substringAfter("$downloadsPath/").substringBefore("/")
      } else null

      val remoteId = content?.remoteBookId ?: remoteIdFromPath

      if (remoteId != null) {
        val books = remoteCatalogRepo.flow().first()
        selectedRemoteBook = books.find { it.id == remoteId }
      } else {
        selectedRemoteBook = null
      }

      val items = viewModels.flatMap { it.items(bookId) }.toMutableSet()
      if (selectedRemoteBook != null) {
        items.add(BottomSheetItem.RemoveDownload)
      }

      _state.value = EditBookBottomSheetState(items.toList().sorted())
    }
  }

  private suspend fun updateStateForRemoteBook() {
    val book = selectedRemoteBook ?: return
    val contentList = contentRepo.flow().first()
    val downloadsPath = File(application.filesDir, RemotePaths.DOWNLOADS_DIR).absolutePath
    val isDownloaded = contentList.any { content ->
      if (content.remoteBookId == book.id) return@any true
      val path = content.id.toUri().path ?: return@any false
      path.startsWith(downloadsPath) && path.contains("/${book.id}/")
    }

    val items = if (isDownloaded) {
      listOf(BottomSheetItem.RemoveDownload)
    } else {
      listOf(BottomSheetItem.Download)
    }
    _state.value = EditBookBottomSheetState(items)
  }

  internal fun onItemClick(item: BottomSheetItem) {
    val bookId = bookId ?: return

    if (item == BottomSheetItem.Download || item == BottomSheetItem.RemoveDownload) {
      val remoteBook = selectedRemoteBook ?: return
      scope.launch {
        if (item == BottomSheetItem.Download) {
          downloadManager.downloadBook(remoteBook).let { }
        } else {
          downloadManager.removeBook(remoteBook).let { }
        }
      }
      return
    }

    scope.launch {
      viewModels.forEach {
        it.onItemClick(bookId, item)
      }
    }
  }
}
