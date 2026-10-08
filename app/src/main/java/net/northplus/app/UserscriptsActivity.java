package net.northplus.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 用户脚本页。
 *
 * <p>页面上只保留两个动作：**一键更新凛+** 与 **本地导入**。
 * 下载源不在这里选——{@link UserscriptInstaller} 会自动在多源之间并行竞速。
 */
public class UserscriptsActivity extends AppCompatActivity {

    private static final int REQ_FILE = 0x81;

    private UserscriptStore store;
    private LinearLayout list;
    private TextView empty;
    private TextView rinState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_userscripts);

        store = UserscriptStore.get(this);
        list = findViewById(R.id.usList);
        empty = findViewById(R.id.usEmpty);
        rinState = findViewById(R.id.usRinState);

        findViewById(R.id.usBack).setOnClickListener(v -> finish());
        findViewById(R.id.usHelp).setOnClickListener(v -> showHelp());

        SwitchCompat sw = findViewById(R.id.usSwEnable);
        sw.setChecked(App.prefs.scriptsEnabled());
        sw.setOnCheckedChangeListener((b, on) -> {
            App.prefs.setScriptsEnabled(on);
            toast(on ? "已启用用户脚本" : "已停用用户脚本");
        });
        findViewById(R.id.usRowEnable).setOnClickListener(v -> sw.setChecked(!sw.isChecked()));

        findViewById(R.id.usBtnRin).setOnClickListener(v -> updateRin());
        findViewById(R.id.usBtnFile).setOnClickListener(v -> pickFile());

        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    // ------------------------------------------------------------------ 凛+

    private void renderRinState() {
        if (rinState == null) return;
        Userscript u = store.findRin();
        if (u == null) {
            rinState.setText(R.string.us_rin_missing);
            return;
        }
        String ver = TextUtils.isEmpty(u.version) ? "" : ("  v" + u.version);
        rinState.setText(getString(R.string.us_rin_installed, u.name + ver));
    }

    private void updateRin() {
        UserscriptInstaller.installById(this, UserscriptSources.RIN_ID,
                UserscriptSources.RIN_NAME, (ok, message) -> {
            if (ok) {
                render();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.us_update_failed)
                    .setMessage(message + "\n\n" + getString(R.string.us_vpn_hint))
                    .setPositiveButton("知道了", null)
                    .show();
        });
    }

    // ------------------------------------------------------------------ 本地导入

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_FILE);
        } catch (Exception e) {
            toast(getString(R.string.us_file_failed));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQ_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                String src = readText(data.getData());
                if (src == null) {
                    toast(getString(R.string.us_file_failed));
                    return;
                }
                UserscriptInstaller.installFromSource(this, src, (ok, msg) -> {
                    if (ok) render();
                });
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private String readText(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 列表

    private void render() {
        renderRinState();
        list.removeAllViews();
        List<Userscript> all = store.all();
        empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inf = LayoutInflater.from(this);
        for (final Userscript u : all) {
            View row = inf.inflate(R.layout.row_userscript, list, false);
            ((TextView) row.findViewById(R.id.rowName)).setText(u.displayTitle());

            TextView sub = row.findViewById(R.id.rowSub);
            int rules = u.matches.size() + u.includes.size();
            sub.setText(u.displaySubtitle() + "\n生效范围 " + rules + " 条 · 运行时机 " + u.runAt);

            TextView err = row.findViewById(R.id.rowError);
            if (!TextUtils.isEmpty(u.lastError)) {
                err.setVisibility(View.VISIBLE);
                err.setText("运行出错：" + u.lastError);
            }

            SwitchCompat sw = row.findViewById(R.id.rowSwitch);
            sw.setChecked(u.enabled);
            sw.setOnCheckedChangeListener((b, on) -> store.setEnabled(u.id, on));

            row.findViewById(R.id.rowDelete).setOnClickListener(v -> confirmDelete(u));
            row.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));

            list.addView(row);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
            divider.setBackgroundColor(getResources().getColor(R.color.np_divider));
            list.addView(divider);
        }
    }

    private void confirmDelete(final Userscript u) {
        new AlertDialog.Builder(this)
                .setTitle("删除脚本？")
                .setMessage(u.displayTitle() + "\n该脚本在本机保存的配置也会一并清除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    store.remove(u.id);
                    render();
                })
                .show();
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.us_help_title)
                .setMessage(R.string.us_help_body)
                .setPositiveButton("知道了", null)
                .show();
    }

    private int dp(float v) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
