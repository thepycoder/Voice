# Remote Books Unified Gallery Integration Plan

## Goal

Migrate from the current separate "Remote library" section to a **unified gallery** where remote books appear alongside local books with full metadata (title, author, cover). The only difference is that remote books show download/remove options instead of playback progress.

## Current State (SFTP_integration branch)

### What Exists
- `core/remote` module with SFTP infrastructure (`SftpManager`, `LibrarySyncManager`, etc.)
- `RemoteBook` data class with metadata (title, author, cover filename, duration)
- `RemoteBookItemViewState` displayed in separate `RemoteLibrarySection`
- Covers downloaded to `filesDir/remote_covers/` during sync
- `BookContent.remoteBookId` field links downloaded books to remote catalog
- `DownloadManager` with progress notifications

### Problems
1. Remote books shown in separate section, not in main gallery
2. `RemoteBookItemViewState` is different from `BookOverviewItemViewState`
3. Covers exist on disk but aren't loaded in the remote book view
4. No unified sorting/categorization

## Target State

- Remote books appear in the same grid/list as local books
- Remote books show cover, title, author like local books
- Remote books show "Not downloaded" or download progress instead of playback progress
- Click on remote book → show download option (or play if downloaded)
- Long-press on remote book → show bottom sheet with Download/Remove options
- Downloaded remote books behave exactly like local books

---

## Implementation Tasks

### Task 1: Extend BookOverviewItemViewState

**File:** `features/bookOverview/src/main/kotlin/voice/features/bookOverview/overview/BookOverviewItemViewState.kt`

**Current:**
```kotlin
@Immutable
data class BookOverviewItemViewState(
  val name: String,
  val author: String?,
  val cover: ImmutableFile?,
  val progress: Float,
  val id: BookId,
  val remainingTime: String,
)
```

**Change to:**
```kotlin
@Immutable
data class BookOverviewItemViewState(
  val name: String,
  val author: String?,
  val cover: ImmutableFile?,
  val progress: Float,
  val id: BookId,
  val remainingTime: String,
  val remoteState: RemoteBookState? = null,
)

sealed interface RemoteBookState {
  val remoteBookId: String
  
  data class NotDownloaded(
    override val remoteBookId: String,
    val durationMs: Long,
  ) : RemoteBookState
  
  data class Downloading(
    override val remoteBookId: String,
    val progress: Float,
  ) : RemoteBookState
  
  data class Downloaded(
    override val remoteBookId: String,
  ) : RemoteBookState
}
```

**Also add imports:**
```kotlin
import voice.core.ui.ImmutableFile
```

**Update the extension function in the same file:**
```kotlin
internal fun Book.toItemViewState(remoteBookId: String? = null) = BookOverviewItemViewState(
  name = content.name,
  author = content.author,
  cover = content.cover?.let(::ImmutableFile),
  id = id,
  progress = progress(),
  remainingTime = DateUtils.formatElapsedTime((duration - position) / 1000),
  remoteState = remoteBookId?.let { RemoteBookState.Downloaded(it) },
)
```

---

### Task 2: Create RemoteBook to ViewState Converter

**File:** `features/bookOverview/src/main/kotlin/voice/features/bookOverview/overview/BookOverviewItemViewState.kt`

**Add this function at the bottom of the file:**
```kotlin
internal fun RemoteBook.toItemViewState(
  coverFile: File?,
  downloadState: DownloadState,
): BookOverviewItemViewState {
  val remoteState = when (downloadState) {
    is DownloadState.Downloading -> {
      if (downloadState.remoteBookId == id) {
        RemoteBookState.Downloading(id, downloadState.progress)
      } else {
        RemoteBookState.NotDownloaded(id, durationMs)
      }
    }
    else -> RemoteBookState.NotDownloaded(id, durationMs)
  }
  
  return BookOverviewItemViewState(
    name = title,
    author = author,
    cover = coverFile?.let(::ImmutableFile),
    id = BookId("remote://$id"),
    progress = 0f,
    remainingTime = if (durationMs > 0) {
      DateUtils.formatElapsedTime(durationMs / 1000)
    } else {
      ""
    },
    remoteState = remoteState,
  )
}
```

