package voice.core.scanner

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookRepository
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.logging.api.Logger
import voice.core.remote.RemoteDownloadsRootProvider
import kotlin.time.measureTime

@SingleIn(AppScope::class)
@Inject
public class MediaScanTrigger
internal constructor(
  private val audiobookFolders: AudiobookFolders,
  private val scanner: MediaScanner,
  private val coverScanner: CoverScanner,
  private val bookRepo: BookRepository,
  private val documentFileFactory: CachedDocumentFileFactory,
  private val remoteDownloadsRootProvider: RemoteDownloadsRootProvider,
) {

  private val _scannerActive = MutableStateFlow(false)
  public val scannerActive: Flow<Boolean> = _scannerActive

  private val scope = CoroutineScope(Dispatchers.IO)
  private var scanningJob: Job? = null

  public fun scan(restartIfScanning: Boolean = false) {
    Logger.i("scanForFiles with restartIfScanning=$restartIfScanning")
    if (scanningJob?.isActive == true && !restartIfScanning) {
      return
    }
    val oldJob = scanningJob
    scanningJob = scope.launch {
      _scannerActive.value = true
      oldJob?.cancelAndJoin()

      measureTime {
        val allFolders = audiobookFolders.all().first()
        val folders: Map<FolderType, List<CachedDocumentFile>> = allFolders
          .mapValues { (_, documentFilesWithUri) ->
            documentFilesWithUri.map {
              documentFileFactory.create(it.documentFile.uri)
            }
          }
          .toMutableMap()
          .apply {
            remoteDownloadsRootProvider.get()?.let { downloadsRoot ->
              merge(FolderType.Root, listOf(downloadsRoot)) { existing, extra ->
                existing + extra
              }
            }
          }
        scanner.scan(folders)
      }.also {
        Logger.i("scan took $it")
      }
      _scannerActive.value = false

      val books = bookRepo.all()
      coverScanner.scan(books)
    }
  }

  public suspend fun scanAndAwait(restartIfScanning: Boolean = false) {
    scan(restartIfScanning)
    scannerActive.first { it }
    scannerActive.first { !it }
  }
}
