package jp.fctv.epg;

import android.os.Bundle;
import android.os.AsyncTask;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    ZoomTableView table;
    SwipeRefreshLayout swipe;
    Calendar day = Calendar.getInstance();
    int mode = 0;
    final String[] modes = {"地上波","BS","CATV"};
    final String[] urls = {
        "https://bangumi.org/epg/td?broad_cast_date=%s&ggm_group_id=62",
        "https://bangumi.org/epg/bs?broad_cast_date=%s",
        "https://bangumi.org/epg/cs?broad_cast_date=%s"
    };
    android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        table=findViewById(R.id.table); swipe=findViewById(R.id.swipe);
        prefs=getSharedPreferences("settings",0);
        table.setScaleValue(prefs.getFloat("scale",1f));
        findViewById(R.id.prev).setOnClickListener(v->{day.add(Calendar.DATE,-1); load();});
        findViewById(R.id.next).setOnClickListener(v->{day.add(Calendar.DATE,1); load();});
        findViewById(R.id.today).setOnClickListener(v->{day=Calendar.getInstance(); load();});
        findViewById(R.id.refresh).setOnClickListener(v->load());
        findViewById(R.id.tabTerrestrial).setOnClickListener(v->{mode=0;load();});
        findViewById(R.id.tabBs).setOnClickListener(v->{mode=1;load();});
        findViewById(R.id.tabCatv).setOnClickListener(v->{mode=2;load();});
        swipe.setOnRefreshListener(this::load);
        load();
    }

    String date() { return new SimpleDateFormat("yyyyMMdd",Locale.JAPAN).format(day.getTime()); }

    void load() {
        String d=date();
        ((TextView)findViewById(R.id.title)).setText(modes[mode]+"  "+new SimpleDateFormat("M/d(E)",Locale.JAPAN).format(day.getTime()));
        swipe.setRefreshing(true);
        new AsyncTask<Void,Void,String>() {
            protected String doInBackground(Void... x) {
                try {
                    Document doc=Jsoup.connect(String.format(urls[mode],d))
                        .userAgent("Mozilla/5.0").timeout(15000).get();
                    StringBuilder out=new StringBuilder();
                    out.append(modes[mode]).append("  ").append(d).append("\n\n");
                    int n=0;
                    for(Element e:doc.select("body *")) {
                        String t=e.ownText().trim();
                        if(t.length()>0 && (t.contains(":") || t.matches(".*[0-9０-９]+時.*"))) {
                            out.append(t.replace("\n"," ")).append("\n");
                            if(++n>=180) break;
                        }
                    }
                    if(n==0) out.append("番組データを取得できませんでした。\n");
                    return out.toString();
                } catch(Exception e) { return "番組データの取得に失敗しました。\n"+e.getClass().getSimpleName(); }
            }
            protected void onPostExecute(String s) {
                table.setContent(s);
                swipe.setRefreshing(false);
            }
        }.execute();
    }

    @Override protected void onPause() {
        prefs.edit().putFloat("scale",table.getScaleValue()).apply();
        super.onPause();
    }
}
