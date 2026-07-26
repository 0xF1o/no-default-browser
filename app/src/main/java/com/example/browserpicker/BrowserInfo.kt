package com.example.browserpicker

import android.graphics.drawable.Drawable
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Simple data class holding info about an installed browser app.
 */
data class BrowserInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
) {
    companion object {
        fun fromPackageManager(pm: PackageManager, ri: ResolveInfo): BrowserInfo {
            return BrowserInfo(
                packageName = ri.activityInfo.packageName,
                label = ri.loadLabel(pm).toString(),
                icon = ri.loadIcon(pm)
            )
        }
    }
}
