package com.zarraqai.salati;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import com.zarraqai.salati.core.AlertPlan;

/**
 * يشغّل التنبيه حسب قوته:
 * عادي: صوت مرة واحدة، ويتوقف بأي زر (التشغيل أو الصوت) أو بزر «إيقاف».
 * قوي: صوت عالٍ متكرر بشاشة كاملة، ويتوقف بأي زر.
 * ذكي: صوت يبدأ يسيرًا ثم مزعجًا ثم مزعجًا جدًا، ولا يتوقف إلا بحل التحدي في شاشة التنبيه.
 * إن لم يستجب المستخدم يُكرَّر التنبيه بعد دقائق (للمستوى المتوسط والمكثّف، ودائمًا للذكي).
 */
public class AdhanService extends Service {

    static final String CHANNEL = "adhan_alert";
    static final String CHANNEL_SOFT = "prayer_reminder";
    static final String ACTION_STOP = "com.zarraqai.salati.STOP_ADHAN";
    static final String ACTION_ESCALATE = "com.zarraqai.salati.ESCALATE";
    static final int NOTIF_ID = 77;
    private static final long MAX_NORMAL_MS = 6 * 60_000L, MAX_STRONG_MS = 3 * 60_000L, MAX_SMART_MS = 10 * 60_000L;

    /** التنبيه الجاري الآن (لتعرضه شاشة التنبيه)، أو null. */
    static volatile Intent current;

    private MediaPlayer player;
    private PowerManager.WakeLock wake;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Intent alert;
    private int strength;
    private int type;
    private boolean test;
    private boolean userActed;
    private long startedAt;
    private int savedAlarmVol = -1;
    private BroadcastReceiver screenRx;
    private ContentObserver volObserver;
    private int[] vols;

    // ---------- واجهة الاستدعاء ----------

