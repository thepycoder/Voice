package voice.features.bookOverview.bottomSheet

import android.app.Application
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
import voice.features.bookOverview.deleteBook.DeleteBookViewModel
import voice.features.bookOverview.editBookCategory.EditBookCategoryViewModel
import voice.features.bookOverview.editTitle.EditBookTitleViewModel
import voice.features.bookOverview.fileCover.FileCoverViewModel
import voice.features.bookOverview.internetCover.InternetCoverViewModel
import voice.features.bookOverview.di.BookOverviewScope
import java.io.File

@BookOverviewScope
@Inject
class BottomSheetViewModel(
  private val application: Application,
  private val deleteBookViewModel: DeleteBookViewModel,
  private val editBookTitleViewModel: EditBookTitleViewModel,
  private val fileCoverViewModel: FileCoverViewModel,
  private val editBookCategoryViewModel: EditBookCategoryViewModel,
  private val internetCoverViewModel: InternetCoverViewModel,
  private val remoteCatalogRepo: RemoteCatalogRepo,
  private val contentRepo: BookContentRepo,
  private val downloadManager: DownloadManager,
) {

  private val viewModels: Set<BottomSheetItemViewModel> =
    setOf(
      deleteBookViewModel,
      editBookTitleViewModel,
      fileCoverViewModel,
      editBookCategoryViewModel,
      internetCoverViewModel,
    )

  /** For integration tests: verifies the graph injected all item view models. */
  val itemViewModelsCountForTest: Int
    get() = viewModels.size

  private val scope = MainScope()

  internal suspend fun prepareBookSelection(bookId: BookId): EditBookBottomSheetState {
    if (bookId.value.startsWith("remote://")) {
      return getStateForRemoteBook(bookId)
    }

    val contentList = contentRepo.flow().first()
    val content = contentList.find { it.id == bookId }
    val downloadsPath = File(application.filesDir, RemotePaths.DOWNLOADS_DIR).absolutePath

    val uri = content?.id?.toUri()
    val path = uri?.path
    val remoteIdFromPath = if (path != null && path.startsWith(downloadsPath)) {
      path.substringAfter("$downloadsPath/").substringBefore("/")
    } else null

    val remoteId = content?.remoteBookId ?: remoteIdFromPath
    val hasRemoteBook = remoteId != null && remoteCatalogRepo.flow().first().any { it.id == remoteId }

    val items = viewModels.flatMap { it.items(bookId) }.toMutableSet()
    if (hasRemoteBook || remoteId != null) {
      items.add(BottomSheetItem.RemoveDownload)
    }

    if (items.isEmpty()) {
      items.add(BottomSheetItem.DeleteBook)
      if (hasRemoteBook || remoteId != null) {
        items.add(BottomSheetItem.RemoveDownload)
      }
    }

    return EditBookBottomSheetState(items.toList().sorted())
  }

  private suspend fun getStateForRemoteBook(bookId: BookId): EditBookBottomSheetState {
    val remoteId = bookId.value.removePrefix("remote://")
    val books = remoteCatalogRepo.flow().first()
    val book = books.find { it.id == remoteId }
    val contentList = contentRepo.flow().first()
    val downloadsPath = File(application.filesDir, RemotePaths.DOWNLOADS_DIR).absolutePath

    if (book == null) {
      val downloadDir = File(application.filesDir, RemotePaths.DOWNLOADS_DIR).resolve(remoteId)
      if (downloadDir.exists()) {
        return EditBookBottomSheetState(listOf(BottomSheetItem.RemoveDownload))
      }
      return EditBookBottomSheetState(emptyList())
    }

    val isDownloaded = contentList.any { content ->
      if (!content.isActive) return@any false
      if (content.remoteBookId == book.id) return@any true
      val path = content.id.toUri().path ?: return@any false
      path.startsWith(downloadsPath) && path.contains("/${book.id}/")
    }

    return if (isDownloaded) {
      EditBookBottomSheetState(listOf(BottomSheetItem.RemoveDownload))
    } else {
      EditBookBottomSheetState(listOf(BottomSheetItem.Download))
    }
  }

  internal fun onItemClick(bookId: BookId, item: BottomSheetItem) {
    if (item == BottomSheetItem.Download || item == BottomSheetItem.RemoveDownload) {
      scope.launch {
        handleDownloadAction(bookId, item)
      }
      return
    }

    scope.launch {
      viewModels.forEach {
        it.onItemClick(bookId, item)
      }
    }
  }

  private suspend fun handleDownloadAction(bookId: BookId, item: BottomSheetItem) {
    val downloadsPath = File(application.filesDir, RemotePaths.DOWNLOADS_DIR).absolutePath

    // Find remote book by looking up from bookId
    val remoteBook: RemoteBook? = if (bookId.value.startsWith("remote://")) {
      val remoteId = bookId.value.removePrefix("remote://")
      remoteCatalogRepo.flow().first().find { it.id == remoteId }
    } else {
      // Local book - find its remote ID
      val content = contentRepo.flow().first().find { it.id == bookId }
      val path = content?.id?.toUri()?.path
      val remoteIdFromPath = if (path != null && path.startsWith(downloadsPath)) {
        path.substringAfter("$downloadsPath/").substringBefore("/")
      } else null
      val remoteId = content?.remoteBookId ?: remoteIdFromPath
      remoteId?.let { id -> remoteCatalogRepo.flow().first().find { it.id == id } }
    }

    if (remoteBook != null) {
      if (item == BottomSheetItem.Download) {
        downloadManager.downloadBook(remoteBook).let { }
      } else {
        downloadManager.removeBook(remoteBook).let { }
      }
    } else if (item == BottomSheetItem.RemoveDownload) {
      // No remote book in catalog, but try to delete local files
      val remoteId = if (bookId.value.startsWith("remote://")) {
        bookId.value.removePrefix("remote://")
      } else {
        val content = contentRepo.flow().first().find { it.id == bookId }
        val path = content?.id?.toUri()?.path
        if (path != null && path.startsWith(downloadsPath)) {
          path.substringAfter("$downloadsPath/").substringBefore("/")
        } else {
          content?.remoteBookId
        }
      }
      if (remoteId != null) {
        val downloadDir = File(application.filesDir, RemotePaths.DOWNLOADS_DIR).resolve(remoteId)
        if (downloadDir.exists()) {
          downloadDir.deleteRecursively()
        }
      }
    }
  }
}
