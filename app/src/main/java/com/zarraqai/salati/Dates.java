package com.zarraqai.salati;

import android.icu.text.SimpleDateFormat;
import android.icu.util.ULocale;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/** نصوص التاريخ الهجري (أم القرى) والميلادي بأرقام موحّدة. */
public final class Dates {
    private static final ULocale HIJRI = new ULocale("ar@calendar=islamic-umalqura;numbers=latn");
    private static final DateTimeFormatter GREG = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ar"));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE", new Locale("ar"));

    private Dates() {}

    public static String hijri(ZonedDateTime t) {
        return new SimpleDateFormat("d MMMM y", HIJRI).format(Date.from(t.toInstant())) + " هـ";
    }

    public static String hijriMonth(ZonedDateTime t) {
        return new SimpleDateFormat("MMMM y", HIJRI).format(Date.from(t.toInstant())) + " هـ";
    }

    public static String greg(ZonedDateTime t) { return t.format(GREG) + " م"; }

    public static String day(ZonedDateTime t) { return t.format(DAY); }

    /** اليوم والهجري في سطر، والميلادي تحته. */
    public static String twoLines(ZonedDateTime t) {
        return day(t) + " " + hijri(t) + "\n" + greg(t);
    }
}
