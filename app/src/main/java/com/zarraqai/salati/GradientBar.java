package com.zarraqai.salati;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** شريط تقدّم يمتلئ من اليمين، بتدرّج من الأخضر إلى الأحمر. */
public class GradientBar extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float progress;
    private boolean warm = true;

    public GradientBar(Context c, AttributeSet a) {
        super(c, a);
        track.setColor(0x2EFFFFFF);
    }

    public void set(float progress, boolean warm) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        this.warm = warm;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), rad = h / 2f;
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, track);
        if (progress <= 0) return;
        int[] cols = warm ? new int[]{0xFF22C55E, 0xFF2F8FE8, 0xFFC2457A, 0xFFFF3B30}
                          : new int[]{0xFF22C55E, 0xFF14B8A6, 0xFF2F8FE8, 0xFF6366F1};
        fill.setShader(new LinearGradient(w, 0, 0, 0, cols, null, Shader.TileMode.CLAMP));
        r.set(w - Math.max(h, w * progress), 0, w, h);
        c.drawRoundRect(r, rad, rad, fill);
    }
}
