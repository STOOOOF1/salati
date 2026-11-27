package com.zarraqai.salati;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.zarraqai.salati.core.AlertPlan;

/** يُستدعى عند موعد أي تنبيه: يطلقه حسب قوته، ثم يجدول التنبيه التالي. */
public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        long at = i.getLongExtra(AlertScheduler.EXTRA_AT, 0);
        String key = i.getStringExtra(AlertScheduler.EXTRA_KEY);
        if (i.getBooleanExtra("extra", false)) Prefs.clearExtra(c);
        if (at > 0) Prefs.setLastFired(c, at);

        boolean stale = at > 0 && System.currentTimeMillis() - at > 15 * 60_000L;
        boolean done = key != null && Prefs.isDone(c, key);
        if (!stale && !done && key != null) {
            int strength = i.getIntExtra(AlertScheduler.EXTRA_STRENGTH, AlertPlan.S_NORMAL);
            if (strength == AlertPlan.S_SILENT) {
                AdhanService.postSilent(c, i);
            } else {
                AdhanService.start(c, i);
            }
        }
        AlertScheduler.reschedule(c);
        WidgetUpdater.updateAll(c);
    }
}
