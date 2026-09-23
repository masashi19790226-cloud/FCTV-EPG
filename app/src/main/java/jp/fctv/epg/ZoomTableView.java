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
import java.util.Locale;

public class ZoomTableView extends View {
    public static class Program {
        public int start;
        public int end;
        public String title;
        public Program(int start, int end, String title) {
            this.start = start;
            this.end = end;
            this.title = title == null ? "" : title;
        }
    }

    public static class Channel {
        public String name;
        public List<Program> programs = new ArrayList<>();
        public Channel(String name) { this.name = name; }
        public Channel(String name, List<Program> programs) {
            this.name = name;
            if (programs != null) this.programs.addAll(programs);
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ScaleGestureDetector detector;
    private float scale = 1f;
    private float density;
    private List<Channel> channels = new ArrayList<>();
    private int minTime = 0;
    private int maxTime = 1440;
    private float timeWidth, channelWidth, headerHeight, hourHeight;
    private boolean scaling;

    public ZoomTableView(Context c) { super(c); init(c); }
    public ZoomTableView(Context c, AttributeSet a) { super(c, a); init(c); }
    public ZoomTableView(Context c, AttributeSet a, int s) { super(c, a, s); init(c); }

    private void init(Context c) {
        density = getResources().getDisplayMetrics().density;
        setBackgroundColor(0xffffffff);
        detector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                scale *= d.getScaleFactor();
                scale = Math.max(0.70f, Math.min(2.50f, scale));
                requestLayout();
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
    }

    public float getScaleValue() { return scale; }
    public void setScaleValue(float v) {
        scale = Math.max(0.70f, Math.min(2.50f, v));
        requestLayout();
        invalidate();
    }

    public void setSchedule(List<Channel> value, int min, int max) {
        channels = value == null ? new ArrayList<Channel>() : value;
        minTime = Math.max(0, min);
        maxTime = Math.max(minTime + 60, max);
        requestLayout();
        invalidate();
    }

    private float dp(float v) { return v * density; }

    private void updateSizes() {
        timeWidth = dp(62) * scale;
        channelWidth = dp(180) * scale;
        headerHeight = dp(58) * scale;
        hourHeight = dp(92) * scale;
    }

    @Override protected void onMeasure(int w, int h) {
        updateSizes();
        int width = Math.round(timeWidth + channels.size() * channelWidth);
        int height = Math.round(headerHeight + ((maxTime - minTime) / 60f) * hourHeight);
        setMeasuredDimension(resolveSize(Math.max(width, getSuggestedMinimumWidth()), w),
                resolveSize(Math.max(height, getSuggestedMinimumHeight()), h));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        updateSizes();

        // Header
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffeeeeee);
        c.drawRect(0, 0, timeWidth, headerHeight, paint);
        paint.setTextSize(dp(13) * scale);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        for (int i = 0; i < channels.size(); i++) {
            float left = timeWidth + i * channelWidth;
            paint.setColor(0xffe5e5e5);
            c.drawRect(left, 0, left + channelWidth, headerHeight, paint);
            paint.setColor(0xff333333);
            drawCentered(c, channels.get(i).name, left + channelWidth / 2f, headerHeight / 2f);
        }
        paint.setTypeface(android.graphics.Typeface.DEFAULT);

        // Grid
        int firstHour = (minTime / 60) * 60;
        int lastHour = ((maxTime + 59) / 60) * 60;
        for (int minute = firstHour; minute <= lastHour; minute += 60) {
            float y = headerHeight + (minute - minTime) / 60f * hourHeight;
            paint.setColor(0xffd0d0d0);
            paint.setStrokeWidth(dp(1));
            c.drawLine(0, y, getWidth(), y, paint);
            paint.setColor(0xff444444);
            paint.setTextSize(dp(11) * scale);
            String label = String.format(Locale.JAPAN, "%02d:%02d", (minute / 60) % 24, minute % 60);
            c.drawText(label, dp(4) * scale, y + dp(16) * scale, paint);
        }
        for (int i = 0; i <= channels.size(); i++) {
            float x = timeWidth + i * channelWidth;
            paint.setColor(0xffcccccc);
            paint.setStrokeWidth(dp(1));
            c.drawLine(x, 0, x, getHeight(), paint);
        }
        paint.setColor(0xffcccccc);
        c.drawLine(0, headerHeight, getWidth(), headerHeight, paint);

        // Programs
        for (int ci = 0; ci < channels.size(); ci++) {
            float left = timeWidth + ci * channelWidth;
            for (Program p : channels.get(ci).programs) {
                if (p.end <= p.start) continue;
                float top = headerHeight + (p.start - minTime) / 60f * hourHeight + dp(1) * scale;
                float bottom = headerHeight + (p.end - minTime) / 60f * hourHeight - dp(1) * scale;
                if (bottom <= headerHeight || top >= getHeight()) continue;
                RectF r = new RectF(left + dp(2) * scale, Math.max(headerHeight, top),
                        left + channelWidth - dp(2) * scale, Math.min(getHeight(), bottom));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xfffafafa);
                c.drawRect(r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xffaaaaaa);
                c.drawRect(r, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xff222222);
                drawProgramText(c, p.title, r);
            }
        }
    }

    private void drawCentered(Canvas c, String text, float cx, float cy) {
        String t = text == null ? "" : text;
        if (t.length() > 18) t = t.substring(0, 18);
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
        int maxLines = Math.max(1, (int)((r.height() - dp(5) * scale) / lineHeight));
        String line = "";
        int lines = 0;
        for (int i = 0; i < text.length(); i++) {
            String test = line + text.charAt(i);
            if (paint.measureText(test) > maxWidth && line.length() > 0) {
                c.drawText(line, x, y, paint);
                lines++;
                if (lines >= maxLines) return;
                y += lineHeight;
                line = "" + text.charAt(i);
            } else {
                line = test;
            }
        }
        if (line.length() > 0 && lines < maxLines) c.drawText(line, x, y, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        detector.onTouchEvent(e);
        if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if ((e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) && !scaling) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return true;
    }
}
