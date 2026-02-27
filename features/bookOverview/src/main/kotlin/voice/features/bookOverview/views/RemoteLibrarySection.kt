package voice.features.bookOverview.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import voice.core.data.BookId
import voice.features.bookOverview.overview.RemoteBookItemViewState

@Composable
internal fun RemoteLibrarySection(
  remoteBooks: List<RemoteBookItemViewState>,
  syncInProgress: Boolean,
  syncError: String?,
  onSync: () -> Unit,
  onDownload: (voice.core.remote.RemoteBook) -> Unit,
  onRemove: (voice.core.remote.RemoteBook) -> Unit,
  onPlay: (BookId) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
    Text(
      text = "Remote library",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Button(
      onClick = onSync,
      enabled = !syncInProgress,
      modifier = Modifier.padding(top = 8.dp),
    ) {
      Text(if (syncInProgress) "Syncing…" else "Sync")
    }
    if (syncError != null) {
      Text(
        text = syncError,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
    Column(
      modifier = Modifier.padding(top = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      remoteBooks.forEach { item ->
        RemoteBookRow(
          item = item,
          onDownload = { onDownload(item.remoteBook) },
          onRemove = { onRemove(item.remoteBook) },
          onPlay = { item.localBookId?.let { onPlay(it) } },
        )
      }
    }
  }
}

@Composable
private fun RemoteBookRow(
  item: RemoteBookItemViewState,
  onDownload: () -> Unit,
  onRemove: () -> Unit,
  onPlay: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = item.remoteBook.title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      item.remoteBook.author?.let { author ->
        Text(
          text = author,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item.downloadProgress?.let { progress ->
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
        )
      }
      androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (item.isDownloaded) {
          OutlinedButton(onClick = onPlay) {
            Text("Play")
          }
          OutlinedButton(onClick = onRemove) {
            Text("Remove")
          }
        } else {
          Button(
            onClick = onDownload,
            enabled = item.downloadProgress == null,
          ) {
            Text(if (item.downloadProgress != null) "Downloading…" else "Download")
          }
        }
      }
    }
  }
}
