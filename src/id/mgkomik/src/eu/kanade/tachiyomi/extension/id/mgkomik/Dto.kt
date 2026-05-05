package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class MangaList(
    val data: List<Manga>? = null,
    val mangas: List<Manga>? = null,
    val posts: List<Manga>? = null,
    val items: List<Manga>? = null,
    val entries: List<Manga>? = null,
    val results: List<Manga>? = null,
    val records: List<Manga>? = null,
    @SerialName("current_page")
    val currentPage: Int? = null,
    @SerialName("last_page")
    val lastPage: Int? = null,
    @SerialName("total_pages")
    val totalPages: Int? = null,
) {
    @Serializable
    class Manga(
        val link: String? = null,
        val slug: String? = null,
        val url: String? = null,
        val tautan: String? = null,
        val title: String? = null,
        val name: String? = null,
        val judul: String? = null,
        val img: String? = null,
        val image: String? = null,
        val thumbnail: String? = null,
        val thumb: String? = null,
        val cover: String? = null,
        val gambar: String? = null,
        val author: String? = null,
        @SerialName("author_name")
        val authorName: String? = null,
        val description: String? = null,
        val synopsis: String? = null,
        val summary: String? = null,
        val content: String? = null,
        val status: String? = null,
        val type: String? = null,
        val genre: String? = null,
        val genres: List<Genre>? = null,
    ) {
        fun toSManga(baseUrl: String) = SManga.create().apply {
            val rawUrl = link ?: slug ?: url ?: tautan ?: ""
            this.url = if (rawUrl.startsWith("http")) {
                runCatching { rawUrl.toHttpUrl().encodedPath }.getOrDefault(rawUrl)
            } else {
                rawUrl
            }.let {
                it.removePrefix("/").removePrefix("komik/").removePrefix("manga/")
            }.let {
                "/komik/$it"
            }
            this.title = (this@Manga.title ?: name ?: judul ?: "").trim()
            val rawImg = img ?: image ?: thumbnail ?: thumb ?: cover ?: gambar ?: ""
            thumbnail_url = when {
                rawImg.isEmpty() -> ""
                rawImg.startsWith("/") -> baseUrl + rawImg
                else -> rawImg
            }
            author = (this@Manga.author ?: authorName)?.trim()
            description = (this@Manga.description ?: synopsis ?: summary ?: content)?.trim()
            genre = (genres?.joinToString { it.title } ?: this@Manga.genre ?: type)?.trim()
            status = when (this@Manga.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed", "selesai" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    fun parseMangas() = data ?: mangas ?: posts ?: items ?: entries ?: results ?: records ?: emptyList()

    fun hasNextPage(page: Int) = (currentPage ?: page) < (lastPage ?: totalPages ?: page)
}

@Serializable
class MangaDetailsDto(
    val manga: MangaList.Manga? = null,
    val post: MangaList.Manga? = null,
    val details: MangaList.Manga? = null,
    val data: MangaList.Manga? = null,
    val record: MangaList.Manga? = null,
)

@Serializable
class GenreList(
    val genres: List<Genre>? = null,
    val data: List<Genre>? = null,
) {
    fun parseGenres() = genres ?: data ?: emptyList()
}

@Serializable
class Genre(
    val title: String,
    val link: String,
)

@Serializable
class ChaptersList(
    val chapters: List<Chapter>? = null,
    val data: List<Chapter>? = null,
    val items: List<Chapter>? = null,
) {
    @Serializable
    class Chapter(
        val link: String? = null,
        val slug: String? = null,
        val url: String? = null,
        val tautan: String? = null,
        val title: String? = null,
        val name: String? = null,
        val judul: String? = null,
        @SerialName("created_at")
        val createdAt: String? = null,
        @SerialName("updated_at")
        val updatedAt: String? = null,
    ) {
        fun toSChapter() = SChapter.create().apply {
            val rawUrl = link ?: slug ?: url ?: tautan ?: ""
            this.url = if (rawUrl.startsWith("http")) {
                runCatching { rawUrl.toHttpUrl().encodedPath }.getOrDefault(rawUrl)
            } else {
                rawUrl
            }.let {
                if (it.startsWith("/")) it else "/$it"
            }
            this.name = (this@Chapter.title ?: this@Chapter.name ?: judul ?: "").trim()
            date_upload = dateFormat.tryParse(updatedAt ?: createdAt)
        }
    }

    fun parseChapters() = chapters ?: data ?: items ?: emptyList()
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ", Locale.ROOT)

@Serializable
class Images(
    val images: List<String>? = null,
    val data: List<String>? = null,
    val items: List<String>? = null,
) {
    fun parseImages() = images ?: data ?: items ?: emptyList()
}
