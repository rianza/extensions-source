package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class MangaList(
    @SerialName("data")
    private val _data: List<Manga>? = null,
    @SerialName("records")
    private val _records: List<Manga>? = null,
    @SerialName("items")
    private val _items: List<Manga>? = null,

    @SerialName("current_page")
    private val currentPage: Int? = null,
    private val page: Int? = null,
    @SerialName("last_page")
    private val lastPage: Int? = null,
    private val maxPage: Int? = null,
) {
    val data: List<Manga> get() = _data ?: _records ?: _items ?: emptyList()

    @Serializable
    class Manga(
        val link: String? = null,
        val slug: String? = null,
        val title: String,
        val img: String? = null,
        val image: String? = null,
        val thumbnail: String? = null,
    ) {
        fun toSManga() = SManga.create().apply {
            val rawUrl = link ?: slug ?: ""
            url = rawUrl.removePrefix("/").removePrefix("komik/").removePrefix("manga/")
            title = this@Manga.title
            thumbnail_url = img ?: image ?: thumbnail
        }
    }

    fun hasNextPage(): Boolean {
        val current = currentPage ?: page ?: 1
        val last = lastPage ?: maxPage ?: 1
        return current < last
    }
}

@Serializable
class MangaDetails(
    val title: String,
    val img: String? = null,
    val image: String? = null,
    val thumbnail: String? = null,
    val author: String? = null,
    val description: String? = null,
    val sinopsis: String? = null,
    val status: String? = null,
    val genres: List<Genre>? = emptyList(),
)

@Serializable
class Genre(
    val title: String,
    val link: String? = null,
    val slug: String? = null,
)

@Serializable
class ChaptersList(
    @SerialName("chapters")
    private val _chapters: List<Chapter>? = null,
    @SerialName("data")
    private val _data: List<Chapter>? = null,
) {
    val chapters: List<Chapter> get() = _chapters ?: _data ?: emptyList()

    @Serializable
    class Chapter(
        val link: String? = null,
        val slug: String? = null,
        val title: String,
        @SerialName("created_at")
        val createdAt: String? = null,
        @SerialName("updated_at")
        val updatedAt: String? = null,
    ) {
        fun toSChapter() = SChapter.create().apply {
            val rawUrl = link ?: slug ?: ""
            url = rawUrl.removePrefix("/")
            name = title
            date_upload = dateFormat.tryParse(updatedAt ?: createdAt)
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ", Locale.ROOT)

@Serializable
class Images(
    @SerialName("images")
    private val _images: List<String>? = null,
    @SerialName("data")
    private val _data: List<String>? = null,
) {
    val images: List<String> get() = _images ?: _data ?: emptyList()
}
