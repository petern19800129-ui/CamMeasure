package com.pete.cammeasure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MeasurementEngine {
    public enum Mode { WIDTH, HEIGHT, DEPTH, BOX }
    public enum Unit { AUTO, MM, CM, M }

    public static final class Point3 {
        public final float x;
        public final float y;
        public final float z;

        public Point3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final List<Point3> points = new ArrayList<>();
    private Mode mode = Mode.WIDTH;
    private Unit unit = Unit.AUTO;

    public void setMode(Mode newMode) {
        mode = newMode;
        points.clear();
    }

    public Mode getMode() { return mode; }
    public Unit getUnit() { return unit; }
    public int getPointCount() { return points.size(); }
    public int getRequiredPointCount() { return mode == Mode.BOX ? 4 : 2; }

    public void addPoint(Point3 point) {
        if (points.size() >= getRequiredPointCount()) points.clear();
        points.add(point);
    }

    public void undo() {
        if (!points.isEmpty()) points.remove(points.size() - 1);
    }

    public void reset() { points.clear(); }

    public void cycleUnit() {
        switch (unit) {
            case AUTO: unit = Unit.MM; break;
            case MM: unit = Unit.CM; break;
            case CM: unit = Unit.M; break;
            default: unit = Unit.AUTO; break;
        }
    }

    public String unitButtonLabel() {
        switch (unit) {
            case MM: return "Units: mm";
            case CM: return "Units: cm";
            case M: return "Units: m";
            default: return "Units: Auto";
        }
    }

    public boolean isComplete() { return points.size() == getRequiredPointCount(); }

    public String instruction() {
        if (mode == Mode.BOX) {
            switch (points.size()) {
                case 0: return "Aim at one corner and mark the origin.";
                case 1: return "From the same corner, mark the WIDTH endpoint.";
                case 2: return "From the origin, mark the HEIGHT endpoint.";
                case 3: return "From the origin, mark the DEPTH endpoint.";
                default: return "Box measured. Mark again to start a new box.";
            }
        }
        if (points.isEmpty()) return "Aim at the first endpoint and tap MARK POINT.";
        if (points.size() == 1) return "Aim at the opposite endpoint and tap MARK POINT.";
        return labelForMode() + " measured. Mark again to start a new measurement.";
    }

    public String resultText() {
        if (!isComplete()) {
            if (points.isEmpty()) return labelForMode() + ": —";
            return labelForMode() + ": point " + points.size() + "/" + getRequiredPointCount();
        }

        if (mode == Mode.BOX) {
            double w = distance(points.get(0), points.get(1));
            double h = distance(points.get(0), points.get(2));
            double d = distance(points.get(0), points.get(3));
            double volume = w * h * d;
            return "W " + formatDistance(w) + "  H " + formatDistance(h) + "  D " + formatDistance(d)
                    + "\nVolume " + formatVolume(volume);
        }

        double value = distance(points.get(0), points.get(1));
        return labelForMode() + ": " + formatDistance(value);
    }

    private String labelForMode() {
        switch (mode) {
            case HEIGHT: return "Height";
            case DEPTH: return "Depth";
            case BOX: return "Box 3D";
            default: return "Width";
        }
    }

    private static double distance(Point3 a, Point3 b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private String formatDistance(double metres) {
        Unit effective = unit;
        if (effective == Unit.AUTO) {
            if (metres < 0.10) effective = Unit.MM;
            else if (metres < 1.0) effective = Unit.CM;
            else effective = Unit.M;
        }

        switch (effective) {
            case MM: return String.format(Locale.US, "%.0f mm", metres * 1000.0);
            case CM: return String.format(Locale.US, "%.1f cm", metres * 100.0);
            default: return String.format(Locale.US, "%.3f m", metres);
        }
    }

    private String formatVolume(double cubicMetres) {
        if (cubicMetres < 0.001) return String.format(Locale.US, "%.0f cm³", cubicMetres * 1_000_000.0);
        return String.format(Locale.US, "%.4f m³", cubicMetres);
    }
}
