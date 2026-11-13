package com.pete.dualtimer;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Locale;

public class WidgetConfigActivity extends Activity {
    private static final long[] DEFAULT_PRESETS = new long[]{
            5 * 60_000L,
            10 * 60_000L,
            15 * 60_000L,
            30 * 60_000L
    };

    private int appWidgetId =
            AppWidgetManager.INVALID_APPWIDGET_ID;
    private Spinner timerSpinner;
    private Spinner presetSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            appWidgetId = intent.getExtras().getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(22));

        TextView title = new TextView(this);
        title.setText("Preset Timer Widget");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText(
                "Choose which Dual Timer preset this widget should use. " +
                "Tap the home-screen widget to start or restart the timer.");
        hint.setTextSize(15);
        hint.setPadding(0, dp(8), 0, dp(18));
        root.addView(hint);

        TextView timerLabel = label("Preset source");
        root.addView(timerLabel);

        timerSpinner = new Spinner(this);
        ArrayAdapter<String> timerAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        new String[]{"Timer 1 presets", "Timer 2 presets"});
        timerAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        timerSpinner.setAdapter(timerAdapter);
        root.addView(
                timerSpinner,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(52)));

        TextView presetLabel = label("Preset");
        presetLabel.setPadding(0, dp(14), 0, 0);
        root.addView(presetLabel);

        presetSpinner = new Spinner(this);
        root.addView(
                presetSpinner,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(52)));

        Button addButton = new Button(this);
        addButton.setText("Add widget");
        addButton.setAllCaps(false);
        addButton.setTextSize(16);
        LinearLayout.LayoutParams buttonLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(54));
        buttonLp.setMargins(0, dp(22), 0, 0);
        root.addView(addButton, buttonLp);

        int savedTimer =
                PresetTimerWidgetProvider.getConfiguredTimer(
                        this,
                        appWidgetId);
        int savedPreset =
                PresetTimerWidgetProvider.getConfiguredPreset(
                        this,
                        appWidgetId);

        timerSpinner.setSelection(
                Math.max(0, Math.min(1, savedTimer)));
        refreshPresetSpinner(
                Math.max(0, Math.min(1, savedTimer)),
                Math.max(0, Math.min(3, savedPreset)));

        timerSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            android.view.View view,
                            int position,
                            long id) {
                        refreshPresetSpinner(position, 0);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        addButton.setOnClickListener(v -> saveAndFinish());

        setContentView(root);
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.START);
        return view;
    }

    private void refreshPresetSpinner(
            int timerIndex,
            int selectedPreset) {
        SharedPreferences prefs =
                getSharedPreferences(
                        "dual_timer_presets",
                        MODE_PRIVATE);

        String[] labels = new String[4];
        for (int i = 0; i < 4; i++) {
            long duration = prefs.getLong(
                    "timer" + timerIndex + "_preset" + i,
                    DEFAULT_PRESETS[i]);
            labels[i] =
                    "P" + (i + 1) + "  •  " +
                    formatDuration(duration);
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        labels);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);
        presetSpinner.setSelection(
                Math.max(0, Math.min(3, selectedPreset)));
    }

    private void saveAndFinish() {
        int timerIndex =
                Math.max(0, timerSpinner.getSelectedItemPosition());
        int presetIndex =
                Math.max(0, presetSpinner.getSelectedItemPosition());

        PresetTimerWidgetProvider.saveConfig(
                this,
                appWidgetId,
                timerIndex,
                presetIndex);

        AppWidgetManager manager =
                AppWidgetManager.getInstance(this);
        PresetTimerWidgetProvider.updateWidget(
                this,
                manager,
                appWidgetId);

        Intent result = new Intent();
        result.putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private String formatDuration(long millis) {
        long totalSeconds =
                Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes =
                (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0) {
            return String.format(
                    Locale.US,
                    "%d:%02d:%02d",
                    hours,
                    minutes,
                    seconds);
        }
        return String.format(
                Locale.US,
                "%02d:%02d",
                minutes,
                seconds);
    }

    private int dp(int value) {
        return Math.round(
                value *
                getResources()
                        .getDisplayMetrics()
                        .density);
    }
}
