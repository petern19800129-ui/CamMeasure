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
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

public class WeekDetailActivity extends Activity {
    private final ZoneId zone=ZoneId.systemDefault();

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        buildUi();
    }

    private void buildUi(){
        long startEpoch=getIntent().getLongExtra("week_start",Long.MIN_VALUE);
        if(startEpoch==Long.MIN_VALUE){ finish(); return; }

        LocalDate start=LocalDate.ofEpochDay(startEpoch);
        LocalDate end=start.plusDays(6);
        double average=getIntent().getDoubleExtra("week_average",Double.NaN);
        double diff=getIntent().getDoubleExtra("week_diff",Double.NaN);
        int measuredDays=getIntent().getIntExtra("measured_days",0);

        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(16),dp(16),dp(28));
        scroll.addView(root);

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new Button(this);
        back.setText("‹ Back");
        back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(-2,-2));

        int weekNo=start.get(WeekFields.SUNDAY_START.weekOfWeekBasedYear());
        TextView title=t("Week "+weekNo,24,true);
        title.setPadding(dp(12),0,0,0);
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(top);

        DateTimeFormatter rangeFmt=DateTimeFormatter.ofPattern("d MMM yyyy",Locale.getDefault());
        TextView range=t(start.format(rangeFmt)+" – "+end.format(rangeFmt),15,false);
        range.setPadding(0,dp(4),0,dp(12));
        root.addView(range);

        LinearLayout summary=new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);

        TextView avg=card("Average",Double.isNaN(average)?"—":String.format(Locale.US,"%.1f kg",average));
        summary.addView(avg,new LinearLayout.LayoutParams(0,dp(86),1));

        String change="—";
        if(!Double.isNaN(diff)) change=String.format(Locale.US,"%+.1f kg",diff);
        TextView diffView=card("Change",change);
        if(!Double.isNaN(diff)){
            long tenth=Math.round(diff*10.0);
            if(tenth>0) diffView.setTextColor(0xffc62828);
            else if(tenth<0) diffView.setTextColor(0xff2e7d32);
            else diffView.setTextColor(0xffd4a000);
        }
        summary.addView(diffView,new LinearLayout.LayoutParams(0,dp(86),1));

        TextView days=card("Measured",measuredDays+"/7 days");
        summary.addView(days,new LinearLayout.LayoutParams(0,dp(86),1));
        root.addView(summary);

        root.addView(t("Daily measurements",18,true));

        TreeMap<LocalDate,List<Double>> byDay=new TreeMap<>();
        long[] times=getIntent().getLongArrayExtra("times");
        double[] weights=getIntent().getDoubleArrayExtra("weights");
        if(times!=null && weights!=null){
            int n=Math.min(times.length,weights.length);
            for(int i=0;i<n;i++){
                LocalDate day=Instant.ofEpochMilli(times[i]).atZone(zone).toLocalDate();
                byDay.computeIfAbsent(day,k->new ArrayList<>()).add(weights[i]);
            }
        }

        DateTimeFormatter dayFmt=DateTimeFormatter.ofPattern("EEE",Locale.getDefault());
        DateTimeFormatter dateFmt=DateTimeFormatter.ofPattern("d MMM yyyy",Locale.getDefault());

        for(int d=0;d<7;d++){
            LocalDate day=start.plusDays(d);
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0,dp(8),0,dp(8));

            TextView dow=t(day.format(dayFmt),15,true);
            dow.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            row.addView(dow,new LinearLayout.LayoutParams(dp(54),-2));

            List<Double> values=byDay.get(day);
            String middle=day.format(dateFmt);
            if(values!=null && values.size()>1) middle+="\n"+values.size()+" readings";
            TextView date=t(middle,14,false);
            row.addView(date,new LinearLayout.LayoutParams(0,-2,1));

            String weight="—";
            if(values!=null && !values.isEmpty()){
                double sum=0;
                for(double v:values) sum+=v;
                weight=String.format(Locale.US,"%.1f kg",sum/values.size());
            }
            TextView kg=t(weight,16,true);
            kg.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            row.addView(kg,new LinearLayout.LayoutParams(dp(92),-2));

            root.addView(row);
            TextView divider=new TextView(this);
            divider.setBackgroundColor(0x22000000);
            root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        }

        TextView note=t("Weekly average gives each measured day equal weight.",12,false);
        note.setPadding(0,dp(12),0,0);
        root.addView(note);

        setContentView(scroll);
    }

    private TextView card(String label,String value){
        TextView v=t(label+"\n"+value,14,true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(4),dp(8),dp(4),dp(8));
        return v;
    }

    private TextView t(String s,int sp,boolean bold){
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setPadding(0,dp(5),0,dp(5));
        if(bold)v.setTypeface(v.getTypeface(),1);
        return v;
    }

    private int dp(int v){
        return (int)(v*getResources().getDisplayMetrics().density+0.5f);
    }
}
