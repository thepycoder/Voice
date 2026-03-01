package voice.features.bookOverview.views.topbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import voice.features.bookOverview.views.BookFolderIcon
import voice.features.bookOverview.views.SettingsIcon

@Composable
internal fun ColumnScope.TopBarTrailingIcon(
  searchActive: Boolean,
  showAddBookHint: Boolean,
  showFolderPickerIcon: Boolean,
  showSyncIcon: Boolean,
  onBookFolderClick: () -> Unit,
  onSettingsClick: () -> Unit,
  onSyncClick: () -> Unit,
) {
  AnimatedVisibility(
    visible = !searchActive,
    enter = fadeIn(),
    exit = fadeOut(),
  ) {
    Row {
      if (showSyncIcon) {
        IconButton(onClick = onSyncClick) {
          Icon(
            imageVector = Icons.Outlined.Sync,
            contentDescription = "Sync remote library",
          )
        }
      }
      if (showFolderPickerIcon) {
        BookFolderIcon(withHint = showAddBookHint, onClick = onBookFolderClick)
      }
      SettingsIcon(onSettingsClick)
    }
  }
}