    public static void start(Context c, Intent alertIntent) {
        Intent i = new Intent(c, AdhanService.class).putExtras(alertIntent);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception e) {
            postReminder(c, alertIntent);
        }
    }

    public static void startTest(Context c) {
        Intent i = new Intent(c, AdhanService.class).putExtra("test", true)
                .putExtra(AlertScheduler.EXTRA_NAME, "").putExtra(AlertScheduler.EXTRA_STRENGTH, AlertPlan.S_NORMAL);
        try { c.startForegroundService(i); } catch (Exception ignored) {}
    }

    /** إيقاف بيد المستخدم (زر، أو حل التحدي). */
    static void stopByUser(Context c) {
        try { c.startService(new Intent(c, AdhanService.class).setAction(ACTION_STOP)); } catch (Exception ignored) {}
    }

    /** لم يجب على سؤال «هل أنت مستيقظ؟»: يعود المنبّه بأعلى صوت. */
    static void escalate(Context c) {
        try { c.startService(new Intent(c, AdhanService.class).setAction(ACTION_ESCALATE)); } catch (Exception ignored) {}
    }

    // ---------- الإشعارات ----------

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "تنبيه الصلاة", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("التنبيه بالصوت عند الأذان وما بعده");
            ch.setSound(null, null); // الصوت يُشغَّل من التطبيق نفسه
            ch.enableVibration(true);
            ch.setBypassDnd(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CHANNEL_SOFT) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_SOFT, "تذكير صامت", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("تذكير بالاهتزاز فقط، مثل وقت إقامة الجماعة");
            ch.setSound(null, null);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
    }

    private static PendingIntent openApp(Context c) {
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent alarmScreen(Context c, int mode) {
        Intent i = new Intent(c, ChallengeActivity.class).putExtra(ChallengeActivity.EXTRA_MODE, mode)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        return PendingIntent.getActivity(c, 6 + mode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static String title(Intent a) {
        return AlertPlan.title(a.getIntExtra(AlertScheduler.EXTRA_TYPE, AlertPlan.T_ADHAN), a.getStringExtra(AlertScheduler.EXTRA_NAME));
    }

    /** يضيف زر «صلّيت» إن كان مسموحًا الآن. */
    private static void addDoneAction(Context c, Notification.Builder b, Intent a) {
        String key = a.getStringExtra(AlertScheduler.EXTRA_KEY);
        if (key == null) return;
        int slot = a.getIntExtra(AlertScheduler.EXTRA_SLOT, 0);
        if (slot == 0 && Prefs.level(c, 0) == AlertPlan.INTENSE && !Prefs.woke(c, key)) return;
        b.addAction(new Notification.Action.Builder(null, "صلّيت ✓", DoneReceiver.pending(c, key, a.getStringExtra(AlertScheduler.EXTRA_NAME), true)).build());
    }

    /** إشعار عادي يبقى بعد انتهاء الصوت، فيه زر «صلّيت». */
    static void postReminder(Context c, Intent a) {
        ensureChannels(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification.Builder b = new Notification.Builder(c, CHANNEL_SOFT)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title(a))
                .setContentText("اضغط «صلّيت» بعد أدائها لإيقاف بقية التنبيهات")
                .setContentIntent(openApp(c))
                .setAutoCancel(true);
        addDoneAction(c, b, a);
        try { nm.notify(NOTIF_ID + 1, b.build()); } catch (SecurityException ignored) {}
    }

    /** تنبيه صامت (اهتزاز فقط)، مثل وقت قيام الجماعة. */
    static void postSilent(Context c, Intent a) {
        postReminder(c, a);
    }

    // ---------- الخدمة ----------

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            userActed = true;
            finish();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_ESCALATE.equals(intent.getAction())) {
            if (alert != null) escalateNow();
            return START_NOT_STICKY;
        }
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }

        if (alert != null) stopPlayer(); // تنبيه جديد يحل محل السابق
        alert = new Intent(intent);
        test = intent.getBooleanExtra("test", false);
        strength = intent.getIntExtra(AlertScheduler.EXTRA_STRENGTH, AlertPlan.S_NORMAL);
        type = intent.getIntExtra(AlertScheduler.EXTRA_TYPE, AlertPlan.T_ADHAN);
        userActed = false;
        startedAt = SystemClock.elapsedRealtime();
        current = test ? null : alert;

        try {
            Notification n = buildNotification();
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTIF_ID, n);
        } catch (Exception e) {
            postReminder(this, alert);
            stopSelf();
            return START_NOT_STICKY;
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && wake == null) {
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "salati:alert");
            wake.acquire(MAX_SMART_MS + 60_000L);
        }

        Uri uri = soundUri();
        if (uri == null) {
            vibrate(false);
            handler.postDelayed(this::noResponse, 60_000L);
        } else {
            play(uri);
        }
        if (strength >= AlertPlan.S_STRONG) vibrate(true);
        if (strength < AlertPlan.S_SMART && !test) listenForAnyButton();
        scheduleStages();
        return START_NOT_STICKY;
    }

    private boolean isCheck() { return type == AlertPlan.T_CHECK; }

    private Notification buildNotification() {
        ensureChannels(this);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(test ? "تجربة صوت التنبيه" : title(alert))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false);
        if (strength == AlertPlan.S_SMART) {
            int mode = isCheck() ? ChallengeActivity.MODE_CHECK : ChallengeActivity.MODE_CHALLENGE;
            PendingIntent screen = alarmScreen(this, mode);
            b.setContentText(isCheck() ? "اضغط لتأكيد استيقاظك" : "حلّ التحدي لإيقاف المنبّه")
                    .setContentIntent(screen)
                    .setFullScreenIntent(screen, true);
        } else {
            Intent stop = new Intent(this, AdhanService.class).setAction(ACTION_STOP);
            PendingIntent stopPi = PendingIntent.getService(this, 4, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setContentText(Prefs.placeName(this) + " • أي زر يوقف الصوت")
                    .setContentIntent(strength == AlertPlan.S_STRONG ? alarmScreen(this, ChallengeActivity.MODE_STOP) : openApp(this))
                    .setDeleteIntent(stopPi)
                    .addAction(new Notification.Action.Builder(null, "إيقاف", stopPi).build());
            if (strength == AlertPlan.S_STRONG) b.setFullScreenIntent(alarmScreen(this, ChallengeActivity.MODE_STOP), true);
            if (!test) addDoneAction(this, b, alert);
        }
        return b.build();
    }

    private Uri soundUri() {
        int mode = Prefs.soundMode(this);
        boolean force = strength == AlertPlan.S_SMART; // المنبّه الذكي له صوت دائمًا
        if (mode == Prefs.SOUND_SILENT && !force) return null;
        if (mode == Prefs.SOUND_ADHAN && Prefs.adhanUri(this) != null) return Uri.parse(Prefs.adhanUri(this));
        if (mode != Prefs.SOUND_SILENT && Prefs.toneUri(this) != null) return Uri.parse(Prefs.toneUri(this));
        Uri d = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        return d != null ? d : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    private float startVolume() {
        if (strength == AlertPlan.S_SMART) return isCheck() ? 0.35f : 0.15f;
        if (strength == AlertPlan.S_STRONG) return 0.7f;
        return 1f;
    }

    /** مراحل الصوت: يسير، ثم مزعج، ثم مزعج جدًا، ثم مهلة عدم الاستجابة. */
    private void scheduleStages() {
        if (strength == AlertPlan.S_SMART) {
            if (isCheck()) {
                // إن لم تظهر شاشة السؤال (أو لم يُجب) يعود المنبّه كاملًا
                handler.postDelayed(this::escalateNow, 75_000L);
            } else {
                handler.postDelayed(() -> setVolume(0.55f), 30_000L);
                handler.postDelayed(() -> { setVolume(1f); maxAlarmVolume(); }, 60_000L);
                handler.postDelayed(this::noResponse, MAX_SMART_MS);
            }
        } else if (strength == AlertPlan.S_STRONG) {
            handler.postDelayed(() -> setVolume(1f), 20_000L);
            handler.postDelayed(this::noResponse, MAX_STRONG_MS);
        } else {
            handler.postDelayed(this::noResponse, MAX_NORMAL_MS);
        }
    }

    private void escalateNow() {
        handler.removeCallbacksAndMessages(null);
        type = AlertPlan.T_SNOOZE;
        strength = AlertPlan.S_SMART;
        alert.putExtra(AlertScheduler.EXTRA_TYPE, type).putExtra(AlertScheduler.EXTRA_STRENGTH, strength);
        String key = alert.getStringExtra(AlertScheduler.EXTRA_KEY);
        if (key != null) Prefs.setWoke(this, key, false);
        current = alert;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) try { nm.notify(NOTIF_ID, buildNotification()); } catch (SecurityException ignored) {}
        if (player == null) { Uri u = soundUri(); if (u != null) play(u); }
        setVolume(1f);
        maxAlarmVolume();
        vibrate(true);
        handler.postDelayed(this::noResponse, MAX_SMART_MS);
    }

    private void play(Uri uri) {
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(strength >= AlertPlan.S_STRONG && !test);
            player.setOnCompletionListener(mp -> { if (!mp.isLooping()) noResponse(); });
            player.setOnErrorListener((mp, what, extra) -> { fallback(); return true; });
            player.setOnPreparedListener(mp -> { setVolume(startVolume()); mp.start(); });
            player.prepareAsync();
        } catch (Exception e) {
            fallback();
        }
    }

    private boolean triedFallback = false;
    private void fallback() {
        if (triedFallback) { vibrate(true); return; }
        triedFallback = true;
        Uri d = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (d == null) { vibrate(true); return; }
        play(d);
    }

    private void setVolume(float v) {
        try { if (player != null) player.setVolume(v, v); } catch (Exception ignored) {}
    }

    /** يرفع صوت المنبّهات إلى أقصاه مؤقتًا، ويعيده بعد الإيقاف. */
    private void maxAlarmVolume() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        try {
            if (savedAlarmVol < 0) savedAlarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            snapshotVolumes();
        } catch (Exception ignored) {}
    }

    private void vibrate(boolean repeat) {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;
        long[] pattern = {0, 600, 400, 600, 1200};
        try { v.vibrate(VibrationEffect.createWaveform(pattern, repeat ? 0 : -1)); } catch (Exception ignored) {}
    }

    // ---------- «أي زر يوقف التنبيه» ----------

    private void listenForAnyButton() {
        // زر التشغيل: يطفئ الشاشة أو يشعلها
        screenRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (SystemClock.elapsedRealtime() - startedAt < 3000) return; // تجاهل إشعال الشاشة بالتنبيه نفسه
                if (strength == AlertPlan.S_STRONG && Intent.ACTION_SCREEN_ON.equals(i.getAction())) return;
                userActed = true;
                finish();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenRx, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenRx, f);

        // أزرار الصوت: أي تغيير في مستوى الصوت يعني أن المستخدم ضغطها
        snapshotVolumes();
        volObserver = new ContentObserver(handler) {
            @Override public void onChange(boolean self) {
                if (vols != null && !java.util.Arrays.equals(vols, readVolumes())) {
                    userActed = true;
                    finish();
                }
            }
        };
        try { getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, volObserver); }
        catch (Exception ignored) {}
    }

    private int[] readVolumes() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return new int[0];
        return new int[]{am.getStreamVolume(AudioManager.STREAM_ALARM), am.getStreamVolume(AudioManager.STREAM_MUSIC),
                am.getStreamVolume(AudioManager.STREAM_RING)};
    }

    private void snapshotVolumes() { vols = readVolumes(); }

    // ---------- النهاية ----------

    /** انتهى الوقت بلا استجابة: نكرر التنبيه بعد دقائق إن لزم. */
    private void noResponse() {
        if (test) { finish(); return; }
        Intent a = alert;
        boolean smart = strength == AlertPlan.S_SMART;
        boolean repeat = smart || a.getBooleanExtra(AlertScheduler.EXTRA_REPEAT, false);
        finish();
        if (repeat) AlertScheduler.snooze(this, a, smart ? 3 : 5);
    }

    private void finish() {
        handler.removeCallbacksAndMessages(null);
        stopPlayer();
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) v.cancel();
        if (savedAlarmVol >= 0) {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            try { if (am != null) am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVol, 0); } catch (Exception ignored) {}
            savedAlarmVol = -1;
        }
        unlisten();
        Intent a = alert;
        boolean showReminder = a != null && !test && type != AlertPlan.T_CHECK;
        current = null;
        alert = null;
        if (wake != null && wake.isHeld()) wake.release();
        wake = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        if (showReminder && a.getStringExtra(AlertScheduler.EXTRA_KEY) != null
                && !Prefs.isDone(this, a.getStringExtra(AlertScheduler.EXTRA_KEY))) {
            postReminder(this, a);
        }
        ChallengeActivity.closeIfOpen();
    }

    private void unlisten() {
        if (screenRx != null) { try { unregisterReceiver(screenRx); } catch (Exception ignored) {} screenRx = null; }
        if (volObserver != null) { try { getContentResolver().unregisterContentObserver(volObserver); } catch (Exception ignored) {} volObserver = null; }
    }

    private void stopPlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopPlayer();
        unlisten();
        if (wake != null && wake.isHeld()) wake.release();
        current = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
