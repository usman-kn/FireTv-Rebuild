package com.minimal.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Terminal-style now-playing block: status line, title, artist · album, a
 * progress bar and prev / play-pause / next. Follows whichever media session
 * is playing (else the most recent paused one) and hides itself when there is
 * none or when notification access hasn't been granted.
 */
public class MiniPlayer extends LinearLayout {
    interface Focus { void on(String command); }

    private static final int FG = 0xFFF2F2F2, DIM = 0xFF9A9A9A, DIR = 0xFFC9C9C9,
            SEL = 0xFF4A6FA5, ACCENT = 0xFF7FD18B, TRACK = 0xFF3A3A3A;

    private final MediaSessionManager msm;
    private final ComponentName listener;
    private final float unit;
    private final TextView status, title, artist, elapsed, total, prev, toggle, next;
    private final Bar bar;
    private final Focus focus;
    private MediaController controller;
    private String appName = "";

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = list -> pick(list);

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { refresh(); }
        @Override public void onMetadataChanged(MediaMetadata m) { refresh(); }
        @Override public void onSessionDestroyed() { pick(null); }
    };

    public MiniPlayer(Context c, float unit, Focus focus) {
        super(c);
        this.unit = unit;
        this.focus = focus;
        msm = c.getSystemService(MediaSessionManager.class);
        listener = new ComponentName(c, MediaListener.class);
        setOrientation(VERTICAL);
        setVisibility(GONE);

        Typeface mono = Typeface.MONOSPACE;
        status = text(26, DIM, mono);
        title = text(34, FG, Typeface.create(mono, Typeface.BOLD));
        artist = text(26, DIR, mono);
        addView(status);
        addView(title);
        addView(artist);

        LinearLayout progress = new LinearLayout(c);
        progress.setGravity(Gravity.CENTER_VERTICAL);
        progress.setPadding(0, px(14), 0, 0);
        elapsed = text(26, DIM, mono);
        total = text(26, DIM, mono);
        bar = new Bar(c);
        progress.addView(elapsed);
        LayoutParams bl = new LayoutParams(0, px(6), 1f);
        bl.setMargins(px(18), 0, px(18), 0);
        progress.addView(bar, bl);
        progress.addView(total);
        addView(progress, new LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(c);
        buttons.setPadding(0, px(14), 0, 0);
        prev = button("|◀", "prev", () -> controls().skipToPrevious());
        toggle = button("▮▮", "pause", this::playPause);
        next = button("▶|", "next", () -> controls().skipToNext());
        buttons.addView(prev);
        buttons.addView(toggle);
        buttons.addView(next);
        addView(buttons);
    }

    // ------------------------------------------------------------ session

    void start() {
        try {
            msm.addOnActiveSessionsChangedListener(sessions, listener);
            pick(msm.getActiveSessions(listener));
        } catch (SecurityException noAccess) {
            setVisibility(GONE);    // notification access not granted: no player
        }
    }

    void stop() {
        try { msm.removeOnActiveSessionsChangedListener(sessions); } catch (Exception ignored) { }
        bind(null);
    }

    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController m : list) {
                PlaybackState s = m.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { best = m; break; }
                if (best == null && m.getMetadata() != null && s != null
                        && s.getState() != PlaybackState.STATE_NONE && s.getState() != PlaybackState.STATE_STOPPED) best = m;
            }
        }
        bind(best);
    }

    /**
     * Called once a second: re-pick in case another session started playing
     * (sessions only report changes to the controller we're bound to), then redraw.
     */
    void tick() {
        try { pick(msm.getActiveSessions(listener)); } catch (SecurityException noAccess) { return; }
        refresh();
    }

    private void bind(MediaController m) {
        if (m != null && controller != null && m.getSessionToken().equals(controller.getSessionToken())) return;
        if (controller != null) controller.unregisterCallback(callback);
        controller = m;
        if (m != null) {
            m.registerCallback(callback);
            appName = label(m.getPackageName());
        }
        refresh();
    }

    private MediaController.TransportControls controls() { return controller.getTransportControls(); }

    private void playPause() {
        PlaybackState s = controller.getPlaybackState();
        if (s != null && s.getState() == PlaybackState.STATE_PLAYING) controls().pause(); else controls().play();
    }

    // ------------------------------------------------------------- render

    /** Redraw from the session; also called once a second for the progress bar. */
    void refresh() {
        MediaMetadata md = controller == null ? null : controller.getMetadata();
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        boolean show = md != null && s != null && s.getState() != PlaybackState.STATE_NONE
                && s.getState() != PlaybackState.STATE_STOPPED;
        if (!show) {
            View target = hasFocus() ? focusSearch(findFocus(), FOCUS_DOWN) : null;
            setVisibility(GONE);
            if (target != null) target.requestFocus();   // don't strand focus on a hidden button
            return;
        }
        setVisibility(VISIBLE);
        boolean playing = s.getState() == PlaybackState.STATE_PLAYING;

        SpannableStringBuilder st = new SpannableStringBuilder("♪ " + appName + " ── ");
        int from = st.length();
        st.append(playing ? "playing" : s.getState() == PlaybackState.STATE_BUFFERING ? "loading" : "paused");
        st.setSpan(new ForegroundColorSpan(playing ? ACCENT : DIM), from, st.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        status.setText(st);

        title.setText(nonNull(md.getString(MediaMetadata.METADATA_KEY_TITLE)));
        String a = nonNull(md.getString(MediaMetadata.METADATA_KEY_ARTIST));
        String al = nonNull(md.getString(MediaMetadata.METADATA_KEY_ALBUM));
        artist.setText(al.isEmpty() ? a : a.isEmpty() ? al : a + " · " + al);
        toggle.setText(playing ? "▮▮" : " ▶");
        toggle.setTag(playing ? "pause" : "play");
        if (toggle.isFocused()) focus.on(appName + " " + toggle.getTag());

        long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = s.getPosition();
        if (playing && s.getLastPositionUpdateTime() > 0) {
            pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        if (dur > 0) pos = Math.max(0, Math.min(pos, dur));
        elapsed.setText(time(pos));
        total.setText(dur > 0 ? time(dur) : "--:--");
        bar.set(dur > 0 ? pos / (float) dur : 0f);
    }

    // ------------------------------------------------------------ helpers

    private TextView button(String glyph, String verb, Runnable action) {
        TextView t = text(30, FG, Typeface.MONOSPACE);
        t.setText(glyph);
        t.setTag(verb);
        t.setFocusable(true);
        t.setPadding(px(8), 0, px(8), 0);
        t.setOnFocusChangeListener((v, has) -> {
            v.setBackgroundColor(has ? SEL : 0);
            if (has) focus.on(appName + " " + v.getTag());
        });
        t.setOnClickListener(v -> { if (controller != null) action.run(); });
        LayoutParams lp = new LayoutParams(-2, -2);
        lp.setMarginEnd(px(28));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView text(int size, int color, Typeface tf) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMaxWidth(px(620));
        return t;
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getContext().getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().toLowerCase(Locale.US).replaceAll("\\s+", "_");
        } catch (Exception e) { return pkg; }
    }

    private static String nonNull(String s) { return s == null ? "" : s; }

    private static String time(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private int px(float v) { return Math.round(v * unit); }

    /** Flat progress line: filled part in the text colour on a dim track. */
    static final class Bar extends View {
        private final Paint p = new Paint();
        private float f;
        Bar(Context c) { super(c); }
        void set(float fraction) { if (fraction != f) { f = fraction; invalidate(); } }
        @Override protected void onDraw(Canvas c) {
            p.setColor(TRACK);
            c.drawRect(0, 0, getWidth(), getHeight(), p);
            p.setColor(FG);
            c.drawRect(0, 0, getWidth() * f, getHeight(), p);
        }
    }
}
