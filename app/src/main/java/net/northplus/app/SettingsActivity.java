package net.northplus.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
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

public class SettingsActivity extends AppCompatActivity {

    private Prefs prefs;

    private TextView siteCurrentName;
    private TextView siteCurrentHost;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = App.prefs;

        findViewById(R.id.setBack).setOnClickListener(v -> finish());

        bindSwitch(R.id.swMobile, prefs.mobileMode(), prefs::setMobileMode);
        bindSwitch(R.id.swNight, prefs.night(), v -> {
            prefs.setNight(v);
            recreate();
        });
        bindSwitch(R.id.swNoImage, prefs.noImage(), prefs::setNoImage);
        bindSwitch(R.id.swAdBlock, prefs.adBlock(), prefs::setAdBlock);
        bindSwitch(R.id.swZoom, prefs.pinchZoom(), prefs::setPinchZoom);
        bindSwitch(R.id.swInAppSite, prefs.inAppSite(), prefs::setInAppSite);
        bindSwitch(R.id.swInAppExternal, prefs.inAppExternal(), prefs::setInAppExternal);

        findViewById(R.id.rowMobile).setOnClickListener(v -> toggle(findViewById(R.id.swMobile)));
        findViewById(R.id.rowNight).setOnClickListener(v -> toggle(findViewById(R.id.swNight)));
        findViewById(R.id.rowNoImage).setOnClickListener(v -> toggle(findViewById(R.id.swNoImage)));
        findViewById(R.id.rowAdBlock).setOnClickListener(v -> toggle(findViewById(R.id.swAdBlock)));
        findViewById(R.id.rowZoom).setOnClickListener(v -> toggle(findViewById(R.id.swZoom)));
        findViewById(R.id.rowInAppSite).setOnClickListener(v -> toggle(findViewById(R.id.swInAppSite)));
        findViewById(R.id.rowInAppExternal).setOnClickListener(v -> toggle(findViewById(R.id.swInAppExternal)));

        findViewById(R.id.btnClear).setOnClickListener(v -> confirmClear());
        findViewById(R.id.btnUserscripts).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, UserscriptsActivity.class)));

        siteCurrentName = findViewById(R.id.siteCurrentName);
        siteCurrentHost = findViewById(R.id.siteCurrentHost);
        findViewById(R.id.rowSite).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, SitesActivity.class)));
        updateSiteRow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSiteRow();
    }

    /** 顶部的「访问站点」行显示当前域名。 */
    private void updateSiteRow() {
        if (siteCurrentName == null) return;
        siteCurrentName.setText("当前：" + Site.displayName(Site.host()));
        siteCurrentHost.setText(Site.host());
    }

    // ------------------------------------------------------------------ 开关行

    private interface BoolSetter {
        void set(boolean v);
    }

    private void bindSwitch(int id, boolean initial, BoolSetter setter) {
        SwitchCompat sw = findViewById(id);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((b, v) -> setter.set(v));
    }

    private static void toggle(android.widget.CompoundButton sw) {
        sw.setChecked(!sw.isChecked());
    }

    // ------------------------------------------------------------------ 清理

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.set_clearcookie)
                .setMessage("将清除登录 Cookie、网页缓存与本地存储。\n下次打开发帖相关页面时需要重新登录。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (d, w) -> {
                    try {
                        CookieManager cm = CookieManager.getInstance();
                        cm.removeAllCookies(null);
                        cm.flush();
                        WebStorage.getInstance().deleteAllData();
                        WebView wv = new WebView(SettingsActivity.this);
                        wv.clearCache(true);
                        wv.clearHistory();
                        wv.clearFormData();
                        wv.destroy();
                        Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "清除失败：" + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private int dp(float v) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
