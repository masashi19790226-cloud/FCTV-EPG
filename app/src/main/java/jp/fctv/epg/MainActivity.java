package jp.fctv.epg;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
        ViewCompat.requestApplyInsets(root);

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
        final int selectedMode = mode;
        title.setText(modes[selectedMode] + "  " + new SimpleDateFormat("M/d(E)", Locale.JAPAN).format(day.getTime()));
        swipe.setRefreshing(true);
        new AsyncTask<Void, Void, Result>() {
            @Override protected Result doInBackground(Void... ignored) { return fetch(selectedMode, d); }
            @Override protected void onPostExecute(Result result) {
                if (result.error != null) {
                    title.setText(modes[selectedMode] + "  " + new SimpleDateFormat("M/d(E)", Locale.JAPAN).format(day.getTime()) + "  取得失敗");
                    table.setSchedule(new ArrayList<ZoomTableView.Channel>(), 0, 1440);
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

            // Gガイドは <line id="局名"><time s="yyyymmddHHMM"><p class="program_title">...</p>...</time></line> という構造を持つ。
            // ここを直接読むことで、時刻と番組名の対応が崩れないようにする。
            for (Element line : doc.select("line[id]")) {
                String channelName = line.attr("id").trim();
                if (channelName.isEmpty()) continue;
                if (selectedMode == 2 && isCommunity(channelName)) continue;

                List<ZoomTableView.Program> programs = new ArrayList<>();
                for (Element time : line.select("time[s]")) {
                    String stamp = time.attr("s").trim();
                    if (stamp.length() < 12) continue;
                    int start = toMinutes(stamp, d);
                    if (start < 0 || start > 2880) continue;
                    Element p = time.selectFirst("p.program_title");
                    String programTitle = p != null ? p.text().trim() : "";
                    if (programTitle.isEmpty()) continue;
                    programs.add(new ZoomTableView.Program(start, start + 1, cleanTitle(programTitle)));
                }

                if (programs.isEmpty()) continue;
                programs.sort(Comparator.comparingInt(p -> p.start));
                for (int i = 0; i < programs.size(); i++) {
                    int end = (i + 1 < programs.size()) ? programs.get(i + 1).start : 1440;
                    if (end <= programs.get(i).start) end = programs.get(i).start + 5;
                    programs.get(i).end = end;
                }
                channels.add(new ZoomTableView.Channel(channelName, programs));
            }

            if (channels.isEmpty()) return new Result("番組データが見つかりませんでした。");
            return new Result(channels, 0, 1440);
        } catch (Exception e) {
            return new Result(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private int toMinutes(String stamp, String requestedDate) {
        try {
            int ymd = Integer.parseInt(stamp.substring(0, 8));
            int req = Integer.parseInt(requestedDate);
            int hour = Integer.parseInt(stamp.substring(8, 10));
            int minute = Integer.parseInt(stamp.substring(10, 12));
            int value = hour * 60 + minute;
            if (ymd > req) value += 1440;
            return value;
        } catch (Exception e) {
            return -1;
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

    @Override protected void onPause() {
        prefs.edit().putFloat("scale", table.getScaleValue()).apply();
        super.onPause();
    }

    private static class Result {
        List<ZoomTableView.Channel> channels;
        int minTime, maxTime;
        String error;
        Result(List<ZoomTableView.Channel> c, int min, int max) { channels = c; minTime = min; maxTime = max; }
        Result(String e) { error = e; channels = new ArrayList<>(); }
    }
}
