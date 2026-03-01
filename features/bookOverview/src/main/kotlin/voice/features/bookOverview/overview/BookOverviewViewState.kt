package voice.features.bookOverview.overview

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import voice.core.remote.SyncState
import voice.features.bookOverview.search.BookSearchViewState

@Immutable
data class BookOverviewViewState(
  val books: ImmutableMap<BookOverviewCategory, List<BookOverviewItemViewState>>,
  val layoutMode: BookOverviewLayoutMode,
  val playButtonState: PlayButtonState?,
  val showAddBookHint: Boolean,
  val showSearchIcon: Boolean,
  val isLoading: Boolean,
  val searchActive: Boolean,
  val searchViewState: BookSearchViewState,
  val showStoragePermissionBugCard: Boolean,
  val showFolderPickerIcon: Boolean,
  val syncState: SyncState = SyncState.Idle,
  val showSyncIcon: Boolean = false,
  val isRefreshing: Boolean = false,
) {

  companion object {
    val Loading = BookOverviewViewState(
      books = persistentMapOf(),
      layoutMode = BookOverviewLayoutMode.List,
      playButtonState = null,
      showAddBookHint = false,
      showSearchIcon = false,
      isLoading = true,
      searchActive = false,
      searchViewState = BookSearchViewState.EmptySearch(
        suggestedAuthors = emptyList(),
        recentQueries = emptyList(),
        query = "",
      ),
      showStoragePermissionBugCard = false,
      showFolderPickerIcon = true,
      syncState = SyncState.Idle,
      showSyncIcon = false,
      isRefreshing = false,
    )
  }

  enum class PlayButtonState {
    Playing,
    Paused,
  }
}
