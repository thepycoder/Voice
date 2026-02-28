package voice.features.bookOverview.bottomSheet

import android.app.Application
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
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
import voice.core.data.repo.BookContentRepo
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BottomSheetViewModelTest {

  private val application = mockk<Application> {
    every { filesDir } returns File("/tmp")
  }
  private val viewModels = emptySet<BottomSheetItemViewModel>()
  private val remoteCatalogRepo = mockk<RemoteCatalogRepo>()
  private val contentRepo = mockk<BookContentRepo>()
  private val downloadManager = mockk<DownloadManager>()

  private val viewModel by lazy {
    BottomSheetViewModel(
      application = application,
      viewModels = viewModels,
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

    viewModel.bookSelected(BookId("remote://remote1"))

    viewModel.state.value.items shouldContain BottomSheetItem.Download
    viewModel.state.value.items shouldNotContain BottomSheetItem.RemoveDownload
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

    viewModel.bookSelected(bookId)

    viewModel.state.value.items shouldContain BottomSheetItem.RemoveDownload
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

    viewModel.bookSelected(bookId)
    viewModel.onItemClick(BottomSheetItem.RemoveDownload)

    coVerify { downloadManager.removeBook(remoteBook).let { } }
  }
}
