package net.northplus.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 版块目录，来自打包在 assets 的 boards.json（抓自站点移动版导航）。 */
public final class Boards {

    public static class Board {
        public final int fid;
        public final String name;

        public Board(int fid, String name) {
            this.fid = fid;
            this.name = name;
        }
    }

    public static class Group {
        public final String name;
        public final List<Board> boards = new ArrayList<>();

        public Group(String name) {
            this.name = name;
        }
    }

    private static List<Group> cache;

    private Boards() {
    }

    public static synchronized List<Group> all(Context c) {
        if (cache != null) return cache;
        List<Group> out = new ArrayList<>();
        try (InputStream in = c.getAssets().open("boards.json")) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONArray gs = root.optJSONArray("groups");
            if (gs != null) {
                for (int i = 0; i < gs.length(); i++) {
                    JSONObject go = gs.getJSONObject(i);
                    Group g = new Group(go.optString("group", ""));
                    JSONArray bs = go.optJSONArray("boards");
                    if (bs != null) {
                        for (int j = 0; j < bs.length(); j++) {
                            JSONObject bo = bs.getJSONObject(j);
                            g.boards.add(new Board(bo.optInt("fid"), bo.optString("name")));
                        }
                    }
                    out.add(g);
                }
            }
        } catch (Exception ignored) {
            // 资源缺失时退化为空目录，界面仍可正常浏览网页
        }
        cache = out;
        return cache;
    }

    /** 关键字过滤（版块名包含，忽略大小写）。 */
    public static List<Group> filter(Context c, String keyword) {
        List<Group> base = all(c);
        if (keyword == null || keyword.trim().isEmpty()) return base;
        String kw = keyword.trim().toLowerCase();
        List<Group> out = new ArrayList<>();
        for (Group g : base) {
            Group ng = new Group(g.name);
            for (Board b : g.boards) {
                if (b.name.toLowerCase().contains(kw)) ng.boards.add(b);
            }
            if (!ng.boards.isEmpty()) out.add(ng);
        }
        return out;
    }
}
