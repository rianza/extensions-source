package eu.kanade.tachiyomi.extension.id.mgkomik

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class MGKomikMangaListDto(
    @SerialName("data") val data: List<MGKomikMangaDto>? = emptyList(),
    @SerialName("current_page") val currentPage: Int? = null,
    @SerialName("last_page") val lastPage: Int? = null,
)

@Serializable
data class MGKomikMangaDto(
    @SerialName("title") val title: String? = null,
    @SerialName("slug") val slug: String? = null,
    @SerialName("image") val image: String? = null,
)

@Serializable
data class MGKomikDetailsDto(
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("author") val author: String? = null,
    @SerialName("genres") val genres: List<MGKomikGenreDto>? = emptyList(),
)

@Serializable
data class MGKomikGenreDto(
    @SerialName("name") val name: String? = null,
)

@Serializable
data class MGKomikChaptersDto(
    @SerialName("chapters") val chapters: List<MGKomikChapterDto>? = emptyList(),
)

@Serializable
data class MGKomikChapterDto(
    @SerialName("title") val title: String? = null,
    @SerialName("slug") val slug: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class MGKomikImagesDto(
    @SerialName("images") val images: List<String>? = emptyList(),
)

internal val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ", Locale.ROOT)
