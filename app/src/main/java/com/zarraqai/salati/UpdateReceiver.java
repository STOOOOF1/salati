package com.zarraqai.salati;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** يحدّث الويدجتات عند المنبّه المجدول، وبعد إعادة التشغيل، وعند تغيّر الوقت أو المنطقة. */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        WidgetUpdater.updateAll(c);
        AlertScheduler.reschedule(c);
    }
}
