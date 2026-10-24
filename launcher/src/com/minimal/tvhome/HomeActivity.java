package com.minimal.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full-screen terminal home: a clock top right and a zsh-style session bottom
 * left. `ls dock/` and `ls apps/` list the apps as plain text; the focused one
 * is highlighted like selected text and echoed as `open <app>` on the last
 * prompt line. No images at all, so it stays tiny in memory.
 * OK opens the app; long-press OK or Menu pins it to / unpins it from the dock.
 */
public class HomeActivity extends Activity {
    private static final String[] DEFAULT_DOCK = {
        "com.netflix.ninja", "com.amazon.firetv.youtube", "com.amazon.firebat",
        "com.apple.atve.amazon.appletv", "com.cbs.ott", "com.spotify.tv.android",
        "com.crunchyroll.crunchyroid",
    };
    /** Built-in apps worth showing; every other system entry point is hidden. */
    private static final List<String> SYSTEM_SHOWN = Arrays.asList(
        "com.amazon.firebat", "com.amazon.venezia", "com.apple.airplay.amazon.airplay");
    private static final String SETTINGS = "#settings";
    private static final int PER_ROW = 4;

    // macOS Terminal "Pro"-style palette
    private static final int BG = 0xFF1E1E1E, FG = 0xFFF2F2F2, DIM = 0xFF9A9A9A,
            DIR = 0xFFC9C9C9, SEL = 0xFF4A6FA5;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, App> apps = new LinkedHashMap<>();
    private SharedPreferences prefs;
    private Typeface mono, monoBold;
    private TextView clock, date, openLine;
    private View cursor;
    private LinearLayout session;
    private MiniPlayer player;
    private String focusedPkg, prompt;

    static final class App {
        final String pkg, name;
        final ComponentName component;
        App(String pkg, String name, ComponentName c) { this.pkg = pkg; this.name = name; this.component = c; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        mono = Typeface.MONOSPACE;
        monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        prompt = getString(R.string.prompt_user) + "@" + getString(R.string.prompt_host) + " ~ % ";
        setContentView(buildLayout());
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter p = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        p.addAction(Intent.ACTION_PACKAGE_REMOVED);
        p.addAction(Intent.ACTION_PACKAGE_CHANGED);
        p.addDataScheme("package");
        registerReceiver(packages, p);
        ui.post(tick);
        ui.post(blink);
        player.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        player.stop();
        unregisterReceiver(packages);
        ui.removeCallbacks(tick);
        ui.removeCallbacks(blink);
    }

    @Override
    public void onBackPressed() { /* already home */ }

    /** Pressing Home again lands on the first dock app, like any launcher. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        focusedPkg = null;
        if (!apps.isEmpty()) render();
    }

    // ---------------------------------------------------------------- layout

    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.END);
        clock = text(96, FG, mono);
        clock.setIncludeFontPadding(false);
        date = text(26, DIM, mono);
        date.setPadding(0, dp(12), 0, 0);
        LinearLayout.LayoutParams end = new LinearLayout.LayoutParams(-2, -2);
        end.gravity = Gravity.END;
        top.addView(clock, end);
        top.addView(date, new LinearLayout.LayoutParams(end));
        player = new MiniPlayer(this, dp(1000) / 1000f, cmd -> {
            focusedPkg = null;
            if (openLine != null) openLine.setText(cmd);
        });
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(dp(620), -2, Gravity.TOP | Gravity.START);
        pl.setMargins(dp(80), dp(72), 0, 0);
        root.addView(player, pl);

        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        tl.setMargins(0, dp(64), dp(80), 0);
        root.addView(top, tl);

        session = new LinearLayout(this);
        session.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        sl.setMargins(dp(80), 0, dp(80), dp(72));
        root.addView(session, sl);
        return root;
    }

    /** Rebuild the session text from the app list. */
    private void render() {
        session.removeAllViews();
        session.addView(prompt("ls dock/"));

        List<String> dock = dock();
        List<App> docked = new ArrayList<>();
        for (String p : dock) docked.add(apps.get(p));
        List<App> rest = new ArrayList<>();
        for (App a : apps.values()) if (!dock.contains(a.pkg) && !SETTINGS.equals(a.pkg)) rest.add(a);
        Collections.sort(rest, (a, b) -> a.name.compareTo(b.name));
        rest.add(apps.get(SETTINGS));

        View first = grid(docked, FG);
        session.addView(prompt("ls apps/"));
        View firstApp = grid(rest, DIR);

        LinearLayout last = new LinearLayout(this);
        last.setGravity(Gravity.CENTER_VERTICAL);
        last.addView(promptText(""));
        openLine = text(34, FG, mono);
        last.addView(openLine);
        cursor = new View(this);
        cursor.setBackgroundColor(FG);
        last.addView(cursor, new LinearLayout.LayoutParams(dp(20), dp(40)));
        session.addView(last);

        View target = focusedPkg == null ? null : session.findViewWithTag(focusedPkg);
        if (target == null) target = first != null ? first : firstApp;
        if (target != null) target.requestFocus();
    }

