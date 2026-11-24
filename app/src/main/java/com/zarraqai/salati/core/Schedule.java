package com.zarraqai.salati.core;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * لقطة لحالة الصلوات في لحظة معيّنة: السابقة والتالية والإقامة ونهاية الوقت الحالي.
 */
public final class Schedule {

    public static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};

    /** صلاة واحدة بوقت أذانها وإقامتها ونهاية وقتها. */
    public static final class Prayer {
        public final int index;          // رقم الصلاة في PrayerCalc
        public final String name;
        public final ZonedDateTime adhan;
        public final ZonedDateTime iqama;
        public ZonedDateTime end;        // نهاية وقت الصلاة

        Prayer(int index, String name, ZonedDateTime adhan, ZonedDateTime iqama) {
            this.index = index; this.name = name; this.adhan = adhan; this.iqama = iqama;
        }
    }

    public final ZonedDateTime now;
    public final Prayer previous;        // آخر صلاة أُذّن لها
    public final Prayer next;            // الصلاة القادمة
    public final Prayer current;         // الصلاة التي ما زال وقتها قائمًا (قد تكون null)
    public final ZonedDateTime iqamaTarget;
    public final String iqamaName;
    public final ZonedDateTime[] today;  // أوقات اليوم الستة
    public final int warnMinutes;

    /**
     * @param iqamaMinutes دقائق الإقامة بعد الأذان: فجر، ظهر، عصر، مغرب، عشاء
     * @param isRamadan    لتأخير العشاء إلى 120 دقيقة في رمضان
     */
    public static Schedule at(ZonedDateTime now, double lat, double lng, int[] iqamaMinutes,
                              int warnMinutes, Predicate<LocalDate> isRamadan) {
        return new Schedule(now, lat, lng, iqamaMinutes, warnMinutes, isRamadan);
    }

    private Schedule(ZonedDateTime now, double lat, double lng, int[] iqamaMinutes,
                     int warnMinutes, Predicate<LocalDate> isRamadan) {
        this.now = now;
        this.warnMinutes = warnMinutes;
        ZoneId zone = now.getZone();
        LocalDate d = now.toLocalDate();

        List<Prayer> list = new ArrayList<>();
        List<ZonedDateTime> sunrises = new ArrayList<>();
        ZonedDateTime[] todayTimes = null;
        for (int off = -1; off <= 2; off++) {
            LocalDate day = d.plusDays(off);
            ZonedDateTime[] t = PrayerCalc.compute(day, lat, lng, zone, isRamadan != null && isRamadan.test(day));
            if (off == 0) todayTimes = t;
            sunrises.add(t[PrayerCalc.SUNRISE]);
            int[] idx = {PrayerCalc.FAJR, PrayerCalc.DHUHR, PrayerCalc.ASR, PrayerCalc.MAGHRIB, PrayerCalc.ISHA};
            for (int k = 0; k < idx.length; k++) {
                int i = idx[k];
                String name = NAMES[i];
                if (i == PrayerCalc.DHUHR && day.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) name = "الجمعة";
                list.add(new Prayer(i, name, t[i], t[i].plusMinutes(iqamaMinutes[k])));
            }
        }
        this.today = todayTimes;

        // نهاية الأوقات: الفجر حتى الشروق، الظهر حتى العصر، العصر حتى المغرب،
        // المغرب حتى العشاء، العشاء حتى منتصف الليل (بين المغرب والفجر التالي).
        for (int j = 0; j < list.size(); j++) {
            Prayer p = list.get(j);
            int day = j / 5;
            switch (p.index) {
                case PrayerCalc.FAJR:
                    p.end = sunrises.get(day);
                    break;
                case PrayerCalc.ISHA: {
                    ZonedDateTime maghrib = list.get(j - 1).adhan;
                    ZonedDateTime nextFajr = j + 1 < list.size() ? list.get(j + 1).adhan : p.adhan.plusHours(9);
                    long half = Duration.between(maghrib, nextFajr).getSeconds() / 2;
                    p.end = maghrib.plusSeconds(half);
                    break;
                }
                default:
                    p.end = list.get(j + 1).adhan;
            }
        }

        Prayer prev = null, nxt = null, cur = null;
        for (Prayer p : list) {
            if (!p.adhan.isAfter(now)) prev = p;
            else { nxt = p; break; }
        }
        if (prev != null && now.isBefore(prev.end)) cur = prev;
        this.previous = prev;
        this.next = nxt;
        this.current = cur;

        if (prev != null && now.isBefore(prev.iqama)) {
            iqamaTarget = prev.iqama; iqamaName = prev.name;
        } else {
            iqamaTarget = nxt.iqama; iqamaName = nxt.name;
        }
    }

    public Duration untilNext() { return Duration.between(now, next.adhan); }
    public Duration sincePrevious() { return Duration.between(previous.adhan, now); }
    public Duration untilIqama() { return Duration.between(now, iqamaTarget); }
    public boolean iqamaIsForCurrent() { return previous != null && iqamaTarget.equals(previous.iqama); }

    /** الوقت الباقي من الصلاة الحالية، أو null إن لم يكن هناك وقت قائم. */
    public Duration currentRemaining() {
        return current == null ? null : Duration.between(now, current.end);
    }

    /** نسبة مضيّ وقت الصلاة الحالية من 0 إلى 1. */
    public float currentProgress() {
        if (current == null) return 0f;
        double total = Duration.between(current.adhan, current.end).getSeconds();
        double done = Duration.between(current.adhan, now).getSeconds();
        return (float) Math.max(0, Math.min(1, done / total));
    }

    /**
     * درجة الإنذار من 0 (عادي) إلى 1 (أحمر كامل): تبدأ قبل نهاية الوقت بـ warnMinutes
     * وتشتد تدريجيًا حتى خروج الوقت.
     */
    public float urgency() {
        Duration r = currentRemaining();
        if (r == null) return 0f;
        double sec = r.getSeconds();
        double win = warnMinutes * 60.0;
        if (sec >= win) return 0f;
        return (float) Math.max(0, Math.min(1, 1 - sec / win));
    }

    /** يمزج بين لون عادي والأحمر حسب درجة الإنذار. */
    public static int blend(int normal, int red, float t) {
        int a = 0xFF;
        int r = (int) (((normal >> 16) & 0xFF) + (((red >> 16) & 0xFF) - ((normal >> 16) & 0xFF)) * t);
        int g = (int) (((normal >> 8) & 0xFF) + (((red >> 8) & 0xFF) - ((normal >> 8) & 0xFF)) * t);
        int b = (int) ((normal & 0xFF) + ((red & 0xFF) - (normal & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** يحوّل مدة إلى نص مثل 1:05:09 أو 12:40. */
    public static String fmt(Duration d) {
        long s = Math.max(0, d.getSeconds());
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(java.util.Locale.US, "%02d:%02d", m, sec);
    }

    /** أقرب لحظة يتغير فيها شيء مهم (أذان، إقامة، نهاية وقت)، لجدولة تحديث الويدجت. */
    public ZonedDateTime nextBoundary() {
        ZonedDateTime b = next.adhan;
        if (iqamaTarget.isAfter(now) && iqamaTarget.isBefore(b)) b = iqamaTarget;
        if (current != null && current.end.isAfter(now) && current.end.isBefore(b)) b = current.end;
        return b;
    }
}
