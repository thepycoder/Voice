package voice.features.bookOverview.overview

import androidx.compose.runtime.Immutable
import voice.core.data.BookId
import voice.core.remote.RemoteBook

@Immutable
data class RemoteBookItemViewState(
  val remoteBook: RemoteBook,
  val isDownloaded: Boolean,
  val localBookId: BookId?,
  val downloadProgress: Float?,
)
