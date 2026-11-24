package com.zarraqai.salati;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** خلفية متوهجة بالأخضر والأزرق والأحمر مع زخارف هندسية خفيفة في الأركان. */
public class GlowBackground extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    public GlowBackground(Context c, AttributeSet a) {
        super(c, a);
        orn.setStyle(Paint.Style.STROKE);
        orn.setStrokeWidth(getResources().getDisplayMetrics().density * 1.1f);
        orn.setColor(0x1CFFFFFF);
    }

    private void glow(Canvas c, float x, float y, float r, int color) {
        fill.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
    }

    /** وردة هندسية من أقواس متداخلة. */
    private void rosette(Canvas c, float cx, float cy, float r) {
        for (int k = 0; k < 12; k++) {
            c.save();
            c.rotate(k * 30f, cx, cy);
            oval.set(cx - r * 0.22f, cy - r, cx + r * 0.22f, cy);
            c.drawOval(oval, orn);
            c.restore();
        }
        c.drawCircle(cx, cy, r * 0.32f, orn);
        c.drawCircle(cx, cy, r * 1.05f, orn);
        c.drawCircle(cx, cy, r * 1.25f, orn);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        c.drawColor(0xFF0A1220);
        fill.setShader(null);
        glow(c, w * 0.1f, h * 0.05f, w * 0.95f, 0xE01C7A45);
        glow(c, w * 1.05f, h * 0.42f, w * 0.8f, 0xB01A4FA8);
        glow(c, -w * 0.05f, h * 0.82f, w * 0.9f, 0xC0B8202A);
        glow(c, w * 0.35f, h * 1.02f, w * 0.65f, 0xA0E0701A);
        float d = getResources().getDisplayMetrics().density;
        rosette(c, w * 0.02f, h * 0.06f, 70 * d);
        rosette(c, w * 0.98f, h * 0.2f, 46 * d);
        rosette(c, w * 0.0f, h * 0.83f, 58 * d);
    }
}
