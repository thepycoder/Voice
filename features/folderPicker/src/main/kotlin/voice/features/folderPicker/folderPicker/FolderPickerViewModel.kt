package voice.features.folderPicker.folderPicker

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.documentfile.nameWithoutExtension
import voice.core.remote.SftpSettingsProvider
import voice.navigation.Destination
import voice.navigation.Navigator
import voice.navigation.Origin

private val REMOTE_FOLDER_URI: Uri = Uri.parse("voice://remote/folder")

@Inject
class FolderPickerViewModel(
  private val audiobookFolders: AudiobookFolders,
  private val sftpSettingsProvider: SftpSettingsProvider,
  private val navigator: Navigator,
) {
  private val scope = MainScope()

  @Composable
  fun viewState(): FolderPickerViewState {
    val folders: List<FolderPickerViewState.Item> by remember {
      items()
    }.collectAsState(initial = emptyList())
    return FolderPickerViewState(folders)
  }

  private fun items(): Flow<List<FolderPickerViewState.Item>> {
    return combine(
      audiobookFolders.all(),
      sftpSettingsProvider.flow(),
    ) { foldersMap, sftpSettings ->
      val localItems = foldersMap.flatMap { (folderType, folders) ->
        folders.map { (documentFile, uri) ->
          FolderPickerViewState.Item(
            name = documentFile.nameWithoutExtension(),
            id = uri,
            folderType = folderType,
          )
        }
      }
      val remoteItem = if (sftpSettings.remotePath.isNotBlank()) {
        listOf(
          FolderPickerViewState.Item(
            name = sftpSettings.remotePath,
            id = REMOTE_FOLDER_URI,
            folderType = FolderType.Remote,
          ),
        )
      } else {
        emptyList()
      }
      (localItems + remoteItem).sortedDescending()
    }
  }

  internal fun onCloseClick() {
    navigator.goBack()
  }

  internal fun add() {
    navigator.goTo(
      Destination.AddContent(
        Origin.Default,
      ),
    )
  }

  fun removeFolder(item: FolderPickerViewState.Item) {
    when (item.folderType) {
      FolderType.Remote -> {
        scope.launch {
          sftpSettingsProvider.update { it.copy(remotePath = "") }
        }
      }
      else -> audiobookFolders.remove(item.id, item.folderType)
    }
  }

  fun editFolder(item: FolderPickerViewState.Item) {
    if (item.folderType == FolderType.Remote) {
      navigator.goTo(Destination.SftpSettings(origin = null))
    }
  }
}
