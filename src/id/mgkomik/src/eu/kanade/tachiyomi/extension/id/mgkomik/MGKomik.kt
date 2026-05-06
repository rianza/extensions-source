package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MGKomik :
    Madara(
        "MG Komik",
        "https://web.mgkomik.cc",
        "id",
        SimpleDateFormat("dd MMM yy", Locale("id")),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = false

    override val mangaSubString = "komik"

    override fun headersBuilder() = super.headersBuilder().apply {
        set("Sec-Fetch-Site", "same-origin")
        set("Upgrade-Insecure-Requests", "1")
        set("Referer", "$baseUrl/")
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder().apply {
                if (request.header("X-Requested-With") == null && request.url.encodedPath.contains("admin-ajax.php")) {
                    set("X-Requested-With", "XMLHttpRequest")
                }
            }.build()

            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .rateLimit(4, 1)
        .build()

    // Popular
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.selectFirst("div.post-title a, h3 a, a")
        setUrlWithoutDomain(titleElement!!.attr("abs:href"))
        title = titleElement.attr("title").ifBlank { titleElement.text() }
        thumbnail_url = element.selectFirst("img")?.let {
            it.attr("abs:src").ifBlank { it.attr("abs:data-src") }
        }
    }

    // Latest
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Filters
    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }

        val filters = super.getFilterList().list.toMutableList()

        if (genresList.isNotEmpty()) {
            filters += listOf(
                Filter.Separator(),
                GenreContentFilter(
                    title = intl["genre_filter_title"],
                    options = genresList.map { it.name to it.id },
                ),
            )
        }

        return FilterList(filters)
    }

    private class GenreContentFilter(title: String, options: List<Pair<String, String>>) :
        UriPartFilter(
            title,
            options.toTypedArray(),
        )

    override fun genresRequest() = GET("$baseUrl/$mangaSubString", headers)

    override fun parseGenres(document: Document): List<Genre> {
        val genres = mutableListOf<Genre>()
        genres += Genre("All", "")
        genres += document.select(".row.genres li a, .genrez li label").map { a ->
            if (a.tagName() == "label") {
                Genre(a.text(), a.parent()?.selectFirst("input")?.attr("value") ?: "")
            } else {
                Genre(a.text(), a.absUrl("href"))
            }
        }
        return genres
    }
}
