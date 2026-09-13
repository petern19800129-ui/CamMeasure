package za.co.petern.weighttrend;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class TrendCalculator {
    private TrendCalculator() {}

    public static final class DailyPoint {
        public final LocalDate day;
        public final double actualMean;
        public final double trend;

        public DailyPoint(LocalDate day, double actualMean, double trend) {
            this.day = day;
            this.actualMean = actualMean;
            this.trend = trend;
        }
    }

    public static final class ForecastPoint {
        public final LocalDate day;
        public final double kilograms;

        public ForecastPoint(LocalDate day, double kilograms) {
            this.day = day;
            this.kilograms = kilograms;
        }
    }

    public static List<DailyPoint> dailyTrend(List<WeightEntry> entries, int windowDays, ZoneId zone) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        TreeMap<LocalDate, List<Double>> byDay = new TreeMap<>();
        for (WeightEntry e : entries) {
            LocalDate d = e.time.atZone(zone).toLocalDate();
            byDay.computeIfAbsent(d, k -> new ArrayList<>()).add(e.kilograms);
        }

        TreeMap<LocalDate, Double> means = new TreeMap<>();
        for (Map.Entry<LocalDate, List<Double>> e : byDay.entrySet()) {
            double sum = 0.0;
            for (double v : e.getValue()) sum += v;
            means.put(e.getKey(), sum / e.getValue().size());
        }

        List<DailyPoint> out = new ArrayList<>();
        for (Map.Entry<LocalDate, Double> e : means.entrySet()) {
            LocalDate day = e.getKey();
            LocalDate start = day.minusDays(Math.max(1, windowDays) - 1L);
            double sum = 0.0;
            int n = 0;
            for (Map.Entry<LocalDate, Double> p : means.subMap(start, true, day, true).entrySet()) {
                sum += p.getValue();
                n++;
            }
            out.add(new DailyPoint(day, e.getValue(), n == 0 ? e.getValue() : sum / n));
        }
        return out;
    }

    public static Map<LocalDate, Double> trendMap(List<WeightEntry> entries, int windowDays, ZoneId zone) {
        Map<LocalDate, Double> out = new LinkedHashMap<>();
        for (DailyPoint p : dailyTrend(entries, windowDays, zone)) out.put(p.day, p.trend);
        return out;
    }

    public static double latestTrend(List<WeightEntry> entries, int windowDays, ZoneId zone) {
        List<DailyPoint> points = dailyTrend(entries, windowDays, zone);
        return points.isEmpty() ? Double.NaN : points.get(points.size() - 1).trend;
    }

    public static double weeklyRate(List<WeightEntry> entries, int windowDays, ZoneId zone) {
        return regressionSlopePerDay(entries, windowDays, 30, zone) * 7.0;
    }

    public static List<ForecastPoint> forecast(
            List<WeightEntry> entries,
            int windowDays,
            int lookbackDays,
            int forecastDays,
            ZoneId zone) {
        List<DailyPoint> points = dailyTrend(entries, windowDays, zone);
        if (points.size() < 2 || forecastDays < 0) return Collections.emptyList();

        double slopePerDay = regressionSlopePerDay(points, lookbackDays);
        if (Double.isNaN(slopePerDay)) return Collections.emptyList();

        DailyPoint last = points.get(points.size() - 1);
        List<ForecastPoint> out = new ArrayList<>();
        for (int d = 0; d <= forecastDays; d++) {
            out.add(new ForecastPoint(last.day.plusDays(d), last.trend + slopePerDay * d));
        }
        return out;
    }

    public static double predictedWeight(
            List<WeightEntry> entries,
            int windowDays,
            int lookbackDays,
            int forecastDays,
            ZoneId zone) {
        List<ForecastPoint> points = forecast(entries, windowDays, lookbackDays, forecastDays, zone);
        return points.isEmpty() ? Double.NaN : points.get(points.size() - 1).kilograms;
    }

    private static double regressionSlopePerDay(
            List<WeightEntry> entries, int windowDays, int lookbackDays, ZoneId zone) {
        return regressionSlopePerDay(dailyTrend(entries, windowDays, zone), lookbackDays);
    }

    private static double regressionSlopePerDay(List<DailyPoint> points, int lookbackDays) {
        if (points.size() < 2) return Double.NaN;
        LocalDate last = points.get(points.size() - 1).day;
        LocalDate cutoff = last.minusDays(Math.max(1, lookbackDays));
        List<DailyPoint> recent = new ArrayList<>();
        for (DailyPoint p : points) if (!p.day.isBefore(cutoff)) recent.add(p);
        if (recent.size() < 2) recent = points;
        if (recent.size() < 2) return Double.NaN;

        long x0 = recent.get(0).day.toEpochDay();
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = recent.size();
        for (DailyPoint p : recent) {
            double x = p.day.toEpochDay() - x0;
            double y = p.trend;
            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
        }
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-12) return Double.NaN;
        return (n * sxy - sx * sy) / denom;
    }

    public static List<WeightEntry> filterPeriod(List<WeightEntry> entries, int periodDays) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        if (periodDays <= 0) return new ArrayList<>(entries);
        Instant latest = entries.get(entries.size() - 1).time;
        Instant cutoff = latest.minus(periodDays, ChronoUnit.DAYS);
        List<WeightEntry> out = new ArrayList<>();
        for (WeightEntry e : entries) if (!e.time.isBefore(cutoff)) out.add(e);
        return out;
    }
}