**Add imports at top:**
```kotlin
import voice.core.remote.RemoteBook
import voice.core.remote.DownloadState
import java.io.File
```

---

### Task 3: Update BookOverviewViewModel to Merge Books

**File:** `features/bookOverview/src/main/kotlin/voice/features/bookOverview/overview/BookOverviewViewModel.kt`

**Step 3a:** Add a helper to get cover file path. Add this private function:

```kotlin
private fun getRemoteCoverFile(remoteBook: RemoteBook): File? {
  if (remoteBook.coverFileName == null) return null
  val coversDir = File(application.filesDir, "remote_covers")
  val coverFile = File(coversDir, "${remoteBook.id}.jpg")
  return if (coverFile.exists()) coverFile else null
}
```

**Note:** You need to inject `Application`. Add to constructor:
```kotlin
private val application: Application,
```

**Step 3b:** Remove `remoteBooks` from the view state return and merge into `books` map.

Find this section in `state()`:
```kotlin
val remoteBookItems = remoteBooksList.map { remoteBook ->
  val isDownloaded = contentList.any { it.remoteBookId == remoteBook.id }
  val localBookId = contentList.find { it.remoteBookId == remoteBook.id }?.id
  val progress = when (downloadState) {
    is DownloadState.Downloading -> if (downloadState.remoteBookId == remoteBook.id) downloadState.progress else null
    else -> null
  }
  RemoteBookItemViewState(
    remoteBook = remoteBook,
    isDownloaded = isDownloaded,
    localBookId = localBookId,
    downloadProgress = progress,
  )
}
```

**Replace with:**
```kotlin
val downloadedRemoteIds = contentList.mapNotNull { it.remoteBookId }.toSet()

val remoteBookViewStates = remoteBooksList
  .filter { it.id !in downloadedRemoteIds }
  .map { remoteBook ->
    remoteBook.toItemViewState(
      coverFile = getRemoteCoverFile(remoteBook),
      downloadState = downloadState,
    )
  }
```

**Step 3c:** Merge remote books into the NOT_STARTED category.

Find where books are grouped:
```kotlin
books = books
  .groupBy { it.category }
  .mapValues { (category, books) ->
    books
      .sortedWith(category.comparator)
      .map { book ->
        val remoteId = book.content.remoteBookId
        book.toItemViewState(remoteId)
      }
  }
  .toSortedMap()
  .toImmutableMap(),
```

**Replace with:**
```kotlin
books = run {
  val groupedBooks = books
    .groupBy { it.category }
    .mapValues { (category, categoryBooks) ->
      categoryBooks
        .sortedWith(category.comparator)
        .map { book ->
          val remoteId = book.content.remoteBookId
          book.toItemViewState(remoteId)
        }
    }
    .toMutableMap()
  
  // Add remote books (not downloaded) to NOT_STARTED category
  val notStartedBooks = groupedBooks[BookOverviewCategory.NOT_STARTED].orEmpty()
  groupedBooks[BookOverviewCategory.NOT_STARTED] = notStartedBooks + remoteBookViewStates
  
  groupedBooks.toSortedMap().toImmutableMap()
},
```

**Step 3d:** Remove `remoteBooks` from the return statement.

Change:
```kotlin
remoteBooks = remoteBookItems,
```

To:
```kotlin
remoteBooks = emptyList(),
```

(We keep the field temporarily for compilation, will remove later)

---

### Task 4: Update GridBooks and ListBooks to Remove RemoteLibrarySection

**File:** `features/bookOverview/src/main/kotlin/voice/features/bookOverview/views/GridBooks.kt`

