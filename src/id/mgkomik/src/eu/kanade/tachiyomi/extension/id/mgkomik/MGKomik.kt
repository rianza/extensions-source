package eu.kanade.tachiyomi.extension.id.mgkomik

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class MGKomik :
    HttpSource(),
    ConfigurableSource {

    override val name = "MG Komik"

    override val baseUrl = "https://web.mgkomik.cc"

    override val lang = "id"

    override val supportsLatest = true

    private val json: Json by lazy { Injekt.get<Json>() }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(4)
        .connectTimeout(2, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Sec-Fetch-Site", "same-origin")

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/komik/?filter=&order_by=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.extractNextJs<MGKomikMangaListDto>()
        val mangas = result?.data?.map {
            SManga.create().apply {
                url = it.slug?.removePrefix("/")?.removePrefix("komik/")?.removePrefix("manga/") ?: ""
                title = it.title ?: ""
                thumbnail_url = it.image
            }
        } ?: emptyList()
        val hasNextPage = (result?.currentPage ?: 1) < (result?.lastPage ?: 1)
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/komik/?filter=&order_by=latest&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search/".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, headers)
        }

        val url = "$baseUrl/komik/".toHttpUrl().newBuilder()
            .addQueryParameter("filter", "")
            .addQueryParameter("order_by", "latest")
            .addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.removePrefix("/").removePrefix("komik/").removePrefix("manga/")
        return GET("$baseUrl/komik/$slug/", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val details = response.extractNextJs<MGKomikDetailsDto>()

        return SManga.create().apply {
            title = details?.title ?: ""
            author = details?.author
            status = parseStatus(details?.status)
            description = details?.description
            genre = details?.genres?.mapNotNull { it.name }?.joinToString()
            initialized = true
        }
    }

    private fun parseStatus(status: String?) = when (status?.lowercase()) {
        "ongoing", "berjalan" -> SManga.ONGOING
        "completed", "tamat", "selesai" -> SManga.COMPLETED
        "on hold", "delay" -> SManga.ON_HIATUS
        "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.extractNextJs<MGKomikChaptersDto>()
        return result?.chapters?.map {
            SChapter.create().apply {
                url = it.slug?.removePrefix("/") ?: ""
                name = it.title ?: ""
                date_upload = dateFormat.tryParse(it.updatedAt)
            }
        }?.reversed() ?: emptyList()
    }

    // Page List
    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url.removePrefix("/")
        return GET("$baseUrl/$slug/", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.extractNextJs<MGKomikImagesDto>()
        return result?.images?.mapIndexed { index, img ->
            Page(index, "", img)
        } ?: emptyList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
    }

    override fun getFilterList(): FilterList = FilterList()
}
