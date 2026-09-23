package jp.fctv.epg;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private ZoomTableView table;
    private SwipeRefreshLayout swipe;
    private TextView title;
    private Calendar day = Calendar.getInstance();
    private int mode = 0;
    private final String[] modes = {"地上波", "BS", "CATV"};
    private final String[] urls = {
            "https://bangumi.org/epg/td?broad_cast_date=%s&ggm_group_id=62",
            "https://bangumi.org/epg/bs?broad_cast_date=%s",
            "https://bangumi.org/epg/cs?broad_cast_date=%s"
    };
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        table = findViewById(R.id.table);
        swipe = findViewById(R.id.swipe);
        title = findViewById(R.id.title);
        prefs = getSharedPreferences("settings", 0);
        table.setScaleValue(prefs.getFloat("scale", 1f));

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        findViewById(R.id.prev).setOnClickListener(v -> { day.add(Calendar.DATE, -1); load(); });
        findViewById(R.id.next).setOnClickListener(v -> { day.add(Calendar.DATE, 1); load(); });
        findViewById(R.id.today).setOnClickListener(v -> { day = Calendar.getInstance(); load(); });
        findViewById(R.id.refresh).setOnClickListener(v -> load());
        findViewById(R.id.tabTerrestrial).setOnClickListener(v -> { mode = 0; load(); });
        findViewById(R.id.tabBs).setOnClickListener(v -> { mode = 1; load(); });
        findViewById(R.id.tabCatv).setOnClickListener(v -> { mode = 2; load(); });
        swipe.setOnRefreshListener(this::load);
        load();
    }

    private String date() {
        return new SimpleDateFormat("yyyyMMdd", Locale.JAPAN).format(day.getTime());
    }

    private void load() {
        final String d = date();
        title.setText(modes[mode] + "  " + new SimpleDateFormat("M/d(E)", Locale.JAPAN).format(day.getTime()));
        swipe.setRefreshing(true);
        final int selectedMode = mode;
        new AsyncTask<Void, Void, Result>() {
            @Override protected Result doInBackground(Void... ignored) {
                return fetch(selectedMode, d);
            }
            @Override protected void onPostExecute(Result result) {
                if (result.error != null) {
                    table.setSchedule(new ArrayList<ZoomTableView.Channel>(), 0, 24 * 60);
                    title.setText(modes[selectedMode] + "  " + new SimpleDateFormat("M/d(E)", Locale.JAPAN).format(day.getTime()) + "  取得失敗");
                } else {
                    table.setSchedule(result.channels, result.minTime, result.maxTime);
                }
                swipe.setRefreshing(false);
            }
        }.execute();
    }

    private Result fetch(int selectedMode, String d) {
        try {
            Document doc = Jsoup.connect(String.format(urls[selectedMode], d))
                    .userAgent("Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    .timeout(20000)
                    .get();

            List<ZoomTableView.Channel> channels = new ArrayList<>();
            for (Element line : doc.select("line[id]")) {
                String channelName = line.attr("id").trim();
                if (channelName.length() == 0) continue;
                if (selectedMode == 2 && isCommunity(channelName)) continue;

                List<ZoomTableView.Program> programs = new ArrayList<>();
                for (Element time : line.select("time[s]")) {
                    String s = time.attr("s").trim();
                    if (s.length() < 12) continue;
                    long start = parseDateMinutes(s);
                    if (start < 0) continue;
                    Element title = time.selectFirst("p.program_title");
                    String text = title == null ? time.text() : title.text();
                    text = cleanTitle(text);
                    if (text.length() == 0) continue;
                    programs.add(new ZoomTableView.Program(start, start + 30, text));
                }
                if (programs.isEmpty()) continue;
                Collections.sort(programs, Comparator.comparingLong(p -> p.start));
                for (int i = 0; i < programs.size(); i++) {
                    long end = (i + 1 < programs.size()) ? programs.get(i + 1).start : programs.get(i).start + 60;
                    if (end <= programs.get(i).start) end = programs.get(i).start + 5;
                    programs.get(i).end = end;
                }
                channels.add(new ZoomTableView.Channel(channelName, programs));
            }

            if (channels.isEmpty()) return new Result("番組データが見つかりませんでした。");
            long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            for (ZoomTableView.Channel c : channels) {
                for (ZoomTableView.Program p : c.programs) {
                    min = Math.min(min, p.start);
                    max = Math.max(max, p.end);
                }
            }
            // Display the whole guide day, including the early morning and late night.
            long baseDay = parseDateMinutes(d + "0000");
            long dayStart = baseDay;
            long dayEnd = baseDay + 24 * 60;
            min = Math.min(min, dayStart);
            max = Math.max(max, dayEnd);
            return new Result(channels, min, max);
        } catch (Exception e) {
            return new Result(e.getClass().getSimpleName());
        }
    }

    private boolean isCommunity(String name) {
        String n = name.toLowerCase(Locale.JAPAN);
        return n.contains("コミュニティ") || n.contains("ふくチャンネル") || n.contains("福いいネ")
                || n.contains("091") || n.contains("092") || n.contains("121") || n.contains("123");
    }

    private String cleanTitle(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private long parseDateMinutes(String s) {
        try {
            int year = Integer.parseInt(s.substring(0, 4));
            int month = Integer.parseInt(s.substring(4, 6));
            int day = Integer.parseInt(s.substring(6, 8));
            int hour = Integer.parseInt(s.substring(8, 10));
            int minute = Integer.parseInt(s.substring(10, 12));
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(year, month - 1, day, 0, 0, 0);
            long base = c.getTimeInMillis() / 60000L;
            return base + hour * 60L + minute;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override protected void onPause() {
        prefs.edit().putFloat("scale", table.getScaleValue()).apply();
        super.onPause();
    }

    private static class Result {
        List<ZoomTableView.Channel> channels;
        long minTime, maxTime;
        String error;
        Result(List<ZoomTableView.Channel> c, long min, long max) { channels = c; minTime = min; maxTime = max; }
        Result(String e) { error = e; channels = new ArrayList<>(); }
    }
}
