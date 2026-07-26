package com.example.browserpicker

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
        Log.d("BrowserPicker", "Found ${browsers.size} browsers")

        if (browsers.isEmpty()) {
            Toast.makeText(this, "No browsers installed! Try installing a browser.", Toast.LENGTH_LONG).show()
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

        Log.d("BrowserPicker", "Attempting to find browsers...")

        // Try multiple different approaches to detect browsers
        val queries = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://example.com")
                addCategory(Intent.CATEGORY_BROWSABLE)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("http://example.com")
                addCategory(Intent.CATEGORY_BROWSABLE)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://example.com")
                addCategory(Intent.CATEGORY_DEFAULT)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("http://example.com")
                addCategory(Intent.CATEGORY_DEFAULT)
            }
        )

        for (intent in queries) {
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            Log.d("BrowserPicker", "Query found ${resolveInfos.size} resolve infos")
            
            for (ri in resolveInfos) {
                val pkg = ri.activityInfo.packageName
                Log.d("BrowserPicker", "Found browser package: $pkg")
                // Skip our own app and system components
                if (pkg == "com.example.browserpicker") continue
                if (pkg.startsWith("android.")) continue
                
                if (handledPackages.add(pkg)) {
                    browsers.add(BrowserInfo.fromPackageManager(pm, ri))
                    Log.d("BrowserPicker", "Added browser: ${ri.loadLabel(pm)}")
                }
            }
        }

        // If still no browsers found, try to find any app that handles URLs
        if (browsers.isEmpty()) {
            Log.d("BrowserPicker", "No browsers found, trying to find apps that handle URLs")
            val allIntent = Intent(Intent.ACTION_VIEW)
            allIntent.data = android.net.Uri.parse("http://example.com")
            
            val allResolveInfos = pm.queryIntentActivities(allIntent, PackageManager.MATCH_ALL)
            for (ri in allResolveInfos) {
                val pkg = ri.activityInfo.packageName
                // Filter out our own app, system apps, and apps that are not browsers
                if (pkg == "com.example.browserpicker") continue
                if (pkg.startsWith("android.")) continue
                
                // Check if this app actually has a BROWSABLE category
                val checkIntent = Intent(Intent.ACTION_VIEW)
                checkIntent.data = android.net.Uri.parse("https://example.com")
                checkIntent.addCategory(Intent.CATEGORY_BROWSABLE)
                
                if (pm.queryIntentActivities(checkIntent, PackageManager.MATCH_ALL).isNotEmpty()) {
                    if (handledPackages.add(pkg)) {
                        browsers.add(BrowserInfo.fromPackageManager(pm, ri))
                        Log.d("BrowserPicker", "Added browser (fallback): ${ri.loadLabel(pm)}")
                    }
                }
            }
        }

        Log.d("BrowserPicker", "Final count: ${browsers.size} browsers")
        return browsers
    }
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
                Log.e("BrowserPicker", "Failed to launch ${browser.label}", e)
                Toast.makeText(this, "Couldn't launch ${browser.label}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            // No URL (app opened directly by user) — just show a message
            Toast.makeText(this, "Tap a browser to open it, or open a URL from another app", Toast.LENGTH_LONG).show()
        }
    }
}
