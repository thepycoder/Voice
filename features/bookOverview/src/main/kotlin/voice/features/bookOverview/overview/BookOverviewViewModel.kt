package voice.features.bookOverview.overview

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.common.comparator.sortedNaturally
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.GridMode
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.BookRepository
import voice.core.data.repo.internals.dao.RecentBookSearchDao
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.GridModeStore
import voice.core.featureflag.FeatureFlag
import voice.core.featureflag.FolderPickerInSettingsFeatureFlagQualifier
import voice.core.playback.PlayerController
import voice.core.playback.playstate.PlayStateManager
import voice.core.scanner.DeviceHasStoragePermissionBug
import voice.core.remote.DownloadManager
import voice.core.remote.DownloadState
import voice.core.remote.LibrarySyncManager
import voice.core.remote.RemoteBook
import voice.core.remote.RemoteCatalogRepo
import voice.core.remote.RemotePaths
import voice.core.remote.SftpSettingsProvider
import voice.core.remote.SyncState
import voice.core.scanner.MediaScanTrigger
import voice.core.search.BookSearch
import voice.core.ui.GridCount
import voice.features.bookOverview.di.BookOverviewScope
import voice.features.bookOverview.search.BookSearchViewState
import voice.navigation.Destination
import voice.navigation.Navigator
import java.io.File

