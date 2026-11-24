package com.zarraqai.salati;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** أساس مشترك للويدجتات الثلاث. */
public abstract class BaseWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        WidgetUpdater.updateAll(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        WidgetUpdater.updateAll(c);
    }

    @Override
    public void onEnabled(Context c) {
        WidgetUpdater.updateAll(c);
    }

    @Override
    public void onDisabled(Context c) {
        WidgetUpdater.updateAll(c);
    }
}
