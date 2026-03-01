package voice.core.scanner

import android.content.Context
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import voice.core.data.Book
import voice.core.data.toUri
import voice.core.logging.api.Logger
import java.io.File
import java.io.IOException

@Inject
internal class CoverScanner(
  private val context: Context,
  private val coverSaver: CoverSaver,
  private val coverExtractor: CoverExtractor,
) {

  suspend fun scan(books: List<Book>) {
    books.forEach { findCoverForBook(it) }
  }

  private suspend fun findCoverForBook(book: Book) {
    val coverFile = book.content.cover
    if (coverFile != null && coverFile.exists()) {
      return
    }

    val foundOnDisc = findAndSaveCoverFromDisc(book)
    if (foundOnDisc) {
      return
    }

    scanForEmbeddedCover(book)
  }

  private suspend fun findAndSaveCoverFromDisc(book: Book): Boolean = withContext(Dispatchers.IO) {
    val uri = book.id.toUri()
    
    if (uri.scheme == "file") {
      findAndSaveCoverFromFile(book, uri.toFile())
    } else {
      findAndSaveCoverFromSaf(book, uri)
    }
  }

  private suspend fun findAndSaveCoverFromFile(book: Book, directory: File): Boolean {
    if (!directory.isDirectory) {
      return false
    }

    val imageFiles = directory.listFiles { file ->
      file.isFile && file.canRead() && isImageFile(file)
    } ?: return false

    for (imageFile in imageFiles) {
      val coverFile = coverSaver.newBookCoverFile()
      val worked = try {
        imageFile.inputStream().use { input ->
          coverFile.outputStream().use { output ->
            input.copyTo(output)
          }
        }
        true
      } catch (e: IOException) {
        Logger.w(e, "Error while copying the cover from ${imageFile.absolutePath}")
        false
      }
      if (worked) {
        coverSaver.setBookCover(coverFile, book.id)
        return true
      }
    }

    return false
  }

  private fun isImageFile(file: File): Boolean {
    val name = file.name.lowercase()
    return name.endsWith(".jpg") || name.endsWith(".jpeg") || 
           name.endsWith(".png") || name.endsWith(".webp")
  }

  private suspend fun findAndSaveCoverFromSaf(book: Book, uri: android.net.Uri): Boolean {
    val documentFile = try {
      DocumentFile.fromTreeUri(context, uri)
    } catch (_: IllegalArgumentException) {
      null
    } ?: return false

    if (!documentFile.isDirectory) {
      return false
    }

    documentFile.listFiles().forEach { child ->
      if (child.isFile && child.canRead() && child.type?.startsWith("image/") == true) {
        val coverFile = coverSaver.newBookCoverFile()
        val worked = try {
          context.contentResolver.openInputStream(child.uri)?.use { input ->
            coverFile.outputStream().use { output ->
              input.copyTo(output)
            }
          }
          true
        } catch (e: IOException) {
          Logger.w(e, "Error while copying the cover from ${child.uri}")
          false
        } catch (e: IllegalStateException) {
          Logger.w(e, "Error while copying the cover from ${child.uri}")
          false
        }
        if (worked) {
          coverSaver.setBookCover(coverFile, book.id)
          return true
        }
      }
    }

    return false
  }

  private suspend fun scanForEmbeddedCover(book: Book) {
    val coverFile = coverSaver.newBookCoverFile()
    book.chapters
      .take(5).forEach { chapter ->
        val success = coverExtractor.extractCover(
          input = chapter.id.toUri(),
          outputFile = coverFile,
        )
        if (success && coverFile.exists() && coverFile.length() > 0) {
          coverSaver.setBookCover(coverFile, bookId = book.id)
          return
        }
      }
  }
}
