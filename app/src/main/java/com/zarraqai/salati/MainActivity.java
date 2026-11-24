package com.zarraqai.salati;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.icu.text.DateFormat;
import android.icu.util.ULocale;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.zarraqai.salati.core.PrayerCalc;
import com.zarraqai.salati.core.Schedule;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    static final Locale AR = new Locale("ar");
    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm:ss", AR);
    static final DateTimeFormatter AMPM = DateTimeFormatter.ofPattern("a", AR);
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", AR);
    static final DateTimeFormatter GREG = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", AR);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView tvPlace, tvClock, tvDate, tvCurrentLabel, tvCurrent, tvNextLabel, tvNext,
            tvPrevLabel, tvPrev, tvIqamaLabel, tvIqama;
    private ProgressBar pbCurrent;
    private LinearLayout timesBox;
    private int colorNormal, colorRed, colorText, colorMuted;
    private String lastTimesKey = "";

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            long delay = 1000 - (System.currentTimeMillis() % 1000);
            handler.postDelayed(this, delay);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        tvPlace = findViewById(R.id.tvPlace);
        tvClock = findViewById(R.id.tvClock);
        tvDate = findViewById(R.id.tvDate);
        tvCurrentLabel = findViewById(R.id.tvCurrentLabel);
        tvCurrent = findViewById(R.id.tvCurrent);
        tvNextLabel = findViewById(R.id.tvNextLabel);
        tvNext = findViewById(R.id.tvNext);
        tvPrevLabel = findViewById(R.id.tvPrevLabel);
        tvPrev = findViewById(R.id.tvPrev);
        tvIqamaLabel = findViewById(R.id.tvIqamaLabel);
        tvIqama = findViewById(R.id.tvIqama);
        pbCurrent = findViewById(R.id.pbCurrent);
        timesBox = findViewById(R.id.timesBox);
        colorNormal = getColor(R.color.accent);
        colorRed = getColor(R.color.danger);
        colorText = getColor(R.color.text);
        colorMuted = getColor(R.color.muted);
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastTimesKey = "";
        handler.post(tick);
        WidgetUpdater.updateAll(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void render() {
        Schedule s = Prefs.schedule(this);
        ZonedDateTime now = s.now;

        tvPlace.setText(Prefs.placeName(this));
        tvClock.setText(now.format(CLOCK) + " " + now.format(AMPM));
        String hijri = DateFormat.getDateInstance(DateFormat.LONG,
                new ULocale("ar_SA@calendar=islamic-umalqura")).format(new Date());
        tvDate.setText(now.format(GREG) + "\n" + hijri);

        float u = s.urgency();
        int c = Schedule.blend(colorNormal, colorRed, u);
        if (s.current != null) {
            tvCurrentLabel.setText("باقي على خروج وقت " + s.current.name);
            tvCurrent.setText(Schedule.fmt(s.currentRemaining()));
            tvCurrent.setTextColor(c);
            pbCurrent.setProgress((int) (s.currentProgress() * 1000));
            pbCurrent.setProgressTintList(ColorStateList.valueOf(c));
            pbCurrent.setVisibility(ProgressBar.VISIBLE);
        } else {
            tvCurrentLabel.setText("لا يوجد وقت صلاة قائم الآن");
            tvCurrent.setText("وقت " + s.next.name + " " + s.next.adhan.format(TIME));
            tvCurrent.setTextColor(colorText);
            pbCurrent.setVisibility(ProgressBar.GONE);
        }

        tvNextLabel.setText("باقي على أذان " + s.next.name);
        tvNext.setText(Schedule.fmt(s.untilNext()));
        tvPrevLabel.setText("مضى على أذان " + s.previous.name);
        tvPrev.setText(Schedule.fmt(s.sincePrevious()));
        tvIqamaLabel.setText("باقي على إقامة " + s.iqamaName);
        tvIqama.setText(Schedule.fmt(s.untilIqama()));
        tvIqama.setTextColor(s.iqamaIsForCurrent() ? colorNormal : colorText);

        renderTimes(s);
    }

    private void renderTimes(Schedule s) {
        String key = s.now.toLocalDate() + "|" + s.next.adhan + "|" + (s.current == null ? "" : s.current.adhan);
        if (key.equals(lastTimesKey)) return;
        lastTimesKey = key;
        timesBox.removeAllViews();
        for (int i = 0; i < 6; i++) {
            ZonedDateTime t = s.today[i];
            String name = Schedule.NAMES[i];
            if (i == PrayerCalc.DHUHR && t.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) name = "الجمعة";
            boolean isNext = t.equals(s.next.adhan);
            boolean isCur = s.current != null && t.equals(s.current.adhan);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(10), 0, dp(10));
            TextView n = new TextView(this);
            n.setText(name);
            n.setTextSize(17);
            TextView v = new TextView(this);
            v.setText(t.format(TIME));
            v.setTextSize(17);
            v.setGravity(Gravity.END);
            int col = isCur ? colorNormal : (isNext ? colorText : colorMuted);
            n.setTextColor(col);
            v.setTextColor(col);
            if (isCur || isNext) { n.setTypeface(Typeface.DEFAULT_BOLD); v.setTypeface(Typeface.DEFAULT_BOLD); }
            row.addView(n, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(v, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            timesBox.addView(row);
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
