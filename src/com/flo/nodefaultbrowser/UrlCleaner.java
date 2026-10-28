package com.flo.nodefaultbrowser;

import android.content.res.Resources;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Strips tracking junk from URLs with ClearURLs provider rules (see README credits),
 * mirroring the extension: providers match on urlPattern (exceptions opt out), rawRules
 * are plain regexes deleted from the whole URL, rules/referralMarketing are regexes
 * matched against query and fragment keys. The Result carries the ranges that vanished
 * from the original so the UI can paint them red.
 */
class UrlCleaner {

    static class Result {
        final String cleaned;
        final List<int[]> removed;

        Result(String cleaned, List<int[]> removed) {
            this.cleaned = cleaned;
            this.removed = removed;
        }
    }

    private static class Provider {
        final Pattern urlPattern;
        final List<Pattern> exceptions;
        final List<Pattern> rawRules;
        final List<Pattern> keyRules;

        Provider(Pattern urlPattern, List<Pattern> exceptions, List<Pattern> rawRules, List<Pattern> keyRules) {
            this.urlPattern = urlPattern;
            this.exceptions = exceptions;
            this.rawRules = rawRules;
            this.keyRules = keyRules;
        }
    }

    private final List<Provider> providers = new ArrayList<>();

    UrlCleaner(Resources res) {
        try {
            InputStream in = res.openRawResource(R.raw.clearurls_rules);
            JSONObject all = new JSONObject(read(in)).getJSONObject("providers");
            for (Iterator<String> names = all.keys(); names.hasNext(); ) {
                JSONObject provider = all.getJSONObject(names.next());
                providers.add(new Provider(pattern(provider.optString("urlPattern", "$^"), false),
                        patterns(provider.optJSONArray("exceptions"), false),
                        patterns(provider.optJSONArray("rawRules"), false),
                        keys(provider.optJSONArray("rules"), provider.optJSONArray("referralMarketing"))));
            }
        } catch (Exception missing) {
            providers.clear();
        }
    }

    Result clean(String url) {
        StringBuilder text = new StringBuilder(url);
        List<Integer> origin = new ArrayList<>();
        for (int i = 0; i < url.length(); i++) origin.add(i);
        List<int[]> removed = new ArrayList<>();
        for (Provider provider : providers) {
            String current = text.toString();
            if (!provider.urlPattern.matcher(current).find()) continue;
            if (any(provider.exceptions, current)) continue;
            for (Pattern raw : provider.rawRules) cut(raw, text, origin, removed);
            params(text, origin, removed, provider.keyRules);
        }
        Collections.sort(removed, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return a[0] - b[0];
            }
        });
        List<int[]> spans = new ArrayList<>();
        for (int[] span : removed) {
            if (!spans.isEmpty() && span[0] <= spans.get(spans.size() - 1)[1]) spans.get(spans.size() - 1)[1] = Math.max(spans.get(spans.size() - 1)[1], span[1]);
            else spans.add(span);
        }
        return new Result(text.toString(), spans);
    }

    /**
     * Delete every match of a raw rule, remembering the span in original coordinates.
     */
    private void cut(Pattern rule, StringBuilder text, List<Integer> origin, List<int[]> removed) {
        List<int[]> hits = new ArrayList<>();
        Matcher matcher = rule.matcher(text);
        while (matcher.find()) hits.add(new int[] { matcher.start(), matcher.end() });
        for (int i = hits.size() - 1; i >= 0; i--) remove(hits.get(i)[0], hits.get(i)[1], text, origin, removed);
    }

    /**
     * Drop query and fragment parameters whose key matches a rule, then keep the
     * separators sane: leading & is eaten with its param, a removed first param takes
     * its trailing &, and a section emptied completely loses its ? or # as well.
     */
    private void params(StringBuilder text, List<Integer> origin, List<int[]> removed, List<Pattern> keys) {
        int hash = text.indexOf("#");
        int query = text.indexOf("?");
        if (query >= 0 && (hash >= 0 && hash < query)) query = -1;
        if (query >= 0) section(text, origin, removed, keys, query, hash < 0 ? text.length() : hash);
        int fragment = text.indexOf("#");
        if (fragment >= 0) section(text, origin, removed, keys, fragment, text.length());
    }

    private void section(StringBuilder text, List<Integer> origin, List<int[]> removed, List<Pattern> keys, int start, int end) {
        List<int[]> doomed = new ArrayList<>();
        int total = 0, i = start + 1;
        while (i < end) {
            int next = text.indexOf("&", i);
            int stop = next >= 0 && next < end ? next : end;
            int equals = text.indexOf("=", i);
            if (equals < 0 || equals > stop) equals = stop;
            total++;
            String key = text.substring(i, equals);
            for (Pattern key_rule : keys) if (key_rule.matcher(key).find()) { doomed.add(new int[] { i, stop }); break; }
            i = stop + 1;
        }
        List<int[]> cuts = merge(text, doomed, start, end, total);
        for (int n = cuts.size() - 1; n >= 0; n--) remove(cuts.get(n)[0], cuts.get(n)[1], text, origin, removed);
    }

    /**
     * Grow doomed spans over the & that belongs to them and merge what touches.
     */
    private List<int[]> merge(StringBuilder text, List<int[]> doomed, int start, int end, int total) {
        List<int[]> cuts = new ArrayList<>();
        if (doomed.isEmpty()) return cuts;
        boolean all = doomed.size() == total;
        for (int[] hit : doomed) {
            int s = hit[0], e = hit[1];
            if (all) s = start;
            else if (s > start + 1) s--;
            else if (e < end) e++;
            if (!cuts.isEmpty() && s <= cuts.get(cuts.size() - 1)[1]) cuts.get(cuts.size() - 1)[1] = Math.max(cuts.get(cuts.size() - 1)[1], e);
            else cuts.add(new int[] { s, e });
        }
        if (!all && cuts.get(0)[0] == start + 1) while (cuts.get(0)[1] < end && text.charAt(cuts.get(0)[1]) == '&') cuts.get(0)[1]++;
        return cuts;
    }

    private void remove(int from, int to, StringBuilder text, List<Integer> origin, List<int[]> removed) {
        removed.add(new int[] { origin.get(from), origin.get(to - 1) + 1 });
        text.delete(from, to);
        for (int i = from; i < to; i++) origin.remove(from);
    }

    private boolean any(List<Pattern> patterns, String text) {
        for (Pattern pattern : patterns) if (pattern.matcher(text).find()) return true;
        return false;
    }

    private List<Pattern> keys(JSONArray... lists) {
        List<Pattern> compiled = new ArrayList<>();
        for (JSONArray list : lists) for (Pattern pattern : patterns(list, true)) compiled.add(pattern);
        return compiled;
    }

    private List<Pattern> patterns(JSONArray array, boolean anchored) {
        List<Pattern> compiled = new ArrayList<>();
        if (array == null) return compiled;
        for (int i = 0; i < array.length(); i++) {
            Pattern pattern = pattern(array.optString(i), anchored);
            if (pattern != null) compiled.add(pattern);
        }
        return compiled;
    }

    /**
     * JS and Java regexes are close enough here; anything that refuses to compile is skipped.
     */
    private Pattern pattern(String regex, boolean anchored) {
        try {
            return Pattern.compile(anchored ? "^" + regex + "$" : regex, Pattern.CASE_INSENSITIVE);
        } catch (Exception unsupported) {
            return null;
        }
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n = in.read(buffer); n > 0; n = in.read(buffer)) out.write(buffer, 0, n);
        in.close();
        return out.toString("UTF-8");
    }
}
