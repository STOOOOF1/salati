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

    /** المفتى به في السعودية (فتاوى ابن باز واللجنة الدائمة). */
    public static final String RULE = "على ما عليه الفتوى في السعودية";

    /** صلاة واحدة بوقت أذانها وإقامتها ونهاية وقتها. */
    public static final class Prayer {
        public final int index;          // رقم الصلاة في PrayerCalc
        public final String name;
        public final ZonedDateTime adhan;
        public final ZonedDateTime iqama;
        public ZonedDateTime end;        // نهاية الوقت كله (مع الضرورة)
        public ZonedDateTime choiceEnd;  // نهاية وقت الاختيار (= end إن لم يكن له ضرورة)

        Prayer(int index, String name, ZonedDateTime adhan, ZonedDateTime iqama) {
            this.index = index; this.name = name; this.adhan = adhan; this.iqama = iqama;
        }
    }

    public final ZonedDateTime now;
    public final List<Prayer> all;     // صلوات أمس واليوم والغد وبعد الغد
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
        List<ZonedDateTime[]> days = new ArrayList<>();
        ZonedDateTime[] todayTimes = null;
        for (int off = -1; off <= 2; off++) {
            LocalDate day = d.plusDays(off);
            ZonedDateTime[] t = PrayerCalc.compute(day, lat, lng, zone, isRamadan != null && isRamadan.test(day), 1);
            days.add(t);
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
        this.all = list;

        // نهاية الأوقات على المفتى به (ابن باز واللجنة الدائمة):
        // الفجر إلى طلوع الشمس، والظهر إلى العصر، والمغرب إلى العشاء، ولا ضرورة فيها.
        // العصر: الاختيار إلى اصفرار الشمس، والضرورة إلى غروبها.
        // العشاء: الاختيار إلى نصف الليل (من الغروب إلى الفجر)، والضرورة إلى طلوع الفجر.
        for (int j = 0; j < list.size(); j++) {
            Prayer p = list.get(j);
            int day = j / 5;
            ZonedDateTime[] t = days.get(day);
            switch (p.index) {
                case PrayerCalc.FAJR:
                    p.end = sunrises.get(day);
                    break;
                case PrayerCalc.ASR:
                    p.end = list.get(j + 1).adhan;
                    p.choiceEnd = t[PrayerCalc.ISFIRAR];
                    break;
                case PrayerCalc.ISHA: {
                    ZonedDateTime maghrib = list.get(j - 1).adhan;
                    ZonedDateTime nextFajr = j + 1 < list.size() ? list.get(j + 1).adhan : p.adhan.plusHours(9);
                    long night = Duration.between(maghrib, nextFajr).getSeconds();
                    p.end = nextFajr;
                    p.choiceEnd = maghrib.plusSeconds(night / 2);
                    break;
                }
                default:
                    p.end = list.get(j + 1).adhan;
            }
            if (p.choiceEnd == null || !p.choiceEnd.isBefore(p.end) || !p.choiceEnd.isAfter(p.adhan)) p.choiceEnd = p.end;
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

    /** هل لهذه الصلاة وقت ضرورة (العصر والعشاء)؟ */
    public static boolean hasDarura(Prayer p) { return p.choiceEnd.isBefore(p.end); }

    /** مفتاح ثابت للصلاة: تاريخ أذانها ورقمها من 0 إلى 4. */
    public static String key(Prayer p) { return p.adhan.toLocalDate() + "_" + slot(p.index); }

    /** رقم الصلاة من 0 (الفجر) إلى 4 (العشاء)، أو -1 للشروق. */
    public static int slot(int prayerIndex) {
        switch (prayerIndex) {
            case PrayerCalc.FAJR: return 0;
            case PrayerCalc.DHUHR: return 1;
            case PrayerCalc.ASR: return 2;
            case PrayerCalc.MAGHRIB: return 3;
            case PrayerCalc.ISHA: return 4;
            default: return -1;
        }
    }

    /** منتصف الليل الذي ينتهي به اختيار العشاء الحالية أو القادمة. */
    public ZonedDateTime midnight() {
        for (Prayer p : all) if (p.index == PrayerCalc.ISHA && p.end.isAfter(now)) return p.choiceEnd;
        return null;
    }

    public Duration untilNext() { return Duration.between(now, next.adhan); }
    public Duration sincePrevious() { return Duration.between(previous.adhan, now); }
    public Duration untilIqama() { return Duration.between(now, iqamaTarget); }
    public boolean iqamaIsForCurrent() { return previous != null && iqamaTarget.equals(previous.iqama); }

    /** عدّ الإقامة يبدأ عند الأذان وينتهي عند الإقامة. */
    public boolean iqamaActive() {
        return previous != null && !now.isBefore(previous.adhan) && now.isBefore(previous.iqama);
    }

    /** دقائق الإقامة بعد أذان الصلاة القادمة (تُعرض قبل الأذان بدل العدّ). */
    public long iqamaMinutesNext() {
        return Duration.between(next.adhan, next.iqama).toMinutes();
    }

    /** هل نحن في وقت الضرورة للصلاة الحالية؟ */
    public boolean inDarura() {
        return current != null && !now.isBefore(current.choiceEnd) && current.choiceEnd.isBefore(current.end);
    }

    /** اللحظة التي يُعدّ إليها: نهاية الاختيار، ثم نهاية الضرورة. */
    public ZonedDateTime currentTarget() {
        if (current == null) return null;
        return inDarura() ? current.end : current.choiceEnd;
    }

    /** الوقت الباقي من الصلاة الحالية، أو null إن لم يكن هناك وقت قائم. */
    public Duration currentRemaining() {
        return current == null ? null : Duration.between(now, currentTarget());
    }

    /** نسبة مضيّ وقت الاختيار من 0 إلى 1 (1 في وقت الضرورة). */
    public float currentProgress() {
        if (current == null) return 0f;
        if (inDarura()) return 1f;
        double total = Duration.between(current.adhan, current.choiceEnd).getSeconds();
        double done = Duration.between(current.adhan, now).getSeconds();
        return (float) Math.max(0, Math.min(1, done / total));
    }

    /**
     * درجة الإنذار من 0 (عادي) إلى 1 (أحمر كامل): تبدأ قبل نهاية وقت الاختيار بـ warnMinutes،
     * وتبقى حمراء طوال وقت الضرورة.
     */
    public float urgency() {
        if (current == null) return 0f;
        if (inDarura()) return 1f;
        double sec = Duration.between(now, current.choiceEnd).getSeconds();
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

    /** يحوّل مدة إلى نص مثل 1:05:09 أو 12:40 (بلا أصفار زائدة في الساعات). */
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
        if (current != null) {
            ZonedDateTime t = currentTarget();
            if (t.isAfter(now) && t.isBefore(b)) b = t;
        }
        return b;
    }
}
