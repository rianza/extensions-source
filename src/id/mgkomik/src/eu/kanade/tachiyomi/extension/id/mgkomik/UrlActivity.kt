package eu.kanade.tachiyomi.extension.id.mgkomik

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log

class UrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val intent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", pathSegments[1])
                putExtra("filter", packageName)
            }

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("UrlActivity", e.toString())
            }
        } else {
            Log.e("UrlActivity", "could not parse: ${intent.data}")
        }

        finish()
    }
}
