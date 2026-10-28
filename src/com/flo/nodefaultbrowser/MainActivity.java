package com.flo.nodefaultbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intercepts http/https links and forwards them to a browser picked by the user,
 * so no default browser ever has to be set.
 */
public class MainActivity extends Activity {

    private static final String HTML = "text/html";
    private static final String[] SCHEMES = { "http", "https" };
    private static final Object COPY = new Object();
    private static final Object QR = new Object();
    private static final int COLLAPSED = 2;
    private static final Pattern LINK = Pattern.compile("https?://[^\\s\"'<>\\[\\]]+");

    private String url;
    private String domain;
    private DomainChoices choices;
    private UrlCleaner cleaner;
    private UrlCleaner.Result result;
    private boolean cleaning;
    private final StringBuilder probes = new StringBuilder();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        url = linkFrom(intent);
        choices = new DomainChoices(this);
        domain = url == null ? null : DomainChoices.domainOf(url);
        cleaning = domain != null && choices.isClean(domain);

        final TextView link = findViewById(R.id.url);
        link.setMaxLines(COLLAPSED);
        link.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                link.setMaxLines(link.getMaxLines() == COLLAPSED ? 24 : COLLAPSED);
            }
        });
        link.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (url != null) copyUrl();
                return url != null;
            }
        });

        final CheckBox clean = findViewById(R.id.clean);
        View cleanRow = findViewById(R.id.clean_row);
        cleanRow.setVisibility(url == null ? View.GONE : View.VISIBLE);
        cleanRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clean.toggle();
            }
        });
        if (url == null) link.setText(R.string.no_url);
        else {
            clean.setChecked(cleaning);
            clean.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean on) {
                    cleaning = on;
                    if (domain != null) choices.putClean(domain, on);
                    showUrl(link);
                }
            });
            showUrl(link);
        }

        List<ResolveInfo> browsers = findBrowsers();
        if (browsers.isEmpty()) {
            showDiagnostics();
            return;
        }

        ListView list = findViewById(R.id.browser_list);
        if (url != null) {
            list.addHeaderView(iconRow(R.drawable.ic_copy, R.string.copy_link), COPY, true);
            list.addHeaderView(iconRow(R.drawable.ic_qr, R.string.show_qr), QR, true);
        }
        list.setAdapter(new BrowserAdapter(this, browsers, domain == null ? null : choices.get(domain)));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object picked = parent.getItemAtPosition(position);
                if (picked == COPY) copyUrl();
                else if (picked == QR) showQr();
                else pick((ResolveInfo) picked);
            }
        });

        if (url != null && browsers.size() == 1) launch(browsers.get(0));
    }

    /**
     * The link we were started with: the data of a VIEW intent, or the first URL found
     * in the text of a SHARE (apps like Chrome share "Title https://...", sometimes with
     * trailing punctuation we trim so the browser does not choke on it).
     */
    private String linkFrom(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) return intent.getData().toString();
        if (!Intent.ACTION_SEND.equals(intent.getAction())) return null;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) return null;
        Matcher found = LINK.matcher(text);
        if (!found.find()) return null;
        String link = found.group();
        while (link.length() > 0 && ".!?,:;)".indexOf(link.charAt(link.length() - 1)) >= 0) link = link.substring(0, link.length() - 1);
        return link;
    }

    /**
     * Extra rows on top of the browser list: hand the link to anything else, or paint it
     * as a QR code for devices without a cable (TVs, consoles, a friend's phone).
     */
    private View iconRow(int icon, int label) {
        View row = getLayoutInflater().inflate(R.layout.browser_item, findViewById(R.id.browser_list), false);
        ((ImageView) row.findViewById(R.id.browser_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.browser_name)).setText(label);
        return row;
    }

    /**
     * The link as QR modules painted to a bitmap: black on white with the standard four
     * module quiet zone, scaled so the picture fills roughly three quarters of the screen.
     */
    private void showQr() {
        boolean[][] modules = QrCode.encode(effectiveUrl());
        if (modules == null) {
            Toast.makeText(this, R.string.qr_too_long, Toast.LENGTH_SHORT).show();
            return;
        }
        int size = modules.length;
        int scale = Math.max(4, getResources().getDisplayMetrics().widthPixels * 3 / (4 * (size + 8)));
        Bitmap bitmap = Bitmap.createBitmap((size + 8) * scale, (size + 8) * scale, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        Paint ink = new Paint();
        ink.setColor(Color.BLACK);
        for (int row = 0; row < size; row++) for (int col = 0; col < size; col++) if (modules[row][col]) canvas.drawRect((col + 4f) * scale, (row + 4f) * scale, (col + 5f) * scale, (row + 5f) * scale, ink);
        ImageView image = new ImageView(this);
        image.setImageBitmap(bitmap);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(pad, pad, pad, pad);
        frame.addView(image);
        new AlertDialog.Builder(this).setView(frame).show();
    }

    /**
     * Remember the pick for this domain (if the link had one) and hand the URL over.
     */
    private void pick(ResolveInfo browser) {
        if (domain != null) choices.put(domain, browser.activityInfo.packageName);
        launch(browser);
    }

    /**
     * The URL text: plain, or with the parts the cleaner would drop painted red.
     */
    private void showUrl(TextView link) {
        if (!cleaning || cleaned().removed.isEmpty()) {
            link.setText(url);
            return;
        }
        SpannableStringBuilder preview = new SpannableStringBuilder(url);
        for (int[] span : cleaned().removed) preview.setSpan(new ForegroundColorSpan(Color.RED), span[0], span[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        link.setText(preview);
    }

    private UrlCleaner.Result cleaned() {
        if (result == null) {
            if (cleaner == null) cleaner = new UrlCleaner(getResources());
            result = cleaner.clean(url);
        }
        return result;
    }

    /**
     * What we copy and launch: the cleaned link while the box is ticked.
     */
    private String effectiveUrl() {
        return url == null || !cleaning ? url : cleaned().cleaned;
    }

    private void copyUrl() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), effectiveUrl()));
        Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show();
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
            Uri target = Uri.parse(effectiveUrl());
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
