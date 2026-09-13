package za.co.petern.weighttrend;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.datatypes.WeightRecord;
import android.os.OutcomeReceiver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class DataRepository {
    private final Context context;
    private final Executor healthExecutor = Executors.newSingleThreadExecutor();

    public DataRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<WeightEntry> loadLibraHistory() throws IOException {
        List<WeightEntry> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                context.getAssets().open("libra_history.csv"), StandardCharsets.UTF_8))) {
            String line;
            boolean inData = false;
            while ((line = br.readLine()) != null) {
                if (!inData) {
                    if (line.startsWith("#date;")) inData = true;
                    continue;
                }
                if (line.isBlank()) continue;
                String[] p = line.split(";", -1);
                if (p.length < 2) continue;
                try {
                    Instant time = Instant.parse(p[0].trim());
                    double kg = Double.parseDouble(p[1].trim());
                    Double trend = null;
                    if (p.length > 2 && !p[2].isBlank()) trend = Double.parseDouble(p[2].trim());
                    if (kg > 0 && kg < 500 && isMorningMeasurement(time, ZoneId.systemDefault())) {
                        out.add(new WeightEntry(time, kg, "Libra import", trend));
                    }
                } catch (RuntimeException ignored) {
                    // Skip malformed rows rather than failing the whole import.
                }
            }
        }
        out.sort(Comparator.comparing(e -> e.time));
        return out;
    }

    public boolean isHealthConnectAvailable() {
        return context.getSystemService(HealthConnectManager.class) != null;
    }

    public void loadHealthWeights(Consumer<List<WeightEntry>> onSuccess, Consumer<Throwable> onError) {
        HealthConnectManager manager = context.getSystemService(HealthConnectManager.class);
        if (manager == null) {
            onError.accept(new IllegalStateException("Health Connect is not available on this device."));
            return;
        }
        readPage(manager, -1L, new ArrayList<>(), onSuccess, onError);
    }

    private void readPage(
            HealthConnectManager manager,
            long pageToken,
            List<WeightEntry> accumulator,
            Consumer<List<WeightEntry>> onSuccess,
            Consumer<Throwable> onError) {

        ReadRecordsRequestUsingFilters.Builder<WeightRecord> builder =
                new ReadRecordsRequestUsingFilters.Builder<>(WeightRecord.class)
                        .setPageSize(5000);
        if (pageToken >= 0) builder.setPageToken(pageToken);
        else builder.setAscending(true);

        manager.readRecords(builder.build(), healthExecutor,
                new OutcomeReceiver<ReadRecordsResponse<WeightRecord>, HealthConnectException>() {
                    @Override
                    public void onResult(ReadRecordsResponse<WeightRecord> result) {
                        for (WeightRecord r : result.getRecords()) {
                            double kg = r.getWeight().getInGrams() / 1000.0;
                            String pkg = r.getMetadata().getDataOrigin().getPackageName();
                            String label = appLabel(pkg);
                            if (isMorningMeasurement(r.getTime(), ZoneId.systemDefault())) {
                                accumulator.add(new WeightEntry(r.getTime(), kg, label, null));
                            }
                        }
                        long next = result.getNextPageToken();
                        if (next >= 0) readPage(manager, next, accumulator, onSuccess, onError);
                        else {
                            accumulator.sort(Comparator.comparing(e -> e.time));
                            onSuccess.accept(accumulator);
                        }
                    }

                    @Override
                    public void onError(HealthConnectException error) {
                        onError.accept(error);
                    }
                });
    }

    /** Keep only morning weigh-ins from 04:00 inclusive to 09:00 exclusive in the device local time zone. */
    public static boolean isMorningMeasurement(Instant time, ZoneId zone) {
        if (time == null) return false;
        ZoneId effectiveZone = zone == null ? ZoneId.systemDefault() : zone;
        LocalTime localTime = time.atZone(effectiveZone).toLocalTime();
        return !localTime.isBefore(LocalTime.of(4, 0)) && localTime.isBefore(LocalTime.of(9, 0));
    }

    private String appLabel(String packageName) {
        if (packageName == null || packageName.isBlank()) return "Health Connect";
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            if (label != null && !label.toString().isBlank()) return label.toString();
        } catch (PackageManager.NameNotFoundException ignored) {}
        return packageName;
    }

    public static List<WeightEntry> merge(List<WeightEntry> libra, List<WeightEntry> health, ZoneId zone) {
        List<WeightEntry> out = new ArrayList<>(libra == null ? Collections.emptyList() : libra);
        if (health != null) {
            for (WeightEntry h : health) {
                LocalDate hd = h.time.atZone(zone).toLocalDate();
                for (int i = out.size() - 1; i >= 0; i--) {
                    WeightEntry x = out.get(i);
                    if (!"Libra import".equals(x.source)) continue;
                    LocalDate xd = x.time.atZone(zone).toLocalDate();
                    if (xd.isBefore(hd.minusDays(1))) break;
                    if (xd.equals(hd) && Math.abs(x.kilograms - h.kilograms) < 0.011) {
                        out.remove(i);
                        break;
                    }
                }
                out.add(h);
            }
        }
        out.sort(Comparator.comparing(e -> e.time));
        return out;
    }
}
