package net.northplus.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.List;

/**
 * 主界面：首页 / 版块 / 搜索 / 我的。
 *
 * <p>「搜索」刻意不做成自绘面板——直接用论坛自己的搜索页，
 * 这样搜索选项、匹配模式、结果排版与站点完全一致，
 * 用户脚本对搜索页的增强（置顶选项、默认匹配模式等）也能生效。
 * 顶部的关键字输入框只是替用户把字填进论坛的表单再提交，不改变搜索逻辑。
 */
public class MainActivity extends BaseWebActivity {

    private static final int TAB_HOME = 0;
    private static final int TAB_BOARDS = 1;
    private static final int TAB_SEARCH = 2;
    private static final int TAB_ME = 3;

    private View boardsPanel;
    private LinearLayout boardList;

    private int currentTab = -1;

    @Override
    protected String initialUrl() {
        return Site.home();
    }

    @Override
    protected boolean rootScreen() {
        return true;
    }

    @Override
    protected void onShellReady() {
        navDivider.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.VISIBLE);
        buildBottomNav();

        LayoutInflater inf = LayoutInflater.from(this);
        boardsPanel = inf.inflate(R.layout.view_boards, content, false);
        content.addView(boardsPanel);
        setupBoardsPanel();

        selectTab(TAB_HOME, true);
    }

    @Override
    public void onBackPressed() {
        if (currentTab == TAB_BOARDS) {
            selectTab(TAB_HOME, false);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        Uri data = intent.getData();
        if (data != null && web != null) {
            openInWeb(data.toString());
        }
    }

    // ---------------------------------------------------------------- 底部导航

    private void buildBottomNav() {
        int[] icons = {
                R.drawable.ic_home, R.drawable.ic_grid,
                R.drawable.ic_search, R.drawable.ic_person
        };
        int[] labels = {
                R.string.tab_home, R.string.tab_boards,
                R.string.tab_search, R.string.tab_me
        };
        bottomNav.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; i < icons.length; i++) {
            View item = inf.inflate(R.layout.nav_item, bottomNav, false);
            ((ImageView) item.findViewById(R.id.navIcon)).setImageResource(icons[i]);
            ((TextView) item.findViewById(R.id.navLabel)).setText(labels[i]);
            final int idx = i;
            item.setOnClickListener(v -> selectTab(idx, false));
            bottomNav.addView(item);
        }
    }

    private void selectTab(int tab, boolean force) {
        if (!force && tab == currentTab) {
            if (tab == TAB_HOME && web != null) web.scrollTo(0, 0);
            return;
        }

        boolean nativePanel = tab == TAB_BOARDS;
        boardsPanel.setVisibility(nativePanel ? View.VISIBLE : View.GONE);

        if (nativePanel) {
            swipe.setVisibility(View.GONE);
            errorView.setVisibility(View.GONE);
        } else {
            showContent();
        }

        if (web != null) {
            String cur = web.getUrl();
            String low = cur == null ? "" : cur.toLowerCase();
            String want = null;
            if (tab == TAB_HOME) {
                // 从搜索 / 个人中心回来时重新落回首页
                if (cur == null || !UrlMapper.isSite(cur)
                        || low.contains("/u.php") || low.contains("/search.php")) {
                    want = isDesktopMode() ? Site.home() : Site.homeMobile();
                }
            } else if (tab == TAB_SEARCH) {
                if (cur == null || !low.contains("/search.php")) {
                    want = Site.search();
                }
            } else if (tab == TAB_ME) {
                if (cur == null || !low.contains("/u.php")) {
                    want = Site.me();
                }
            }
            if (want != null) web.loadUrl(want);
        }

        currentTab = tab;
        selectBottomNav(tab);
    }

    /** 把某个地址交给首页 WebView 打开（版块面板的结果走这里）。 */
    private void openInWeb(String url) {
        boardsPanel.setVisibility(View.GONE);
        showContent();
        currentTab = TAB_HOME;
        selectBottomNav(TAB_HOME);
        if (web == null) return;
        String target = UrlMapper.standardize(url);
        if (!isDesktopMode()) target = UrlMapper.toMobile(target);
        web.loadUrl(target);
    }

    // ---------------------------------------------------------------- 版块面板

    private void setupBoardsPanel() {
        boardList = boardsPanel.findViewById(R.id.boardList);
        EditText filter = boardsPanel.findViewById(R.id.boardFilter);
        View clear = boardsPanel.findViewById(R.id.boardClear);

        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clear.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                renderBoards(s == null ? "" : s.toString());
            }
        });
        clear.setOnClickListener(v -> filter.setText(""));
        renderBoards("");
    }

    private void renderBoards(String keyword) {
        boardList.removeAllViews();
        List<Boards.Group> groups = Boards.filter(this, keyword);
        int total = 0;
        for (Boards.Group g : groups) total += g.boards.size();

        if (total == 0) {
            boardList.addView(textRow("没有匹配的版块", true));
            return;
        }

        for (Boards.Group g : groups) {
            boardList.addView(groupHeader(g.name + "  (" + g.boards.size() + ")"));
            for (final Boards.Board b : g.boards) {
                TextView row = textRow(b.name, false);
                row.setOnClickListener(v -> openInWeb(UrlMapper.boardUrl(b.fid, !isDesktopMode())));
                boardList.addView(row);
            }
        }
    }

    private TextView groupHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(ContextCompat.getColor(this, R.color.np_accent));
        tv.setBackgroundColor(ContextCompat.getColor(this, R.color.np_chip));
        tv.setPadding(dp(16), dp(11), dp(16), dp(11));
        return tv;
    }

    private TextView textRow(String text, boolean dim) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15f);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setTextColor(ContextCompat.getColor(this,
                dim ? R.color.np_text_dim : R.color.np_text));
        tv.setPadding(dp(16), dp(13), dp(16), dp(13));
        tv.setBackgroundResource(selectableBackground());
        return tv;
    }

    // ---------------------------------------------------------------- 工具

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private int selectableBackground() {
        TypedValue out = new TypedValue();
        if (getTheme().resolveAttribute(
                androidx.appcompat.R.attr.selectableItemBackground, out, true)) {
            return out.resourceId;
        }
        return android.R.color.transparent;
    }
}
