package com.studymind.nativeapp.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pan / pinch-zoom node graph drawn on a Canvas — the native stand-in for the web's ReactFlow canvases
 * (Knowledge Gap dependency chain and the mind map). Callers position nodes in dp; the view fits them.
 */
public class GraphView extends View {

    public static class Node {
        public final String id;
        public float x, y, w, h; // dp, top-left
        public String title, subtitle;
        public int fill, stroke, text;
        public float strokeWidth = 1.5f;
        public boolean dashed;
        public float alpha = 1f;
        public float titleSp = 11f;
        public boolean bold = true;

        public Node(String id) { this.id = id; }
    }

    public static class Edge {
        public final String from, to;
        public int color = 0xFF94A3B8;
        public float width = 1.5f;
        public float alpha = 1f;
        public boolean arrow = true;
        public boolean dashed;

        public Edge(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }

    public interface OnNodeClick { void onClick(Node node); }

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Map<String, Node> byId = new HashMap<>();
    private OnNodeClick listener;

    private final float density;
    private float scale = 1f, tx, ty;
    private boolean fitted;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private int dotColor = 0xFFE2E8F0;

    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;

    public GraphView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        float sd = c.getResources().getDisplayMetrics().scaledDensity;
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStyle(Paint.Style.STROKE);
        dotPaint.setStyle(Paint.Style.FILL);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextSize(11 * sd);
        subPaint.setTextSize(9 * sd);

        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float next = Math.max(0.3f, Math.min(2.5f, scale * d.getScaleFactor()));
                float f = next / scale;
                tx = d.getFocusX() - (d.getFocusX() - tx) * f;
                ty = d.getFocusY() - (d.getFocusY() - ty) * f;
                scale = next;
                invalidate();
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                tx -= dx;
                ty -= dy;
                invalidate();
                return true;
            }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                Node hit = hitTest(e.getX(), e.getY());
                if (hit != null && listener != null) listener.onClick(hit);
                return hit != null;
            }
            @Override public boolean onDoubleTap(MotionEvent e) { fit(); return true; }
        });
    }

    public void setOnNodeClick(OnNodeClick l) { listener = l; }

    public void setDotColor(int argb) { dotColor = argb; }

    /** Replace the graph; keepView keeps the current pan/zoom (e.g. after expanding a node) */
    public void setGraph(List<Node> n, List<Edge> e, boolean keepView) {
        nodes.clear();
        edges.clear();
        byId.clear();
        nodes.addAll(n);
        edges.addAll(e);
        for (Node node : n) byId.put(node.id, node);
        if (!keepView) fitted = false;
        invalidate();
    }

    /** Fit all nodes in view with padding (the web's fitView) */
    public void fit() {
        if (nodes.isEmpty() || getWidth() == 0) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Node n : nodes) {
            minX = Math.min(minX, n.x);
            minY = Math.min(minY, n.y);
            maxX = Math.max(maxX, n.x + n.w);
            maxY = Math.max(maxY, n.y + n.h);
        }
        float pad = 20;
        float gw = (maxX - minX + pad * 2) * density, gh = (maxY - minY + pad * 2) * density;
        scale = Math.max(0.3f, Math.min(1.2f, Math.min(getWidth() / gw, getHeight() / gh)));
        tx = (getWidth() - gw * scale) / 2 - (minX - pad) * density * scale;
        ty = (getHeight() - gh * scale) / 2 - (minY - pad) * density * scale;
        fitted = true;
        invalidate();
    }

    /** Whole graph rendered at 2× on a solid background (the web "Xuất ảnh" PNG export) */
    public android.graphics.Bitmap exportBitmap(int backgroundArgb) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Node n : nodes) {
            minX = Math.min(minX, n.x);
            minY = Math.min(minY, n.y);
            maxX = Math.max(maxX, n.x + n.w);
            maxY = Math.max(maxY, n.y + n.h);
        }
        if (nodes.isEmpty()) return null;
        float pad = 40, out = 2f / density; // 2 px per dp
        int w = (int) ((maxX - minX + pad * 2) * density * out), h = (int) ((maxY - minY + pad * 2) * density * out);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), android.graphics.Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(backgroundArgb);
        float s0 = scale, x0 = tx, y0 = ty;
        boolean f0 = fitted;
        scale = out;
        tx = -(minX - pad) * density * out;
        ty = -(minY - pad) * density * out;
        fitted = true;
        int saved = dotColor;
        dotColor = 0;
        draw(cv);
        dotColor = saved;
        scale = s0;
        tx = x0;
        ty = y0;
        fitted = f0;
        return bmp;
    }

    public void zoom(float factor) {
        float next = Math.max(0.3f, Math.min(2.5f, scale * factor));
        float f = next / scale;
        tx = getWidth() / 2f - (getWidth() / 2f - tx) * f;
        ty = getHeight() / 2f - (getHeight() / 2f - ty) * f;
        scale = next;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // Keep the parent ScrollView from stealing pans that start on the canvas
        getParent().requestDisallowInterceptTouchEvent(true);
        boolean a = scaler.onTouchEvent(e);
        boolean b = gestures.onTouchEvent(e);
        return a || b || super.onTouchEvent(e);
    }

    private Node hitTest(float sx, float sy) {
        float gx = (sx - tx) / (scale * density), gy = (sy - ty) / (scale * density);
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            if (gx >= n.x && gx <= n.x + n.w && gy >= n.y && gy <= n.y + n.h) return n;
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!fitted) fit();
        // Dotted background (ReactFlow Background variant="dots")
        dotPaint.setColor(dotColor);
        float gap = 18 * density * scale;
        if (gap > 6) {
            float offX = tx % gap, offY = ty % gap;
            for (float x = offX; x < getWidth(); x += gap)
                for (float y = offY; y < getHeight(); y += gap) canvas.drawCircle(x, y, density * 0.9f, dotPaint);
        }

        canvas.save();
        canvas.translate(tx, ty);
        canvas.scale(scale * density, scale * density);

        for (Edge e : edges) {
            Node a = byId.get(e.from), b = byId.get(e.to);
            if (a == null || b == null) continue;
            edgePaint.setColor(e.color);
            edgePaint.setAlpha((int) (255 * e.alpha));
            edgePaint.setStrokeWidth(e.width);
            edgePaint.setPathEffect(e.dashed ? new DashPathEffect(new float[]{5, 5}, 0) : null);
            boolean leftToRight = b.x >= a.x + a.w / 2;
            float x1 = leftToRight ? a.x + a.w : a.x, y1 = a.y + a.h / 2;
            float x2 = leftToRight ? b.x : b.x + b.w, y2 = b.y + b.h / 2;
            float mx = (x1 + x2) / 2;
            path.reset();
            path.moveTo(x1, y1);
            path.cubicTo(mx, y1, mx, y2, x2, y2);
            canvas.drawPath(path, edgePaint);
            if (e.arrow) {
                float dir = leftToRight ? -1 : 1;
                path.reset();
                path.moveTo(x2, y2);
                path.lineTo(x2 + dir * 7, y2 - 4);
                path.lineTo(x2 + dir * 7, y2 + 4);
                path.close();
                edgePaint.setStyle(Paint.Style.FILL);
                edgePaint.setPathEffect(null);
                canvas.drawPath(path, edgePaint);
                edgePaint.setStyle(Paint.Style.STROKE);
            }
        }

        float sd = getResources().getDisplayMetrics().scaledDensity / density;
        for (Node n : nodes) {
            rect.set(n.x, n.y, n.x + n.w, n.y + n.h);
            fillPaint.setColor(n.fill);
            fillPaint.setAlpha((int) (Color_alpha(n.fill) * n.alpha));
            canvas.drawRoundRect(rect, 12, 12, fillPaint);
            strokePaint.setColor(n.stroke);
            strokePaint.setAlpha((int) (255 * n.alpha));
            strokePaint.setStrokeWidth(n.strokeWidth);
            strokePaint.setPathEffect(n.dashed ? new DashPathEffect(new float[]{5, 4}, 0) : null);
            canvas.drawRoundRect(rect, 12, 12, strokePaint);

            titlePaint.setColor(n.text);
            titlePaint.setAlpha((int) (255 * n.alpha));
            titlePaint.setTextSize(n.titleSp * sd);
            titlePaint.setFakeBoldText(n.bold);
            int innerW = (int) Math.max(10, n.w - 16);
            StaticLayout title = StaticLayout.Builder.obtain(n.title != null ? n.title : "", 0, n.title != null ? n.title.length() : 0, titlePaint, innerW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(n.subtitle != null ? 2 : 3)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END).build();
            float th = title.getHeight();
            StaticLayout sub = null;
            if (n.subtitle != null) {
                subPaint.setColor(n.text);
                subPaint.setAlpha((int) (190 * n.alpha));
                subPaint.setTextSize(9 * sd);
                sub = StaticLayout.Builder.obtain(n.subtitle, 0, n.subtitle.length(), subPaint, innerW).setMaxLines(1)
                        .setEllipsize(android.text.TextUtils.TruncateAt.END).build();
                th += sub.getHeight() + 2;
            }
            canvas.save();
            canvas.translate(n.x + 8, n.y + (n.h - th) / 2);
            title.draw(canvas);
            if (sub != null) {
                canvas.translate(0, title.getHeight() + 2);
                sub.draw(canvas);
            }
            canvas.restore();
        }
        canvas.restore();
    }

    private static int Color_alpha(int argb) { return (argb >>> 24) & 0xFF; }
}
