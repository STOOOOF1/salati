package com.zarraqai.salati;

import android.content.Context;
import android.content.SharedPreferences;
import android.icu.util.IslamicCalendar;

import com.zarraqai.salati.core.Schedule;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

/** الإعدادات المحفوظة: الموقع ودقائق الإقامة ومدة الإنذار. */
public final class Prefs {

    public static final String[] CITY_NAMES = {
            "المدينة المنورة", "مكة المكرمة", "الرياض", "جدة", "الدمام",
            "الطائف", "تبوك", "بريدة", "حائل", "أبها", "جازان", "نجران"
    };
    public static final double[][] CITY_COORDS = {
            {24.4672, 39.6111}, {21.4225, 39.8262}, {24.7136, 46.6753}, {21.4858, 39.1925},
            {26.4207, 50.0888}, {21.2703, 40.4158}, {28.3835, 36.5662}, {26.3260, 43.9750},
            {27.5114, 41.7208}, {18.2164, 42.5053}, {16.8892, 42.5511}, {17.5650, 44.2289}
    };
    public static final int[] DEFAULT_IQAMA = {25, 20, 20, 10, 20};
    public static final String[] IQAMA_KEYS = {"iq_fajr", "iq_dhuhr", "iq_asr", "iq_maghrib", "iq_isha"};

    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("salati", Context.MODE_PRIVATE);
    }

    public static double lat(Context c) { return Double.longBitsToDouble(sp(c).getLong("lat", Double.doubleToLongBits(CITY_COORDS[0][0]))); }
    public static double lng(Context c) { return Double.longBitsToDouble(sp(c).getLong("lng", Double.doubleToLongBits(CITY_COORDS[0][1]))); }
    public static String placeName(Context c) { return sp(c).getString("place", CITY_NAMES[0]); }
    public static int cityIndex(Context c) { return sp(c).getInt("city", 0); }

    public static void setPlace(Context c, String name, double lat, double lng, int cityIndex) {
        sp(c).edit()
                .putString("place", name)
                .putLong("lat", Double.doubleToLongBits(lat))
                .putLong("lng", Double.doubleToLongBits(lng))
                .putInt("city", cityIndex)
                .apply();
    }

    public static int[] iqama(Context c) {
        int[] r = new int[5];
        for (int i = 0; i < 5; i++) r[i] = sp(c).getInt(IQAMA_KEYS[i], DEFAULT_IQAMA[i]);
        return r;
    }

    // ---------- التنبيه ----------
    public static final String[] ALERT_KEYS = {"al_fajr", "al_dhuhr", "al_asr", "al_maghrib", "al_isha"};
    public static final int SOUND_SILENT = 0, SOUND_TONE = 1, SOUND_ADHAN = 2;

    public static boolean[] alerts(Context c) {
        boolean[] r = new boolean[5];
        for (int i = 0; i < 5; i++) r[i] = sp(c).getBoolean(ALERT_KEYS[i], false);
        return r;
    }

    public static void setAlerts(Context c, boolean[] v) {
        SharedPreferences.Editor e = sp(c).edit();
        for (int i = 0; i < 5; i++) e.putBoolean(ALERT_KEYS[i], v[i]);
        e.apply();
    }

    public static int soundMode(Context c) { return sp(c).getInt("sound_mode", SOUND_TONE); }
    public static void setSoundMode(Context c, int m) { sp(c).edit().putInt("sound_mode", m).apply(); }
    public static String toneUri(Context c) { return sp(c).getString("tone_uri", null); }
    public static String toneName(Context c) { return sp(c).getString("tone_name", "نغمة المنبّه الافتراضية"); }
    public static void setTone(Context c, String uri, String name) { sp(c).edit().putString("tone_uri", uri).putString("tone_name", name).apply(); }
    public static String adhanUri(Context c) { return sp(c).getString("adhan_uri", null); }
    public static String adhanName(Context c) { return sp(c).getString("adhan_name", "لم يُختر ملف"); }
    public static void setAdhan(Context c, String uri, String name) { sp(c).edit().putString("adhan_uri", uri).putString("adhan_name", name).apply(); }

    // ---------- التحديث ----------
    public static long lastUpdateCheck(Context c) { return sp(c).getLong("upd_check", 0); }
    public static void setLastUpdateCheck(Context c, long t) { sp(c).edit().putLong("upd_check", t).apply(); }

    public static int warnMinutes(Context c) { return sp(c).getInt("warn", 30); }

    public static void save(Context c, int[] iqama, int warn) {
        SharedPreferences.Editor e = sp(c).edit();
        for (int i = 0; i < 5; i++) e.putInt(IQAMA_KEYS[i], iqama[i]);
        e.putInt("warn", warn).apply();
    }

    /** رمضان حسب تقويم أم القرى. */
    public static boolean isRamadan(LocalDate d) {
        try {
            IslamicCalendar cal = new IslamicCalendar();
            cal.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
            cal.setTime(Date.from(d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()));
            return cal.get(IslamicCalendar.MONTH) == IslamicCalendar.RAMADAN;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Schedule schedule(Context c) {
        return Schedule.at(ZonedDateTime.now(), lat(c), lng(c), iqama(c), warnMinutes(c), Prefs::isRamadan);
    }
}
