package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
data class MangaList(
    val records: List<MangaItem>,
    val totalRecords: Int,
) {
    fun hasNextPage(currentPage: Int): Boolean = totalRecords > currentPage * 20
}

@Serializable
data class MangaItem(
    val title: String,
    val slug: String,
    val image: String,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaItem.title
        thumbnail_url = image
    }
}

@Serializable
data class MangaDetails(
    val manga: MangaData,
)

@Serializable
data class MangaData(
    val title: String,
    val slug: String,
    val image: String,
    val author: String? = null,
    val status: String? = null,
    val synopsis: String? = null,
    val genres: List<GenreItem>? = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaData.title
        thumbnail_url = image
        author = this@MangaData.author
        description = synopsis
        genre = genres?.joinToString { it.name }
        status = when (this@MangaData.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed", "tamat" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
data class GenreItem(
    val name: String,
    val slug: String,
)

@Serializable
data class ChaptersData(
    val chapters: List<ChapterItem>,
)

@Serializable
data class ChapterItem(
    val title: String,
    val slug: String,
    val chapterNumber: Double,
    val createdAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/komik/$mangaSlug/$slug"
        name = title
        chapter_number = chapterNumber.toFloat()
    }
}

@Serializable
data class PageData(
    val images: List<String>,
)
