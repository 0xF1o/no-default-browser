package com.flo.nodefaultbrowser;

import android.app.Activity;
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
            Toast.makeText(this, R.string.no_browsers, Toast.LENGTH_LONG).show();
            finish();
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
     */
    private List<ResolveInfo> findBrowsers() {
        Intent probe = new Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE).setData(Uri.parse("http://example.com/"));
        Map<String, ResolveInfo> unique = new LinkedHashMap<>();
        PackageManager pm = getPackageManager();
        for (ResolveInfo info : pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (info.activityInfo == null || getPackageName().equals(info.activityInfo.packageName)) continue;
            unique.put(info.activityInfo.packageName, info);
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
