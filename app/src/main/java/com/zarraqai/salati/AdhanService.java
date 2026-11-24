package com.zarraqai.salati;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/** يعرض إشعار الأذان ويشغّل النغمة أو الأذان المختار، مع زر إيقاف. */
public class AdhanService extends Service {

    static final String CHANNEL = "adhan_alert";
    static final String ACTION_STOP = "com.zarraqai.salati.STOP_ADHAN";
    static final int NOTIF_ID = 77;
    private static final long MAX_PLAY_MS = 6 * 60_000L;

    private MediaPlayer player;
    private PowerManager.WakeLock wake;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String name = "";
    private boolean test = false;

    /** @param test تجربة الصوت من الإعدادات */
    public static void start(Context c, String prayerName, boolean test) {
        Intent i = new Intent(c, AdhanService.class)
                .putExtra("name", prayerName).putExtra("test", test);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception e) {
            // إن منع النظام تشغيل الخدمة نكتفي بإشعار عادي
            postPlain(c, prayerName);
        }
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "تنبيه الأذان", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("إشعار عند دخول وقت الصلاة");
        ch.setSound(null, null); // الصوت يُشغَّل من التطبيق نفسه حسب الاختيار
        ch.enableVibration(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private static PendingIntent openApp(Context c) {
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static Notification.Builder base(Context c, String prayerName) {
        ensureChannel(c);
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("حان الآن موعد أذان " + prayerName)
                .setContentText(Prefs.placeName(c))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(openApp(c))
                .setAutoCancel(true);
    }

    static void postPlain(Context c, String prayerName) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        try { nm.notify(NOTIF_ID + 1, base(c, prayerName).build()); } catch (SecurityException ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            finish(true);
            return START_NOT_STICKY;
        }
        name = intent != null && intent.getStringExtra("name") != null ? intent.getStringExtra("name") : "";
        test = intent != null && intent.getBooleanExtra("test", false);

        Intent stop = new Intent(this, AdhanService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 4, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = base(this, name)
                .setOngoing(true)
                .setAutoCancel(false)
                .setDeleteIntent(stopPi)
                .addAction(new Notification.Action.Builder(null, "إيقاف", stopPi).build());
        if (test) b.setContentTitle("تجربة صوت التنبيه");
        Notification n = b.build();

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            postPlain(this, name);
            stopSelf();
            return START_NOT_STICKY;
        }

        Uri uri = soundUri();
        if (uri == null) {
            // بدون صوت: نُبقي إشعارًا عاديًا فقط
            done();
            return START_NOT_STICKY;
        }
        play(uri);
        return START_NOT_STICKY;
    }

    private Uri soundUri() {
        int mode = Prefs.soundMode(this);
        if (mode == Prefs.SOUND_SILENT) return null;
        if (mode == Prefs.SOUND_ADHAN && Prefs.adhanUri(this) != null) return Uri.parse(Prefs.adhanUri(this));
        if (Prefs.toneUri(this) != null) return Uri.parse(Prefs.toneUri(this));
        Uri d = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        return d != null ? d : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    private void play(Uri uri) {
        stopPlayer();
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "salati:adhan");
                wake.acquire(MAX_PLAY_MS + 10_000L);
            }
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, uri);
            player.setOnCompletionListener(mp -> done());
            player.setOnErrorListener((mp, what, extra) -> { fallback(); return true; });
            player.setOnPreparedListener(MediaPlayer::start);
            player.prepareAsync();
            handler.postDelayed(this::done, MAX_PLAY_MS);
        } catch (Exception e) {
            fallback();
        }
    }

    /** إن تعذّر تشغيل الملف المختار نجرّب نغمة المنبّه الافتراضية مرة واحدة. */
    private boolean triedFallback = false;
    private void fallback() {
        if (triedFallback) { done(); return; }
        triedFallback = true;
        Uri d = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (d == null) { done(); return; }
        play(d);
    }

    /** انتهى الصوت: نزيل إشعار التشغيل ونترك إشعارًا عاديًا بدخول الوقت. */
    private void done() {
        finish(true);
        if (!test) postPlain(this, name);
    }

    private void stopPlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    private void finish(boolean removeNotification) {
        handler.removeCallbacksAndMessages(null);
        stopPlayer();
        if (wake != null && wake.isHeld()) wake.release();
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(removeNotification ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(removeNotification);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopPlayer();
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
