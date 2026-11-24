package com.zarraqai.salati.core;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * حساب مواقيت الصلاة بطريقة أم القرى:
 * الفجر 18.5° تحت الأفق، العشاء بعد المغرب بـ 90 دقيقة (120 في رمضان)،
 * العصر بالمذهب الجمهوري (ظل المثل).
 * لا يعتمد على أندرويد حتى يمكن اختباره مباشرة.
 */
public final class PrayerCalc {

    public static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;

    private PrayerCalc() {}

    /** يعيد ستة أوقات: الفجر، الشروق، الظهر، العصر، المغرب، العشاء. */
    public static ZonedDateTime[] compute(LocalDate date, double lat, double lng, ZoneId zone, boolean ramadan) {
        ZonedDateTime noonLocal = date.atTime(12, 0).atZone(zone);
        double tz = noonLocal.getOffset().getTotalSeconds() / 3600.0;
        double jd = julian(date.getYear(), date.getMonthValue(), date.getDayOfMonth()) - lng / (15.0 * 24.0);

        // تقديرات أولية بالساعات ثم تكرار لتحسين الدقة
        double fajr = 5, sunrise = 6, dhuhr = 12, asr = 13, sunset = 18;
        for (int i = 0; i < 2; i++) {
            fajr = sunAngleTime(jd, 18.5, fajr / 24.0, lat, true);
            sunrise = sunAngleTime(jd, 0.833, sunrise / 24.0, lat, true);
            dhuhr = midDay(jd, dhuhr / 24.0);
            asr = asrTime(jd, 1, asr / 24.0, lat);
            sunset = sunAngleTime(jd, 0.833, sunset / 24.0, lat, false);
        }
        double adj = tz - lng / 15.0;
        fajr += adj; sunrise += adj; dhuhr += adj; asr += adj; sunset += adj;
        double isha = sunset + (ramadan ? 120 : 90) / 60.0;

        double[] hours = {fajr, sunrise, dhuhr, asr, sunset, isha};
        ZonedDateTime[] out = new ZonedDateTime[6];
        ZonedDateTime midnight = date.atStartOfDay(zone);
        for (int i = 0; i < 6; i++) {
            long minutes = Math.round(hours[i] * 60.0);
            out[i] = midnight.plusMinutes(minutes);
        }
        return out;
    }

    // ---------- رياضيات موقع الشمس ----------

    private static double julian(int year, int month, int day) {
        if (month <= 2) { year -= 1; month += 12; }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** يعيد {الميل، معادلة الزمن} */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0;
        double eqt = q / 15.0 - fixHour(ra);
        double decl = darcsin(dsin(e) * dsin(l));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jd, double t) {
        double eqt = sunPosition(jd + t)[1];
        return fixHour(12 - eqt);
    }

    private static double sunAngleTime(double jd, double angle, double t, double lat, boolean ccw) {
        double decl = sunPosition(jd + t)[0];
        double noon = midDay(jd, t);
        double cos = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat));
        cos = Math.max(-1, Math.min(1, cos));
        double ht = darccos(cos) / 15.0;
        return noon + (ccw ? -ht : ht);
    }

    private static double asrTime(double jd, double factor, double t, double lat) {
        double decl = sunPosition(jd + t)[0];
        double angle = -darccot(factor + dtan(Math.abs(lat - decl)));
        return sunAngleTime(jd, angle, t, lat, false);
    }

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double darccot(double x) { return Math.toDegrees(Math.atan(1 / x)); }
    private static double fixAngle(double a) { a = a - 360.0 * Math.floor(a / 360.0); return a < 0 ? a + 360 : a; }
    private static double fixHour(double a) { a = a - 24.0 * Math.floor(a / 24.0); return a < 0 ? a + 24 : a; }
}
