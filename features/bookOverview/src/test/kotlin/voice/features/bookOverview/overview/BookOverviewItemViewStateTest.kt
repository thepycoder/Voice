package voice.features.bookOverview.overview

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.remote.DownloadState
import voice.core.remote.RemoteBook
import java.io.File

class BookOverviewItemViewStateTest {

  @Test
  fun `RemoteBook toItemViewState uses folder when title is blank`() {
    val remoteBook = RemoteBook(
      id = "id1",
      folder = "MyFolder",
      title = "",
      author = "Author",
      durationMs = 0L,
      dateAdded = "2023-01-01",
      error = null,
    )
    val state = remoteBook.toItemViewState(
      coverFile = null,
      downloadState = DownloadState.Idle,
      unknownAuthor = "Unknown author",
      unknownDuration = "Unknown duration",
    )
    state.name shouldBe "MyFolder"
  }

  @Test
  fun `RemoteBook toItemViewState uses unknownAuthor when author is null`() {
    val remoteBook = RemoteBook(
      id = "id1",
      folder = "Folder",
      title = "Title",
      author = null,
      durationMs = 0L,
      dateAdded = "2023-01-01",
      error = null,
    )
    val state = remoteBook.toItemViewState(
      coverFile = null,
      downloadState = DownloadState.Idle,
      unknownAuthor = "Unknown author",
      unknownDuration = "Unknown duration",
    )
    state.author shouldBe "Unknown author"
  }

  @Test
  fun `RemoteBook toItemViewState uses unknownDuration when durationMs is zero`() {
    val remoteBook = RemoteBook(
      id = "id1",
      folder = "Folder",
      title = "Title",
      author = "Author",
      durationMs = 0L,
      dateAdded = "2023-01-01",
      error = null,
    )
    val state = remoteBook.toItemViewState(
      coverFile = null,
      downloadState = DownloadState.Idle,
      unknownAuthor = "Unknown author",
      unknownDuration = "Unknown duration",
    )
    state.remainingTime shouldBe "Unknown duration"
  }
}
