package com.zarraqai.salati;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.zarraqai.salati.core.AlertPlan;
import com.zarraqai.salati.core.Schedule;

import java.util.List;

/**
 * يجدول منبّهًا دقيقًا واحدًا لأقرب تنبيه قادم: من خطة كل صلاة حسب مستواها،
 * أو التنبيه الإضافي (تكرار عند عدم الاستجابة، أو سؤال «هل أنت مستيقظ؟»).
 * تُتجاوز الصلوات التي ضُغط فيها «صلّيت».
 */
public final class AlertScheduler {

    public static final String ACTION_ADHAN = "com.zarraqai.salati.ADHAN";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_AT = "at";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_STRENGTH = "strength";
    public static final String EXTRA_SLOT = "slot";
    public static final String EXTRA_KEY = "key";
    public static final String EXTRA_REP = "rep";
    public static final String EXTRA_REPEAT = "repeat";

    private AlertScheduler() {}

    /** أقرب تنبيه قادم، أو null. */
    static Intent nextAlert(Context c) {
        boolean[] on = Prefs.alerts(c);
        Schedule s = Prefs.schedule(c);
        long now = System.currentTimeMillis();
        long after = Math.max(Prefs.lastFired(c), now - 60_000L);

        Intent best = null;
        long bestAt = Long.MAX_VALUE;
        for (Schedule.Prayer p : s.all) {
            int slot = Schedule.slot(p.index);
            if (slot < 0 || !on[slot] || !p.end.isAfter(s.now)) continue;
            String key = Schedule.key(p);
            if (Prefs.isDone(c, key)) continue;
            int level = Prefs.level(c, slot);
            List<AlertPlan.Event> plan = AlertPlan.forPrayer(p, level, Prefs.atHome(c, slot), Prefs.smart(c, slot));
            for (AlertPlan.Event e : plan) {
                long at = e.at.toInstant().toEpochMilli();
                if (at <= after || at >= bestAt) continue;
                bestAt = at;
                best = intent(c, e.slot, e.type, e.strength, at, e.key, e.name, 0, level >= AlertPlan.MEDIUM);
                break; // الخطة مرتبة زمنيًا
            }
        }
        long xAt = Prefs.extraAt(c);
        if (xAt > 0) {
            if (Prefs.isDone(c, Prefs.extraKey(c))) {
                Prefs.clearExtra(c);
            } else if (xAt > after && xAt < bestAt) {
                int slot = Prefs.extraSlot(c);
                best = intent(c, slot, Prefs.extraType(c), Prefs.extraStrength(c), xAt, Prefs.extraKey(c),
                        Prefs.extraName(c), Prefs.extraRep(c), Prefs.level(c, slot) >= AlertPlan.MEDIUM);
                best.putExtra("extra", true);
            }
        }
        return best;
    }

    static Intent intent(Context c, int slot, int type, int strength, long at, String key, String name, int rep, boolean repeat) {
        return new Intent(c, AdhanReceiver.class).setAction(ACTION_ADHAN)
                .putExtra(EXTRA_SLOT, slot).putExtra(EXTRA_TYPE, type).putExtra(EXTRA_STRENGTH, strength)
                .putExtra(EXTRA_AT, at).putExtra(EXTRA_KEY, key).putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_REP, rep).putExtra(EXTRA_REPEAT, repeat);
    }

    public static void reschedule(Context ctx) {
        Context c = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent next = nextAlert(c);
        if (next == null) {
            PendingIntent old = PendingIntent.getBroadcast(c, 2, new Intent(c, AdhanReceiver.class).setAction(ACTION_ADHAN),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (old != null) am.cancel(old);
            return;
        }
        long when = next.getLongExtra(EXTRA_AT, 0);
        PendingIntent pi = PendingIntent.getBroadcast(c, 2, next, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                // منبّه من نوع «ساعة منبّه»: الأوثق، ولا تؤخره أوضاع توفير البطارية
                PendingIntent show = PendingIntent.getActivity(c, 5, new Intent(c, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    /** تكرار التنبيه بعد دقائق إن لم يستجب المستخدم (حتى مرتين). */
    static void snooze(Context c, Intent fired, int minutes) {
        int rep = fired.getIntExtra(EXTRA_REP, 0);
        if (rep >= 2) return;
        String key = fired.getStringExtra(EXTRA_KEY);
        if (key == null || Prefs.isDone(c, key)) return;
        int strength = fired.getIntExtra(EXTRA_STRENGTH, AlertPlan.S_NORMAL);
        Prefs.setExtra(c, System.currentTimeMillis() + minutes * 60_000L, AlertPlan.T_SNOOZE,
                Math.max(strength, AlertPlan.S_STRONG), fired.getIntExtra(EXTRA_SLOT, 0), key,
                fired.getStringExtra(EXTRA_NAME), rep + 1);
        reschedule(c);
    }

    /** سؤال «هل أنت مستيقظ؟» بعد اجتياز التحدي. */
    static void scheduleCheck(Context c, int slot, String key, String name, int minutes) {
        Prefs.setExtra(c, System.currentTimeMillis() + minutes * 60_000L, AlertPlan.T_CHECK, AlertPlan.S_SMART, slot, key, name, 0);
        reschedule(c);
    }
}
