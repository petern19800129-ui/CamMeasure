package za.co.petern.weighttrend;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.health.connect.HealthPermissions;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_HEALTH = 42;
    private final List<WeightEntry> libra = new ArrayList<>();
    private final List<WeightEntry> health = new ArrayList<>();
    private List<WeightEntry> merged = new ArrayList<>();
    private DataRepository repo;
    private TextView latest, trend, rate, forecast, healthyStatus, healthyForecast, weeklyLast, weeklyThis, weeklyArrow, status, recent;
    private EditText healthyLowInput, healthyHighInput;
    private WeightChartView chart;
    private int trendDays=10;
    private int forecastDays=30;
    private double healthyLow=Double.NaN;
    private double healthyHigh=Double.NaN;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(Intent.ACTION_VIEW_PERMISSION_USAGE.equals(getIntent().getAction())){showPrivacy();return;}
        repo=new DataRepository(this); loadHealthyRange(); buildUi(); loadLibra();
    }

    private void showPrivacy(){
        TextView v=new TextView(this);
        v.setPadding(dp(24),dp(48),dp(24),dp(24)); v.setTextSize(18);
        v.setText("Weight Trend privacy\n\nThis app reads weight measurements from Health Connect only after you grant permission. The supplied Libra history is stored locally inside the app. No weight or health data is sent over the network. The app requests read access only and never writes to Health Connect. Forecasts are calculated locally from your recent smoothed weight trend and are estimates, not medical advice.");
        setContentView(v);
    }

    private void buildUi(){
        ScrollView sv=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(18),dp(18),dp(28)); sv.addView(root);
        root.addView(t("Weight Trend",28,true));
        status=t("Loading Libra history…",13,false); root.addView(status);

        LinearLayout cards=new LinearLayout(this); cards.setOrientation(LinearLayout.HORIZONTAL); root.addView(cards);
        latest=card("Latest"); trend=card("Trend"); rate=card("kg/week");
        cards.addView(latest,new LinearLayout.LayoutParams(0,dp(86),1));
        cards.addView(trend,new LinearLayout.LayoutParams(0,dp(86),1));
        cards.addView(rate,new LinearLayout.LayoutParams(0,dp(86),1));

        forecast=t("30-day forecast\n—",18,true); forecast.setGravity(Gravity.CENTER); forecast.setPadding(dp(8),dp(8),dp(8),dp(10)); root.addView(forecast);

        root.addView(t("Weekly average",14,true));
        LinearLayout weeklyRow=new LinearLayout(this); weeklyRow.setOrientation(LinearLayout.HORIZONTAL); weeklyRow.setGravity(Gravity.CENTER_VERTICAL);
        weeklyLast=card("Last week");
        weeklyArrow=t("—",32,true); weeklyArrow.setGravity(Gravity.CENTER);
        weeklyThis=card("This week");
        weeklyRow.addView(weeklyLast,new LinearLayout.LayoutParams(0,dp(86),1));
        weeklyRow.addView(weeklyArrow,new LinearLayout.LayoutParams(dp(64),dp(86)));
        weeklyRow.addView(weeklyThis,new LinearLayout.LayoutParams(0,dp(86),1));
        root.addView(weeklyRow);
        Button weeklyHistory=new Button(this); weeklyHistory.setText("Weekly history"); weeklyHistory.setOnClickListener(v->openWeeklyHistory()); root.addView(weeklyHistory);

        healthyStatus=t("Healthy range not set",15,true); healthyStatus.setGravity(Gravity.CENTER); healthyStatus.setPadding(dp(6),dp(8),dp(6),dp(4)); root.addView(healthyStatus);
        healthyForecast=t("Forecast to healthy range: set a range first",14,true); healthyForecast.setGravity(Gravity.CENTER); healthyForecast.setPadding(dp(6),0,dp(6),dp(10)); root.addView(healthyForecast);

        chart=new WeightChartView(this); chart.setForecastDays(forecastDays); chart.setHealthyRange(healthyLow,healthyHigh); root.addView(chart,new LinearLayout.LayoutParams(-1,dp(320)));

        root.addView(t("Graph range",14,true));
        HorizontalScrollView hs=new HorizontalScrollView(this); LinearLayout pr=new LinearLayout(this); hs.addView(pr);
        addPeriod(pr,"30D",30);addPeriod(pr,"3M",90);addPeriod(pr,"6M",180);addPeriod(pr,"1Y",365);addPeriod(pr,"ALL",0); root.addView(hs);

        root.addView(t("Trend smoothing",14,true));
        HorizontalScrollView ts=new HorizontalScrollView(this); LinearLayout tr=new LinearLayout(this); ts.addView(tr);
        for(int d:new int[]{7,10,14,30}){
            Button x=new Button(this);x.setText(d+" days");
            x.setOnClickListener(v->{trendDays=d;chart.setTrendDays(d);refreshSummary();}); tr.addView(x);
        }
        root.addView(ts);

        root.addView(t("Prediction horizon",14,true));
        HorizontalScrollView fs=new HorizontalScrollView(this); LinearLayout fr=new LinearLayout(this); fs.addView(fr);
        for(int d:new int[]{14,30,60}){
            Button x=new Button(this);x.setText(d+" days");
            x.setOnClickListener(v->{forecastDays=d;chart.setForecastDays(d);refreshSummary();}); fr.addView(x);
        }
        root.addView(fs);

        root.addView(t("Healthy weight range (your setting)",14,true));
        LinearLayout rangeInputs=new LinearLayout(this); rangeInputs.setOrientation(LinearLayout.HORIZONTAL);
        healthyLowInput=new EditText(this); healthyLowInput.setHint("Lower kg"); healthyLowInput.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        healthyHighInput=new EditText(this); healthyHighInput.setHint("Upper kg"); healthyHighInput.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if(!Double.isNaN(healthyLow)) healthyLowInput.setText(fmt(healthyLow));
        if(!Double.isNaN(healthyHigh)) healthyHighInput.setText(fmt(healthyHigh));
        rangeInputs.addView(healthyLowInput,new LinearLayout.LayoutParams(0,-2,1));
        rangeInputs.addView(healthyHighInput,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(rangeInputs);
        LinearLayout rangeButtons=new LinearLayout(this); rangeButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button saveRange=new Button(this); saveRange.setText("Save range"); saveRange.setOnClickListener(v->saveHealthyRange());
        Button clearRange=new Button(this); clearRange.setText("Clear range"); clearRange.setOnClickListener(v->clearHealthyRange());
        rangeButtons.addView(saveRange,new LinearLayout.LayoutParams(0,-2,1));
        rangeButtons.addView(clearRange,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(rangeButtons);

        Button connect=new Button(this); connect.setText("Refresh Health Connect"); connect.setOnClickListener(v->connectHealth()); root.addView(connect);
        root.addView(t("Recent measurements",18,true)); recent=t("",14,false); root.addView(recent);
        setContentView(sv);
    }

    private void openWeeklyHistory(){
        Intent i=new Intent(this,WeeklyHistoryActivity.class);
        long[] times=new long[merged.size()];
        double[] weights=new double[merged.size()];
        for(int n=0;n<merged.size();n++){
            WeightEntry e=merged.get(n);
            times[n]=e.time.toEpochMilli();
            weights[n]=e.kilograms;
        }
        i.putExtra("times",times);
        i.putExtra("weights",weights);
        startActivity(i);
    }

    private void addPeriod(LinearLayout row,String label,int days){ Button b=new Button(this);b.setText(label);b.setOnClickListener(v->chart.setPeriodDays(days));row.addView(b); }
    private TextView card(String label){ TextView v=t(label+"\n—",14,true); v.setGravity(Gravity.CENTER); v.setPadding(dp(4),dp(8),dp(4),dp(8)); return v; }
    private TextView t(String s,int sp,boolean bold){ TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setPadding(0,dp(6),0,dp(6)); if(bold)v.setTypeface(v.getTypeface(),1);return v; }

    private void loadLibra(){
        Executors.newSingleThreadExecutor().execute(()->{
            try{
                libra.addAll(repo.loadLibraHistory());
                runOnUiThread(()->{ status.setText("Libra history loaded: "+libra.size()+" records"); mergeAndRender(); if(hasWeightPermission()) refreshHealth(); });
            }catch(Exception e){runOnUiThread(()->status.setText("Libra import error: "+e.getMessage()));}
        });
    }

    private void connectHealth(){
        if(!repo.isHealthConnectAvailable()){status.setText("Health Connect is not available on this device.");return;}
        if(hasWeightPermission()) refreshHealth();
        else {
            List<String> p=new ArrayList<>(); p.add(HealthPermissions.READ_WEIGHT);
            if(Build.VERSION.SDK_INT>=35)p.add(HealthPermissions.READ_HEALTH_DATA_HISTORY);
            requestPermissions(p.toArray(new String[0]),REQ_HEALTH);
        }
    }

    private boolean hasWeightPermission(){ return Build.VERSION.SDK_INT>=34 && checkSelfPermission(HealthPermissions.READ_WEIGHT)==PackageManager.PERMISSION_GRANTED; }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==REQ_HEALTH){ if(hasWeightPermission())refreshHealth(); else status.setText("Health Connect weight permission was not granted."); }
    }

    private void refreshHealth(){
        status.setText("Reading Health Connect…");
        repo.loadHealthWeights(list->{
            synchronized(health){health.clear();health.addAll(list);}
            runOnUiThread(()->{status.setText("Health Connect: "+list.size()+" weight records");mergeAndRender();});
        }, err->runOnUiThread(()->status.setText("Health Connect error: "+err.getMessage())));
    }

    private void mergeAndRender(){ merged=DataRepository.merge(libra,health,ZoneId.systemDefault()); chart.setEntries(merged); refreshSummary(); }

    private void refreshSummary(){
        if(merged.isEmpty())return;
        ZoneId zone=ZoneId.systemDefault();
        WeightEntry e=merged.get(merged.size()-1);
        double tv=TrendCalculator.latestTrend(merged,trendDays,zone);
        double wr=TrendCalculator.weeklyRate(merged,trendDays,zone);
        double predicted=TrendCalculator.predictedWeight(merged,trendDays,30,forecastDays,zone);
        latest.setText("Latest\n"+fmt(e.kilograms)+" kg");
        trend.setText("Trend\n"+(Double.isNaN(tv)?"—":fmt(tv)+" kg"));
        rate.setText("kg/week\n"+(Double.isNaN(wr)?"—":String.format(Locale.US,"%+.2f",wr)));
        if(Double.isNaN(predicted) || Double.isNaN(tv)) forecast.setText(forecastDays+"-day forecast\n—");
        else forecast.setText(forecastDays+"-day forecast\n"+fmt(predicted)+" kg   ("+String.format(Locale.US,"%+.1f",predicted-tv)+" kg)");
        updateHealthyStatus(tv,e.kilograms);
        updateHealthyForecast(tv,wr,e,zone);
        updateWeeklyComparison(zone);

        StringBuilder sb=new StringBuilder();
        DateTimeFormatter f=DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
        for(int i=merged.size()-1,n=0;i>=0&&n<12;i--,n++){
            WeightEntry w=merged.get(i);
            sb.append(w.time.atZone(zone).format(f)).append("   ").append(fmt(w.kilograms)).append(" kg   ").append(w.source).append('\n');
        }
        recent.setText(sb.toString());
    }

    private void updateWeeklyComparison(ZoneId zone){
        TrendCalculator.WeeklyComparison wc=TrendCalculator.weeklyComparison(merged,zone,LocalDate.now(zone));
        weeklyLast.setText("Last week\n"+(Double.isNaN(wc.lastWeekAverage)?"—":fmt(wc.lastWeekAverage)+" kg"));
        weeklyThis.setText("This week\n"+(Double.isNaN(wc.thisWeekAverage)?"—":fmt(wc.thisWeekAverage)+" kg"));

        if(Double.isNaN(wc.lastWeekAverage) || Double.isNaN(wc.thisWeekAverage)){
            weeklyArrow.setText("—");
            weeklyArrow.setTextColor(0xff777777);
            return;
        }

        long lastTenth=Math.round(wc.lastWeekAverage*10.0);
        long thisTenth=Math.round(wc.thisWeekAverage*10.0);
        if(thisTenth>lastTenth){
            weeklyArrow.setText("↑");
            weeklyArrow.setTextColor(0xffc62828);
        }else if(thisTenth<lastTenth){
            weeklyArrow.setText("↓");
            weeklyArrow.setTextColor(0xff2e7d32);
        }else{
            weeklyArrow.setText("→");
            weeklyArrow.setTextColor(0xffd4a000);
        }
    }

    private void loadHealthyRange(){
        SharedPreferences sp=getSharedPreferences("weight_settings",MODE_PRIVATE);
        if(sp.contains("healthy_low") && sp.contains("healthy_high")){
            healthyLow=Double.longBitsToDouble(sp.getLong("healthy_low",Double.doubleToRawLongBits(Double.NaN)));
            healthyHigh=Double.longBitsToDouble(sp.getLong("healthy_high",Double.doubleToRawLongBits(Double.NaN)));
            if(!validRange(healthyLow,healthyHigh)){ healthyLow=Double.NaN; healthyHigh=Double.NaN; }
        }
    }

    private void saveHealthyRange(){
        try{
            double low=Double.parseDouble(healthyLowInput.getText().toString().trim().replace(',','.'));
            double high=Double.parseDouble(healthyHighInput.getText().toString().trim().replace(',','.'));
            if(!validRange(low,high)){
                healthyStatus.setText("Enter a lower value below the upper value (20–300 kg).");
                return;
            }
            healthyLow=low; healthyHigh=high;
            getSharedPreferences("weight_settings",MODE_PRIVATE).edit()
                    .putLong("healthy_low",Double.doubleToRawLongBits(low))
                    .putLong("healthy_high",Double.doubleToRawLongBits(high)).apply();
            healthyLowInput.setText(fmt(low)); healthyHighInput.setText(fmt(high));
            chart.setHealthyRange(low,high);
            refreshSummary();
        }catch(Exception ex){ healthyStatus.setText("Enter both lower and upper weights."); }
    }

    private void clearHealthyRange(){
        healthyLow=Double.NaN; healthyHigh=Double.NaN;
        getSharedPreferences("weight_settings",MODE_PRIVATE).edit().remove("healthy_low").remove("healthy_high").apply();
        healthyLowInput.setText(""); healthyHighInput.setText("");
        chart.setHealthyRange(Double.NaN,Double.NaN);
        healthyStatus.setText("Healthy range not set");
        healthyForecast.setText("Forecast to healthy range: set a range first");
    }

    private boolean validRange(double low,double high){
        return !Double.isNaN(low) && !Double.isNaN(high) && low>=20 && high<=300 && high>low;
    }

    private void updateHealthyStatus(double trendKg,double latestKg){
        if(!validRange(healthyLow,healthyHigh)){ healthyStatus.setText("Healthy range not set"); return; }
        double reference=Double.isNaN(trendKg)?latestKg:trendKg;
        String state=reference<healthyLow?"below":(reference>healthyHigh?"above":"inside");
        String source=Double.isNaN(trendKg)?"Latest":"Trend";
        healthyStatus.setText("Your range: "+fmt(healthyLow)+"–"+fmt(healthyHigh)+" kg  •  "+source+" is "+state+" range");
    }

    private void updateHealthyForecast(double trendKg,double weeklyRate,WeightEntry latestEntry,ZoneId zone){
        if(!validRange(healthyLow,healthyHigh)){
            healthyForecast.setText("Forecast to healthy range: set a range first");
            return;
        }
        if(Double.isNaN(trendKg) || Double.isNaN(weeklyRate)){
            healthyForecast.setText("Forecast to healthy range: not enough trend data");
            return;
        }
        if(trendKg>=healthyLow && trendKg<=healthyHigh){
            healthyForecast.setText("Forecast: already in healthy range");
            healthyForecast.setTextColor(0xff2e7d32);
            return;
        }

        double perDay=weeklyRate/7.0;
        double distance;
        if(trendKg>healthyHigh){
            if(perDay>=-0.0001){
                healthyForecast.setText("No healthy-range entry forecast at current trend");
                healthyForecast.setTextColor(0xff777777);
                return;
            }
            distance=trendKg-healthyHigh;
        }else{
            if(perDay<=0.0001){
                healthyForecast.setText("No healthy-range entry forecast at current trend");
                healthyForecast.setTextColor(0xff777777);
                return;
            }
            distance=healthyLow-trendKg;
        }

        int days=(int)Math.ceil(distance/Math.abs(perDay));
        if(days<0 || days>3650){
            healthyForecast.setText("No healthy-range entry forecast at current trend");
            healthyForecast.setTextColor(0xff777777);
            return;
        }

        LocalDate base=latestEntry.time.atZone(zone).toLocalDate();
        LocalDate date=base.plusDays(days);
        String duration;
        if(days<14) duration=days+" day"+(days==1?"":"s");
        else if(days<70) duration=String.format(Locale.US,"%.1f weeks",days/7.0);
        else duration=String.format(Locale.US,"%.1f months",days/30.44);
        DateTimeFormatter df=DateTimeFormatter.ofPattern("d MMM yyyy");
        healthyForecast.setText("Forecast to healthy range: ~"+duration+" ("+date.format(df)+")");
        healthyForecast.setTextColor(0xff1565c0);
    }

    private String fmt(double v){ return String.format(Locale.US,"%.1f",v); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
}
