package voice.features.bookOverview.overview

import android.text.format.DateUtils
import androidx.compose.runtime.Immutable
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.logging.api.Logger
import voice.core.remote.DownloadState
import voice.core.remote.RemoteBook
import voice.core.ui.ImmutableFile
import java.io.File

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
    val error: String?,
  ) : RemoteBookState

  data class Downloading(
    override val remoteBookId: String,
    val progress: Float,
  ) : RemoteBookState

  data class Downloaded(
    override val remoteBookId: String,
  ) : RemoteBookState
}

internal fun Book.toItemViewState(remoteBookId: String? = null) = BookOverviewItemViewState(
  name = content.name,
  author = content.author,
  cover = content.cover?.let(::ImmutableFile),
  id = id,
  progress = progress(),
  remainingTime = DateUtils.formatElapsedTime((duration - position) / 1000),
  remoteState = remoteBookId?.let { RemoteBookState.Downloaded(it) },
)

private fun Book.progress(): Float {
  val globalPosition = position
  val totalDuration = duration
  val progress = globalPosition.toFloat() / totalDuration.toFloat()
  if (progress < 0F) {
    Logger.w("Couldn't determine progress for book=$this")
  }
  return progress.coerceIn(0F, 1F)
}

internal fun RemoteBook.toItemViewState(
  coverFile: File?,
  downloadState: DownloadState,
): BookOverviewItemViewState {
  val remoteState = when (downloadState) {
    is DownloadState.Downloading -> {
      if (downloadState.remoteBookId == id) {
        RemoteBookState.Downloading(id, downloadState.progress)
      } else {
        RemoteBookState.NotDownloaded(id, durationMs, error)
      }
    }
    else -> RemoteBookState.NotDownloaded(id, durationMs, error)
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
