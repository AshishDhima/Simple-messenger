package com.simple.messenger;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

/** Colours and small view builders. The interface is built in code, so there are no layout files. */
final class Ui {
    static final int PRIMARY = Color.parseColor("#1E6F62");
    static final int PRIMARY_DARK = Color.parseColor("#155247");
    static final int BACKGROUND = Color.parseColor("#ECEFEA");
    static final int SURFACE = Color.WHITE;
    static final int MINE = Color.parseColor("#D6F0E4");
    static final int TEXT = Color.parseColor("#1B1F1D");
    static final int MUTED = Color.parseColor("#6B7570");
    static final int LINE = Color.parseColor("#DDE2DE");
    static final int ERROR = Color.parseColor("#B3261E");
    static final int ONLINE = Color.parseColor("#2EA866");

    private static final int[] AVATAR_COLORS = {
        Color.parseColor("#C0563B"), Color.parseColor("#3D6FB6"), Color.parseColor("#7A55A8"),
        Color.parseColor("#B07A12"), Color.parseColor("#2E8A6E"), Color.parseColor("#B23A6E"),
        Color.parseColor("#4F7A2A"), Color.parseColor("#5B6470"),
    };

    private Ui() { }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, String value, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length && b.length() < 2; i++) {
            if (!parts[i].isEmpty()) b.appendCodePoint(parts[i].codePointAt(0));
        }
        return b.toString().toUpperCase();
    }

    static TextView avatar(Context c, String name, int sizeDp, boolean group) {
        TextView a = text(c, group ? "#" : initials(name), sizeDp * 0.38f, Color.WHITE, true);
        a.setGravity(Gravity.CENTER);
        int color = group ? PRIMARY : AVATAR_COLORS[((name.toLowerCase().hashCode() % AVATAR_COLORS.length) + AVATAR_COLORS.length) % AVATAR_COLORS.length];
        a.setBackground(circle(color));
        int size = dp(c, sizeDp);
        a.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return a;
    }

    /** Green top bar with a title and subtitle. Returns the bar; children are added by the caller. */
    static LinearLayout bar(Activity a) {
        a.getWindow().setStatusBarColor(PRIMARY_DARK);
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(PRIMARY);
        bar.setMinimumHeight(dp(a, 60));
        bar.setPadding(dp(a, 8), dp(a, 6), dp(a, 8), dp(a, 6));
        return bar;
    }

    static LinearLayout titles(Context c, TextView title, TextView subtitle) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(title);
        box.addView(subtitle);
        box.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return box;
    }

    static TextView barButton(Context c, String label, View.OnClickListener click) {
        TextView b = text(c, label, 15, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        b.setMinWidth(dp(c, 48));
        b.setOnClickListener(click);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    static String time(Context c, long ts) {
        return android.text.format.DateFormat.getTimeFormat(c).format(new Date(ts));
    }

    /** Time for today, otherwise a short date. */
    static String listTime(Context c, long ts) {
        if (DateUtils.isToday(ts)) return time(c, ts);
        return DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(ts));
    }

    static String day(long ts) {
        if (DateUtils.isToday(ts)) return "Today";
        if (DateUtils.isToday(ts + DateUtils.DAY_IN_MILLIS)) return "Yesterday";
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(ts));
    }
}
