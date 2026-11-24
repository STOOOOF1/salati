package com.zarraqai.salati;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.icu.text.DateFormat;
import android.icu.util.ULocale;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
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
    private TextView tvPlace, tvClock, tvAmPm, tvDate, tvCurrentLabel, tvCurrent,
            tvNextLabel, tvNext, tvNextAt, tvPrevLabel, tvPrev, tvPrevAt,
            tvIqamaLabel, tvIqama, tvIqamaAt;
    private RingView ring;
    private LinearLayout timesBox;
    private int colorNormal, colorRed, colorText, colorMuted, colorGold;
    private Typeface fontRegular, fontBold;
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
        tvAmPm = findViewById(R.id.tvAmPm);
        tvDate = findViewById(R.id.tvDate);
        tvCurrentLabel = findViewById(R.id.tvCurrentLabel);
        tvCurrent = findViewById(R.id.tvCurrent);
        tvNextLabel = findViewById(R.id.tvNextLabel);
        tvNext = findViewById(R.id.tvNext);
        tvNextAt = findViewById(R.id.tvNextAt);
        tvPrevLabel = findViewById(R.id.tvPrevLabel);
        tvPrev = findViewById(R.id.tvPrev);
        tvPrevAt = findViewById(R.id.tvPrevAt);
        tvIqamaLabel = findViewById(R.id.tvIqamaLabel);
        tvIqama = findViewById(R.id.tvIqama);
        tvIqamaAt = findViewById(R.id.tvIqamaAt);
        ring = findViewById(R.id.ring);
        timesBox = findViewById(R.id.timesBox);
        colorNormal = getColor(R.color.accent);
        colorRed = getColor(R.color.danger);
        colorText = getColor(R.color.text);
        colorMuted = getColor(R.color.muted);
        colorGold = getColor(R.color.gold);
        fontRegular = getResources().getFont(R.font.tajawal_regular);
        fontBold = getResources().getFont(R.font.tajawal_bold);

        Runnable openSettings = () -> startActivity(new Intent(this, SettingsActivity.class));
        findViewById(R.id.btnSettings).setOnClickListener(v -> openSettings.run());
        findViewById(R.id.placeBox).setOnClickListener(v -> openSettings.run());
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
        tvClock.setText(now.format(CLOCK));
        tvAmPm.setText(now.getHour() < 12 ? "صباحًا" : "مساءً");
        String hijri = DateFormat.getDateInstance(DateFormat.LONG,
                new ULocale("ar_SA@calendar=islamic-umalqura")).format(new Date());
        tvDate.setText(now.format(GREG) + "  •  " + hijri);

        float u = s.urgency();
        int c = Schedule.blend(colorNormal, colorRed, u);
        if (s.current != null) {
            tvCurrentLabel.setText("باقي على خروج وقت " + s.current.name);
            tvCurrent.setText(Schedule.fmt(s.currentRemaining()));
            tvCurrent.setTextColor(c);
            ring.set(s.currentProgress(), c, true);
        } else {
            tvCurrentLabel.setText("لا يوجد وقت صلاة قائم");
            tvCurrent.setText(s.next.name + " " + s.next.adhan.format(TIME));
            tvCurrent.setTextColor(colorText);
            ring.set(0, colorMuted, false);
        }

        tvNextLabel.setText("أذان " + s.next.name + " بعد");
        tvNext.setText(Schedule.fmt(s.untilNext()));
        tvNextAt.setText("عند " + s.next.adhan.format(TIME));

        tvPrevLabel.setText("مضى على أذان " + s.previous.name);
        tvPrev.setText(Schedule.fmt(s.sincePrevious()));
        tvPrevAt.setText("أُذّن " + s.previous.adhan.format(TIME));

        tvIqamaLabel.setText("إقامة " + s.iqamaName + " بعد");
        tvIqamaAt.setText("عند " + s.iqamaTarget.format(TIME));
        tvIqama.setText(Schedule.fmt(s.untilIqama()));
        tvIqama.setTextColor(s.iqamaIsForCurrent() ? colorGold : colorText);

        renderTimes(s);
    }

    private void renderTimes(Schedule s) {
        String key = s.now.toLocalDate() + "|" + s.next.adhan + "|" + (s.current == null ? "" : s.current.adhan)
                + "|" + Math.round(s.urgency() * 10);
        if (key.equals(lastTimesKey)) return;
        lastTimesKey = key;
        timesBox.removeAllViews();
        int cur = Schedule.blend(colorNormal, colorRed, s.urgency());
        for (int i = 0; i < 6; i++) {
            ZonedDateTime t = s.today[i];
            String name = Schedule.NAMES[i];
            if (i == PrayerCalc.DHUHR && t.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) name = "الجمعة";
            boolean isNext = t.equals(s.next.adhan);
            boolean isCur = s.current != null && t.equals(s.current.adhan);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));
            if (isCur || isNext) row.setBackgroundResource(R.drawable.pill);

            TextView n = new TextView(this);
            n.setText(name);
            n.setTextSize(17);
            TextView tag = new TextView(this);
            tag.setTextSize(12);
            tag.setTypeface(fontBold);
            tag.setPadding(dp(8), 0, dp(8), 0);
            if (isCur) { tag.setText("الآن"); tag.setTextColor(cur); }
            else if (isNext) { tag.setText("التالية"); tag.setTextColor(colorGold); }
            TextView v = new TextView(this);
            v.setText(t.format(TIME));
            v.setTextSize(17);
            v.setGravity(Gravity.END);

            int col = isCur ? cur : (isNext ? colorText : colorMuted);
            n.setTextColor(col);
            v.setTextColor(col);
            Typeface tf = (isCur || isNext) ? fontBold : fontRegular;
            n.setTypeface(tf);
            v.setTypeface(tf);

            row.addView(n, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(tag, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(v, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            timesBox.addView(row);
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
