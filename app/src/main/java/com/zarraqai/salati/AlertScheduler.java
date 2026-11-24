package com.zarraqai.salati;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.zarraqai.salati.core.PrayerCalc;
import com.zarraqai.salati.core.Schedule;

/** يجدول منبّهًا دقيقًا لأقرب أذان مفعّل التنبيه. */
public final class AlertScheduler {

    public static final String ACTION_ADHAN = "com.zarraqai.salati.ADHAN";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_AT = "at";

    private AlertScheduler() {}

    /** رقم الصلاة في إعدادات التنبيه (0..4) أو -1. */
    static int alertIndex(int prayerIndex) {
        switch (prayerIndex) {
            case PrayerCalc.FAJR: return 0;
            case PrayerCalc.DHUHR: return 1;
            case PrayerCalc.ASR: return 2;
            case PrayerCalc.MAGHRIB: return 3;
            case PrayerCalc.ISHA: return 4;
            default: return -1;
        }
    }

    public static void reschedule(Context ctx) {
        Context c = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        boolean[] on = Prefs.alerts(c);
        Schedule s = Prefs.schedule(c);
        Schedule.Prayer target = null;
        // نتجاوز ما مضى، ونترك هامش 30 ثانية حتى لا يتكرر تنبيه الأذان نفسه
        java.time.ZonedDateTime after = s.now.plusSeconds(30);
        for (Schedule.Prayer p : s.all) {
            int ai = alertIndex(p.index);
            if (ai >= 0 && on[ai] && p.adhan.isAfter(after)) { target = p; break; }
        }
        PendingIntent old = pending(c, null, 0, PendingIntent.FLAG_NO_CREATE);
        if (target == null) {
            if (old != null) am.cancel(old);
            return;
        }
        long when = target.adhan.toInstant().toEpochMilli();
        PendingIntent pi = pending(c, target.name, when, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    private static PendingIntent pending(Context c, String name, long at, int flag) {
        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACTION_ADHAN);
        if (name != null) { i.putExtra(EXTRA_NAME, name); i.putExtra(EXTRA_AT, at); }
        return PendingIntent.getBroadcast(c, 2, i, flag | PendingIntent.FLAG_IMMUTABLE);
    }
}