@BookOverviewScope
@Inject
class BookOverviewViewModel(
  private val application: Application,
  private val repo: BookRepository,
  private val mediaScanner: MediaScanTrigger,
  private val playStateManager: PlayStateManager,
  private val playerController: PlayerController,
  @CurrentBookStore
  private val currentBookStoreDataStore: DataStore<BookId?>,
  @GridModeStore
  private val gridModeStore: DataStore<GridMode>,
  private val gridCount: GridCount,
  private val navigator: Navigator,
  private val recentBookSearchDao: RecentBookSearchDao,
  private val search: BookSearch,
  private val contentRepo: BookContentRepo,
  private val deviceHasStoragePermissionBug: DeviceHasStoragePermissionBug,
  @FolderPickerInSettingsFeatureFlagQualifier
  private val folderPickerInSettingsFeatureFlag: FeatureFlag<Boolean>,
  private val remoteCatalogRepo: RemoteCatalogRepo,
  private val librarySyncManager: LibrarySyncManager,
  private val downloadManager: DownloadManager,
  private val sftpSettingsProvider: SftpSettingsProvider,
) {

  private val scope = MainScope()
  private var searchActive by mutableStateOf(false)
  private var query by mutableStateOf("")
  private var isRefreshing by mutableStateOf(false)

  fun attach() {
    scope.launch {
      val settings = sftpSettingsProvider.get()
      if (settings.remotePath.isBlank()) {
        librarySyncManager.clearAll()
      }
      mediaScanner.scan()
    }
  }

  @Composable
  internal fun state(
    unknownAuthor: String,
    unknownDuration: String,
  ): BookOverviewViewState {
    val playState = remember { playStateManager.flow }
      .collectAsState(initial = PlayStateManager.PlayState.Paused).value
    val hasStoragePermissionBug = remember { deviceHasStoragePermissionBug.hasBug }
      .collectAsState().value
    val booksList = remember { repo.flow() }
      .collectAsState(initial = emptyList()).value
    val currentBookId = remember { currentBookStoreDataStore.data }
      .collectAsState(initial = null).value
    val scannerActive = remember { mediaScanner.scannerActive }
      .collectAsState(initial = false).value
    val gridMode = remember { gridModeStore.data }
      .collectAsState(initial = null).value
      ?: return BookOverviewViewState.Loading
    val remoteBooksList = remember { remoteCatalogRepo.flow() }
      .collectAsState(initial = emptyList()).value
    val contentList = remember { contentRepo.flow() }
      .collectAsState(initial = emptyList()).value
    val downloadState = remember { downloadManager.downloadState }
      .collectAsState(initial = DownloadState.Idle).value
    val syncState = remember { librarySyncManager.syncState }
      .collectAsState(initial = SyncState.Idle).value
    val sftpSettings = remember { sftpSettingsProvider.flow() }
      .collectAsState(initial = null).value
    val hasRemoteConfigured = sftpSettings?.remotePath?.isNotBlank() == true

    val downloadsPath = File(application.filesDir, RemotePaths.DOWNLOADS_DIR).absolutePath
    val downloadedRemoteIds = contentList
      .filter { it.isActive }
      .mapNotNull { content -> getEffectiveRemoteId(content, downloadsPath) }
      .toSet()

    val remoteBookViewStates = remoteBooksList
      .filter { it.id !in downloadedRemoteIds }
      .map { remoteBook ->
        remoteBook.toItemViewState(
          coverFile = getRemoteCoverFile(remoteBook),
          downloadState = downloadState,
          unknownAuthor = unknownAuthor,
          unknownDuration = unknownDuration,
        )
      }

    val noBooks = !scannerActive && booksList.isEmpty() && remoteBookViewStates.isEmpty() && !hasRemoteConfigured

    val layoutMode = when (gridMode) {
      GridMode.LIST -> BookOverviewLayoutMode.List
      GridMode.GRID -> BookOverviewLayoutMode.Grid
      GridMode.FOLLOW_DEVICE -> if (gridCount.useGridAsDefault()) {
        BookOverviewLayoutMode.Grid
      } else {
        BookOverviewLayoutMode.List
      }
    }

    val bookSearchViewState = bookSearchViewState(layoutMode)

    return BookOverviewViewState(
      layoutMode = layoutMode,
      books = run {
        val groupedBooks = booksList
          .groupBy { it.category }
          .mapValues { (category, categoryBooks) ->
            categoryBooks
              .sortedWith(category.comparator)
              .map { book ->
                val remoteId = getEffectiveRemoteId(book.content, downloadsPath)
                book.toItemViewState(remoteId)
              }
          }
          .toMutableMap()

        // Add remote books (not downloaded) to NOT_STARTED category
        val notStartedBooks = groupedBooks[BookOverviewCategory.NOT_STARTED].orEmpty()
        groupedBooks[BookOverviewCategory.NOT_STARTED] = notStartedBooks + remoteBookViewStates

        groupedBooks.toSortedMap().toImmutableMap()
      },
      playButtonState = if (playState == PlayStateManager.PlayState.Playing) {
        BookOverviewViewState.PlayButtonState.Playing
      } else {
        BookOverviewViewState.PlayButtonState.Paused
      }.takeIf { currentBookId != null },
      showAddBookHint = if (hasStoragePermissionBug) {
        false
      } else {
        noBooks
      },
      showSearchIcon = booksList.isNotEmpty(),
      isLoading = scannerActive,
      searchActive = searchActive,
      searchViewState = bookSearchViewState,
      showStoragePermissionBugCard = hasStoragePermissionBug,
      showFolderPickerIcon = !folderPickerInSettingsFeatureFlag.get(),
      syncState = syncState,
      showSyncIcon = hasRemoteConfigured,
      isRefreshing = isRefreshing,
    )
  }

  @Composable
  private fun bookSearchViewState(layoutMode: BookOverviewLayoutMode): BookSearchViewState {
    return if (searchActive) {
      val recentBookSearch = remember {
        recentBookSearchDao.recentBookSearches()
      }.collectAsState(initial = emptyList()).value.reversed()
      var searchBooks by remember {
        mutableStateOf(emptyList<BookOverviewItemViewState>())
      }
      LaunchedEffect(query) {
        searchBooks = search.search(query).map { it.toItemViewState() }
      }
      val suggestedAuthors: List<String> by produceState(initialValue = emptyList()) {
        value = contentRepo.all()
          .filter { it.isActive }
          .mapNotNull { it.author }
          .toSet()
          .sortedNaturally()
      }

      val bookSearchViewState = if (query.isNotBlank()) {
        BookSearchViewState.SearchResults(
          query = query,
          books = searchBooks,
          layoutMode = layoutMode,
        )
      } else {
        BookSearchViewState.EmptySearch(
          recentQueries = recentBookSearch,
          suggestedAuthors = suggestedAuthors,
          query = query,
        )
      }
      bookSearchViewState
    } else {
      BookSearchViewState.EmptySearch(
        recentQueries = emptyList(),
        suggestedAuthors = emptyList(),
        query = query,
      )
    }
  }

  fun onSettingsClick() {
    navigator.goTo(Destination.Settings)
  }

  fun onBookClick(id: BookId) {
    // Check if this is a remote book
    if (id.value.startsWith("remote://")) {
      val remoteId = id.value.removePrefix("remote://")
      scope.launch {
        val books = remoteCatalogRepo.flow().first()
        val book = books.find { it.id == remoteId }
        if (book != null && book.audioFileName != null) {
          downloadManager.downloadBook(book).let { }
        }
      }
      return
    }
    navigator.goTo(Destination.Playback(id))
  }

  fun onBookFolderClick() {
    navigator.goTo(Destination.FolderPicker)
  }

  fun onSearchActiveChange(active: Boolean) {
    if (active && !searchActive) {
      query = ""
    }
    this.searchActive = active
  }

  fun onSearchQueryChange(query: String) {
    this.query = query
  }

  fun onSearchBookClick(id: BookId) {
    val query = query.trim()
    if (query.isNotBlank()) {
      scope.launch {
        recentBookSearchDao.add(query)
      }
    }
    searchActive = false
    navigator.goTo(Destination.Playback(id))
  }

  fun playPause() {
    playerController.playPause()
  }

  fun onPermissionBugCardClick() {
    if (Build.VERSION.SDK_INT >= 30) {
      navigator.goTo(
        Destination.Activity(
          Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData("package:com.android.externalstorage".toUri()),
        ),
      )
    }
  }

  fun onSyncStateDismissed() {
    librarySyncManager.clearSyncState()
  }

  fun onSyncClick() {
    scope.launch {
      val settings = sftpSettingsProvider.get()
      if (settings.remotePath.isNotBlank()) {
        librarySyncManager.sync().let { }
      }
    }
  }

  fun onRefresh() {
    scope.launch {
      isRefreshing = true
      val settings = sftpSettingsProvider.get()
      if (settings.remotePath.isNotBlank()) {
        librarySyncManager.sync().let { }
      }
      mediaScanner.scanAndAwait()
      isRefreshing = false
    }
  }

  private fun getRemoteCoverFile(remoteBook: RemoteBook): File? {
    if (remoteBook.coverFileName == null) return null
    val coversDir = File(application.filesDir, RemotePaths.COVERS_DIR)
    val coverFile = File(coversDir, "${remoteBook.id}.jpg")
    return if (coverFile.exists()) coverFile else null
  }

  private fun getEffectiveRemoteId(content: BookContent, downloadsPath: String): String? {
    content.remoteBookId?.let { return it }
    val uri = content.id.toUri()
    val path = uri.path ?: return null
    return if (path.startsWith(downloadsPath)) {
      path.substringAfter("$downloadsPath/").substringBefore("/")
    } else null
  }
}
