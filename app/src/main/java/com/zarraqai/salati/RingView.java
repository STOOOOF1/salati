package com.zarraqai.salati;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

/** حلقة متوهجة بتدرّج من الأخضر إلى الأحمر تبيّن ما مضى من وقت الصلاة. */
public class RingView extends View {

    static final int[] WARM = {0xFF22C55E, 0xFF22C55E, 0xFF2F8FE8, 0xFFC2457A, 0xFFFF8A1F, 0xFFFF3B30, 0xFFFF3B30};
    static final float[] WARM_POS = {0f, 0.04f, 0.28f, 0.45f, 0.58f, 0.72f, 1f};
    static final int[] COOL = {0xFF22C55E, 0xFF22C55E, 0xFF14B8A6, 0xFF2F8FE8, 0xFF6366F1, 0xFF6366F1};
    static final float[] COOL_POS = {0f, 0.04f, 0.35f, 0.65f, 0.95f, 1f};

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float progress = 0f;
    private boolean warm = true;

    public RingView(Context c, AttributeSet a) {
        super(c, a);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // لازم للتوهج
        float d = getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(9 * d);
        track.setColor(0x2EFFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(13 * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(20 * d);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setAlpha(150);
        glow.setMaskFilter(new BlurMaskFilter(14 * d, BlurMaskFilter.Blur.NORMAL));
    }

    /** @param warm تدرّج دافئ (وقت صلاة قائم) أو بارد (انتظار الأذان) */
    public void set(float progress, boolean warm) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        this.warm = warm;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = MeasureSpec.getSize(w);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float pad = 24 * d;
        box.set(pad, pad, getWidth() - pad, getHeight() - pad);
        c.drawArc(box, 0, 360, false, track);
        SweepGradient g = new SweepGradient(box.centerX(), box.centerY(),
                warm ? WARM : COOL, warm ? WARM_POS : COOL_POS);
        Matrix m = new Matrix();
        m.setRotate(-96, box.centerX(), box.centerY()); // يبدأ من الأعلى، مع هامش لطرف الخط
        g.setLocalMatrix(m);
        arc.setShader(g);
        glow.setShader(g);
        float sweep = Math.max(1f, 360f * progress);
        c.drawArc(box, -90, sweep, false, glow);
        c.drawArc(box, -90, sweep, false, arc);
    }
}
