package com.zarraqai.salati;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/** يمنع تداخل المحتوى مع شريط الحالة وشريط التنقل (أندرويد 15 يرسم التطبيق تحتهما). */
final class Insets {
    private Insets() {}

    interface Apply { void apply(int top, int bottom); }

    static void on(View v, Apply a) {
        v.setOnApplyWindowInsetsListener((view, ins) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets b = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = b.top; bottom = b.bottom;
            } else {
                top = ins.getSystemWindowInsetTop(); bottom = ins.getSystemWindowInsetBottom();
            }
            a.apply(top, bottom);
            return ins;
        });
        v.requestApplyInsets();
    }
}
