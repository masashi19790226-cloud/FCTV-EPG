package jp.fctv.epg;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) controller.setAppearanceLightStatusBars(true);

        table = findViewById(R.id.table);
        swipe = findViewById(R.id.swipe);
        title = findViewById(R.id.title);
        prefs = getSharedPreferences("settings", 0);
        table.setScaleValue(prefs.getFloat("scale", 1f));

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        findViewById(R.id.prev).setOnClickListener(v -> {
            day.add(Calendar.DATE, -1);
            load();
        });
        findViewById(R.id.next).setOnClickListener(v -> {
            day.add(Calendar.DATE, 1);
            load();
        });
        findViewById(R.id.today).setOnClickListener(v -> {
            day = Calendar.getInstance();
            load();
        });
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

    private String dateLabel() {
        return modes[mode] + "  " +
                new SimpleDateFormat("M/d(E)", Locale.JAPAN).format(day.getTime());
    }

    private void load() {
        final String d = date();
        final int selectedMode = mode;
        title.setText(dateLabel());
        swipe.setRefreshing(true);

        new AsyncTask<Void, Void, Result>() {
            @Override
            protected Result doInBackground(Void... ignored) {
                return fetch(selectedMode, d);
            }

            @Override
            protected void onPostExecute(Result result) {
                if (result.error != null) {
                    table.setSchedule(new ArrayList<ZoomTableView.Channel>(), 0, 1440);
                    table.setMessage("番組データを取得できませんでした。\n" + result.error);
                } else {
                    table.setSchedule(result.channels, result.minTime, result.maxTime);
                    table.setMessage("");
                }
                swipe.setRefreshing(false);
            }
        }.execute();
    }

    private Result fetch(int selectedMode, String d) {
        try {
            Document doc = Jsoup.connect(String.format(urls[selectedMode], d))
                    .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    .referrer("https://bangumi.org/")
                    .timeout(25000)
                    .get();

            Map<String, ZoomTableView.Channel> map = new LinkedHashMap<>();

            // Gガイドの構造変更に備え、<line>に限定せず、開始時刻を持つ
            // time要素から親方向へたどってチャンネル名を探す。
            for (Element time : doc.select("time[s]")) {
                String stamp = time.attr("s").trim();
                if (!stamp.matches("\\d{12,14}")) continue;

                String channelName = findChannelName(time);
                if (channelName.isEmpty()) continue;
                if (selectedMode == 2 && isCommunity(channelName)) continue;

                Element p = time.selectFirst(".program_title");
                if (p == null) p = time.selectFirst("[class*=program_title]");
                String programTitle = p != null ? cleanTitle(p.text()) : "";
                if (programTitle.isEmpty()) continue;

                int start = toMinutes(stamp, d);
                if (start < 0 || start >= 2880) continue;

                ZoomTableView.Channel channel = map.get(channelName);
                if (channel == null) {
                    channel = new ZoomTableView.Channel(channelName);
                    map.put(channelName, channel);
                }
                channel.programs.add(new ZoomTableView.Program(start, start + 1, programTitle));
            }

            List<ZoomTableView.Channel> channels = new ArrayList<>(map.values());
            for (ZoomTableView.Channel channel : channels) {
                channel.programs.sort(Comparator.comparingInt(p -> p.start));
                for (int i = 0; i < channel.programs.size(); i++) {
                    ZoomTableView.Program p = channel.programs.get(i);
                    int end = (i + 1 < channel.programs.size())
                            ? channel.programs.get(i + 1).start
                            : 1440;
                    if (end <= p.start) end = p.start + 5;
                    p.end = Math.min(end, 2880);
                }
            }

            if (channels.isEmpty()) {
                return new Result("番組データが見つかりませんでした。");
            }

            return new Result(channels, 0, 1440);
        } catch (Exception e) {
            return new Result(e.getClass().getSimpleName() + ": " +
                    (e.getMessage() == null ? "接続できません" : e.getMessage()));
        }
    }

    private String findChannelName(Element time) {
        Element p = time.parent();
        for (int depth = 0; p != null && depth < 8; depth++, p = p.parent()) {
            String id = p.id();
            if (id != null && !id.isEmpty()) {
                String cleaned = cleanChannelName(id);
                if (looksLikeChannel(cleaned)) return cleaned;
            }
        }
        return "";
    }

    private String cleanChannelName(String name) {
        String s = name.replace('\u00a0', ' ').trim();
        s = s.replaceFirst("^\\d+\\s*", "");
        s = s.replaceAll("[.。]+$", "").trim();
        s = s.replaceAll("[\\s　]+$", "");
        return s;
    }

    private boolean looksLikeChannel(String name) {
        return name.contains("NHK") || name.contains("Eテレ") ||
                name.contains("福井放送") || name.contains("福井テレビ") ||
                name.contains("BS") || name.contains("WOWOW") ||
                name.contains("J SPORTS") || name.contains("GAORA") ||
                name.contains("スカイA") || name.contains("日テレ") ||
                name.contains("TBS") || name.contains("テレ東") ||
                name.contains("フジ") || name.contains("スポーツライブ") ||
                name.contains("チャンネル") || name.matches(".*\\d{3}ch.*");
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
        return n.contains("コミュニティ") || n.contains("ふくチャンネル") ||
                n.contains("091") || n.contains("092") ||
                n.contains("121") || n.contains("123");
    }

    private String cleanTitle(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    @Override
    protected void onPause() {
        prefs.edit().putFloat("scale", table.getScaleValue()).apply();
        super.onPause();
    }

    private static class Result {
        List<ZoomTableView.Channel> channels;
        int minTime, maxTime;
        String error;
        Result(List<ZoomTableView.Channel> c, int min, int max) {
            channels = c; minTime = min; maxTime = max;
        }
        Result(String e) {
            error = e; channels = new ArrayList<>(); minTime = 0; maxTime = 1440;
        }
    }
}
