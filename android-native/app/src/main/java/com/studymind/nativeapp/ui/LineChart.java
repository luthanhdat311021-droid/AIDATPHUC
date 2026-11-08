package com.studymind.nativeapp.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Small area line chart (Stitch "Tiến độ cải thiện"): teal line, soft fill, dots, last point highlighted. */
public class LineChart extends View {
    private final float[] values;
    private final int highlight;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** values are 0..max; highlight = index drawn in amber (best attempt), -1 for none */
    public LineChart(Context c, float[] values, float max, int highlight) {
        super(c);
        this.values = new float[values.length];
        for (int i = 0; i < values.length; i++) this.values[i] = max > 0 ? values[i] / max : 0;
        this.highlight = highlight;
        line.setColor(0xFF005C55);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dp(c, 2.5f));
        line.setStrokeJoin(Paint.Join.ROUND);
        dot.setStyle(Paint.Style.FILL);
        grid.setColor(0x22000000);
        grid.setStrokeWidth(1);
        grid.setPathEffect(new android.graphics.DashPathEffect(new float[]{8, 8}, 0));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (values.length == 0 || w == 0) return;
        float pad = Ui.dp(getContext(), 8);
        float left = pad, right = w - pad, top = pad, bottom = h - pad;
        canvas.drawLine(left, bottom, right, bottom, grid);
        canvas.drawLine(left, (top + bottom) / 2, right, (top + bottom) / 2, grid);
        float step = values.length > 1 ? (right - left) / (values.length - 1) : 0;
        Path p = new Path(), area = new Path();
        for (int i = 0; i < values.length; i++) {
            float x = values.length > 1 ? left + step * i : (left + right) / 2;
            float y = bottom - (bottom - top) * values[i];
            if (i == 0) { p.moveTo(x, y); area.moveTo(x, bottom); }
            p.lineTo(x, y);
            area.lineTo(x, y);
            if (i == values.length - 1) area.lineTo(x, bottom);
        }
        area.close();
        fill.setShader(new LinearGradient(0, top, 0, bottom, 0x550F766E, 0x080F766E, Shader.TileMode.CLAMP));
        canvas.drawPath(area, fill);
        canvas.drawPath(p, line);
        float r = Ui.dp(getContext(), 5);
        for (int i = 0; i < values.length; i++) {
            float x = values.length > 1 ? left + step * i : (left + right) / 2;
            float y = bottom - (bottom - top) * values[i];
            dot.setColor(0xFFFFFFFF);
            canvas.drawCircle(x, y, r, dot);
            dot.setColor(i == highlight ? 0xFF945D00 : 0xFF005C55);
            canvas.drawCircle(x, y, r - Ui.dp(getContext(), 2), dot);
        }
    }
}
