package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            "Semua" to "",
            "Ongoing" to "Ongoing",
            "Selesai" to "Selesai",
            "Hiatus" to "Hiatus",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        arrayOf(
            "Semua" to "",
            "Manga" to "Manga",
            "Manhua" to "Manhua",
            "Manhwa" to "Manhwa",
        ),
    )

class SortFilter :
    SelectFilter(
        "Urutkan berdasarkan",
        arrayOf(
            "Populer" to "views",
            "Terbaru" to "latest",
            "A-Z" to "alphabet",
        ),
    )
