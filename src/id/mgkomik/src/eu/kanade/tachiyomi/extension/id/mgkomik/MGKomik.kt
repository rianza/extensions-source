package eu.kanade.tachiyomi.extension.id.mgkomik

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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

    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter()))

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 1 }))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isNotBlank()) {
            return if (query.startsWith("https://")) {
                deepLink(query)
            } else {
                querySearch(query)
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    private fun querySearch(query: String): Observable<MangasPage> {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .build()
        val request = GET(url, headers)

        return client.newCall(request)
            .asObservableSuccess()
            .map { response ->
                val document = response.asJsoup()

                val mangas = document.select("a[href*=/komik/]").map { element ->
                    SManga.create().apply {
                        setUrlWithoutDomain(element.absUrl("href"))
                        title = element.selectFirst("h3")?.text() ?: ""
                        thumbnail_url = element.selectFirst("img")?.absUrl("src")
                    }
                }

                MangasPage(mangas, hasNextPage = false)
            }
    }

    private fun deepLink(link: String): Observable<MangasPage> {
        val url = link.toHttpUrl()
        if (url.host == "web.mgkomik.cc" && url.pathSegments[0] == "komik") {
            val slug = url.pathSegments[1]
            val tmpManga = SManga.create().apply {
                this@apply.url = "/komik/$slug"
            }

            return fetchMangaDetails(tmpManga)
                .map { MangasPage(listOf(it), false) }
        }

        throw Exception("Unsupported url")
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
                        addQueryParameter("order", sort)
                    }
                    is GenreFilter -> filter.checked.forEach { genre ->
                        addQueryParameter("genre[]", genre)
                    }
                    else -> {}
                }
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, rscHeaders)
    }

    private val genreCacheFile by lazy {
        Injekt.get<Application>().cacheDir
            .resolve("source_$id")
            .also { it.mkdirs() }
            .resolve("genres.json")
    }
    private val genreLock = Any()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Filters are ignored with text search"),
            StatusFilter(),
            TypeFilter(),
            SortFilter(),
        )

        if (genreCacheFile.exists()) {
            val fileContent = synchronized(genreLock) {
                genreCacheFile.readText()
            }
            val genres = fileContent.parseAs<List<Genre>>()
            filters.add(GenreFilter(genres))
        } else {
            filters.add(Filter.Separator())
            filters.add(Filter.Header("Press 'reset' to load genres"))
        }

        return FilterList(filters)
    }

    private inline fun <reified T> Response.extractData(noinline predicate: (JsonElement) -> Boolean): T? {
        val bodyString = body.string()
        return if (header("Content-Type")?.contains("text/x-component") == true) {
            bodyString.extractNextJsRsc(predicate)
        } else {
            asJsoup(bodyString).extractNextJs(predicate)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.peekBody(Long.MAX_VALUE).string()
        cacheGenres(body)

        val data = response.extractData<MangaList> {
            it is JsonObject && (it.containsKey("records") || it.containsKey("data") || it.containsKey("mangas") || it.containsKey("posts"))
        }
        val page = response.request.url.queryParameter("page")?.toInt() ?: 1

        val mangas = data?.parseMangas().orEmpty().map { it.toSManga(baseUrl) }
        val hasNextPage = data?.hasNextPage(page) ?: false

        if (mangas.isEmpty()) {
            val document = asJsoup(body)
            val jMangas = document.select("a[href*=/komik/]").map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    title = element.selectFirst("h3")?.text() ?: ""
                    thumbnail_url = element.selectFirst("img")?.absUrl("src")
                }
            }
            if (jMangas.isNotEmpty()) {
                return MangasPage(jMangas, jMangas.size >= 10)
            }
        }

        return MangasPage(mangas, hasNextPage)
    }

    private fun asJsoup(html: String): org.jsoup.nodes.Document = org.jsoup.Jsoup.parse(html, baseUrl)

    private fun cacheGenres(body: String) {
        val genres = body.extractNextJsRsc<GenreList> {
            it is JsonObject && (it.containsKey("genres") || it.containsKey("data"))
        }?.parseGenres()
            ?.takeIf { it.isNotEmpty() }
            ?.toJsonString()
            ?: return

        synchronized(genreLock) {
            genreCacheFile.writeText(genres)
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), rscHeaders)

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.removePrefix("/").removePrefix("komik/").removePrefix("manga/")
        return "$baseUrl/komik/$slug"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val body = response.peekBody(Long.MAX_VALUE).string()
        val data = response.extractData<MangaDetailsDto> {
            it is JsonObject && (it.containsKey("manga") || it.containsKey("post") || it.containsKey("details") || it.containsKey("record"))
        }

        val document = asJsoup(body)
        val details = SManga.create()

        if (data != null) {
            val manga = data.manga ?: data.post ?: data.details ?: data.data ?: data.record
                ?: data.entry ?: data.result ?: data.item
            if (manga != null) {
                manga.toSManga(baseUrl).copyTo(details)
            }
        }

        if (details.title.isBlank()) {
            details.title = document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.removeSuffix(" - MG Komik")?.removeSuffix(" - MGKOMIK")?.trim() ?: ""
        }
        if (details.thumbnail_url.isNullOrBlank()) {
            details.thumbnail_url = document.selectFirst("img.object-cover, .aspect-video img, [class*=aspect-] img")?.absUrl("src")
        }
        if (details.author.isNullOrBlank()) {
            details.author = document.selectFirst("span:contains(author:), span:contains(Penulis:) + span")?.ownText()?.trim()
                ?: document.selectFirst("span:contains(author:), span:contains(Penulis:)")?.parent()?.select("span")?.last()?.ownText()?.trim()
        }
        if (details.genre.isNullOrBlank()) {
            details.genre = buildList {
                document.selectFirst("span:contains(type:), span:contains(Tipe:) + span")
                    ?.ownText()?.trim()
                    ?.also { add(it) }
                document.selectFirst("span:contains(rilis:), span:contains(Rilis:) + span")
                    ?.ownText()?.trim()
                    ?.also { add(it) }
                document.select(".bg-zinc-700, .bg-zinc-800, a[href*=/genre/]").forEach {
                    add(it.text().trim())
                }
            }.filter { it.isNotBlank() }.distinct().joinToString()
        }
        if (details.description.isNullOrBlank()) {
            details.description = document.select("p.line-clamp-4, .prose p, #synopsis p, .sinopsis p").joinToString("\n") { it.text().trim() }
        }
        if (details.status == SManga.UNKNOWN) {
            val statusText = document.selectFirst(".bg-gray-100.text-gray-800, .bg-green-100, .bg-blue-100, span:contains(Status:) + span")?.text()?.trim()
            details.status = when (statusText?.lowercase()) {
                "ongoing", "berjalan" -> SManga.ONGOING
                "selesai", "completed", "tamat" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }

        return details
    }

    private fun SManga.copyTo(target: SManga) {
        target.url = this.url
        target.title = this.title
        target.thumbnail_url = this.thumbnail_url
        target.author = this.author
        target.description = this.description
        target.genre = this.genre
        target.status = this.status
    }

    override fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.peekBody(Long.MAX_VALUE).string()
        val data = response.extractData<ChaptersList> {
            it is JsonObject && (it.containsKey("chapters") || it.containsKey("data") || it.containsKey("items") || it.containsKey("records"))
        }

        val chapters = data?.parseChapters().orEmpty().map { it.toSChapter() }
        if (chapters.isNotEmpty()) return chapters

        val document = asJsoup(body)
        return document.select("a[href*=/chapter/], a[href*=/bab/]").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text().trim()
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), rscHeaders)

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("/")) {
        baseUrl + chapter.url
    } else {
        "$baseUrl/${chapter.url}"
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractData<Images> {
            it is JsonObject && (it.containsKey("images") || it.containsKey("data") || it.containsKey("items"))
        }

        return data?.parseImages().orEmpty().mapIndexed { index, img ->
            Page(
                index,
                imageUrl = img,
            )
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
