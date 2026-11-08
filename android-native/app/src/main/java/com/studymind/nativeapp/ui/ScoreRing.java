package com.studymind.nativeapp.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Circular progress ring (Stitch score / timer rings). Children are drawn by a FrameLayout around it. */
public class ScoreRing extends View {
    private final float fraction;
    private final int track, fill;
    private final float stroke;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public ScoreRing(Context c, float fraction, int trackArgb, int fillArgb, float strokeDp) {
        super(c);
        this.fraction = Math.max(0, Math.min(1, fraction));
        this.track = trackArgb;
        this.fill = fillArgb;
        this.stroke = Ui.dp(c, strokeDp);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(stroke);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight());
        float off = stroke / 2f;
        r.set((getWidth() - s) / 2f + off, (getHeight() - s) / 2f + off, (getWidth() + s) / 2f - off, (getHeight() + s) / 2f - off);
        p.setColor(track);
        canvas.drawArc(r, 0, 360, false, p);
        p.setColor(fill);
        canvas.drawArc(r, -90, 360 * fraction, false, p);
    }
}