**Remove this item block:**
```kotlin
item(span = { GridItemSpan(maxLineSpan) }, key = "remote_section", contentType = "remote") {
  RemoteLibrarySection(
    remoteBooks = remoteBooks,
    onDownload = onRemoteDownload,
    onRemove = onRemoteRemove,
    onPlay = onRemotePlay,
  )
}
```

**Remove these parameters from the function signature:**
```kotlin
remoteBooks: List<RemoteBookItemViewState> = emptyList(),
onRemoteDownload: (voice.core.remote.RemoteBook) -> Unit = {},
onRemoteRemove: (voice.core.remote.RemoteBook) -> Unit = {},
onRemotePlay: (BookId) -> Unit = {},
```

**File:** `features/bookOverview/src/main/kotlin/voice/features/bookOverview/views/ListBooks.kt`

**Same changes:** Remove the `RemoteLibrarySection` item and the remote-related parameters.

---

### Task 5: Update Book Click Handling for Remote Books

**File:** `features/bookOverview/src/main/kotlin/voice/features/bookOverview/overview/BookOverviewViewModel.kt`

**Modify `onBookClick`:**

```kotlin
fun onBookClick(id: BookId) {
  // Check if this is a remote book
  if (id.value.startsWith("remote://")) {
    val remoteId = id.value.removePrefix("remote://")
    val remoteBook = scope.launch {
      val books = remoteCatalogRepo.flow().first()
      val book = books.find { it.id == remoteId }
      if (book != null) {
        downloadManager.downloadBook(book)
      }
    }
    return
  }
  navigator.goTo(Destination.Playback(id))
}
```

**Add import:**
```kotlin
import kotlinx.coroutines.flow.first
```

---

### Task 6: Update Bottom Sheet for Remote Books

**File:** `features/bookOverview/src/main/kotlin/voice/features/bookOverview/bottomSheet/BottomSheetViewModel.kt`

Find this file and understand its current structure first. Then modify `bookSelected` to handle remote books differently.

**Add these fields to track remote state:**
```kotlin
private var selectedRemoteBook: RemoteBook? = null
```

**Modify to detect remote books:**
```kotlin
fun bookSelected(bookId: BookId) {
  if (bookId.value.startsWith("remote://")) {
    val remoteId = bookId.value.removePrefix("remote://")
    scope.launch {
      val books = remoteCatalogRepo.flow().first()
      selectedRemoteBook = books.find { it.id == remoteId }
      // Update state to show remote-specific options
      updateStateForRemoteBook()
    }
    return
  }
  selectedRemoteBook = null
  // ... existing local book handling
}
```

**Add a function to provide remote-specific bottom sheet items:**
```kotlin
private fun updateStateForRemoteBook() {
  val book = selectedRemoteBook ?: return
  // Show Download option for not-downloaded, Remove for downloaded
  // This depends on existing BottomSheetItem enum - may need to add new items
}
```

**Note:** This task may require adding new `BottomSheetItem` enum values like `Download` and `RemoveDownload`. Check `BottomSheetItem.kt` for the current structure.

---

### Task 7: Show Download State in Grid/List Items

**File:** `features/bookOverview/src/main/kotlin/voice/features/bookOverview/views/GridBooks.kt`

In `GridBook` composable, after the progress indicator, add download state UI:

```kotlin
@Composable
private fun GridBook(
  book: BookOverviewItemViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
) {
  // ... existing code ...
  
  // Inside the Column, after the existing progress indicator:
  when (val remote = book.remoteState) {
    is RemoteBookState.NotDownloaded -> {
      Text(
        text = "Tap to download",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    is RemoteBookState.Downloading -> {
      LinearProgressIndicator(
        progress = { remote.progress },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      )
    }
    is RemoteBookState.Downloaded, null -> {
      // Show normal progress for local/downloaded books
      if (book.progress > 0.05) {
        LinearProgressIndicator(
          progress = { book.progress },
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
      }
    }
  }
}
```

