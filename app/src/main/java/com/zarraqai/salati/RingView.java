package com.zarraqai.salati;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** حلقة تبيّن نسبة ما مضى من وقت الصلاة الحالية، ويتغير لونها مع اقتراب خروج الوقت. */
public class RingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float progress = 0f;
    private boolean active = true;

    public RingView(Context c, AttributeSet a) {
        super(c, a);
        float w = getResources().getDisplayMetrics().density * 10;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(0x1FFFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(c.getColor(R.color.accent));
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(0xFFFFFFFF);
    }

    public void set(float progress, int color, boolean active) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        this.active = active;
        arc.setColor(color);
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = MeasureSpec.getSize(w);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas c) {
        float sw = track.getStrokeWidth();
        float pad = sw / 2f + sw * 0.6f;
        box.set(pad, pad, getWidth() - pad, getHeight() - pad);
        c.drawArc(box, 0, 360, false, track);
        if (!active) return;
        float sweep = 360f * progress;
        // يبدأ من الأعلى ويسير باتجاه عقارب الساعة
        c.drawArc(box, -90, Math.max(sweep, 0.5f), false, arc);
        double ang = Math.toRadians(-90 + sweep);
        float cx = box.centerX() + (float) (box.width() / 2 * Math.cos(ang));
        float cy = box.centerY() + (float) (box.height() / 2 * Math.sin(ang));
        c.drawCircle(cx, cy, sw * 0.32f, dot);
    }
}
