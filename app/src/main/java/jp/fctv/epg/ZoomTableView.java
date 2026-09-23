package jp.fctv.epg;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class ZoomTableView extends View {
    public static class Program {
        public long start;
        public long end;
        public String title;
        public Program(long start, long end, String title) {
            this.start = start;
            this.end = end;
            this.title = title == null ? "" : title;
        }
    }

    public static class Channel {
        public String name;
        public List<Program> programs = new ArrayList<>();
        public Channel(String name) { this.name = name; }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ScaleGestureDetector detector;
    private float scale = 1f;
    private float density;
    private List<Channel> channels = new ArrayList<>();
    private long minTime = 0;
    private long maxTime = 24 * 60;
    private float timeWidth;
    private float channelWidth;
    private float headerHeight;
    private float hourHeight;
    private float downX, downY;
    private boolean scaling;

    public ZoomTableView(Context c) { super(c); init(c); }
    public ZoomTableView(Context c, AttributeSet a) { super(c, a); init(c); }
    public ZoomTableView(Context c, AttributeSet a, int s) { super(c, a, s); init(c); }

    private void init(Context c) {
        density = getResources().getDisplayMetrics().density;
        setBackgroundColor(0xffffffff);
        detector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float old = scale;
                scale *= d.getScaleFactor();
                scale = Math.max(0.70f, Math.min(2.50f, scale));
                if (Math.abs(scale - old) > 0.001f) requestLayout();
                invalidate();
                return true;
            }
            @Override public boolean onScaleBegin(ScaleGestureDetector d) {
                scaling = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d) {
                scaling = false;
                getParent().requestDisallowInterceptTouchEvent(false);
            }
        });
        setFocusable(true);
    }

    public float getScaleValue() { return scale; }
    public void setScaleValue(float v) {
        scale = Math.max(0.70f, Math.min(2.50f, v));
        requestLayout();
        invalidate();
    }

    public void setSchedule(List<Channel> value, long min, long max) {
        channels = value == null ? new ArrayList<Channel>() : value;
        minTime = min;
        maxTime = Math.max(min + 60, max);
        requestLayout();
        invalidate();
    }

    private float dp(float v) { return v * density; }

    private void updateSizes() {
        timeWidth = dp(68) * scale;
        channelWidth = dp(178) * scale;
        headerHeight = dp(58) * scale;
        hourHeight = dp(92) * scale;
    }

    @Override protected void onMeasure(int w, int h) {
        updateSizes();
        int width = Math.round(timeWidth + channels.size() * channelWidth);
        int minutes = Math.max(60, maxTime - minTime);
        int height = Math.round(headerHeight + (minutes / 60f) * hourHeight);
        setMeasuredDimension(resolveSize(Math.max(width, getSuggestedMinimumWidth()), w),
                resolveSize(Math.max(height, getSuggestedMinimumHeight()), h));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        updateSizes();
        paint.setTypeface(Paint.DEFAULT);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(dp(13) * scale);
        paint.setColor(0xff222222);

        // Header background and time column.
        paint.setColor(0xffeeeeee);
        c.drawRect(0, 0, timeWidth, headerHeight, paint);
        for (int i = 0; i < channels.size(); i++) {
            float left = timeWidth + i * channelWidth;
            paint.setColor(0xffe5e5e5);
            c.drawRect(left, 0, left + channelWidth, headerHeight, paint);
            paint.setColor(0xff333333);
            drawCentered(c, channels.get(i).name, left + channelWidth / 2f, headerHeight / 2f);
        }

        // Time rows and vertical channel lines.
        int firstHour = (minTime / 60) * 60;
        int lastHour = ((maxTime + 59) / 60) * 60;
        for (int minute = firstHour; minute <= lastHour; minute += 60) {
            float y = headerHeight + (minute - minTime) / 60f * hourHeight;
            paint.setColor(0xffd0d0d0);
            paint.setStrokeWidth(dp(1));
            c.drawLine(0, y, getWidth(), y, paint);
            paint.setColor(0xff444444);
            paint.setTextSize(dp(12) * scale);
            String label = String.format(java.util.Locale.JAPAN, "%02d:%02d", (minute / 60) % 24, minute % 60);
            c.drawText(label, dp(5) * scale, y + dp(17) * scale, paint);
        }

        for (int i = 0; i <= channels.size(); i++) {
            float x = timeWidth + i * channelWidth;
            paint.setColor(0xffcccccc);
            paint.setStrokeWidth(dp(1));
            c.drawLine(x, 0, x, getHeight(), paint);
        }
        paint.setColor(0xffcccccc);
        c.drawLine(0, headerHeight, getWidth(), headerHeight, paint);

        // Program blocks.
        for (int ci = 0; ci < channels.size(); ci++) {
            float left = timeWidth + ci * channelWidth;
            for (Program p : channels.get(ci).programs) {
                float top = headerHeight + (p.start - minTime) / 60f * hourHeight + dp(1) * scale;
                float bottom = headerHeight + (p.end - minTime) / 60f * hourHeight - dp(1) * scale;
                if (bottom <= 0 || top >= getHeight()) continue;
                RectF r = new RectF(left + dp(2) * scale, Math.max(headerHeight, top),
                        left + channelWidth - dp(2) * scale, Math.min(getHeight(), Math.max(top + dp(20) * scale, bottom)));
                paint.setColor(0xfffafafa);
                c.drawRect(r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xffbdbdbd);
                c.drawRect(r, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xff222222);
                drawProgramText(c, p.title, r);
            }
        }
    }

    private void drawCentered(Canvas c, String text, float cx, float cy) {
        String t = text == null ? "" : text;
        if (t.length() > 16) t = t.substring(0, 16);
        float width = paint.measureText(t);
        c.drawText(t, cx - width / 2f, cy - (paint.ascent() + paint.descent()) / 2f, paint);
    }

    private void drawProgramText(Canvas c, String text, RectF r) {
        float size = dp(12) * scale;
        paint.setTextSize(size);
        float lineHeight = size * 1.25f;
        float x = r.left + dp(5) * scale;
        float y = r.top + size + dp(2) * scale;
        float maxWidth = r.width() - dp(10) * scale;
        String[] words = text.replace("\n", " ").split("(?<=\\s)|(?=\\s)");
        String line = "";
        int lines = 0;
        int maxLines = Math.max(1, (int)((r.height() - dp(5) * scale) / lineHeight));
        for (String word : words) {
            String test = line + word;
            if (paint.measureText(test) > maxWidth && line.length() > 0) {
                c.drawText(line.trim(), x, y, paint);
                y += lineHeight;
                lines++;
                if (lines >= maxLines) return;
                line = word;
            } else {
                line = test;
            }
        }
        if (line.length() > 0 && lines < maxLines) c.drawText(line.trim(), x, y, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        detector.onTouchEvent(e);
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (!scaling) getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        return true;
    }
}
