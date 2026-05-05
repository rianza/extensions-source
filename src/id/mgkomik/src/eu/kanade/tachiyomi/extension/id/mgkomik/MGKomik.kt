package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Duration.Companion.minutes

class MGKomik : HttpSource() {
    override val name = "MG Komik"
    override val baseUrl = "https://web.mgkomik.cc"
    override val lang = "id"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .connectTimeout(2.minutes)
        .readTimeout(2.minutes)
        .callTimeout(2.minutes)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    // ======================== Popular ========================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/komik/".toHttpUrl().newBuilder()
            .addQueryParameter("order_by", "views")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, rscHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ======================== Latest ========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/komik/".toHttpUrl().newBuilder()
            .addQueryParameter("order_by", "latest")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, rscHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ======================== Search ========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search/".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, rscHeaders)
        }

        val url = "$baseUrl/komik/".toHttpUrl().newBuilder()
        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("order_by", filter.selected)
                is StatusFilter -> url.addQueryParameter("status", filter.selected)
                is TypeFilter -> url.addQueryParameter("type", filter.selected)
                is GenreFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("genre[]", it.value) }
                }
                else -> {}
            }
        }

        return GET(url.build(), rscHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.extractNextJs<MangaList> {
            val obj = it.jsonObject
            obj.containsKey("data") || obj.containsKey("records") || obj.containsKey("items")
        } ?: throw Exception("Gagal memuat daftar komik")

        val mangas = data.data.map { it.toSManga() }
        return MangasPage(mangas, data.hasNextPage())
    }

    // ======================== Details ========================
    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.removePrefix("/").removePrefix("komik/").removePrefix("manga/")
        return "$baseUrl/komik/$slug"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.extractNextJs<MangaDetails>()
            ?: throw Exception("Gagal memuat detail komik")

        return SManga.create().apply {
            val slug = response.request.url.pathSegments.lastOrNull()
            url = slug?.removePrefix("/").orEmpty().removePrefix("komik/").removePrefix("manga/")
            title = data.title
            thumbnail_url = data.img ?: data.image ?: data.thumbnail
            author = data.author
            description = data.description ?: data.sinopsis
            genre = data.genres?.joinToString { it.title }
            status = when (data.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed", "tamat" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ======================== Chapters ========================
    override fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<ChaptersList> {
            it.jsonObject.containsKey("chapters") || it.jsonObject.containsKey("data")
        } ?: throw Exception("Gagal memuat daftar chapter")

        return data.chapters.map { it.toSChapter() }
    }

    // ======================== Pages ========================
    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/${chapter.url}", rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<Images> {
            it.jsonObject.containsKey("images") || it.jsonObject.containsKey("data")
        } ?: throw Exception("Gagal memuat gambar")

        return data.images.mapIndexed { i, img ->
            Page(i, imageUrl = img)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ======================== Filters ========================
    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(getGenreList()),
    )

    private class SortFilter :
        Filter.Select<String>(
            "Urutan",
            arrayOf("Bawaan", "Terpopuler", "Terbaru", "Update", "A-Z", "Z-A"),
        ) {
        val selected get() = arrayOf("", "views", "latest", "update", "title", "titlereverse")[state]
    }

    private class StatusFilter :
        Filter.Select<String>(
            "Status",
            arrayOf("Semua", "Ongoing", "Completed"),
        ) {
        val selected get() = arrayOf("", "ongoing", "completed")[state]
    }

    private class TypeFilter :
        Filter.Select<String>(
            "Tipe",
            arrayOf("Semua", "Manga", "Manhwa", "Manhua"),
        ) {
        val selected get() = arrayOf("", "manga", "manhwa", "manhua")[state]
    }

    private class GenreFilter(genres: Array<Pair<String, String>>) :
        Filter.Group<GenreCheckBox>(
            "Genre",
            genres.map { GenreCheckBox(it.first, it.second) },
        )

    private class GenreCheckBox(name: String, val value: String) : Filter.CheckBox(name)

    private fun getGenreList() = arrayOf(
        "Action" to "action",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Fantasy" to "fantasy",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Isekai" to "isekai",
        "Martial Arts" to "martial-arts",
        "Mature" to "mature",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shounen" to "shounen",
        "Slice of Life" to "slice-of-life",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
    )
}
