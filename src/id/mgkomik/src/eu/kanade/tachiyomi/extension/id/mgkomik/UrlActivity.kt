package eu.kanade.tachiyomi.extension.id.mgkomik

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class UrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val slug = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", slug)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: Exception) {
                Log.e("UrlActivity", e.toString())
            }
        }

        finish()
    }
}
