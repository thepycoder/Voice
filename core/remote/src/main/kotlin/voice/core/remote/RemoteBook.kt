package voice.core.remote

import kotlinx.serialization.Serializable

@Serializable
public data class RemoteBook(
  val id: String,
  val folder: String,
  val title: String,
  val author: String? = null,
  val durationMs: Long = 0L,
  val hasPdf: Boolean = false,
  val dateAdded: String,
  val coverFileName: String? = null,
  val audioFileName: String? = null,
  val error: String?,
  val contentHash: String? = null,
)

@Serializable
public data class RemoteCatalogStorage(
  val books: List<RemoteBook> = emptyList(),
)
