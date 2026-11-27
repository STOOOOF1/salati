package com.zarraqai.salati;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zarraqai.salati.core.PrayerCalc;
import com.zarraqai.salati.core.Schedule;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** الشاشة الرئيسية: الساعة منفردة، وصندوق الصلاة الحاضرة إذا دخل وقتها، ثم مواقيت اليوم. */
public class MainActivity extends Activity {

    static final Locale AR = new Locale("ar");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", AR);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("h:mm", Locale.US);
    private static final DateTimeFormatter SEC = DateTimeFormatter.ofPattern("ss", Locale.US);
    private static final DateTimeFormatter ROW_TIME = DateTimeFormatter.ofPattern("hh:mm", Locale.US);
    private static final String[] ROWS = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء", "منتصف الليل"};

    // فتاوى الشيخ ابن باز رحمه الله (موقعه الرسمي)
    private static final String RULE_MAIN = "«أما تأخير العصر إلى أن تصفر الشمس، أو العشاء إلى ما بعد نصف الليل، فهذا أمر لا يجوز مطلقًا»";
    private static final String URL_MAIN = "https://binbaz.org.sa/fatwas/7404";
    private static final String RULE_ASR = "«يصليها ولو بعد اصفرار الشمس، ويكون آثم إذا كان تعمد هذا، ولكن صلاتها في الوقت»";
    private static final String RULE_ISHA = "«لو صلاها بعد نصف الليل تكون في الوقت، لكن مع الإثم، مع الإثم إذا أخرها عامدًا»";
    private static final String URL_ASR = "https://binbaz.org.sa/fatwas/8293";
    private static final String URL_ISHA = "https://binbaz.org.sa/fatwas/8795";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView tvHijri, tvGreg, tvPlace, tvClock, tvSec, tvAmPm, tvNext;
    private View curBox, daruraBox, btnDone, doneRow;
    private TextView curName, curChip, curTimes, curElapsed, curRemLabel, curRemaining, curIqama,
            daruraTitle, daruraRule, daruraSource;
    private GradientBar bar;
    private final LinearLayout[] rows = new LinearLayout[7];
    private final TextView[] rowName = new TextView[7], rowNote = new TextView[7], rowTime = new TextView[7];
    private int cGreen, cRed, cText, cMuted, cGold;
    private String curKey, ruleUrl;

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
        tvClock = findViewById(R.id.tvClock);
        tvSec = findViewById(R.id.tvSec);
        tvAmPm = findViewById(R.id.tvAmPm);
        tvNext = findViewById(R.id.tvNext);
        curBox = findViewById(R.id.curBox);
        curName = findViewById(R.id.curName);
        curChip = findViewById(R.id.curChip);
        curTimes = findViewById(R.id.curTimes);
        curElapsed = findViewById(R.id.curElapsed);
        curRemLabel = findViewById(R.id.curRemLabel);
        curRemaining = findViewById(R.id.curRemaining);
        curIqama = findViewById(R.id.curIqama);
        daruraBox = findViewById(R.id.daruraBox);
        daruraTitle = findViewById(R.id.daruraTitle);
        daruraRule = findViewById(R.id.daruraRule);
        daruraSource = findViewById(R.id.daruraSource);
        btnDone = findViewById(R.id.btnDone);
        doneRow = findViewById(R.id.doneRow);
        bar = findViewById(R.id.bar);
        cGreen = 0xFF34D399;
        cRed = 0xFFFF5A4E;
        cText = getColor(R.color.text);
        cMuted = 0xFFB8C4C2;
        cGold = getColor(R.color.gold);
        buildRows();

        View content = findViewById(R.id.content);
        View nav = findViewById(R.id.navBar);
        Insets.on(content, (top, bottom) -> {
            content.setPadding(0, top, 0, 0);
            nav.setPadding(0, 0, 0, bottom);
        });

