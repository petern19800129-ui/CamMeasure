package za.co.petern.weighttrend;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class WeightChartView extends View {
    private List<WeightEntry> entries = Collections.emptyList();
    private int trendDays = 10;
    private int periodDays = 90;
    private int forecastDays = 30;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public WeightChartView(Context context) { super(context); setMinimumHeight((int) dp(300)); }
    public void setEntries(List<WeightEntry> e) { entries = e == null ? Collections.emptyList() : e; invalidate(); }
    public void setTrendDays(int d) { trendDays = d; invalidate(); }
    public void setPeriodDays(int d) { periodDays = d; invalidate(); }
    public void setForecastDays(int d) { forecastDays = d; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        List<WeightEntry> data = TrendCalculator.filterPeriod(entries, periodDays);
        if (data.size() < 2) {
            p.setColor(0xff777777); p.setTextSize(dp(16));
            c.drawText("Not enough data to plot", dp(24), dp(60), p);
            return;
        }

        ZoneId zone = ZoneId.systemDefault();
        Map<LocalDate,Double> tm = TrendCalculator.trendMap(entries, trendDays, zone);
        List<TrendCalculator.ForecastPoint> forecast = TrendCalculator.forecast(entries, trendDays, 30, forecastDays, zone);

        float l=dp(48), r=getWidth()-dp(16), t=dp(30), b=getHeight()-dp(36);
        double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;
        for(WeightEntry e:data){
            min=Math.min(min,e.kilograms); max=Math.max(max,e.kilograms);
            Double tv=tm.get(e.time.atZone(zone).toLocalDate());
            if(tv!=null){min=Math.min(min,tv);max=Math.max(max,tv);}
        }
        for (TrendCalculator.ForecastPoint fp : forecast) {
            min = Math.min(min, fp.kilograms);
            max = Math.max(max, fp.kilograms);
        }
        if(max-min<2){ double mid=(max+min)/2; min=mid-1; max=mid+1; }
        else { min-=0.5; max+=0.5; }

        long x0=data.get(0).time.toEpochMilli();
        LocalDate lastActualDay = entries.get(entries.size()-1).time.atZone(zone).toLocalDate();
        long actualEnd = data.get(data.size()-1).time.toEpochMilli();
        long x1 = lastActualDay.plusDays(forecastDays).atStartOfDay(zone).toInstant().toEpochMilli();
        if (forecastDays <= 0 || x1 <= actualEnd) x1 = actualEnd;
        if(x1==x0)x1=x0+1;

        p.setPathEffect(null);
        p.setStrokeWidth(dp(1)); p.setTextSize(dp(11)); p.setStyle(Paint.Style.STROKE);
        for(int i=0;i<=4;i++){
            float y=t+(b-t)*i/4f;
            p.setColor(0xffdddddd); c.drawLine(l,y,r,y,p);
            p.setStyle(Paint.Style.FILL); p.setColor(0xff666666);
            double v=max-(max-min)*i/4.0; c.drawText(String.format("%.1f",v),dp(4),y+dp(4),p);
            p.setStyle(Paint.Style.STROKE);
        }

        Path actual=new Path(); boolean first=true;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.5f)); p.setColor(0xff6c7a89);
        for(WeightEntry e:data){
            float x=l+(r-l)*(e.time.toEpochMilli()-x0)/(float)(x1-x0);
            float y=(float)(b-(e.kilograms-min)/(max-min)*(b-t));
            if(first){actual.moveTo(x,y);first=false;}else actual.lineTo(x,y);
        }
        c.drawPath(actual,p);

        Path trend=new Path(); first=true; p.setStrokeWidth(dp(3)); p.setColor(0xff2457c5);
        LocalDate lastDay=null;
        for(WeightEntry e:data){
            LocalDate d=e.time.atZone(zone).toLocalDate();
            if(d.equals(lastDay)) continue;
            lastDay=d; Double v=tm.get(d); if(v==null)continue;
            float x=l+(r-l)*(e.time.toEpochMilli()-x0)/(float)(x1-x0);
            float y=(float)(b-(v-min)/(max-min)*(b-t));
            if(first){trend.moveTo(x,y);first=false;}else trend.lineTo(x,y);
        }
        c.drawPath(trend,p);

        if (forecast.size() >= 2) {
            Path prediction = new Path(); first=true;
            p.setStrokeWidth(dp(2.5f)); p.setColor(0xffb05a00);
            p.setPathEffect(new DashPathEffect(new float[]{dp(8), dp(6)}, 0));
            for (TrendCalculator.ForecastPoint fp : forecast) {
                long millis = fp.day.atStartOfDay(zone).toInstant().toEpochMilli();
                float x=l+(r-l)*(millis-x0)/(float)(x1-x0);
                float y=(float)(b-(fp.kilograms-min)/(max-min)*(b-t));
                if(first){prediction.moveTo(x,y);first=false;}else prediction.lineTo(x,y);
            }
            c.drawPath(prediction,p);
            p.setPathEffect(null);
        }

        p.setStyle(Paint.Style.FILL); p.setTextSize(dp(11)); p.setColor(0xff666666);
        DateTimeFormatter f=DateTimeFormatter.ofPattern("d MMM");
        c.drawText(data.get(0).time.atZone(zone).toLocalDate().format(f),l,b+dp(20),p);
        String end=lastActualDay.plusDays(forecastDays).format(f);
        c.drawText(end,r-p.measureText(end),b+dp(20),p);

        p.setColor(0xff2457c5); c.drawText(trendDays+"-day trend",l,t-dp(10),p);
        p.setColor(0xffb05a00);
        String label=forecastDays+"-day forecast";
        c.drawText(label,r-p.measureText(label),t-dp(10),p);
    }

    private float dp(float v){ return v*getResources().getDisplayMetrics().density; }
}
