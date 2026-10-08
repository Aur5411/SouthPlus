package net.northplus.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 访问站点选择页：一排开关行，打开哪一个就用哪个域名（互斥）。
 * 也可以测试各站点连通性，或手动输入域名。
 */
public class SitesActivity extends AppCompatActivity {

    private LinearLayout siteList;
    private TextView currentView;
    private final List<SwitchCompat> switches = new ArrayList<>();
    private final List<TextView> states = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sites);

        siteList = findViewById(R.id.siteList);
        currentView = findViewById(R.id.sitesCurrent);

        findViewById(R.id.sitesBack).setOnClickListener(v -> finish());
        findViewById(R.id.siteTest).setOnClickListener(v -> probe());
        findViewById(R.id.siteCustom).setOnClickListener(v -> promptCustom());

        build();
    }

    private void build() {
        siteList.removeAllViews();
        switches.clear();
        states.clear();

        LayoutInflater inf = LayoutInflater.from(this);
        final Site.Entry[] all = Site.BUILT_IN;
        for (int i = 0; i < all.length; i++) {
            final Site.Entry e = all[i];
            View row = inf.inflate(R.layout.row_site, siteList, false);

            ((TextView) row.findViewById(R.id.siteName)).setText(e.label);
            ((TextView) row.findViewById(R.id.siteHost)).setText(e.host);

            TextView state = row.findViewById(R.id.siteState);
            SwitchCompat sw = row.findViewById(R.id.siteSwitch);
            sw.setChecked(Site.isCurrent(e.host));
            sw.setOnCheckedChangeListener((b, checked) -> {
                if (checked) {
                    apply(e);
                } else if (Site.isCurrent(e.host)) {
                    b.setChecked(true);   // 不允许把生效中的站点关掉
                }
            });
            row.setOnClickListener(v -> {
                if (!Site.isCurrent(e.host)) sw.setChecked(true);
            });

            switches.add(sw);
            states.add(state);
            siteList.addView(row);

            if (i < all.length - 1) {
                siteList.addView(divider());
            }
        }
        refreshCurrent();
    }

    private View divider() {
        View div = new View(this);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        div.setBackgroundColor(ContextCompat.getColor(this, R.color.np_divider));
        return div;
    }

    private void refreshCurrent() {
        currentView.setText(Site.displayName(Site.host()) + " · " + Site.host());
    }

    /** 切换站点并保存；其余开关全部关掉。 */
    private void apply(Site.Entry target) {
        Site.setHost(target.host);
        App.prefs.setSiteHost(Site.host());
        for (int i = 0; i < switches.size(); i++) {
            final Site.Entry e = Site.BUILT_IN[i];
            SwitchCompat sw = switches.get(i);
            boolean on = Site.isCurrent(e.host);
            if (sw.isChecked() == on) continue;
            sw.setOnCheckedChangeListener(null);
            sw.setChecked(on);
            sw.setOnCheckedChangeListener((b, checked) -> {
                if (checked) apply(e);
                else if (Site.isCurrent(e.host)) b.setChecked(true);
            });
        }
        refreshCurrent();
        Toast.makeText(this, getString(R.string.site_switched, target.host),
                Toast.LENGTH_SHORT).show();
    }

    private void probe() {
        Toast.makeText(this, R.string.site_testing, Toast.LENGTH_SHORT).show();
        SiteProbe.probe(reachable -> {
            if (isFinishing()) return;
            boolean any = false;
            for (int i = 0; i < states.size() && i < reachable.length; i++) {
                TextView tv = states.get(i);
                tv.setVisibility(View.VISIBLE);
                boolean ok = reachable[i];
                if (ok) any = true;
                tv.setText(ok ? R.string.site_reachable : R.string.site_unreachable);
                tv.setTextColor(ContextCompat.getColor(this,
                        ok ? android.R.color.holo_green_dark : android.R.color.holo_red_light));
            }
            if (!any) {
                Toast.makeText(this, R.string.site_none_reachable, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void promptCustom() {
        final EditText et = new EditText(this);
        et.setHint(R.string.site_custom_hint);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        et.setPadding(dp(20), dp(12), dp(20), dp(12));
        new AlertDialog.Builder(this)
                .setTitle(R.string.site_custom_title)
                .setView(et)
                .setNegativeButton("取消", null)
                .setPositiveButton("使用", (d, w) -> {
                    Editable e = et.getText();
                    String raw = e == null ? "" : e.toString().trim();
                    if (TextUtils.isEmpty(raw) || !Site.setHost(raw)) {
                        Toast.makeText(this, R.string.site_bad_host, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    App.prefs.setSiteHost(Site.host());
                    build();
                    Toast.makeText(this, getString(R.string.site_switched, Site.host()),
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private int dp(float v) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
