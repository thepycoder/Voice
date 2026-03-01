package voice.features.bookOverview.views

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableMap
import voice.core.data.BookId
import voice.core.ui.ImmutableFile
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.RemoteBookState
import voice.core.ui.R as UiR

@Composable
internal fun ListBooks(
  books: ImmutableMap<BookOverviewCategory, List<BookOverviewItemViewState>>,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  showPermissionBugCard: Boolean,
  onPermissionBugCardClick: () -> Unit,
) {
  LazyColumn(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(top = 24.dp, start = 8.dp, end = 8.dp, bottom = 16.dp),
  ) {
    if (showPermissionBugCard) {
      item {
        PermissionBugCard(onPermissionBugCardClick)
      }
    }
    books.forEach { (category, books) ->
      if (books.isEmpty()) return@forEach
      stickyHeader(
        key = category,
        contentType = "header",
      ) {
        Header(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp, horizontal = 8.dp),
          category = category,
        )
      }
      items(
        items = books,
        key = { it.id.value },
        contentType = { "item" },
      ) { book ->
        ListBookRow(
          book = book,
          onBookClick = onBookClick,
          onBookLongClick = onBookLongClick,
        )
      }
      item {
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
      }
    }
  }
}

@Composable
internal fun ListBookRow(
  book: BookOverviewItemViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  modifier: Modifier = Modifier,
) {
  ElevatedCard(
    shape = MaterialTheme.shapes.extraLarge,
    modifier = modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = { onBookClick(book.id) },
        onLongClick = { onBookLongClick(book.id) },
      ),
  ) {
    Row(
      modifier = Modifier.padding(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CoverImage(book.cover)

      Column(
          Modifier
            .padding(start = 12.dp)
            .weight(1f),
        ) {
          if (book.author != null) {
            Text(
              text = book.author.toUpperCase(LocaleList.current),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }

          Text(
            text = book.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
          )

          Row(
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = book.remainingTime,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )

            if (book.progress > 0f) {
              Text(
                text = "${(book.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
              )
            }
          }

          when (val remote = book.remoteState) {
            is RemoteBookState.NotDownloaded -> {
              Spacer(Modifier.height(4.dp))
              RemoteBadge(
                icon = Icons.Outlined.CloudDownload,
                text = "Remote",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            is RemoteBookState.Downloading -> {
              Spacer(Modifier.height(4.dp))
              RemoteBadge(
                icon = Icons.Outlined.CloudDownload,
                text = "${(remote.progress * 100).toInt()}%",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Spacer(Modifier.height(4.dp))
              LinearProgressIndicator(
                progress = { remote.progress },
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(end = 12.dp)
                  .clip(MaterialTheme.shapes.small)
                  .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
              )
            }
            is RemoteBookState.Downloaded -> {
              Spacer(Modifier.height(4.dp))
              RemoteBadge(
                icon = Icons.Outlined.CloudDone,
                text = "Downloaded",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
            null -> {
              if (book.progress > 0.05f) {
                LinearProgressIndicator(
                  progress = { book.progress },
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .height(4.dp),
                  color = MaterialTheme.colorScheme.primary,
                  trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
              }
            }
          }
        }
    }
  }
}

@Composable
private fun RemoteBadge(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
  containerColor: androidx.compose.ui.graphics.Color,
  contentColor: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .background(containerColor, MaterialTheme.shapes.small)
      .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(14.dp),
      tint = contentColor,
    )
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
    )
  }
}

@Composable
private fun CoverImage(cover: ImmutableFile?) {
  val startPadding = 16.dp
  val endPadding = 16.dp
  AsyncImage(
    modifier = Modifier
      .padding(top = 8.dp, start = 8.dp, bottom = 8.dp)
      .size(76.dp)
      .clip(RoundedCornerShape(topStart = startPadding, bottomStart = startPadding, topEnd = endPadding, bottomEnd = endPadding)),
    model = cover?.file,
    placeholder = painterResource(id = UiR.drawable.album_art),
    error = painterResource(id = UiR.drawable.album_art),
    contentScale = ContentScale.Crop,
    contentDescription = null,
  )
}

@Composable
@Preview
private fun ListBookRowPreviewWithProgress() {
  ListBookRow(BookOverviewPreviewParameterProvider().book().copy(progress = 0.6f), {}, {})
}

@Composable
@Preview
private fun ListBookRowPreviewWithoutProgress() {
  ListBookRow(BookOverviewPreviewParameterProvider().book().copy(progress = 0f), {}, {})
}
