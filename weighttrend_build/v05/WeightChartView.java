package za.co.petern.weighttrend;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class WeightChartView extends View {
    private static final float MAX_ZOOM = 20f;

    private List<WeightEntry> entries = Collections.emptyList();
    private int trendDays = 10;
    private int periodDays = 90;
    private int forecastDays = 30;
    private double healthyLow = Double.NaN;
    private double healthyHigh = Double.NaN;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float zoomFactor = 1f;
    private float viewportCenter = 0.5f;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private boolean panning;

    public WeightChartView(Context context) {
        super(context);
        setMinimumHeight((int) dp(300));
        setClickable(true);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
                float left = dp(48);
                float right = getWidth() - dp(16);
                float width = Math.max(1f, right - left);
                float focus = clamp((detector.getFocusX() - left) / width, 0f, 1f);

                float oldSpan = 1f / zoomFactor;
                float oldStart = viewportStart(oldSpan);
                float focusInFullRange = oldStart + focus * oldSpan;

                float nextZoom = clamp(zoomFactor * detector.getScaleFactor(), 1f, MAX_ZOOM);
                float nextSpan = 1f / nextZoom;
                float nextStart = focusInFullRange - focus * nextSpan;
                nextStart = clamp(nextStart, 0f, 1f - nextSpan);

                zoomFactor = nextZoom;
                viewportCenter = nextStart + nextSpan / 2f;
                invalidate();
                return true;
            }

            @Override public void onScaleEnd(ScaleGestureDetector detector) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onDoubleTap(MotionEvent e) {
                resetZoom();
                return true;
            }

            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (scaleDetector.isInProgress()) return false;
                if (zoomFactor <= 1.001f) return false;
                if (!panning && Math.abs(distanceX) <= Math.abs(distanceY)) return false;
                panning = true;
                getParent().requestDisallowInterceptTouchEvent(true);

                float graphWidth = Math.max(1f, getWidth() - dp(64));
                float span = 1f / zoomFactor;
                float start = viewportStart(span) + (distanceX / graphWidth) * span;
                start = clamp(start, 0f, 1f - span);
                viewportCenter = start + span / 2f;
                invalidate();
                return true;
            }
        });
    }

    public void setEntries(List<WeightEntry> e) {
        entries = e == null ? Collections.emptyList() : e;
        invalidate();
    }
    public void setTrendDays(int d) { trendDays = d; invalidate(); }
    public void setPeriodDays(int d) { periodDays = d; resetZoom(); }
    public void setForecastDays(int d) { forecastDays = d; resetZoom(); }
    public void setHealthyRange(double low, double high) { healthyLow=low; healthyHigh=high; invalidate(); }

    public void resetZoom() {
        zoomFactor = 1f;
        viewportCenter = 0.5f;
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        boolean scaled = scaleDetector.onTouchEvent(event);
        boolean gestured = gestureDetector.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            panning = false;
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return scaled || gestured || super.onTouchEvent(event);
    }

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
        long fullX0=data.get(0).time.toEpochMilli();
        LocalDate lastActualDay = entries.get(entries.size()-1).time.atZone(zone).toLocalDate();
        long actualEnd = data.get(data.size()-1).time.toEpochMilli();
        long fullX1 = lastActualDay.plusDays(forecastDays).atStartOfDay(zone).toInstant().toEpochMilli();
        if (forecastDays <= 0 || fullX1 <= actualEnd) fullX1 = actualEnd;
        if(fullX1==fullX0) fullX1=fullX0+1;

        float visibleSpan = 1f / zoomFactor;
        float visibleStartFraction = viewportStart(visibleSpan);
        long x0 = fullX0 + (long)((fullX1-fullX0) * visibleStartFraction);
        long x1 = fullX0 + (long)((fullX1-fullX0) * (visibleStartFraction + visibleSpan));
        if (x1 <= x0) x1 = x0 + 1;

        double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;
        boolean foundVisible = false;
        for(WeightEntry e:data){
            long millis=e.time.toEpochMilli();
            if(millis < x0 || millis > x1) continue;
            min=Math.min(min,e.kilograms); max=Math.max(max,e.kilograms);
            Double tv=tm.get(e.time.atZone(zone).toLocalDate());
            if(tv!=null){min=Math.min(min,tv);max=Math.max(max,tv);}
            foundVisible = true;
        }
        for (TrendCalculator.ForecastPoint fp : forecast) {
            long millis = fp.day.atStartOfDay(zone).toInstant().toEpochMilli();
            if(millis < x0 || millis > x1) continue;
            min = Math.min(min, fp.kilograms);
            max = Math.max(max, fp.kilograms);
            foundVisible = true;
        }
        if (hasHealthyRange()) {
            min = Math.min(min, healthyLow);
            max = Math.max(max, healthyHigh);
            foundVisible = true;
        }
        if (!foundVisible) {
            for(WeightEntry e:data){ min=Math.min(min,e.kilograms); max=Math.max(max,e.kilograms); }
        }
        if(max-min<2){ double mid=(max+min)/2; min=mid-1; max=mid+1; }
        else { min-=0.5; max+=0.5; }

        p.setPathEffect(null);
        p.setStrokeWidth(dp(1)); p.setTextSize(dp(11)); p.setStyle(Paint.Style.STROKE);
        for(int i=0;i<=4;i++){
            float y=t+(b-t)*i/4f;
            p.setColor(0xffdddddd); c.drawLine(l,y,r,y,p);
            p.setStyle(Paint.Style.FILL); p.setColor(0xff666666);
            double v=max-(max-min)*i/4.0; c.drawText(String.format("%.1f",v),dp(4),y+dp(4),p);
            p.setStyle(Paint.Style.STROKE);
        }

        c.save();
        c.clipRect(l, t, r, b);

        if (hasHealthyRange()) {
            float yHigh=(float)(b-(healthyHigh-min)/(max-min)*(b-t));
            float yLow=(float)(b-(healthyLow-min)/(max-min)*(b-t));
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x2230a46c);
            c.drawRect(l,Math.min(yHigh,yLow),r,Math.max(yHigh,yLow),p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(0x9930a46c);
            c.drawLine(l,yHigh,r,yHigh,p);
            c.drawLine(l,yLow,r,yLow,p);
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
        c.restore();

        p.setStyle(Paint.Style.FILL); p.setTextSize(dp(11)); p.setColor(0xff666666);
        DateTimeFormatter f=DateTimeFormatter.ofPattern("d MMM");
        LocalDate visibleStartDay = Instant.ofEpochMilli(x0).atZone(zone).toLocalDate();
        LocalDate visibleEndDay = Instant.ofEpochMilli(x1).atZone(zone).toLocalDate();
        c.drawText(visibleStartDay.format(f),l,b+dp(20),p);
        String end=visibleEndDay.format(f);
        c.drawText(end,r-p.measureText(end),b+dp(20),p);

        p.setColor(0xff2457c5); c.drawText(trendDays+"-day trend",l,t-dp(10),p);
        p.setColor(0xffb05a00);
        String label=forecastDays+"-day forecast";
        c.drawText(label,r-p.measureText(label),t-dp(10),p);

        if (hasHealthyRange()) {
            p.setColor(0xff2f7d52);
            p.setTextSize(dp(10));
            String rangeLabel=String.format("Your range %.1f–%.1f kg",healthyLow,healthyHigh);
            c.drawText(rangeLabel,r-p.measureText(rangeLabel),b-dp(8),p);
        }

        if (zoomFactor > 1.01f) {
            p.setColor(0xff666666);
            p.setTextSize(dp(10));
            String zoomLabel=String.format("%.1fx  •  double-tap to reset", zoomFactor);
            c.drawText(zoomLabel, l, b-dp(hasHealthyRange()?20:8), p);
        }
    }

    private boolean hasHealthyRange() {
        return !Double.isNaN(healthyLow) && !Double.isNaN(healthyHigh) && healthyHigh>healthyLow;
    }

    private float viewportStart(float span) {
        return clamp(viewportCenter - span/2f, 0f, 1f-span);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private float dp(float v){ return v*getResources().getDisplayMetrics().density; }
}