    /** App names as `ls` output: PER_ROW per line, each one focusable. Returns the first item. */
    private View grid(List<App> list, int color) {
        View first = null;
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % PER_ROW == 0) {
                row = new LinearLayout(this);
                session.addView(row);
            }
            App app = list.get(i);
            TextView t = text(34, color, mono);
            t.setText(app.name);
            t.setTag(app.pkg);
            t.setFocusable(true);
            t.setPadding(dp(4), 0, dp(4), 0);
            t.setOnFocusChangeListener((v, has) -> {
                TextView tv = (TextView) v;
                tv.setBackgroundColor(has ? SEL : 0);
                tv.setTextColor(has ? 0xFFFFFFFF : color);
                if (has) { focusedPkg = app.pkg; openLine.setText("open " + app.name); }
            });
            t.setOnClickListener(v -> launch(app));
            t.setOnLongClickListener(v -> { toggleDock(app); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(dp(92));   // roughly five spaces, like `ls` columns
            row.addView(t, lp);
            if (first == null) first = t;
        }
        return first;
    }

    private View prompt(String cmd) {
        LinearLayout l = new LinearLayout(this);
        l.addView(promptText(cmd));
        return l;
    }

    private TextView promptText(String cmd) {
        TextView t = text(34, FG, mono);
        SpannableString s = new SpannableString(prompt + cmd);
        s.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, prompt.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        t.setText(s);
        return t;
    }

    // ------------------------------------------------------------------ apps

    /** "Prime Video" -> "prime_video", like a file name. */
    private static String fileName(String label) {
        return label.trim().toLowerCase(Locale.US).replaceAll("[™®]", "").replaceAll("\\s+", "_");
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Map<String, App> found = new LinkedHashMap<>();
            for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                    ActivityInfo ai = ri.activityInfo;
                    if (ai.packageName.equals(getPackageName()) || found.containsKey(ai.packageName)) continue;
                    boolean system = (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    if (system && !SYSTEM_SHOWN.contains(ai.packageName)) continue;
                    found.put(ai.packageName, new App(ai.packageName, fileName(ri.loadLabel(pm).toString()),
                            new ComponentName(ai.packageName, ai.name)));
                }
            }
            ui.post(() -> {
                apps.clear();
                apps.putAll(found);
                apps.put(SETTINGS, new App(SETTINGS, "settings", null));
                render();
            });
        }).start();
    }

    private List<String> dock() {
        String saved = prefs.getString("dock", null);
        List<String> d = new ArrayList<>(saved == null ? Arrays.asList(DEFAULT_DOCK)
                : Arrays.asList(saved.isEmpty() ? new String[0] : saved.split(",")));
        d.retainAll(apps.keySet());
        return d;
    }

    private void launch(App app) {
        try {
            if (SETTINGS.equals(app.pkg)) {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            } else {
                startActivity(new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setComponent(app.component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            }
        } catch (Exception e) {
            Toast.makeText(this, "zsh: can't open " + app.name, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleDock(App app) {
        if (SETTINGS.equals(app.pkg)) return;
        List<String> d = dock();
        boolean add = !d.remove(app.pkg);
        if (add) d.add(app.pkg);
        prefs.edit().putString("dock", String.join(",", d)).apply();
        Toast.makeText(this, (add ? "mv " + app.name + " dock/" : "mv dock/" + app.name + " apps/"),
                Toast.LENGTH_SHORT).show();
        focusedPkg = app.pkg;
        render();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU && focusedPkg != null && apps.containsKey(focusedPkg)) {
            toggleDock(apps.get(focusedPkg));
            return true;
        }
        return super.onKeyDown(code, e);
    }

    // ---------------------------------------------------------- clock/cursor

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Calendar now = Calendar.getInstance();
            boolean h24 = DateFormat.is24HourFormat(HomeActivity.this);
            String hm = DateFormat.format(h24 ? "HH:mm" : "h:mm", now).toString();
            String ss = ":" + DateFormat.format("ss", now) + (h24 ? "" : " " + DateFormat.format("a", now));
            SpannableString s = new SpannableString(hm + ss);
            s.setSpan(new AbsoluteSizeSpan(dp(44)), hm.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(DIM), hm.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            clock.setText(s);
            date.setText(DateFormat.format("EEE MMM d yyyy", now));
            player.tick();
            ui.postDelayed(this, 1000 - now.get(Calendar.MILLISECOND));
        }
    };

    private final Runnable blink = new Runnable() {
        @Override public void run() {
            if (cursor != null) cursor.setVisibility(cursor.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
            ui.postDelayed(this, 550);
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    // --------------------------------------------------------------- helpers

    private TextView text(int px, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(px));
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.3f);
        return t;
    }

    /** Sizes are designed in 1080p pixels and scaled to the real screen, ignoring density. */
    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().heightPixels / 1080f);
    }
}
