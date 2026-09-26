package za.co.petern.weighttrend;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class WeeklyHistoryActivity extends Activity {
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        buildUi(loadEntries());
    }

    private List<WeightEntry> loadEntries(){
        long[] times=getIntent().getLongArrayExtra("times");
        double[] weights=getIntent().getDoubleArrayExtra("weights");
        List<WeightEntry> out=new ArrayList<>();
        if(times==null || weights==null) return out;
        int n=Math.min(times.length,weights.length);
        for(int i=0;i<n;i++) out.add(new WeightEntry(Instant.ofEpochMilli(times[i]),weights[i],"History",null));
        return out;
    }

    private void buildUi(List<WeightEntry> entries){
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(16),dp(16),dp(28));
        scroll.addView(root);

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new Button(this); back.setText("‹ Back"); back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(-2,-2));
        TextView title=t("Weekly history",24,true); title.setPadding(dp(12),0,0,0);
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(top);

        TextView info=t("Sunday–Saturday • each day counts once",13,false);
        info.setPadding(0,dp(4),0,dp(12)); root.addView(info);

        List<TrendCalculator.WeeklyPoint> weeks=TrendCalculator.weeklyHistory(entries,ZoneId.systemDefault(),LocalDate.now(ZoneId.systemDefault()));
        if(weeks.isEmpty()){
            root.addView(t("No weekly data available.",16,false));
            setContentView(scroll); return;
        }

        Collections.reverse(weeks);
        DateTimeFormatter sameMonth=DateTimeFormatter.ofPattern("d MMM",Locale.getDefault());
        DateTimeFormatter withYear=DateTimeFormatter.ofPattern("d MMM yyyy",Locale.getDefault());

        for(TrendCalculator.WeeklyPoint w:weeks){
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0,dp(10),0,dp(10));

            String range;
            if(w.start.getYear()==w.end.getYear()){
                range=w.start.format(sameMonth)+" – "+w.end.format(withYear);
            }else{
                range=w.start.format(withYear)+" – "+w.end.format(withYear);
            }
            if(w.currentWeek) range += "  • current";

            TextView week=t(range+"\n"+w.measuredDays+(w.measuredDays==1?" measured day":" measured days"),14,false);
            row.addView(week,new LinearLayout.LayoutParams(0,-2,1.6f));

            TextView avg=t(String.format(Locale.US,"%.1f kg",w.average),16,true);
            avg.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            row.addView(avg,new LinearLayout.LayoutParams(0,-2,0.75f));

            TextView diff=t("—",15,true);
            diff.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            if(!Double.isNaN(w.differenceFromPreviousWeek)){
                diff.setText(String.format(Locale.US,"%+.1f kg",w.differenceFromPreviousWeek));
            }
            row.addView(diff,new LinearLayout.LayoutParams(0,-2,0.75f));

            TextView arrow=t("—",28,true); arrow.setGravity(Gravity.CENTER);
            if(!Double.isNaN(w.differenceFromPreviousWeek)){
                long tenth=Math.round(w.differenceFromPreviousWeek*10.0);
                if(tenth>0){ arrow.setText("↑"); arrow.setTextColor(0xffc62828); }
                else if(tenth<0){ arrow.setText("↓"); arrow.setTextColor(0xff2e7d32); }
                else { arrow.setText("→"); arrow.setTextColor(0xffd4a000); }
            }else arrow.setTextColor(0xff777777);
            row.addView(arrow,new LinearLayout.LayoutParams(dp(46),-2));

            root.addView(row);
            TextView divider=new TextView(this); divider.setBackgroundColor(0x22000000);
            root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        }
        setContentView(scroll);
    }

    private TextView t(String s,int sp,boolean bold){
        TextView v=new TextView(this); v.setText(s); v.setTextSize(sp);
        if(bold)v.setTypeface(v.getTypeface(),1); return v;
    }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
}
