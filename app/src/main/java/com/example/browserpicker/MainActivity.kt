package com.example.browserpicker

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BrowserAdapter
    private var url: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Grab the URL from the intent that launched us
        val action = intent.action
        val uri = intent.data

        if (action == Intent.ACTION_VIEW && uri != null) {
            url = uri.toString()
        } else {
            // Fallback: no URL, just show browsers anyway
            url = null
        }

        // Setup RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Find all browsers
        val browsers = findBrowsers()

        if (browsers.isEmpty()) {
            Toast.makeText(this, "No browsers installed!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Setup adapter
        adapter = BrowserAdapter(browsers) { browser ->
            launchBrowser(browser)
        }
        recyclerView.adapter = adapter

        // Auto-launch if only one browser
        if (browsers.size == 1) {
            launchBrowser(browsers[0])
        }
    }

    /**
     * Query PackageManager for all apps that can handle web URLs.
     * Excludes our own app and system components.
     */
    private fun findBrowsers(): List<BrowserInfo> {
        val browsers = mutableListOf<BrowserInfo>()
        val pm = packageManager
        val handledPackages = mutableSetOf<String>()

        // Query with different category+scheme combos so we catch all browsers.
        // Some browsers declare BROWSABLE, some declare DEFAULT, some both.
        val queries = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                data = android.net.Uri.parse("https://example.com")
            },
            Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                data = android.net.Uri.parse("https://example.com")
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("http://example.com")
            }
        )

        for (queryIntent in queries) {
            val resolveInfos = pm.queryIntentActivities(queryIntent, PackageManager.MATCH_ALL)
            for (ri in resolveInfos) {
                val pkg = ri.activityInfo.packageName
                if (pkg == "com.example.browserpicker") continue
                if (pkg.startsWith("android.")) continue
                if (handledPackages.add(pkg)) {
                    browsers.add(BrowserInfo.fromPackageManager(pm, ri))
                }
            }
        }

        return browsers
    }

    /**
     * Launch the chosen browser with the captured URL.
     */
    private fun launchBrowser(browser: BrowserInfo) {
        url?.let { url ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(browser.packageName)
                data = android.net.Uri.parse(url)
            }
            try {
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't launch ${browser.label}", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            // No URL (app opened directly by user) — just show a message
            Toast.makeText(this, "Tap a browser to open it, or open a URL from another app", Toast.LENGTH_LONG).show()
        }
    }
}