**File:** `features/bookOverview/src/main/kotlin/voice/features/bookOverview/views/ListBooks.kt`

Apply similar changes to `ListBookRow`.

---

### Task 8: Clean Up Unused Code

After all above tasks are complete and working:

1. **Delete:** `features/bookOverview/src/main/kotlin/voice/features/bookOverview/views/RemoteLibrarySection.kt`

2. **Delete:** `features/bookOverview/src/main/kotlin/voice/features/bookOverview/overview/RemoteBookItemViewState.kt`

3. **Remove from BookOverviewViewState:**
```kotlin
val remoteBooks: List<RemoteBookItemViewState>,  // DELETE THIS LINE
```

4. **Remove from BookOverview.kt:**
   - Remove `onRemoteBookDownload`, `onRemoteBookRemove`, `onRemoteBookPlay` parameters
   - Remove corresponding calls

5. **Remove from BookOverviewViewModel:**
   - Remove `onRemoteBookDownload`, `onRemoteBookRemove`, `onRemoteBookPlay` functions

---

### Task 9: Handle Edge Cases

**9a. Empty state when only remote books exist:**

In `BookOverviewViewModel.state()`, update the `noBooks` logic:
```kotlin
val noBooks = !scannerActive && books.isEmpty() && remoteBookViewStates.isEmpty()
```

**9b. Search should include remote books:**

The search functionality in `BookSearch` currently only searches local books. For now, this is acceptable - remote books will only appear in the main list, not search results. Document this as a known limitation or future enhancement.

**9c. Category for remote books:**

Remote books go into `NOT_STARTED` category. After download, they appear normally based on playback state. This matches user expectation.

---

## Testing Checklist

After implementation, verify:

- [ ] Remote books appear in the gallery grid/list alongside local books
- [ ] Remote books show covers (if available from sync)
- [ ] Remote books show title and author
- [ ] Remote books show "Tap to download" or duration instead of progress
- [ ] Clicking a remote book starts download
- [ ] Download progress is shown on the book item
- [ ] After download completes, book appears as normal local book
- [ ] Long-press on remote book shows appropriate options
- [ ] Downloaded remote books can be removed
- [ ] Removing a downloaded book shows it as "not downloaded" again
- [ ] App doesn't crash when SFTP is not configured
- [ ] App doesn't crash when remote catalog is empty

---

## File Summary

| File | Action |
|------|--------|
| `BookOverviewItemViewState.kt` | Extend with `RemoteBookState`, add converter |
| `BookOverviewViewModel.kt` | Merge remote books into main list |
| `BookOverviewViewState.kt` | Remove `remoteBooks` field (after cleanup) |
| `GridBooks.kt` | Remove RemoteLibrarySection, show download state |
| `ListBooks.kt` | Remove RemoteLibrarySection, show download state |
| `BookOverview.kt` | Remove remote-specific callbacks |
| `BottomSheetViewModel.kt` | Handle remote book selection |
| `RemoteLibrarySection.kt` | DELETE |
| `RemoteBookItemViewState.kt` | DELETE |

---

## Dependencies

The `features/bookOverview` module needs access to remote types. Check `features/bookOverview/build.gradle.kts` has:
```kotlin
implementation(projects.core.remote)
```

This should already exist on the branch.

---

## Order of Operations

1. Task 1 (extend view state) - Foundation
2. Task 2 (converter function) - Foundation  
3. Task 3 (merge in ViewModel) - Core logic
4. Task 7 (UI indicators) - Visual feedback
5. Task 5 (click handling) - Interaction
6. Task 4 (remove old UI) - Cleanup
7. Task 6 (bottom sheet) - Enhanced interaction
8. Task 8 (delete files) - Final cleanup
9. Task 9 (edge cases) - Polish

Build and test after each major task to catch issues early.
