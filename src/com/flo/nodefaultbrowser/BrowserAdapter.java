package com.flo.nodefaultbrowser;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.List;

/**
 * List of browser apps: icon + name.
 */
class BrowserAdapter extends BaseAdapter {

    private final List<ResolveInfo> browsers;
    private final LayoutInflater inflater;

    BrowserAdapter(Context context, List<ResolveInfo> browsers) {
        this.browsers = browsers;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return browsers.size();
    }

    @Override
    public ResolveInfo getItem(int position) {
        return browsers.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        View view = row == null ? inflater.inflate(R.layout.browser_item, parent, false) : row;
        Context context = view.getContext();
        PackageManager pm = context.getPackageManager();
        ResolveInfo browser = browsers.get(position);
        ((ImageView) view.findViewById(R.id.browser_icon)).setImageDrawable(browser.loadIcon(pm));
        ((TextView) view.findViewById(R.id.browser_name)).setText(browser.loadLabel(pm));
        return view;
    }
}
