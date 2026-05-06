package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
data class MangaList(
    val records: List<MangaItem>? = emptyList(),
    val totalRecords: Int? = 0,
    val items: List<MangaItem>? = emptyList(),
    val entry: List<MangaItem>? = emptyList(),
    val result: List<MangaItem>? = emptyList(),
) {
    fun toMangas(): List<MangaItem> = records.orEmpty()
        .ifEmpty { items.orEmpty() }
        .ifEmpty { entry.orEmpty() }
        .ifEmpty { result.orEmpty() }

    fun hasNextPage(currentPage: Int): Boolean {
        val total = totalRecords ?: 0
        return total > currentPage * 20
    }
}

@Serializable
data class MangaItem(
    val title: String,
    val slug: String,
    val image: String? = null,
    val thumbnail: String? = null,
    val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaItem.title
        thumbnail_url = image ?: thumbnail ?: cover
    }
}

@Serializable
data class MangaDetails(
    val manga: MangaData? = null,
    val data: MangaData? = null,
) {
    fun toMangaData(): MangaData = manga ?: data ?: throw Exception("Manga data not found")
}

@Serializable
data class MangaData(
    val title: String,
    val slug: String,
    val image: String? = null,
    val thumbnail: String? = null,
    val author: String? = null,
    val status: String? = null,
    val synopsis: String? = null,
    val genres: List<GenreItem>? = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaData.title
        thumbnail_url = image ?: thumbnail
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
    val chapters: List<ChapterItem>? = emptyList(),
    val items: List<ChapterItem>? = emptyList(),
    val result: List<ChapterItem>? = emptyList(),
) {
    fun toChapterList(): List<ChapterItem> = chapters.orEmpty()
        .ifEmpty { items.orEmpty() }
        .ifEmpty { result.orEmpty() }
}

@Serializable
data class ChapterItem(
    val title: String,
    val slug: String,
    val chapterNumber: Double? = null,
    val number: Double? = null,
    val createdAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/komik/$mangaSlug/$slug"
        name = title
        chapter_number = (chapterNumber ?: number ?: -1.0).toFloat()
    }
}

@Serializable
data class PageData(
    val images: List<String>? = emptyList(),
    val data: List<String>? = emptyList(),
) {
    fun toImageList(): List<String> = images.orEmpty()
        .ifEmpty { data.orEmpty() }
}
