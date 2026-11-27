package com.zarraqai.salati;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** زر «صلّيت» (وزر «تراجع») من الإشعارات: يسجّل الصلاة ويوقف بقية تنبيهاتها. */
public class DoneReceiver extends BroadcastReceiver {

    static final String ACTION = "com.zarraqai.salati.DONE";
    static final String ACTION_STOP = "com.zarraqai.salati.STOP_SOUND";
    static final int UNDO_ID = 79;

    static PendingIntent pending(Context c, String key, String name, boolean done) {
        Intent i = new Intent(c, DoneReceiver.class).setAction(ACTION)
                .putExtra("key", key).putExtra("name", name).putExtra("done", done);
        return PendingIntent.getBroadcast(c, (key + done).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION_STOP.equals(i.getAction())) { AdhanService.stopByUser(c); return; }
        String key = i.getStringExtra("key");
        String name = i.getStringExtra("name");
        boolean done = i.getBooleanExtra("done", true);
        if (key == null) return;
        mark(c, key, done);

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.cancel(AdhanService.NOTIF_ID + 1);
        nm.cancel(UNDO_ID);
        if (done) {
            AdhanService.ensureChannels(c);
            Notification n = new Notification.Builder(c, AdhanService.CHANNEL_SOFT)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("سُجّلت صلاة " + (name == null ? "" : name) + " • تقبّل الله")
                    .setContentText("ضغطتها بالخطأ؟ اضغط «تراجع»")
                    .setTimeoutAfter(15_000L)
                    .setAutoCancel(true)
                    .addAction(new Notification.Action.Builder(null, "تراجع", pending(c, key, name, false)).build())
                    .build();
            try { nm.notify(UNDO_ID, n); } catch (SecurityException ignored) {}
        }
    }

    /** يسجّل الصلاة (أو يلغي تسجيلها) ويعيد جدولة التنبيهات. */
    static void mark(Context c, String key, boolean done) {
        Prefs.setDone(c, key, done);
        if (done) {
            Intent cur = AdhanService.current;
            if (cur != null && key.equals(cur.getStringExtra(AlertScheduler.EXTRA_KEY))) AdhanService.stopByUser(c);
            if (key.equals(Prefs.extraKey(c))) Prefs.clearExtra(c);
        }
        AlertScheduler.reschedule(c);
        WidgetUpdater.updateAll(c);
    }
}
