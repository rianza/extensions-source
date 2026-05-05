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
    val data: List<Manga>,
    @SerialName("current_page")
    private val currentPage: Int,
    @SerialName("last_page")
    private val lastPage: Int,
) {
    @Serializable
    class Manga(
        val link: String,
        val title: String,
        val img: String,
    ) {
        fun toSManga() = SManga.create().apply {
            url = link.removePrefix("/").removePrefix("komik/").removePrefix("manga/")
            title = this@Manga.title
            thumbnail_url = img
        }
    }

    fun hasNextPage() = currentPage < lastPage
}

@Serializable
class MangaDetails(
    val title: String,
    val img: String,
    val author: String? = null,
    val description: String? = null,
    val status: String? = null,
    val genres: List<Genre>? = emptyList(),
)

@Serializable
class Genre(
    val title: String,
    val link: String,
)

@Serializable
class ChaptersList(
    val chapters: List<Chapter>,
) {
    @Serializable
    class Chapter(
        private val link: String,
        private val title: String,
        @SerialName("created_at")
        private val createdAt: String? = null,
    ) {
        fun toSChapter() = SChapter.create().apply {
            url = link.removePrefix("/")
            name = title
            date_upload = dateFormat.tryParse(createdAt)
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ", Locale.ROOT)

@Serializable
class Images(
    val images: List<String>,
)
