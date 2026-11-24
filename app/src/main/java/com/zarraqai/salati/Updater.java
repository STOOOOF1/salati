package com.zarraqai.salati;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** يتحقق من آخر إصدار على GitHub وينزّله ويثبّته من داخل التطبيق. */
public final class Updater {

    static final String BASE = "https://github.com/STOOOOF1/salati/releases/download/latest/";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Updater() {}

    public static long currentCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) { return 0; }
    }

    public static String currentName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    /** @param quiet تحقق تلقائي: لا رسائل إلا إذا وُجد تحديث */
    public static void check(Activity a, boolean quiet) {
        if (!quiet) toast(a, "جارٍ البحث عن تحديث…");
        new Thread(() -> {
            try {
                String json = new String(get(BASE + "version.json?t=" + System.currentTimeMillis()), "UTF-8");
                JSONObject o = new JSONObject(json);
                long code = o.getLong("versionCode");
                String name = o.optString("versionName", String.valueOf(code));
                String notes = o.optString("notes", "");
                Prefs.setLastUpdateCheck(a, System.currentTimeMillis());
                MAIN.post(() -> {
                    if (a.isFinishing()) return;
                    if (code > currentCode(a)) offer(a, name, notes);
                    else if (!quiet) toast(a, "لديك آخر إصدار (" + currentName(a) + ")");
                });
            } catch (Exception e) {
                if (!quiet) MAIN.post(() -> toast(a, "تعذّر الاتصال، تأكد من الإنترنت"));
            }
        }).start();
    }

    private static void offer(Activity a, String name, String notes) {
        String msg = "يتوفر الإصدار " + name + " (لديك " + currentName(a) + ")."
                + (notes.isEmpty() ? "" : "\n\n" + notes) + "\n\nهل تريد تنزيله وتثبيته الآن؟";
        new AlertDialog.Builder(a)
                .setTitle("تحديث جديد")
                .setMessage(msg)
                .setPositiveButton("تحديث", (d, w) -> download(a))
                .setNegativeButton("لاحقًا", null)
                .show();
    }

    private static void download(Activity a) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle("إذن التثبيت")
                    .setMessage("اسمح لتطبيق صلاتي بتثبيت التحديثات، ثم ارجع واضغط «تحديث» مرة أخرى.")
                    .setPositiveButton("فتح الإعدادات", (d, w) -> a.startActivity(
                            new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName()))))
                    .setNegativeButton("إلغاء", null)
                    .show();
            return;
        }
        AlertDialog progress = new AlertDialog.Builder(a)
                .setTitle("تنزيل التحديث")
                .setMessage("جارٍ التنزيل…")
                .setCancelable(false)
                .show();
        new Thread(() -> {
            try {
                byte[] apk = get(BASE + "salati.apk?t=" + System.currentTimeMillis());
                PackageInstaller pi = a.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(a.getPackageName());
                int id = pi.createSession(params);
                try (PackageInstaller.Session session = pi.openSession(id)) {
                    try (OutputStream out = session.openWrite("salati.apk", 0, apk.length)) {
                        out.write(apk);
                        session.fsync(out);
                    }
                    Intent cb = new Intent(a, InstallReceiver.class);
                    int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent pending = PendingIntent.getBroadcast(a, 5, cb, fl);
                    session.commit(pending.getIntentSender());
                }
                MAIN.post(progress::dismiss);
            } catch (Exception e) {
                MAIN.post(() -> {
                    progress.dismiss();
                    toast(a, "فشل التنزيل: " + e.getMessage());
                });
            }
        }).start();
    }

    private static byte[] get(String url) throws Exception {
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "salati-app");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new Exception("redirect");
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            if (code != 200) { c.disconnect(); throw new Exception("HTTP " + code); }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                return bo.toByteArray();
            } finally {
                c.disconnect();
            }
        }
        throw new Exception("too many redirects");
    }

    private static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_LONG).show(); }
}
