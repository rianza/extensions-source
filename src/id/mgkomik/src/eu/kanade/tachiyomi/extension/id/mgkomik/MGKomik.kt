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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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

    private val dateFormats = listOf(
        SimpleDateFormat("d MMM yy", Locale.US),
        SimpleDateFormat("d MMM yyyy", Locale.US),
        SimpleDateFormat("MMMM d, yyyy", Locale("id")),
        SimpleDateFormat("d MMMM yyyy", Locale("id")),
    )

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter()))

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 1 }))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isNotBlank()) {
            if (query.startsWith("https://")) {
                val url = runCatching { query.toHttpUrl() }.getOrNull()
                if (url != null && url.host == baseUrl.toHttpUrl().host && url.pathSegments.isNotEmpty()) {
                    val slug = url.pathSegments.lastOrNull { it.isNotBlank() }
                    if (slug != null && (url.pathSegments.contains("komik") || url.pathSegments.contains("manga"))) {
                        val tmpManga = SManga.create().apply {
                            this@apply.url = "/komik/$slug"
                        }

                        return fetchMangaDetails(tmpManga)
                            .map { MangasPage(listOf(it), false) }
                    }
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

        val mangas = document.select(".manga-card, a[href*='/komik/']:has(img), .grid a[href*='/komik/'], a:has(h3)").mapNotNull { element ->
            val link = element.selectFirst("a[href*='/komik/']") ?: if (element.tagName() == "a") element else null
            if (link == null) return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = element.selectFirst("h3, .title, p, .manga-title")?.text()?.trim() ?: ""
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }.filter { it.title.isNotBlank() }.distinctBy { it.url }

        val hasNextPage = document.selectFirst("a:contains(Next), a:contains(Berikutnya), a[href*='page=${document.location().toHttpUrl().queryParameter("page")?.toIntOrNull()?.plus(1) ?: 2}']") != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.trim().removePrefix("/").removePrefix("komik/").removePrefix("manga/").removeSuffix("/")
        return "$baseUrl/komik/$slug"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1, .manga-title, meta[property=og:title]")?.text()
                ?.removeSuffix(" - MG Komik")?.removeSuffix(" - MGKOMIK")?.trim() ?: ""
            thumbnail_url = document.selectFirst("img.object-cover, .aspect-video img, [class*=aspect-] img, .manga-thumbnail img")?.absUrl("src")
            author = document.selectFirst("span:contains(author:), span:contains(Penulis:) + span, .author, span:contains(Author:)")?.text()
                ?.replace("author:", "", true)?.replace("Penulis:", "", true)?.trim()

            val descriptionElements = document.select("p.line-clamp-4, .prose p, #synopsis p, .sinopsis p, .description, .manga-description")
            val alternativeTitle = document.selectFirst("span:contains(Alt Title:) + span, .alt-title, span:contains(Alternatif:)")?.text()?.trim()

            description = buildString {
                append(descriptionElements.joinToString("\n") { it.text().trim() })
                if (!alternativeTitle.isNullOrBlank()) {
                    append("\n\nAlternative Title: $alternativeTitle")
                }
            }

            val statusText = document.selectFirst(".bg-gray-100.text-gray-800, .bg-green-100, .bg-blue-100, span:contains(Status:) + span, .status, .manga-status")?.text()?.trim()
            status = when (statusText?.lowercase()) {
                "ongoing", "berjalan" -> SManga.ONGOING
                "selesai", "completed", "tamat" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            val type = document.selectFirst("span:contains(Type:) + span, .type, .manga-type")?.text()?.trim()
            val genres = document.select(".bg-zinc-700, .bg-zinc-800, a[href*=/genre/], .genre, .manga-genre a").map { it.text().trim() }.toMutableList()
            if (!type.isNullOrBlank()) genres.add(0, type)
            genre = genres.joinToString()
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("a[href*='/chapter/'], a[href*='/bab/'], .chapter-list a, .list-chapters a, .manga-chapters a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))

                // Clean chapter name by removing date text if it's in a child element
                val dateElement = element.selectFirst("span, p, .chapter-date")
                name = if (dateElement != null) {
                    val dateText = dateElement.text()
                    element.text().replace(dateText, "").trim()
                } else {
                    element.text().trim()
                }

                date_upload = parseChapterDate(dateElement?.text() ?: "")
            }
        }.distinctBy { it.url }
    }

    private fun parseChapterDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        val trimmedDate = dateStr.lowercase().trim()

        // Handle relative dates
        if (trimmedDate.contains("lalu") || trimmedDate.contains("ago")) {
            val value = trimmedDate.split(" ")[0].toIntOrNull() ?: return 0L
            val calendar = Calendar.getInstance()
            when {
                trimmedDate.contains("menit") || trimmedDate.contains("minute") -> calendar.add(Calendar.MINUTE, -value)
                trimmedDate.contains("jam") || trimmedDate.contains("hour") -> calendar.add(Calendar.HOUR_OF_DAY, -value)
                trimmedDate.contains("hari") || trimmedDate.contains("day") -> calendar.add(Calendar.DAY_OF_YEAR, -value)
                trimmedDate.contains("minggu") || trimmedDate.contains("week") -> calendar.add(Calendar.WEEK_OF_YEAR, -value)
                trimmedDate.contains("bulan") || trimmedDate.contains("month") -> calendar.add(Calendar.MONTH, -value)
                trimmedDate.contains("tahun") || trimmedDate.contains("year") -> calendar.add(Calendar.YEAR, -value)
            }
            return calendar.timeInMillis
        }

        for (format in dateFormats) {
            try {
                return format.parse(trimmedDate)?.time ?: continue
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("/")) {
        baseUrl + chapter.url
    } else {
        "$baseUrl/${chapter.url}"
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img[src*='/uploads/'], .reader-area img, .chapter-content img, .manga-reader img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }.filter { it.imageUrl?.isNotBlank() == true }.distinctBy { it.imageUrl }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
