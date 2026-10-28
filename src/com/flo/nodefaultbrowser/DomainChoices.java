package com.flo.nodefaultbrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * Remembers which browser was picked for which domain, in a plain
 * SharedPreferences file (domain -> package name).
 */
class DomainChoices {

    private static final String PREFS = "domain_choices";

    private final SharedPreferences prefs;

    DomainChoices(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Lowercase host without www., or null when the link has no usable domain
     * (funny schemes, opaque data, garbage) in which case we neither save nor highlight.
     */
    static String domainOf(String url) {
        String host = Uri.parse(url).getHost();
        if (host == null) return null;
        String lower = host.toLowerCase();
        return lower.startsWith("www.") ? lower.substring(4) : lower;
    }

    String get(String domain) {
        return prefs.getString(domain, null);
    }

    void put(String domain, String packageName) {
        prefs.edit().putString(domain, packageName).apply();
    }

    boolean isClean(String domain) {
        return prefs.getBoolean("clean:" + domain, false);
    }

    void putClean(String domain, boolean on) {
        prefs.edit().putBoolean("clean:" + domain, on).apply();
    }
}
