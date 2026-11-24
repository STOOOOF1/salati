package com.zarraqai.salati;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import com.zarraqai.salati.core.PrayerCalc;
import com.zarraqai.salati.core.Schedule;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** يبني الويدجتات السبع ويجدول التحديث القادم. */
public final class WidgetUpdater {

    public static final String ACTION_UPDATE = "com.zarraqai.salati.UPDATE";

    static final int SMALL = 0, RING = 1, STRIP = 2, MEDIUM = 3, LARGE = 4, TODAY = 5, WEEK = 6;
    private static final Class<?>[] CLASSES = {SmallWidget.class, RingWidget.class, StripWidget.class,
            MediumWidget.class, LargeWidget.class, TodayWidget.class, WeekWidget.class};
    private static final int[] LAYOUTS = {R.layout.widget_small, R.layout.widget_ring, R.layout.widget_strip,
            R.layout.widget_medium, R.layout.widget_large, R.layout.widget_today, R.layout.widget_week};

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("h:mm", new Locale("ar"));
    private static final String[] SIX = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final int[] FIVE = {PrayerCalc.FAJR, PrayerCalc.DHUHR, PrayerCalc.ASR, PrayerCalc.MAGHRIB, PrayerCalc.ISHA};

    private WidgetUpdater() {}

    public static void updateAll(Context ctx) {
        Context c = ctx.getApplicationContext();
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        Schedule s = Prefs.schedule(c);
        int total = 0;
        for (int k = 0; k < CLASSES.length; k++) {
            int[] ids = m.getAppWidgetIds(new ComponentName(c, CLASSES[k]));
            if (ids == null || ids.length == 0) continue;
            try {
                m.updateAppWidget(ids, build(c, s, k));
            } catch (Exception ignored) {
                // لا نترك خطأ في ويدجت يوقف البقية
            }
            total += ids.length;
        }
        if (total > 0) scheduleNext(c, s);
        else cancel(c);
    }

    // ---------- الألوان ----------
    private static final class Pal {
        final int accent, red, text, muted, gold, cur;
        final float u;
        Pal(Context c, Schedule s) {
            accent = c.getColor(R.color.accent); red = c.getColor(R.color.danger);
            text = c.getColor(R.color.text); muted = c.getColor(R.color.muted); gold = c.getColor(R.color.gold);
            u = s.urgency();
            cur = Schedule.blend(accent, red, u);
        }
    }

