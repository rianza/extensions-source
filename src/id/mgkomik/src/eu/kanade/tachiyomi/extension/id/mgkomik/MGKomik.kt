package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import okhttp3.Headers
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
        .rateLimit(2)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Sec-Fetch-Site", "same-origin")

    private val rscHeaders = headersBuilder()
        .add("rsc", "1")
        .build()

    // ======================== Popular ========================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/komik".toHttpUrl().newBuilder()
            .addQueryParameter("order_by", "views")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, rscHeaders)
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ======================== Latest ========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/komik".toHttpUrl().newBuilder()
            .addQueryParameter("order_by", "latest")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, rscHeaders)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // ======================== Search ========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/komik".toHttpUrl().newBuilder()
        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("order_by", filter.selected)
                is StatusFilter -> if (filter.selected.isNotEmpty()) url.addQueryParameter("status", filter.selected)
                is TypeFilter -> if (filter.selected.isNotEmpty()) url.addQueryParameter("type", filter.selected)
                else -> {}
            }
        }

        url.addQueryParameter("page", page.toString())

        return GET(url.build(), rscHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.extractNextJs<MangaList> {
            it.toString().contains("title") && it.toString().contains("slug")
        } ?: throw Exception("Could not find manga list data")

        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val mangas = data.toMangas().map { it.toSManga() }
        return MangasPage(mangas, data.hasNextPage(page))
    }

    // ======================== Details ========================
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/komik/${manga.url}", rscHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.extractNextJs<MangaDetails> {
            it.toString().contains("title") && it.toString().contains("synopsis")
        } ?: throw Exception("Could not find manga details")

        return data.toMangaData().toSManga()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/komik/${manga.url}"

    // ======================== Chapters ========================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<ChaptersData> {
            it.toString().contains("chapters") || it.toString().contains("chapterNumber")
        } ?: throw Exception("Could not find chapters data")

        val mangaSlug = response.request.url.pathSegments.last()

        return data.toChapterList().map { it.toSChapter(mangaSlug) }
    }

    // ======================== Pages ========================
    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<PageData> {
            it.toString().contains("images") || (it.toString().contains("data") && it.toString().contains("http"))
        } ?: throw Exception("Could not find page data")

        return data.toImageList().mapIndexed { i, img ->
            Page(i, imageUrl = img)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ============================= Utilities ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
    )
}
