package jp.fctv.epg;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.List;

public class ZoomTableView extends ScrollView {

    public static class Program {
        public String start;
        public String end;
        public String title;

        public Program(String start, String end, String title) {
            this.start = start;
            this.end = end;
            this.title = title;
        }
    }

    public static class Channel {
        public String name;
        public List<Program> programs = new ArrayList<>();

        public Channel(String name) {
            this.name = name;
        }

        public Channel(String name, List<Program> programs) {
            this.name = name;
            if (programs != null) {
                this.programs.addAll(programs);
            }
        }
    }

    private ScaleGestureDetector detector;
    private float scale = 1f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<Channel> channels = new ArrayList<>();

    private static final float BASE_CHANNEL_WIDTH = 180f;
    private static final float BASE_ROW_HEIGHT = 60f;
    private static final float TIME_WIDTH = 65f;
    private static final float TEXT_SIZE = 14f;

    public ZoomTableView(Context context) {
        super(context);
        init(context);
    }

    public ZoomTableView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomTableView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        detector = new ScaleGestureDetector(
                context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        scale *= d.getScaleFactor();
                        scale = Math.max(0.65f, Math.min(2.5f, scale));

                        requestLayout();
                        invalidate();

                        return true;
                    }
                }
        );

        setFillViewport(true);
        setWillNotDraw(false);
        setBackgroundColor(0xffffffff);
    }

    public void setChannels(List<Channel> list) {
        channels = list != null ? list : new ArrayList<>();
        requestLayout();
        invalidate();
    }

    public float getScaleValue() {
        return scale;
    }

    public void setScaleValue(float value) {
        scale = Math.max(0.65f, Math.min(2.5f, value));
        requestLayout();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        detector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        if (channels != null && !channels.isEmpty()) {
            int maxMinutes = 24 * 60;

            int contentWidth =
                    (int) ((TIME_WIDTH +
                            BASE_CHANNEL_WIDTH * channels.size()) * scale);

            int contentHeight =
                    (int) ((maxMinutes / 30f) * BASE_ROW_HEIGHT * scale);

            setMeasuredDimension(
                    Math.max(width, contentWidth),
                    Math.max(height, contentHeight)
            );
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (channels == null || channels.isEmpty()) {
            paint.setTextSize(TEXT_SIZE * scale);
            paint.setColor(0xff202020);
            canvas.drawText(
                    "番組データを取得できませんでした。",
                    20,
                    40,
                    paint
            );
            return;
        }

        paint.setTypeface(Typeface.DEFAULT);

        float channelWidth = BASE_CHANNEL_WIDTH * scale;
        float timeWidth = TIME_WIDTH * scale;
        float rowHeight = BASE_ROW_HEIGHT * scale;

        // 背景
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffffffff);
        canvas.drawRect(
                0,
                0,
                getWidth(),
                getHeight(),
                paint
        );

        // テレビ局名
        paint.setTextSize(TEXT_SIZE * scale);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setColor(0xff202020);

        for (int i = 0; i < channels.size(); i++) {
            float x = timeWidth + i * channelWidth;

            canvas.drawText(
                    channels.get(i).name,
                    x + 8,
                    28 * scale,
                    paint
            );
        }

        paint.setTypeface(Typeface.DEFAULT);

        // 時間軸
        for (int hour = 0; hour <= 24; hour++) {
            float y = hour * 2 * rowHeight;

            paint.setColor(0xffaaaaaa);
            paint.setStrokeWidth(1);

            canvas.drawLine(
                    0,
                    y,
                    getWidth(),
                    y,
                    paint
            );

            if (hour < 24) {
                paint.setTextSize(12 * scale);
                paint.setColor(0xff333333);

                canvas.drawText(
                        String.format("%02d:00", hour),
                        5,
                        y + 18 * scale,
                        paint
                );
            }
        }

        // 番組
        for (int c = 0; c < channels.size(); c++) {

            Channel channel = channels.get(c);

            float x = timeWidth + c * channelWidth;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            paint.setColor(0xffbbbbbb);

            canvas.drawRect(
                    x,
                    0,
                    x + channelWidth,
                    24 * 2 * rowHeight,
                    paint
            );

            paint.setStyle(Paint.Style.FILL);

            for (Program program : channel.programs) {

                int start = parseMinutes(program.start);
                int end = parseMinutes(program.end);

                if (start < 0 || end < 0) {
                    continue;
                }

                if (end <= start) {
                    end = start + 30;
                }

                float top =
                        (start / 30f) * rowHeight;

                float bottom =
                        (end / 30f) * rowHeight;

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xfff5f5f5);

                canvas.drawRect(
                        x + 2,
                        top + 2,
                        x + channelWidth - 2,
                        bottom - 2,
                        paint
                );

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xff999999);

                canvas.drawRect(
                        x + 2,
                        top + 2,
                        x + channelWidth - 2,
                        bottom - 2,
                        paint
                );

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xff202020);
                paint.setTextSize(TEXT_SIZE * scale);

                drawWrappedText(
                        canvas,
                        program.title,
                        x + 7,
                        top + 20 * scale,
                        channelWidth - 14,
                        Math.max(1, (int)((bottom - top) / (TEXT_SIZE * scale + 4)))
                );
            }
        }
    }

    private int parseMinutes(String value) {
        if (value == null) {
            return -1;
        }

        try {
            String[] p = value.split(":");

            if (p.length < 2) {
                return -1;
            }

            int hour = Integer.parseInt(p[0]);
            int minute = Integer.parseInt(p[1]);

            return hour * 60 + minute;

        } catch (Exception e) {
            return -1;
        }
    }

    private void drawWrappedText(
            Canvas canvas,
            String text,
            float x,
            float y,
            float maxWidth,
            int maxLines
    ) {
        if (text == null) {
            return;
        }

        StringBuilder line = new StringBuilder();
        int lineCount = 0;

        for (int i = 0; i < text.length(); i++) {

            String next = line.toString() + text.charAt(i);

            if (paint.measureText(next) > maxWidth) {

                canvas.drawText(
                        line.toString(),
                        x,
                        y + lineCount * (TEXT_SIZE * scale + 4),
                        paint
                );

                line.setLength(0);
                line.append(text.charAt(i));

                lineCount++;

                if (lineCount >= maxLines) {
                    break;
                }

            } else {
                line.append(text.charAt(i));
            }
        }

        if (lineCount < maxLines && line.length() > 0) {
            canvas.drawText(
                    line.toString(),
                    x,
                    y + lineCount * (TEXT_SIZE * scale + 4),
                    paint
            );
        }
    }
}
