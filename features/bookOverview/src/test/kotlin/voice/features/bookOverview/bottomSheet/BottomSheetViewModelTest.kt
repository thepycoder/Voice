package voice.features.bookOverview.bottomSheet

import android.app.Application
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.remote.DownloadManager
import voice.core.remote.RemoteBook
import voice.core.remote.RemoteCatalogRepo
import voice.core.remote.RemotePaths
import voice.core.data.repo.BookContentRepo
import voice.features.bookOverview.deleteBook.DeleteBookViewModel
import voice.features.bookOverview.editBookCategory.EditBookCategoryViewModel
import voice.features.bookOverview.editTitle.EditBookTitleViewModel
import voice.features.bookOverview.fileCover.FileCoverViewModel
import voice.features.bookOverview.internetCover.InternetCoverViewModel
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BottomSheetViewModelTest {

  private val application = mockk<Application> {
    every { filesDir } returns File("/tmp")
  }
  private val deleteBookViewModel = mockk<DeleteBookViewModel>()
  private val editBookTitleViewModel = mockk<EditBookTitleViewModel>()
  private val fileCoverViewModel = mockk<FileCoverViewModel>()
  private val editBookCategoryViewModel = mockk<EditBookCategoryViewModel>()
  private val internetCoverViewModel = mockk<InternetCoverViewModel>()
  private val remoteCatalogRepo = mockk<RemoteCatalogRepo>()
  private val contentRepo = mockk<BookContentRepo>()
  private val downloadManager = mockk<DownloadManager>()

  init {
    coEvery { deleteBookViewModel.items(any()) } returns emptyList()
    coEvery { editBookTitleViewModel.items(any()) } returns emptyList()
    coEvery { fileCoverViewModel.items(any()) } returns emptyList()
    coEvery { editBookCategoryViewModel.items(any()) } returns emptyList()
    coEvery { internetCoverViewModel.items(any()) } returns emptyList()
  }

  private val viewModel by lazy {
    BottomSheetViewModel(
      application = application,
      deleteBookViewModel = deleteBookViewModel,
      editBookTitleViewModel = editBookTitleViewModel,
      fileCoverViewModel = fileCoverViewModel,
      editBookCategoryViewModel = editBookCategoryViewModel,
      internetCoverViewModel = internetCoverViewModel,
      remoteCatalogRepo = remoteCatalogRepo,
      contentRepo = contentRepo,
      downloadManager = downloadManager,
    )
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(Dispatchers.Unconfined)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `remote book shows Download option`() = runTest {
    val remoteBook = RemoteBook(
      id = "remote1",
      folder = "folder1",
      title = "Remote Title",
      dateAdded = "2023-01-01",
    )
    every { remoteCatalogRepo.flow() } returns flowOf(listOf(remoteBook))
    every { contentRepo.flow() } returns flowOf(emptyList())

    val state = viewModel.prepareBookSelection(BookId("remote://remote1"))

    state.items shouldContain BottomSheetItem.Download
    state.items shouldNotContain BottomSheetItem.RemoveDownload
  }

  @Test
  fun `downloaded book shows Remove Download option`() = runTest {
    val bookId = BookId("local1")
    val remoteId = "remote1"
    val content = mockk<BookContent> {
      every { id } returns bookId
      every { remoteBookId } returns remoteId
    }
    val remoteBook = RemoteBook(
      id = remoteId,
      folder = "folder1",
      title = "Remote Title",
      dateAdded = "2023-01-01",
    )
    every { contentRepo.flow() } returns flowOf(listOf(content))
    every { remoteCatalogRepo.flow() } returns flowOf(listOf(remoteBook))

    val state = viewModel.prepareBookSelection(bookId)

    state.items shouldContain BottomSheetItem.RemoveDownload
  }

  @Test
  fun `long press on local book shows non-empty bottom sheet menu`() = runTest {
    val bookId = BookId("content://local/book1")
    val content = mockk<BookContent> {
      every { id } returns bookId
      every { remoteBookId } returns null
    }
    every { contentRepo.flow() } returns flowOf(listOf(content))
    every { remoteCatalogRepo.flow() } returns flowOf(emptyList())

    coEvery { deleteBookViewModel.items(bookId) } returns listOf(BottomSheetItem.DeleteBook)
    coEvery { editBookTitleViewModel.items(bookId) } returns listOf(BottomSheetItem.Title)
    coEvery { fileCoverViewModel.items(bookId) } returns listOf(BottomSheetItem.FileCover)
    coEvery { internetCoverViewModel.items(bookId) } returns listOf(BottomSheetItem.InternetCover)
    coEvery { editBookCategoryViewModel.items(bookId) } returns listOf(
      BottomSheetItem.BookCategoryMarkAsCurrent,
    )

    val state = viewModel.prepareBookSelection(bookId)

    state.items.shouldNotBeEmpty()
    state.items shouldContain BottomSheetItem.DeleteBook
    state.items shouldContain BottomSheetItem.Title
    state.items shouldContain BottomSheetItem.FileCover
    state.items shouldContain BottomSheetItem.InternetCover
  }

  @Test
  fun `bottom sheet shows DeleteBook as fallback when no item view model returns items`() = runTest {
    val bookId = BookId("content://local/book1")
    val content = mockk<BookContent> {
      every { id } returns bookId
      every { remoteBookId } returns null
    }
    every { contentRepo.flow() } returns flowOf(listOf(content))
    every { remoteCatalogRepo.flow() } returns flowOf(emptyList())
    coEvery { deleteBookViewModel.items(any()) } returns emptyList()
    coEvery { editBookTitleViewModel.items(any()) } returns emptyList()
    coEvery { fileCoverViewModel.items(any()) } returns emptyList()
    coEvery { editBookCategoryViewModel.items(any()) } returns emptyList()
    coEvery { internetCoverViewModel.items(any()) } returns emptyList()

    val state = viewModel.prepareBookSelection(bookId)

    state.items.size shouldBe 1
    state.items shouldContain BottomSheetItem.DeleteBook
  }

  @Test
  fun `downloaded book from remote shows all standard items plus RemoveDownload`() = runTest {
    val remoteId = "remote1"
    val downloadsPath = "/tmp/${RemotePaths.DOWNLOADS_DIR}"
    val bookId = BookId("$downloadsPath/$remoteId/book.m4b")
    val content = mockk<BookContent> {
      every { id } returns bookId
      every { remoteBookId } returns remoteId
    }
    val remoteBook = RemoteBook(
      id = remoteId,
      folder = "folder1",
      title = "Remote Title",
      dateAdded = "2023-01-01",
    )
    every { contentRepo.flow() } returns flowOf(listOf(content))
    every { remoteCatalogRepo.flow() } returns flowOf(listOf(remoteBook))

    coEvery { deleteBookViewModel.items(bookId) } returns listOf(BottomSheetItem.DeleteBook)
    coEvery { editBookTitleViewModel.items(bookId) } returns listOf(BottomSheetItem.Title)
    coEvery { fileCoverViewModel.items(bookId) } returns listOf(BottomSheetItem.FileCover)
    coEvery { internetCoverViewModel.items(bookId) } returns listOf(BottomSheetItem.InternetCover)
    coEvery { editBookCategoryViewModel.items(bookId) } returns emptyList()

    val state = viewModel.prepareBookSelection(bookId)

    state.items.shouldNotBeEmpty()
    state.items shouldContain BottomSheetItem.DeleteBook
    state.items shouldContain BottomSheetItem.Title
    state.items shouldContain BottomSheetItem.FileCover
    state.items shouldContain BottomSheetItem.InternetCover
    state.items shouldContain BottomSheetItem.RemoveDownload
  }

  @Test
  fun `clicking Remove Download calls downloadManager`() = runTest {
    val bookId = BookId("local1")
    val remoteId = "remote1"
    val content = mockk<BookContent> {
      every { id } returns bookId
      every { remoteBookId } returns remoteId
    }
    val remoteBook = RemoteBook(
      id = remoteId,
      folder = "folder1",
      title = "Remote Title",
      dateAdded = "2023-01-01",
    )
    every { contentRepo.flow() } returns flowOf(listOf(content))
    every { remoteCatalogRepo.flow() } returns flowOf(listOf(remoteBook))
    coEvery { downloadManager.removeBook(any()) } returns Result.success(Unit)

    viewModel.prepareBookSelection(bookId).let { }
    viewModel.onItemClick(bookId, BottomSheetItem.RemoveDownload)

    coVerify { downloadManager.removeBook(remoteBook).let { } }
  }
}
