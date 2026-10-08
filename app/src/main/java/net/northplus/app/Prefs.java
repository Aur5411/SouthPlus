package net.northplus.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** 应用偏好设置。 */
public class Prefs {

    private static final String FILE = "np_prefs";
    private static final String K_NIGHT = "night";
    private static final String K_NO_IMG = "no_image";
    private static final String K_AD_BLOCK = "ad_block";
    private static final String K_ZOOM = "pinch_zoom";
    private static final String K_INAPP_SITE = "inapp_site";
    private static final String K_INAPP_EXTERNAL = "inapp_external";
    private static final String K_FONT = "font_scale";
    private static final String K_SCRIPTS = "userscripts_enabled";
    private static final String K_MOBILE = "mobile_mode";
    private static final String K_SCRIPT_SOURCE = "userscript_source";
    private static final String K_SITE_HOST = "site_host";
    private static final String K_SITE_PICKED = "site_auto_picked";

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean night() {
        return sp.getBoolean(K_NIGHT, false);
    }

    public void setNight(boolean v) {
        sp.edit().putBoolean(K_NIGHT, v).apply();
        applyNightMode(v);
    }

    /** 应用整体（含网页）的夜间模式。 */
    public static void applyNightMode(boolean night) {
        AppCompatDelegate.setDefaultNightMode(
                night ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public boolean noImage() {
        return sp.getBoolean(K_NO_IMG, false);
    }

    public void setNoImage(boolean v) {
        sp.edit().putBoolean(K_NO_IMG, v).apply();
    }

    public boolean adBlock() {
        return sp.getBoolean(K_AD_BLOCK, true);
    }

    public void setAdBlock(boolean v) {
        sp.edit().putBoolean(K_AD_BLOCK, v).apply();
    }

    /** 双指缩放。桌面版页面是 920px 定宽，默认开启缩放才好读。 */
    public boolean pinchZoom() {
        return sp.getBoolean(K_ZOOM, true);
    }

    public void setPinchZoom(boolean v) {
        sp.edit().putBoolean(K_ZOOM, v).apply();
    }

    /**
     * 是否使用移动版网页。默认 false —— 用电脑版页面 + 桌面 UA，
     * 这样才能和站点功能（含只在桌面 DOM 上生效的用户脚本）保持一致。
     */
    public boolean mobileMode() {
        return sp.getBoolean(K_MOBILE, false);
    }

    public void setMobileMode(boolean v) {
        sp.edit().putBoolean(K_MOBILE, v).apply();
    }

    /** 站内链接是否在应用内打开。默认开——本应用本身就是浏览器。 */
    public boolean inAppSite() {
        return sp.getBoolean(K_INAPP_SITE, true);
    }

    public void setInAppSite(boolean v) {
        sp.edit().putBoolean(K_INAPP_SITE, v).apply();
    }

    /** 站外链接是否在应用内打开。默认开；关掉后点击即交给系统浏览器。 */
    public boolean inAppExternal() {
        return sp.getBoolean(K_INAPP_EXTERNAL, true);
    }

    public void setInAppExternal(boolean v) {
        sp.edit().putBoolean(K_INAPP_EXTERNAL, v).apply();
    }

    /** 用户脚本总开关：关闭后不再向网页注册脚本宿主。 */
    public boolean scriptsEnabled() {
        return sp.getBoolean(K_SCRIPTS, true);
    }

    public void setScriptsEnabled(boolean v) {
        sp.edit().putBoolean(K_SCRIPTS, v).apply();
    }

    /** 上次下载脚本成功的源（存 label）；空串表示还没成功过。 */
    public String userscriptSource() {
        return sp.getString(K_SCRIPT_SOURCE, "");
    }

    public void setUserscriptSource(String label) {
        sp.edit().putString(K_SCRIPT_SOURCE, label == null ? "" : label).apply();
    }

    /** 生效的访问域名；空串表示用内置默认。 */
    public String siteHost() {
        return sp.getString(K_SITE_HOST, "");
    }

    public void setSiteHost(String host) {
        sp.edit().putString(K_SITE_HOST, host == null ? "" : host).apply();
    }

    /** 是否已经做过一次「自动挑可达站点」。 */
    public boolean siteAutoPicked() {
        return sp.getBoolean(K_SITE_PICKED, false);
    }

    public void setSiteAutoPicked(boolean v) {
        sp.edit().putBoolean(K_SITE_PICKED, v).apply();
    }

    /** 网页字号缩放百分比，跟随系统无障碍设置。 */    public int fontScale(Context c) {
        int system = Math.round(c.getResources().getConfiguration().fontScale * 100f);
        return sp.getInt(K_FONT, Math.max(80, Math.min(system, 160)));
    }

    public void setFontScale(int v) {
        sp.edit().putInt(K_FONT, v).apply();
    }
}
