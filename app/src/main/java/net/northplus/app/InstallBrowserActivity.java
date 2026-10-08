package net.northplus.app;

import android.content.Intent;
import android.os.Bundle;

/**
 * 应用内脚本安装浏览器：站外站点也在本界面内浏览，
 * 这样点「安装此脚本」触发的 {@code .user.js} 请求才能被接管安装。
 */
public class InstallBrowserActivity extends BaseWebActivity {

    private String url = Site.home();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent i = getIntent();
        if (i != null) {
            String u = i.getStringExtra(Const.EXTRA_URL);
            if (u != null && !u.isEmpty()) url = u;
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String initialUrl() {
        return url;
    }

    @Override
    protected boolean rootScreen() {
        return false;
    }

    @Override
    protected boolean inAppBrowsing() {
        return true;
    }
}
