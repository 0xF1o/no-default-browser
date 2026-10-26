package com.flo.nodefaultbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Intercepts http/https links and forwards them to a browser picked by the user,
 * so no default browser ever has to be set.
 */
public class MainActivity extends Activity {

    private static final String HTML = "text/html";
    private static final String[] SCHEMES = { "http", "https" };

    private String url;
    private final StringBuilder probes = new StringBuilder();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        url = Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null ? intent.getData().toString() : null;

        TextView header = findViewById(R.id.header);
        header.setText(url == null ? getString(R.string.no_url) : getString(R.string.choose_browser) + "\n" + url);

        List<ResolveInfo> browsers = findBrowsers();
        if (browsers.isEmpty()) {
            showDiagnostics();
            return;
        }

        ListView list = findViewById(R.id.browser_list);
        list.setAdapter(new BrowserAdapter(this, browsers));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launch(browsers.get(position));
            }
        });

        if (url != null && browsers.size() == 1) launch(browsers.get(0));
    }

    /**
     * Every app that can render a web page, one entry per package, sorted by name.
     * Browsers declare their web filters two different ways: some list only the scheme,
     * newer ones (Chrome 14x on Android 16+) pair the scheme with a MIME type, so probe
     * both flavours and merge. The categories vary between browsers the same way.
     */
    private List<ResolveInfo> findBrowsers() {
        PackageManager pm = getPackageManager();
        Map<String, ResolveInfo> unique = new LinkedHashMap<>();
        for (String scheme : SCHEMES) {
            for (String type : new String[] { null, HTML }) {
                for (String category : new String[] { null, Intent.CATEGORY_BROWSABLE }) {
                    collect(pm, unique, scheme + "://example.com/", category, type);
                }
            }
        }
        List<ResolveInfo> browsers = new ArrayList<>(unique.values());
        Collections.sort(browsers, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
        return browsers;
    }

    /**
     * One probe run, results merged into unique, count remembered for the empty-list dialog.
     */
    private void collect(PackageManager pm, Map<String, ResolveInfo> unique, String link, String category, String type) {
        Uri uri = Uri.parse(link);
        Intent probe = type == null ? new Intent(Intent.ACTION_VIEW, uri) : new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type);
        if (category != null) probe.addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        probes.append(link.split("://")[0]).append(type == null ? " any-type" : " " + type);
        probes.append(category == null ? "" : " browsable").append(" -> ").append(found.size()).append("\n");
        for (ResolveInfo info : found) {
            if (info.activityInfo == null || getPackageName().equals(info.activityInfo.packageName)) continue;
            if (!unique.containsKey(info.activityInfo.packageName)) unique.put(info.activityInfo.packageName, info);
        }
    }

    /**
     * Hand the URL to the chosen browser, or just open the browser if we were started from the
     * launcher. Both intent flavours are tried because a browser that filters on a MIME type
     * rejects the plain one and vice versa.
     */
    private void launch(ResolveInfo browser) {
        String pkg = browser.activityInfo.packageName;
        List<Intent> candidates = new ArrayList<>();
        if (url == null) {
            candidates.add(new Intent(Intent.ACTION_MAIN).setClassName(pkg, browser.activityInfo.name));
        } else {
            Uri target = Uri.parse(url);
            candidates.add(new Intent(Intent.ACTION_VIEW, target).setPackage(pkg));
            candidates.add(new Intent(Intent.ACTION_VIEW).setDataAndType(target, HTML).setPackage(pkg));
        }
        for (Intent open : candidates) {
            try {
                startActivity(open);
                finish();
                return;
            } catch (ActivityNotFoundException missing) {
                // try the next flavour
            }
        }
        Toast.makeText(this, getString(R.string.launch_failed, browser.loadLabel(getPackageManager())), Toast.LENGTH_LONG).show();
    }

    /**
     * Nothing matched: show what PackageManager answered per probe, so a device quirk is
     * visible on screen instead of lost in a toast. Long-press to copy.
     */
    private void showDiagnostics() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.no_browsers)
                .setMessage(probes.toString())
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }
}