        findViewById(R.id.navHome).setOnClickListener(v -> ((android.widget.ScrollView) findViewById(R.id.scroll)).smoothScrollTo(0, 0));
        findViewById(R.id.navSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.placeBox).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnDone.setOnClickListener(v -> { if (curKey != null) { DoneReceiver.mark(this, curKey, true); render(); } });
        findViewById(R.id.btnUndo).setOnClickListener(v -> { if (curKey != null) { DoneReceiver.mark(this, curKey, false); render(); } });
        daruraSource.setOnClickListener(v -> {
            if (ruleUrl == null) return;
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ruleUrl))); } catch (Exception ignored) {}
        });
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

    private void buildRows() {
        LinearLayout box = findViewById(R.id.timesBox);
        Typeface bold = getResources().getFont(R.font.tajawal_extrabold);
        Typeface med = getResources().getFont(R.font.tajawal_medium);
        for (int i = 0; i < 7; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(11), dp(16), dp(11));
            row.setBackgroundResource(R.drawable.row_card);

            TextView n = new TextView(this);
            n.setTypeface(bold);
            n.setTextSize(17);
            n.setTextColor(cText);
            TextView note = new TextView(this);
            note.setTypeface(med);
            note.setTextSize(13);
            note.setMaxLines(1);
            note.setEllipsize(android.text.TextUtils.TruncateAt.END);
            note.setPadding(dp(10), 0, dp(8), 0);
            TextView t = new TextView(this);
            t.setTypeface(bold);
            t.setTextSize(17);
            t.setTextColor(cText);

            row.addView(n, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(note, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            box.addView(row, lp);
            rows[i] = row; rowName[i] = n; rowNote[i] = note; rowTime[i] = t;
        }
    }

    private static String ampm(ZonedDateTime t) { return t.getHour() < 12 ? "ص" : "م"; }
    private static String hm(ZonedDateTime t) { return t.format(HM) + " " + ampm(t); }

    private void render() {
        Schedule s = Prefs.schedule(this);
        ZonedDateTime now = s.now;

        tvHijri.setText(Dates.day(now) + "، " + Dates.hijri(now));
        tvGreg.setText(Dates.greg(now));
        tvPlace.setText(Prefs.placeName(this));
        tvClock.setText(now.format(HM));
        tvSec.setText(now.format(SEC));
        tvAmPm.setText(ampm(now));

        float u = s.urgency();
        Schedule.Prayer cur = s.current;
        if (cur != null) renderCurrent(s, cur, u);
        else { curBox.setVisibility(View.GONE); curKey = null; }

        boolean tomorrow = !s.next.adhan.toLocalDate().equals(now.toLocalDate());
        tvNext.setText("الصلاة القادمة: " + s.next.name + (tomorrow ? " غدًا " : " ") + hm(s.next.adhan)
                + " • بعد " + Schedule.fmt(s.untilNext()));

        renderRows(s, u);
    }

    private void renderCurrent(Schedule s, Schedule.Prayer cur, float u) {
        curBox.setVisibility(View.VISIBLE);
        curKey = Schedule.key(cur);
        boolean darura = s.inDarura();
        boolean hasDarura = Schedule.hasDarura(cur);

        curName.setText("صلاة " + cur.name);
        if (darura) { curChip.setText("وقت الضرورة"); curChip.setTextColor(cRed); }
        else if (u > 0) { curChip.setText("يوشك على الخروج"); curChip.setTextColor(cRed); }
        else { curChip.setText("الوقت الحاضر"); curChip.setTextColor(cGreen); }
        curBox.setBackgroundResource(u > 0 ? R.drawable.row_card_warn : R.drawable.row_card_cur);

        curTimes.setText(hasDarura
                ? "الأذان " + hm(cur.adhan) + " • الاختيار حتى " + hm(cur.choiceEnd) + "\nالضرورة حتى " + hm(cur.end)
                : "الأذان " + hm(cur.adhan) + " • ينتهي " + hm(cur.end));
        curElapsed.setText(Schedule.fmt(s.sincePrevious()));
        curRemLabel.setText(darura || !hasDarura ? "باقي على خروج الوقت" : "باقي من وقت الاختيار");
        curRemaining.setText(Schedule.fmt(s.currentRemaining()));
        curRemaining.setTextColor(Schedule.blend(cText, cRed, u));
        bar.set(s.currentProgress(), true);

        if (s.iqamaActive()) {
            curIqama.setVisibility(View.VISIBLE);
            curIqama.setText("الإقامة بعد " + Schedule.fmt(s.untilIqama()));
        } else curIqama.setVisibility(View.GONE);

        // وقت الضرورة: تنبيه بلون أحمر مع حكم التأخير
        if (hasDarura && (darura || u > 0)) {
            daruraBox.setVisibility(View.VISIBLE);
            boolean isha = cur.index == PrayerCalc.ISHA;
            daruraTitle.setText(darura
                    ? "دخل وقت الضرورة لصلاة " + cur.name + "، فبادر بها"
                    : "يقترب وقت الضرورة لصلاة " + cur.name + " (" + hm(cur.choiceEnd) + ")");
            daruraRule.setText(darura ? RULE_MAIN + "\n" + (isha ? RULE_ISHA : RULE_ASR) : RULE_MAIN);
            ruleUrl = darura ? (isha ? URL_ISHA : URL_ASR) : URL_MAIN;
            daruraSource.setText("— الشيخ عبدالعزيز بن باز رحمه الله • اضغط لقراءة الفتوى");
        } else if (u > 0) {
            daruraBox.setVisibility(View.VISIBLE);
            daruraTitle.setText("يوشك وقت " + cur.name + " على الخروج (" + hm(cur.end) + ")");
            daruraRule.setText("بادر بالصلاة قبل خروج وقتها.");
            daruraSource.setText("");
            ruleUrl = null;
        } else {
            daruraBox.setVisibility(View.GONE);
            ruleUrl = null;
        }

        boolean done = Prefs.isDone(this, curKey);
        doneRow.setVisibility(done ? View.VISIBLE : View.GONE);
        btnDone.setVisibility(!done && Prefs.canMarkDone(this, cur) ? View.VISIBLE : View.GONE);
        if (done) {
            daruraBox.setVisibility(View.GONE);
            curChip.setText("أُدّيت");
            curChip.setTextColor(cGreen);
            curBox.setBackgroundResource(R.drawable.row_card_cur);
        }
    }

    private void renderRows(Schedule s, float u) {
        boolean nextIsTomorrowFajr = s.next.index == PrayerCalc.FAJR && !s.next.adhan.toLocalDate().equals(s.now.toLocalDate());
        ZonedDateTime mid = s.midnight();
        for (int i = 0; i < 7; i++) {
            ZonedDateTime t = i < 6 ? s.today[i] : mid;
            if (t == null) { rows[i].setVisibility(View.GONE); continue; }
            rows[i].setVisibility(View.VISIBLE);
            if (i == PrayerCalc.FAJR && nextIsTomorrowFajr) t = s.next.adhan;
            String name = ROWS[i];
            if (i == PrayerCalc.DHUHR && t.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) name = "الجمعة";
            boolean isCur = i < 6 && s.current != null && i == s.current.index && t.equals(s.current.adhan);
            boolean isNext = i < 6 && t.equals(s.next.adhan);

            rowName[i].setText(name);
            rowTime[i].setText(t.format(ROW_TIME) + " " + ampm(t));
            rowName[i].setTextColor(i == 6 ? cMuted : cText);
            rowTime[i].setTextColor(i == 6 ? cMuted : cText);

            String note = "";
            int noteColor = cMuted;
            int bg = R.drawable.row_card;
            if (i == 6) {
                note = "نهاية اختيار العشاء";
            } else if (isCur) {
                if (Prefs.isDone(this, Schedule.key(s.current))) { note = "أُدّيت ✓"; noteColor = cGreen; }
                else if (s.inDarura()) { note = "وقت الضرورة"; noteColor = cRed; }
                else if (u > 0) { note = "يوشك"; noteColor = cRed; }
                else { note = "الآن"; noteColor = cGreen; }
                bg = u > 0 && !Prefs.isDone(this, Schedule.key(s.current)) ? R.drawable.row_card_warn : R.drawable.row_card_cur;
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

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
