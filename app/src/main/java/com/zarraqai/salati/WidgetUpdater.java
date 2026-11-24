package com.zarraqai.salati;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.icu.text.DateFormat;
import android.icu.util.ULocale;
import android.os.Build;
import android.os.SystemClock;
import android.widget.RemoteViews;

import com.zarraqai.salati.core.PrayerCalc;
import com.zarraqai.salati.core.Schedule;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;

/** يبني الويدجتات الثلاث ويجدول التحديث القادم. */
public final class WidgetUpdater {

    public static final String ACTION_UPDATE = "com.zarraqai.salati.UPDATE";
    private static final int[] N_IDS = {R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5};
    private static final int[] T_IDS = {R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5};
    private static final int[] R_IDS = {R.id.r0, R.id.r1, R.id.r2, R.id.r3, R.id.r4, R.id.r5};

    private WidgetUpdater() {}

    public static void updateAll(Context ctx) {
        Context c = ctx.getApplicationContext();
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        Schedule s = Prefs.schedule(c);
        int total = 0;
        total += update(c, m, s, SmallWidget.class, R.layout.widget_small, 0);
        total += update(c, m, s, MediumWidget.class, R.layout.widget_medium, 1);
        total += update(c, m, s, LargeWidget.class, R.layout.widget_large, 2);
        if (total > 0) scheduleNext(c, s);
        else cancel(c);
    }

    private static int update(Context c, AppWidgetManager m, Schedule s, Class<?> cls, int layout, int size) {
        int[] ids = m.getAppWidgetIds(new ComponentName(c, cls));
        if (ids == null || ids.length == 0) return 0;
        RemoteViews v = build(c, s, layout, size);
        m.updateAppWidget(ids, v);
        return ids.length;
    }

    private static RemoteViews build(Context c, Schedule s, int layout, int size) {
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        int normal = c.getColor(R.color.accent), red = c.getColor(R.color.danger);
        int text = c.getColor(R.color.text), muted = c.getColor(R.color.muted);
        boolean small = size == 0;
        long nowMs = System.currentTimeMillis();
        long nowEl = SystemClock.elapsedRealtime();

        int gold = c.getColor(R.color.gold);
        boolean medium = size == 1;

        // الوقت الحالي ولونه وشريط التقدم
        float u = s.urgency();
        int col = Schedule.blend(normal, red, u);
        if (s.current != null) {
            v.setTextViewText(R.id.curLabel, small ? "يخرج " + s.current.name : "باقي على خروج وقت " + s.current.name);
            countdown(v, R.id.curChrono, s.current.end, nowMs, nowEl);
            v.setTextColor(R.id.curChrono, col);
            v.setTextColor(R.id.curLabel, u > 0 ? col : muted);
            v.setProgressBar(R.id.progress, 1000, (int) (s.currentProgress() * 1000), false);
            if (Build.VERSION.SDK_INT >= 31) {
                v.setColorStateList(R.id.progress, "setProgressTintList", ColorStateList.valueOf(col));
            }
        } else {
            v.setTextViewText(R.id.curLabel, "لا يوجد وقت صلاة قائم");
            v.setChronometer(R.id.curChrono, nowEl, "", false);
            v.setTextViewText(R.id.curChrono, "");
            v.setTextColor(R.id.curLabel, muted);
            v.setProgressBar(R.id.progress, 1000, 0, false);
        }

        v.setTextViewText(R.id.nextLabel, medium ? "أذان " + s.next.name : "أذان " + s.next.name + " بعد");
        countdown(v, R.id.nextChrono, s.next.adhan, nowMs, nowEl);

        v.setTextViewText(R.id.prevLabel, small ? "مضى " + s.previous.name : "مضى على " + s.previous.name);
        long since = nowMs - s.previous.adhan.toInstant().toEpochMilli();
        v.setChronometer(R.id.prevChrono, nowEl - since, null, true);
        v.setChronometerCountDown(R.id.prevChrono, false);

        if (s.iqamaActive()) {
            v.setTextViewText(R.id.iqLabel, "إقامة " + s.previous.name);
            countdown(v, R.id.iqChrono, s.previous.iqama, nowMs, nowEl);
            v.setTextColor(R.id.iqChrono, gold);
        } else {
            // قبل الأذان: لا عدّ، تظهر مدة الإقامة بعد الأذان فقط
            v.setTextViewText(R.id.iqLabel, small ? "إقامة " + s.next.name : "إقامة " + s.next.name + " بعد الأذان");
            v.setChronometer(R.id.iqChrono, nowEl, "+" + s.iqamaMinutesNext() + " د", false);
            v.setTextColor(R.id.iqChrono, muted);
        }

        if (!small) {
            String hijri = DateFormat.getDateInstance(DateFormat.LONG,
                    new ULocale("ar_SA@calendar=islamic-umalqura")).format(new Date());
            v.setTextViewText(R.id.date, medium ? hijri + "\n" + Prefs.placeName(c) : Prefs.placeName(c) + "  •  " + hijri);
        }

        if (size == 2) {
            for (int i = 0; i < 6; i++) {
                ZonedDateTime t = s.today[i];
                String name = Schedule.NAMES[i];
                if (i == PrayerCalc.DHUHR && t.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) name = "الجمعة";
                boolean isCur = s.current != null && t.equals(s.current.adhan);
                boolean isNext = t.equals(s.next.adhan);
                int cc = isCur ? col : (isNext ? text : muted);
                v.setTextViewText(N_IDS[i], name);
                v.setTextViewText(T_IDS[i], t.format(MainActivity.TIME));
                v.setTextColor(N_IDS[i], cc);
                v.setTextColor(T_IDS[i], cc);
                v.setInt(R_IDS[i], "setBackgroundResource", (isCur || isNext) ? R.drawable.pill : 0);
            }
        }

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.root, pi);
        return v;
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
        if (s.current != null) {
            ZonedDateTime warnStart = s.current.end.minusMinutes(s.warnMinutes);
            if (now.isBefore(warnStart)) {
                if (warnStart.isBefore(at)) at = warnStart;
            } else {
                // داخل فترة الإنذار: حدّث اللون كل دقيقة
                ZonedDateTime minute = now.plusMinutes(1).withSecond(0).withNano(0);
                if (minute.isBefore(at)) at = minute;
            }
        }
        // تحديث احتياطي كل ساعة على الأقل، وعند منتصف الليل لتغيّر التاريخ
        ZonedDateTime hour = now.plusHours(1);
        if (hour.isBefore(at)) at = hour;
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()).plusSeconds(5);
        if (midnight.isBefore(at)) at = midnight;

        long when = at.toInstant().toEpochMilli() + 1000;
        long min = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
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
