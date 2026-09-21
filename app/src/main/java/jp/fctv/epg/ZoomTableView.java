package jp.fctv.epg;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ScrollView;

public class ZoomTableView extends ScrollView {
    private final ScaleGestureDetector detector;
    private float scale = 1f;
    private float baseText = 15f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String content = "";

    public ZoomTableView(Context c) {
        super(c);
        detector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            public boolean onScale(ScaleGestureDetector d) {
                scale *= d.getScaleFactor();
                scale = Math.max(.65f, Math.min(2.5f, scale));
                invalidate();
                return true;
            }
        });
        setFillViewport(true);
        setWillNotDraw(false);
        setBackgroundColor(0xffffffff);
    }

    public void setContent(String s) { content = s == null ? "" : s; invalidate(); }

    public float getScaleValue() { return scale; }
    public void setScaleValue(float v) { scale = Math.max(.65f, Math.min(2.5f, v)); invalidate(); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        detector.onTouchEvent(e);
        return super.onTouchEvent(e);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        paint.setTextSize(baseText * scale);
        paint.setColor(0xff202020);
        float y = 34 * scale;
        String[] lines = content.split("\n");
        for (String line : lines) {
            c.drawText(line, 12, y, paint);
            y += 34 * scale;
        }
    }
}
