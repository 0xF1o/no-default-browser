package com.flo.nodefaultbrowser;

import android.app.Activity;
import android.app.AlertDialog;
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

    private String url;

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

    private final StringBuilder probes = new StringBuilder();

    /**
     * Every app that can render a web page, one entry per package, sorted by name.
     */
    private List<ResolveInfo> findBrowsers() {
        PackageManager pm = getPackageManager();
        Map<String, ResolveInfo> unique = new LinkedHashMap<>();
        for (String scheme : new String[] { "http", "https" }) {
            collect(pm, unique, scheme + "://example.com/", Intent.CATEGORY_BROWSABLE);
            collect(pm, unique, scheme + "://example.com/", Intent.CATEGORY_DEFAULT);
            collect(pm, unique, scheme + "://", null);
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

    /**
     * One probe run, results merged into unique, count remembered for the empty-list message.
     */
    private void collect(PackageManager pm, Map<String, ResolveInfo> unique, String url, String category) {
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (category != null) probe.addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        probes.append(url.split("://")[0]).append(category == null ? "-" : category.substring(category.lastIndexOf('.') + 1)).append("=").append(found.size()).append("\n");
        for (ResolveInfo info : found) {
            if (info.activityInfo == null || getPackageName().equals(info.activityInfo.packageName)) continue;
            if (!unique.containsKey(info.activityInfo.packageName)) unique.put(info.activityInfo.packageName, info);
        }
    }

    /**
     * Hand the URL to the chosen browser, or just open the browser if we were started from the launcher.
     */
    private void launch(ResolveInfo browser) {
        PackageManager pm = getPackageManager();
        Intent open = url == null
                ? new Intent(Intent.ACTION_MAIN).setClassName(browser.activityInfo.packageName, browser.activityInfo.name)
                : new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(browser.activityInfo.packageName);
        try {
            startActivity(open);
            finish();
        } catch (Exception error) {
            Toast.makeText(this, getString(R.string.launch_failed, browser.loadLabel(pm)), Toast.LENGTH_LONG).show();
        }
    }
}
