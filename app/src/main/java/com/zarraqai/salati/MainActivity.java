package com.zarraqai.salati;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zarraqai.salati.core.PrayerCalc;
import com.zarraqai.salati.core.Schedule;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainActivity extends Activity {

    static final Locale AR = new Locale("ar");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", AR);
    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm:ss", AR);
    private static final DateTimeFormatter ROW_TIME = DateTimeFormatter.ofPattern("hh:mm", AR);
    private static final String[] SIX = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView tvHijri, tvGreg, tvPlace, tvRingLabel, tvRingTime, tvPercent, tvWarn, tvNow;
    private RingView ring;
    private GradientBar bar;
    private final LinearLayout[] rows = new LinearLayout[6];
    private final TextView[] rowName = new TextView[6], rowNote = new TextView[6], rowTime = new TextView[6];
    private int cGreen, cRed, cText, cMuted, cGold;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF113A2C);
        getWindow().setNavigationBarColor(0xFF0B121C);
        setContentView(R.layout.activity_main);
        tvHijri = findViewById(R.id.tvHijri);
        tvGreg = findViewById(R.id.tvGreg);
        tvPlace = findViewById(R.id.tvPlace);
        tvRingLabel = findViewById(R.id.tvRingLabel);
        tvRingTime = findViewById(R.id.tvRingTime);
        tvPercent = findViewById(R.id.tvPercent);
        tvWarn = findViewById(R.id.tvWarn);
        tvNow = findViewById(R.id.tvNow);
        ring = findViewById(R.id.ring);
        bar = findViewById(R.id.bar);
        cGreen = 0xFF34D399;
        cRed = 0xFFFF5A4E;
        cText = getColor(R.color.text);
        cMuted = 0xFFB8C4C2;
        cGold = getColor(R.color.gold);
        buildRows();
        fitInsetsAndRing();

        findViewById(R.id.navHome).setOnClickListener(v -> ((android.widget.ScrollView) findViewById(R.id.scroll)).smoothScrollTo(0, 0));
        findViewById(R.id.navSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.placeBox).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        WidgetUpdater.updateAll(this);
        AlertScheduler.reschedule(this);
        if (System.currentTimeMillis() - Prefs.lastUpdateCheck(this) > 12 * 3600_000L) {
            Updater.check(this, true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    /** يُبعد المحتوى عن أشرطة النظام، ويضبط عرض محتوى الحلقة ليتسع داخلها في كل الشاشات. */
    private void fitInsetsAndRing() {
        android.view.View content = findViewById(R.id.content);
        android.view.View nav = findViewById(R.id.navBar);
        Insets.on(content, (top, bottom) -> {
            content.setPadding(0, top, 0, 0);
            nav.setPadding(0, 0, 0, bottom);
        });
        tvRingTime.setAutoSizeTextTypeUniformWithConfiguration(26, 46, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        ring.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            if (w <= 0) return;
            float d = getResources().getDisplayMetrics().density;
            int inner = (int) (w - 2 * 24 * d - 2 * 14 * d); // قطر الدائرة الداخلية
            int target = (int) (inner * 0.74f);
            android.view.View box = findViewById(R.id.ringInner);
            if (box.getLayoutParams().width != target) {
                box.getLayoutParams().width = target;
                box.post(box::requestLayout);
            }
        });
    }

    private void buildRows() {
        LinearLayout box = findViewById(R.id.timesBox);
        Typeface bold = getResources().getFont(R.font.tajawal_extrabold);
        Typeface med = getResources().getFont(R.font.tajawal_medium);
        for (int i = 0; i < 6; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(14), dp(18), dp(14));
            row.setBackgroundResource(R.drawable.row_card);

            TextView n = new TextView(this);
            n.setTypeface(bold);
            n.setTextSize(20);
            n.setTextColor(cText);
            TextView note = new TextView(this);
            note.setTypeface(med);
            note.setTextSize(13);
            note.setMaxLines(1);
            note.setEllipsize(android.text.TextUtils.TruncateAt.END);
            note.setPadding(dp(10), 0, dp(8), 0);
            TextView t = new TextView(this);
            t.setTypeface(bold);
            t.setTextSize(20);
            t.setTextColor(cText);

            row.addView(n, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(note, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            box.addView(row, lp);
            rows[i] = row; rowName[i] = n; rowNote[i] = note; rowTime[i] = t;
        }
    }

    private static String ampm(ZonedDateTime t) { return t.getHour() < 12 ? "ص" : "م"; }

    private void render() {
        Schedule s = Prefs.schedule(this);
        ZonedDateTime now = s.now;

        tvHijri.setText(Dates.day(now) + "، " + Dates.hijri(now));
        tvGreg.setText(Dates.greg(now));
        tvPlace.setText(Prefs.placeName(this));
        tvNow.setText("الوقت الحالي " + now.format(CLOCK) + " " + ampm(now));

        float u = s.urgency();
        if (s.current != null) {
            float prog = s.currentProgress();
            boolean darura = s.inDarura();
            boolean hasDarura = s.current.choiceEnd.isBefore(s.current.end);
            tvRingLabel.setText(darura ? "وقت الضرورة • باقي على خروج " + s.current.name + ":"
                    : (hasDarura ? "باقي من وقت اختيار " : "باقي على خروج وقت ") + s.current.name + ":");
            tvRingTime.setText(hms(s.currentRemaining()));
            ring.set(prog, true);
            bar.set(prog, true);
            tvPercent.setText((darura ? "انتهى وقت الاختيار" : "مضى " + Math.round(prog * 100) + "%")
                    + " • منذ الأذان " + Schedule.fmt(s.sincePrevious()));
            String madh = "المذهب " + Schedule.MADHHABS[s.madhhab];
            if (darura) {
                tvWarn.setText("أنت في وقت الضرورة (" + madh + ") • ينتهي " + s.current.end.format(TIME));
                tvWarn.setTextColor(cRed);
            } else if (u > 0) {
                tvWarn.setText("الوقت المتبقي يوشك على النهاية"
                        + (hasDarura ? " (ثم وقت الضرورة حتى " + s.current.end.format(TIME) + ")" : ""));
                tvWarn.setTextColor(cRed);
            } else {
                tvWarn.setText((hasDarura ? "الاختيار حتى " + s.current.choiceEnd.format(TIME) + " • الضرورة حتى "
                        : "ينتهي وقت " + s.current.name + " ") + s.current.end.format(TIME));
                tvWarn.setTextColor(cMuted);
            }
            tvRingTime.setTextColor(Schedule.blend(cText, cRed, u));
        } else {
            double total = Duration.between(s.previous.adhan, s.next.adhan).getSeconds();
            float prog = (float) Math.max(0, Math.min(1, Duration.between(s.previous.adhan, now).getSeconds() / total));
            tvRingLabel.setText("باقي على أذان " + s.next.name + ":");
            tvRingTime.setText(hms(s.untilNext()));
            tvRingTime.setTextColor(cText);
            ring.set(prog, false);
            bar.set(prog, false);
            tvPercent.setText("مضى على أذان " + s.previous.name + " " + Schedule.fmt(s.sincePrevious()));
            tvWarn.setText("الأذان " + s.next.adhan.format(TIME) + " • الإقامة بعده بـ" + s.iqamaMinutesNext() + " د");
            tvWarn.setTextColor(cMuted);
        }

        renderRows(s, u);
    }

    private void renderRows(Schedule s, float u) {
        boolean nextIsTomorrowFajr = s.next.index == PrayerCalc.FAJR && !s.next.adhan.toLocalDate().equals(s.now.toLocalDate());
        for (int i = 0; i < 6; i++) {
            ZonedDateTime t = s.today[i];
            if (i == PrayerCalc.FAJR && nextIsTomorrowFajr) t = s.next.adhan;
            String name = SIX[i];
            if (i == PrayerCalc.DHUHR && t.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) name = "الجمعة";
            boolean isCur = s.current != null && i == s.current.index && t.equals(s.current.adhan);
            boolean isNext = t.equals(s.next.adhan);

            rowName[i].setText(name);
            rowTime[i].setText(t.format(ROW_TIME) + " " + ampm(t));
            rowName[i].setTextColor(cText);
            rowTime[i].setTextColor(cText);

            String note = "";
            int noteColor = cMuted;
            int bg = R.drawable.row_card;
            if (isCur) {
                if (s.iqamaActive()) {
                    note = "الإقامة بعد " + Schedule.fmt(s.untilIqama());
                    noteColor = cGold;
                } else if (s.inDarura()) {
                    note = "وقت الضرورة - المذهب " + Schedule.MADHHABS[s.madhhab];
                    noteColor = cRed;
                } else if (u > 0) {
                    note = "أوشكت على النهاية - المذهب " + Schedule.MADHHABS[s.madhhab];
                    noteColor = cRed;
                } else {
                    note = "الوقت الحالي";
                    noteColor = cGreen;
                }
                bg = u > 0 ? R.drawable.row_card_warn : R.drawable.row_card_cur;
            } else if (isNext) {
                note = (nextIsTomorrowFajr ? "غدًا • " : "") + "بعد " + Schedule.fmt(s.untilNext());
                noteColor = cGold;
                bg = R.drawable.row_card_next;
            } else if (t.isBefore(s.now)) {
                rowName[i].setTextColor(cMuted);
                rowTime[i].setTextColor(cMuted);
            }
            rowNote[i].setText(note);
            rowNote[i].setTextColor(noteColor);
            rows[i].setBackgroundResource(bg);
        }
    }

    /** دائمًا بصيغة 00:00:00 كما في التصميم. */
    private static String hms(Duration d) {
        long sec = Math.max(0, d.getSeconds());
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
