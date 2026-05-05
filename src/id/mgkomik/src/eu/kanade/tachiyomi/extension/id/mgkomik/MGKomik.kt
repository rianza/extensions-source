package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.lang.UnsupportedOperationException
import kotlin.time.Duration.Companion.minutes

class MGKomik : HttpSource() {
    override val name = "MG Komik"
    override val lang = "id"
    override val baseUrl = "https://web.mgkomik.cc"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .connectTimeout(2.minutes)
        .readTimeout(2.minutes)
        .callTimeout(2.minutes)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter()))

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 1 }))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isNotBlank()) {
            if (query.startsWith("https://")) {
                val url = query.toHttpUrl()
                if (url.host == baseUrl.toHttpUrl().host && (url.pathSegments[0] == "komik" || url.pathSegments[0] == "manga")) {
                    val slug = url.pathSegments[1]
                    val tmpManga = SManga.create().apply {
                        this@apply.url = "/komik/$slug"
                    }

                    return fetchMangaDetails(tmpManga)
                        .map { MangasPage(listOf(it), false) }
                }
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/komik".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query.trim())
            }
            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> filter.selected.takeIf { it.isNotEmpty() }?.also { status ->
                        addQueryParameter("status", status)
                    }
                    is TypeFilter -> filter.selected.takeIf { it.isNotEmpty() }?.also { type ->
                        addQueryParameter("type", type)
                    }
                    is SortFilter -> filter.selected.takeIf { it.isNotEmpty() }?.also { sort ->
                        addQueryParameter("order_by", sort)
                    }
                    else -> {}
                }
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun getFilterList(): FilterList = FilterList(
        StatusFilter(),
        TypeFilter(),
        SortFilter(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".manga-card, a[href*='/komik/']:has(img), .grid a[href*='/komik/']").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a") ?: element
                setUrlWithoutDomain(link.absUrl("href"))
                title = element.selectFirst("h3, .title, p")?.text()?.trim() ?: ""
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }.filter { it.title.isNotBlank() }.distinctBy { it.url }

        return MangasPage(mangas, mangas.size >= 10)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.removePrefix("/").removePrefix("komik/").removePrefix("manga/")
        return "$baseUrl/komik/$slug"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1, .manga-title, meta[property=og:title]")?.text()
                ?.removeSuffix(" - MG Komik")?.removeSuffix(" - MGKOMIK")?.trim() ?: ""
            thumbnail_url = document.selectFirst("img.object-cover, .aspect-video img, [class*=aspect-] img")?.absUrl("src")
            author = document.selectFirst("span:contains(author:), span:contains(Penulis:) + span, .author")?.text()?.replace("author:", "", true)?.trim()
            description = document.select("p.line-clamp-4, .prose p, #synopsis p, .sinopsis p, .description").joinToString("\n") { it.text().trim() }

            val statusText = document.selectFirst(".bg-gray-100.text-gray-800, .bg-green-100, .bg-blue-100, span:contains(Status:) + span, .status")?.text()?.trim()
            status = when (statusText?.lowercase()) {
                "ongoing", "berjalan" -> SManga.ONGOING
                "selesai", "completed", "tamat" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            genre = document.select(".bg-zinc-700, .bg-zinc-800, a[href*=/genre/], .genre").joinToString { it.text().trim() }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("a[href*='/chapter/'], a[href*='/bab/'], .chapter-list a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text().trim()
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("/")) {
        baseUrl + chapter.url
    } else {
        "$baseUrl/${chapter.url}"
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img[src*='/uploads/'], .reader-area img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
