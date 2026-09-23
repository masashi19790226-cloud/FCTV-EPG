package jp.fctv.epg;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ZoomTableView table;
    private SwipeRefreshLayout swipe;

    private Calendar day = Calendar.getInstance();

    private int mode = 0;

    private final String[] modes = {
            "地上波",
            "BS",
            "CATV"
    };

    private final String[] urls = {
            "https://bangumi.org/epg/td?broad_cast_date=%s&ggm_group_id=62",
            "https://bangumi.org/epg/bs?broad_cast_date=%s",
            "https://bangumi.org/epg/cs?broad_cast_date=%s"
    };

    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setContentView(R.layout.activity_main);

        table = findViewById(R.id.table);
        swipe = findViewById(R.id.swipe);

        prefs = getSharedPreferences("settings", 0);

        table.setScaleValue(
                prefs.getFloat("scale", 1f)
        );

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

        findViewById(R.id.tabTerrestrial).setOnClickListener(v -> {
            mode = 0;
            load();
        });

        findViewById(R.id.tabBs).setOnClickListener(v -> {
            mode = 1;
            load();
        });

        findViewById(R.id.tabCatv).setOnClickListener(v -> {
            mode = 2;
            load();
        });

        swipe.setOnRefreshListener(this::load);

        load();
    }

    private String date() {
        return new SimpleDateFormat(
                "yyyyMMdd",
                Locale.JAPAN
        ).format(day.getTime());
    }

    private void load() {

        String d = date();

        TextView title = findViewById(R.id.title);

        title.setText(
                modes[mode] +
                        "  " +
                        new SimpleDateFormat(
                                "M/d(E)",
                                Locale.JAPAN
                        ).format(day.getTime())
        );

        swipe.setRefreshing(true);

        new AsyncTask<Void, Void, List<ZoomTableView.Channel>>() {

            @Override
            protected List<ZoomTableView.Channel> doInBackground(Void... x) {

                try {

                    Document doc = Jsoup.connect(
                                    String.format(urls[mode], d)
                            )
                            .userAgent(
                                    "Mozilla/5.0 (Android 14; Mobile)"
                            )
                            .timeout(20000)
                            .get();

                    return parsePrograms(doc);

                } catch (Exception e) {

                    return new ArrayList<>();

                }
            }

            @Override
            protected void onPostExecute(
                    List<ZoomTableView.Channel> channels
            ) {

                table.setChannels(channels);

                swipe.setRefreshing(false);
            }

        }.execute();
    }

    private List<ZoomTableView.Channel> parsePrograms(
            Document doc
    ) {

        List<ZoomTableView.Channel> channels =
                new ArrayList<>();

        /*
         * Gガイドのページから
         * チャンネル名・時刻・番組名を取得する。
         */

        ZoomTableView.Channel currentChannel = null;

        for (Element element : doc.select("body *")) {

            String text = element.ownText().trim();

            if (text.length() == 0) {
                continue;
            }

            // チャンネル名らしい文字列
            if (isChannelName(text)) {

                currentChannel =
                        findOrCreateChannel(
                                channels,
                                text
                        );

                continue;
            }

            // 時刻＋番組名らしい文字列
            if (currentChannel != null &&
                    isTimeText(text)) {

                String time = extractTime(text);

                String title =
                        text.replace(
                                time,
                                ""
                        ).trim();

                if (title.length() == 0) {
                    continue;
                }

                currentChannel.programs.add(
                        new ZoomTableView.Program(
                                time,
                                nextTime(time),
                                title
                        )
                );
            }
        }

        /*
         * 何も拾えなかった場合でも、
         * 空の番組表として表示できるようにする。
         */
        if (channels.isEmpty()) {

            if (mode == 0) {
                addChannel(channels, "NHK総合");
                addChannel(channels, "Eテレ");
                addChannel(channels, "FBC");
                addChannel(channels, "福井テレビ");
            }

            if (mode == 1) {
                addChannel(channels, "NHK BS");
                addChannel(channels, "BS日テレ");
                addChannel(channels, "BS朝日");
                addChannel(channels, "BS-TBS");
                addChannel(channels, "BSテレ東");
                addChannel(channels, "BSフジ");
                addChannel(channels, "BS10");
                addChannel(channels, "BS11");
                addChannel(channels, "BS12");
            }
        }

        return channels;
    }

    private ZoomTableView.Channel findOrCreateChannel(
            List<ZoomTableView.Channel> channels,
            String name
    ) {

        for (ZoomTableView.Channel c : channels) {

            if (c.name.equals(name)) {
                return c;
            }
        }

        ZoomTableView.Channel channel =
                new ZoomTableView.Channel(name);

        channels.add(channel);

        return channel;
    }

    private void addChannel(
            List<ZoomTableView.Channel> channels,
            String name
    ) {

        channels.add(
                new ZoomTableView.Channel(name)
        );
    }

    private boolean isChannelName(String text) {

        return text.contains("NHK") ||
                text.contains("Eテレ") ||
                text.contains("福井") ||
                text.contains("FBC") ||
                text.contains("BS10") ||
                text.contains("BS11") ||
                text.contains("BS12") ||
                text.contains("BS日テレ") ||
                text.contains("BS朝日") ||
                text.contains("BS-TBS") ||
                text.contains("BSテレ東") ||
                text.contains("BSフジ");
    }

    private boolean isTimeText(String text) {

        return text.matches(
                ".*[0-9０-９]{1,2}:[0-9０-９]{2}.*"
        );
    }

    private String extractTime(String text) {

        java.util.regex.Matcher m =
                java.util.regex.Pattern
                        .compile(
                                "([0-9０-９]{1,2}:[0-9０-９]{2})"
                        )
                        .matcher(text);

        if (m.find()) {
            return m.group(1);
        }

        return "00:00";
    }

    private String nextTime(String time) {

        try {

            String[] p = time.split(":");

            int hour =
                    Integer.parseInt(p[0]);

            int minute =
                    Integer.parseInt(p[1]);

            minute += 30;

            if (minute >= 60) {
                hour++;
                minute -= 60;
            }

            return String.format(
                    Locale.JAPAN,
                    "%02d:%02d",
                    hour,
                    minute
            );

        } catch (Exception e) {

            return time;
        }
    }

    @Override
    protected void onPause() {

        prefs.edit()
                .putFloat(
                        "scale",
                        table.getScaleValue()
                )
                .apply();

        super.onPause();
    }
}
