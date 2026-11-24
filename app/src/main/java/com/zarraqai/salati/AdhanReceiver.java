package com.zarraqai.salati;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** يُستدعى عند وقت الأذان: يشغّل التنبيه ثم يجدول الأذان التالي. */
public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String name = i.getStringExtra(AlertScheduler.EXTRA_NAME);
        long at = i.getLongExtra(AlertScheduler.EXTRA_AT, 0);
        // نتجاهل التنبيه المتأخر كثيرًا (مثل بعد إعادة تشغيل طويلة)
        if (name != null && (at == 0 || System.currentTimeMillis() - at < 15 * 60_000L)) {
            AdhanService.start(c, name, false);
        }
        AlertScheduler.reschedule(c);
        WidgetUpdater.updateAll(c);
    }
}
