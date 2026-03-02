package voice.features.bookOverview.overview

import android.app.Application
import android.text.format.DateUtils
import androidx.datastore.core.DataStore
import androidx.test.core.app.ApplicationProvider
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookId
import voice.core.data.GridMode
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.BookRepository
import voice.core.data.repo.internals.dao.RecentBookSearchDao
import voice.core.featureflag.MemoryFeatureFlag
import voice.core.playback.PlayerController
import voice.core.playback.playstate.PlayStateManager
import voice.core.remote.DownloadManager
import voice.core.remote.DownloadState
import voice.core.remote.LibrarySyncManager
import voice.core.remote.RemoteBook
import voice.core.remote.RemoteCatalogRepo
import voice.core.remote.RemotePaths
import voice.core.remote.SftpSettings
import voice.core.remote.SftpSettingsProvider
import voice.core.remote.SyncState
import voice.core.scanner.DeviceHasStoragePermissionBug
import voice.core.scanner.MediaScanTrigger
import voice.core.search.BookSearch
import voice.core.ui.GridCount
import voice.features.bookOverview.book
import voice.navigation.Navigator
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BookOverviewViewModelTest {

  private lateinit var application: Application
  private val repo = mockk<BookRepository> {
    every { flow() } returns flowOf(emptyList())
  }
  private val mediaScanner = mockk<MediaScanTrigger> {
    every { scannerActive } returns flowOf(false)
  }
  private val playStateManager = mockk<PlayStateManager> {
    every { flow } returns MutableStateFlow(PlayStateManager.PlayState.Paused)
  }
  private val playerController = mockk<PlayerController>()
  private val currentBookStore = MemoryDataStore<BookId?>(null)
  private val gridModeStore = MemoryDataStore<GridMode>(GridMode.LIST)
  private val gridCount = mockk<GridCount> {
    every { useGridAsDefault() } returns false
  }
  private val navigator = mockk<Navigator>()
  private val recentBookSearchDao = mockk<RecentBookSearchDao>()
  private val search = mockk<BookSearch>()
  private val contentRepo = mockk<BookContentRepo> {
    every { flow() } returns flowOf(emptyList())
    coEvery { all() } returns emptyList()
  }
  private val deviceHasStoragePermissionBug = mockk<DeviceHasStoragePermissionBug> {
    every { hasBug } returns MutableStateFlow(false)
  }
  private val folderPickerInSettingsFeatureFlag = MemoryFeatureFlag(false)
  private val remoteCatalogRepo = mockk<RemoteCatalogRepo> {
    every { flow() } returns flowOf(emptyList())
  }
  private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
  private val librarySyncManager = mockk<LibrarySyncManager> {
    every { syncState } returns _syncState
  }
  private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
  private val downloadManager = mockk<DownloadManager> {
    every { downloadState } returns _downloadState
  }
  private val _sftpSettings = MutableStateFlow(SftpSettings())
  private val sftpSettingsProvider = mockk<SftpSettingsProvider> {
    every { flow() } returns _sftpSettings
  }

  private val viewModel by lazy {
    BookOverviewViewModel(
      application = application,
      repo = repo,
      mediaScanner = mediaScanner,
      playStateManager = playStateManager,
      playerController = playerController,
      currentBookStoreDataStore = currentBookStore,
      gridModeStore = gridModeStore,
      gridCount = gridCount,
      navigator = navigator,
      recentBookSearchDao = recentBookSearchDao,
      search = search,
      contentRepo = contentRepo,
      deviceHasStoragePermissionBug = deviceHasStoragePermissionBug,
      folderPickerInSettingsFeatureFlag = folderPickerInSettingsFeatureFlag,
      remoteCatalogRepo = remoteCatalogRepo,
      librarySyncManager = librarySyncManager,
      downloadManager = downloadManager,
      sftpSettingsProvider = sftpSettingsProvider,
    )
  }

  @Before
  fun setUp() {
    application = ApplicationProvider.getApplicationContext()
    Dispatchers.setMain(Dispatchers.Unconfined)
    mockkStatic(DateUtils::class)
    every { DateUtils.formatElapsedTime(any()) } returns "00:00"
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `remote books appear in NOT_STARTED category`() = runTest {
    val remoteBook = RemoteBook(
      id = "remote1",
      folder = "folder1",
      title = "Remote Title",
      author = "Remote Author",
      dateAdded = "2023-01-01",
      error = null,
    )
    every { remoteCatalogRepo.flow() } returns flowOf(listOf(remoteBook))

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.state(unknownAuthor = "Unknown author", unknownDuration = "Unknown duration")
    }.test {
      var state = awaitItem()
      while (state.books.isEmpty() || state.books.values.flatten().isEmpty()) {
        state = awaitItem()
      }

      val allItems = state.books.values.flatten()
      allItems shouldHaveSize 1
      allItems.first().name shouldBe "Remote Title"
      allItems.first().remoteState shouldBe RemoteBookState.NotDownloaded("remote1", 0L, null)
    }
  }

  @Test
  fun `remote book with error shows error in view state`() = runTest {
    val remoteBook = RemoteBook(
      id = "remote1",
      folder = "folder1",
      title = "Remote Title",
      author = null,
      dateAdded = "2023-01-01",
      error = "No audio file found",
    )
    every { remoteCatalogRepo.flow() } returns flowOf(listOf(remoteBook))

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.state(unknownAuthor = "Unknown author", unknownDuration = "Unknown duration")
    }.test {
      var state = awaitItem()
      while (state.books.isEmpty() || state.books.values.flatten().isEmpty()) {
        state = awaitItem()
      }

      val allItems = state.books.values.flatten()
      allItems shouldHaveSize 1
      val notDownloaded = allItems.first().remoteState as? RemoteBookState.NotDownloaded
      notDownloaded shouldNotBe null
      notDownloaded?.error shouldBe "No audio file found"
    }
  }

  @Test
  fun `downloaded remote book is filtered from remote list and appears in local list`() = runTest {
    val remoteId = "remote1"
    val remoteBook = RemoteBook(
      id = remoteId,
      folder = "folder1",
      title = "Remote Title",
      author = "Remote Author",
      dateAdded = "2023-01-01",
      error = null,
    )
    every { remoteCatalogRepo.flow() } returns flowOf(listOf(remoteBook))

    val localBook = book(name = "Remote Title", author = "Remote Author").let {
      it.copy(content = it.content.copy(remoteBookId = remoteId))
    }
    every { repo.flow() } returns flowOf(listOf(localBook))
    every { contentRepo.flow() } returns flowOf(listOf(localBook.content))

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.state(unknownAuthor = "Unknown author", unknownDuration = "Unknown duration")
    }.test {
      var state = awaitItem()
      while (state.books.isEmpty() || state.books.values.flatten().isEmpty()) {
        state = awaitItem()
      }

      val allItems = state.books.values.flatten()
      // This is the key check: only 1 book total, not 2 (duplicate)
      allItems shouldHaveSize 1

      val remoteViewStates = allItems.filter { it.id.value.startsWith("remote://") }
      remoteViewStates shouldHaveSize 0

      val localViewState = allItems.find { it.name == "Remote Title" }
      localViewState shouldNotBe null
      localViewState?.remoteState shouldBe RemoteBookState.Downloaded(remoteId)
    }
  }

  @Test
  fun `local book in remote_downloads folder is recognized as remote even without remoteBookId`() = runTest {
    val remoteId = "remote1"
    val downloadsPath = File(application.filesDir, RemotePaths.DOWNLOADS_DIR).absolutePath
    val remoteBook = RemoteBook(
      id = remoteId,
      folder = "folder1",
      title = "Remote Title",
      author = "Remote Author",
      dateAdded = "2023-01-01",
      error = null,
    )
    every { remoteCatalogRepo.flow() } returns flowOf(listOf(remoteBook))

    val localBook = book(name = "Remote Title", author = "Remote Author").let {
      it.copy(content = it.content.copy(
        id = BookId("$downloadsPath/$remoteId/book.m4b"),
        remoteBookId = null
      ))
    }
    every { repo.flow() } returns flowOf(listOf(localBook))
    every { contentRepo.flow() } returns flowOf(listOf(localBook.content))

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.state(unknownAuthor = "Unknown author", unknownDuration = "Unknown duration")
    }.test {
      var state = awaitItem()
      while (state.books.isEmpty() || state.books.values.flatten().isEmpty()) {
        state = awaitItem()
      }

      val allItems = state.books.values.flatten()
      // This is the key check: only 1 book total, not 2 (duplicate)
      allItems shouldHaveSize 1

      val remoteViewStates = allItems.filter { it.id.value.startsWith("remote://") }
      remoteViewStates shouldHaveSize 0

      val localViewState = allItems.find { it.name == "Remote Title" }
      localViewState shouldNotBe null
      localViewState?.remoteState shouldBe RemoteBookState.Downloaded(remoteId)
    }
  }

  @Test
  fun `download progress is reflected in view state`() = runTest {
    val remoteId = "remote1"
    val remoteBook = RemoteBook(
      id = remoteId,
      folder = "folder1",
      title = "Remote Title",
      author = "Remote Author",
      dateAdded = "2023-01-01",
      error = null,
    )
    every { remoteCatalogRepo.flow() } returns flowOf(listOf(remoteBook))

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.state(unknownAuthor = "Unknown author", unknownDuration = "Unknown duration")
    }.test {
      _downloadState.value = DownloadState.Downloading(remoteId, 0.5f)

      var state = awaitItem()
      while (state.books.values.flatten().find { it.name == "Remote Title" }?.remoteState !is RemoteBookState.Downloading) {
        state = awaitItem()
      }

      val allItems = state.books.values.flatten()
      val book = allItems.find { it.name == "Remote Title" }
      book?.remoteState shouldBe RemoteBookState.Downloading(remoteId, 0.5f)
    }
  }

  @Test
  fun `sync state is reflected in view state`() = runTest {
    every { remoteCatalogRepo.flow() } returns flowOf(emptyList())

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.state(unknownAuthor = "Unknown author", unknownDuration = "Unknown duration")
    }.test {
      skipItems(1)
      awaitItem().syncState shouldBe SyncState.Idle

      _syncState.value = SyncState.Syncing("Processing: folder1")
      val state = awaitItem()
      state.syncState shouldBe SyncState.Syncing("Processing: folder1")
    }
  }
}

private class MemoryDataStore<T : Any?>(initial: T) : DataStore<T> {
  private val value = MutableStateFlow(initial)
  override val data: Flow<T> get() = value
  override suspend fun updateData(transform: suspend (t: T) -> T): T {
    return value.updateAndGet { transform(it) }
  }
}
