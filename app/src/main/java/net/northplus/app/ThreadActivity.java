package net.northplus.app;

import android.content.Intent;
import android.os.Bundle;

/** 帖子 / 派生页面所在的独立层。 */
public class ThreadActivity extends BaseWebActivity {

    private String url = Site.home();
    private int depth = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent i = getIntent();
        if (i != null) {
            String u = i.getStringExtra(Const.EXTRA_URL);
            if (u != null) url = u;
            depth = i.getIntExtra(Const.EXTRA_DEPTH, 1);
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
    protected int screenDepth() {
        return depth;
    }
}