    private static RemoteViews build(Context c, Schedule s, int kind) {
        RemoteViews v = new RemoteViews(c.getPackageName(), LAYOUTS[kind]);
        Pal p = new Pal(c, s);
        long nowMs = System.currentTimeMillis();
        long nowEl = SystemClock.elapsedRealtime();

        switch (kind) {
            case SMALL:
                next(v, s, nowMs, nowEl, "أذان " + s.next.name + " بعد");
                current(v, s, p, nowMs, nowEl, "يخرج " + nameOr(s));
                iqama(v, s, p, nowMs, nowEl, true);
                break;
            case RING:
                ring(c, v, s, p);
                v.setTextViewText(R.id.nextLabel, s.next.name);
                countdown(v, R.id.nextChrono, s.next.adhan, nowMs, nowEl);
                v.setTextViewText(R.id.sub, s.iqamaActive()
                        ? "إقامة " + s.previous.name + " " + s.previous.iqama.format(SHORT)
                        : (s.current != null ? (s.inDarura() ? "ضرورة حتى " : "يخرج " + s.current.name + " ") + s.currentTarget().format(SHORT)
                                             : "عند " + s.next.adhan.format(MainActivity.TIME)));
                v.setTextColor(R.id.sub, s.iqamaActive() ? p.gold : (p.u > 0 ? p.cur : p.muted));
                break;
            case STRIP:
                strip(v, s, p, nowMs, nowEl);
                break;
            case MEDIUM:
            case LARGE:
                dates(v, s);
                next(v, s, nowMs, nowEl, "أذان " + s.next.name + " بعد");
                current(v, s, p, nowMs, nowEl, "يخرج " + nameOr(s));
                iqama(v, s, p, nowMs, nowEl, true);
                v.setTextViewText(R.id.prevLabel, "مضى على " + s.previous.name);
                long since = nowMs - s.previous.adhan.toInstant().toEpochMilli();
                v.setChronometer(R.id.prevChrono, nowEl - since, null, true);
                v.setChronometerCountDown(R.id.prevChrono, false);
                if (kind == LARGE) sixRows(v, s, p, false);
                break;
            case TODAY:
                dates(v, s);
                v.setTextViewText(R.id.hijri, Dates.day(s.now) + " " + Dates.hijri(s.now));
                next(v, s, nowMs, nowEl, s.next.name + " بعد");
                sixRows(v, s, p, true);
                break;
            case WEEK:
                week(c, v, s, p);
                break;
        }

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.root, pi);
        return v;
    }

    private static String nameOr(Schedule s) { return s.current != null ? s.current.name : ""; }

    // ---------- أجزاء مشتركة ----------

    private static void dates(RemoteViews v, Schedule s) {
        v.setTextViewText(R.id.hijri, Dates.hijri(s.now));
        v.setTextViewText(R.id.greg, Dates.greg(s.now));
    }

    private static void next(RemoteViews v, Schedule s, long nowMs, long nowEl, String label) {
        v.setTextViewText(R.id.nextLabel, label);
        countdown(v, R.id.nextChrono, s.next.adhan, nowMs, nowEl);
    }

    private static void current(RemoteViews v, Schedule s, Pal p, long nowMs, long nowEl, String label) {
        if (s.current != null) {
            v.setTextViewText(R.id.curLabel, label);
            if (s.inDarura()) v.setTextViewText(R.id.curLabel, "ضرورة " + s.current.name);
            countdown(v, R.id.curChrono, s.currentTarget(), nowMs, nowEl);
            v.setTextColor(R.id.curChrono, p.cur);
            v.setTextColor(R.id.curLabel, p.u > 0 ? p.cur : p.muted);
            v.setProgressBar(R.id.progress, 1000, (int) (s.currentProgress() * 1000), false);
            if (Build.VERSION.SDK_INT >= 31) {
                v.setColorStateList(R.id.progress, "setProgressTintList", ColorStateList.valueOf(p.cur));
            }
        } else {
            v.setTextViewText(R.id.curLabel, "لا وقت قائم");
            v.setChronometer(R.id.curChrono, nowEl, "—", false);
            v.setTextColor(R.id.curLabel, p.muted);
            v.setTextColor(R.id.curChrono, p.muted);
            v.setProgressBar(R.id.progress, 1000, 0, false);
        }
    }

    private static void iqama(RemoteViews v, Schedule s, Pal p, long nowMs, long nowEl, boolean shortLabel) {
        if (s.iqamaActive()) {
            v.setTextViewText(R.id.iqLabel, "إقامة " + s.previous.name);
            countdown(v, R.id.iqChrono, s.previous.iqama, nowMs, nowEl);
            v.setTextColor(R.id.iqChrono, p.gold);
        } else {
            v.setTextViewText(R.id.iqLabel, "إقامة " + s.next.name);
            v.setChronometer(R.id.iqChrono, nowEl, "+" + s.iqamaMinutesNext() + " د", false);
            v.setTextColor(R.id.iqChrono, p.muted);
        }
    }

    /** صفوف اليوم الستة (مع الشروق). */
    private static void sixRows(RemoteViews v, Schedule s, Pal p, boolean withIqama) {
        int[] iq = null;
        for (int i = 0; i < 6; i++) {
            ZonedDateTime t = s.today[i];
            String name = SIX[i];
            if (i == PrayerCalc.DHUHR && t.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) name = "الجمعة";
            boolean isCur = s.current != null && t.equals(s.current.adhan);
            boolean isNext = t.equals(s.next.adhan);
            boolean passed = !t.isAfter(s.now) && !isCur;
            int col = isCur ? p.cur : (isNext ? p.gold : (passed ? p.muted : p.text));
            v.setTextViewText(WidgetIds.NAME[i], name);
            v.setTextViewText(WidgetIds.TIME[i], withIqama ? t.format(SHORT) : t.format(MainActivity.TIME));
            v.setTextColor(WidgetIds.NAME[i], col);
            v.setTextColor(WidgetIds.TIME[i], col);
            int bg = isNext ? R.drawable.pill_gold : (isCur ? R.drawable.row_today : 0);
            v.setInt(WidgetIds.ROW[i], "setBackgroundResource", bg);
            if (withIqama) {
                String q = "";
                for (Schedule.Prayer pr : s.all) {
                    if (pr.adhan.equals(t)) { q = "إقامة " + pr.iqama.format(SHORT); break; }
                }
                v.setTextViewText(WidgetIds.IQ[i], q);
                v.setTextColor(WidgetIds.IQ[i], isNext ? p.gold : p.muted);
            }
        }
    }

    private static void strip(RemoteViews v, Schedule s, Pal p, long nowMs, long nowEl) {
        for (int k = 0; k < 5; k++) {
            int idx = FIVE[k];
            ZonedDateTime t = s.today[idx];
            String name = Schedule.NAMES[idx];
            if (idx == PrayerCalc.DHUHR && t.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) name = "الجمعة";
            // إن انتهت صلوات اليوم نُبرز فجر الغد في عمود الفجر
            boolean isNext = s.next.index == idx && Duration.between(s.now, s.next.adhan).toHours() < 20;
            boolean isCur = s.current != null && s.current.index == idx && t.equals(s.current.adhan);
            ZonedDateTime show = isNext ? s.next.adhan : t;
            v.setTextViewText(WidgetIds.COL_NAME[k], name);
            v.setTextViewText(WidgetIds.COL_TIME[k], show.format(SHORT));
            int col = isNext ? p.gold : (isCur ? p.cur : (t.isBefore(s.now) ? p.muted : p.text));
            v.setTextColor(WidgetIds.COL_NAME[k], isNext ? p.gold : p.muted);
            v.setTextColor(WidgetIds.COL_TIME[k], col);
            v.setInt(WidgetIds.COL[k], "setBackgroundResource", isNext ? R.drawable.pill_gold : (isCur ? R.drawable.row_today : 0));
            if (isNext) {
                countdown(v, WidgetIds.COL_CHRONO[k], s.next.adhan, nowMs, nowEl);
                v.setTextColor(WidgetIds.COL_CHRONO[k], p.gold);
                v.setViewVisibility(WidgetIds.COL_CHRONO[k], View.VISIBLE);
            } else if (isCur && s.current != null) {
                countdown(v, WidgetIds.COL_CHRONO[k], s.currentTarget(), nowMs, nowEl);
                v.setTextColor(WidgetIds.COL_CHRONO[k], p.cur);
                v.setViewVisibility(WidgetIds.COL_CHRONO[k], p.u > 0 ? View.VISIBLE : View.GONE);
            } else {
                v.setViewVisibility(WidgetIds.COL_CHRONO[k], View.GONE);
            }
        }
    }

    private static void week(Context c, RemoteViews v, Schedule s, Pal p) {
        v.setTextViewText(R.id.month, Dates.hijriMonth(s.now));
        v.setTextViewText(R.id.place, Prefs.placeName(c) + " • " + Dates.greg(s.now));
        double lat = Prefs.lat(c), lng = Prefs.lng(c);
        DateTimeFormatter dayF = DateTimeFormatter.ofPattern("EEEE", new Locale("ar"));
        for (int d = 0; d < 7; d++) {
            LocalDate day = s.now.toLocalDate().plusDays(d);
            ZonedDateTime[] t = PrayerCalc.compute(day, lat, lng, s.now.getZone(), Prefs.isRamadan(day),
                    s.madhhab == Schedule.HANAFI ? 2 : 1);
            ZonedDateTime noon = day.atTime(12, 0).atZone(s.now.getZone());
            boolean today = d == 0;
            v.setTextViewText(WidgetIds.WEEK_DAY[d], today ? "اليوم" : noon.format(dayF));
            v.setTextViewText(WidgetIds.WEEK_DATE[d], Dates.hijri(noon).replace(" هـ", ""));
            v.setTextColor(WidgetIds.WEEK_DAY[d], today ? p.gold : p.text);
            v.setInt(WidgetIds.WEEK_ROW[d], "setBackgroundResource", today ? R.drawable.row_today : 0);
            for (int j = 0; j < 5; j++) {
                ZonedDateTime x = t[FIVE[j]];
                v.setTextViewText(WidgetIds.WEEK_CELL[d][j], x.format(SHORT));
                int col = p.text;
                if (today) {
                    if (x.equals(s.next.adhan)) col = p.gold;
                    else if (x.isBefore(s.now)) col = p.muted;
                }
                v.setTextColor(WidgetIds.WEEK_CELL[d][j], col);
            }
        }
    }

    /** يرسم حلقة التقدّم صورةً، لأن الويدجت لا يدعم الرسم المباشر. */
    private static void ring(Context c, RemoteViews v, Schedule s, Pal p) {
        int size = 360;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        float sw = 22f;
        RectF box = new RectF(sw, sw, size - sw, size - sw);
        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(sw);
        track.setColor(0x22FFFFFF);
        cv.drawArc(box, 0, 360, false, track);

        float prog;
        if (s.current != null) {
            prog = s.currentProgress();
        } else {
            // بين الأوقات: نُظهر قرب الأذان القادم
            double total = Duration.between(s.previous.adhan, s.next.adhan).getSeconds();
            double done = Duration.between(s.previous.adhan, s.now).getSeconds();
            prog = (float) Math.max(0, Math.min(1, done / total));
        }
        Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(sw);
        arc.setStrokeCap(Paint.Cap.ROUND);
        boolean warm = s.current != null;
        android.graphics.SweepGradient g = new android.graphics.SweepGradient(size / 2f, size / 2f,
                warm ? RingView.WARM : RingView.COOL, warm ? RingView.WARM_POS : RingView.COOL_POS);
        android.graphics.Matrix mx = new android.graphics.Matrix();
        mx.setRotate(-96, size / 2f, size / 2f);
        g.setLocalMatrix(mx);
        arc.setShader(g);
        cv.drawArc(box, -90, Math.max(1f, 360f * prog), false, arc);

        // علامات الدقائق الخفيفة
        Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        tick.setColor(0x33FFFFFF);
        float cx = size / 2f, r = size / 2f - sw * 2.1f;
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30);
            cv.drawCircle(cx + (float) (r * Math.cos(a)), cx + (float) (r * Math.sin(a)), 2.6f, tick);
        }
        v.setImageViewBitmap(R.id.ring, bmp);
    }

    private static void countdown(RemoteViews v, int id, ZonedDateTime target, long nowMs, long nowEl) {
        long left = target.toInstant().toEpochMilli() - nowMs;
        v.setChronometer(id, nowEl + left, null, true);
        v.setChronometerCountDown(id, true);
    }

    // ---------- الجدولة ----------

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, UpdateReceiver.class).setAction(ACTION_UPDATE);
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleNext(Context c, Schedule s) {
        ZonedDateTime now = s.now;
        ZonedDateTime at = s.nextBoundary();
        if (s.iqamaActive() && s.previous.iqama.isBefore(at)) at = s.previous.iqama;
        if (s.current != null && !s.inDarura()) {
            ZonedDateTime warnStart = s.current.choiceEnd.minusMinutes(s.warnMinutes);
            if (now.isBefore(warnStart)) {
                if (warnStart.isBefore(at)) at = warnStart;
            } else {
                // داخل فترة الإنذار: حدّث اللون كل دقيقة
                ZonedDateTime minute = now.plusMinutes(1).withSecond(0).withNano(0);
                if (minute.isBefore(at)) at = minute;
            }
        }
        // تحديث كل 15 دقيقة لحلقة التقدّم وشريطه، وعند منتصف الليل لتغيّر التاريخ
        ZonedDateTime q = now.plusMinutes(15);
        if (q.isBefore(at)) at = q;
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()).plusSeconds(5);
        if (midnight.isBefore(at)) at = midnight;

        long when = at.toInstant().toEpochMilli() + 1000;
        long min = System.currentTimeMillis() + 20_000L;
        if (when < min) when = min;

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = alarmIntent(c);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setWindow(AlarmManager.RTC, when, 30_000, pi);
            } else {
                am.setExact(AlarmManager.RTC, when, pi);
            }
        } catch (SecurityException e) {
            am.setWindow(AlarmManager.RTC, when, 30_000, pi);
        }
    }

    private static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(alarmIntent(c));
    }
}
