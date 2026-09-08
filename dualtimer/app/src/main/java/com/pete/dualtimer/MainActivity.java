package com.pete.dualtimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TimerController[] timers = new TimerController[2];
    private SharedPreferences prefs;
    private ToneGenerator toneGenerator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("dual_timer_presets", MODE_PRIVATE);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(6), dp(10), dp(8));
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        TextView heading = new TextView(this);
        heading.setText("Dual Timer");
        heading.setTextSize(24);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(Color.rgb(25, 25, 25));
        heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        headingLp.setMargins(0, 0, 0, dp(5));
        root.addView(heading, headingLp);

        for (int i = 0; i < timers.length; i++) {
            timers[i] = new TimerController(i, savedInstanceState);
            LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            panelLp.setMargins(0, 0, 0, i == 0 ? dp(6) : 0);
            root.addView(timers[i].panel, panelLp);
        }

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (TimerController timer : timers) {
            if (timer != null) timer.refreshAfterResume();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        for (TimerController timer : timers) {
            if (timer != null) timer.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        for (TimerController timer : timers) {
            if (timer != null) timer.stopTicker();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        super.onDestroy();
    }

    private void alarmFinished() {
        if (toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450);
            handler.postDelayed(() -> {
                if (toneGenerator != null) {
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450);
                }
            }, 650);
            handler.postDelayed(() -> {
                if (toneGenerator != null) {
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 650);
                }
            }, 1300);
        }

        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = new long[]{0, 250, 150, 250, 150, 450};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                //noinspection deprecation
                vibrator.vibrate(pattern, -1);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundedBackground(int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radiusDp));
        return bg;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable bg = roundedBackground(Color.WHITE, 16);
        bg.setStroke(dp(1), Color.rgb(220, 220, 220));
        return bg;
    }

    private StateListDrawable actionBackground(int normal, int pressed, int disabled) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, roundedBackground(disabled, 8));
        states.addState(new int[]{android.R.attr.state_pressed}, roundedBackground(pressed, 8));
        states.addState(new int[]{}, roundedBackground(normal, 8));
        return states;
    }

    private class TimerController {
        private final int index;
        private final String statePrefix;
        private final LinearLayout panel;
        private final TextView display;
        private final TextView status;
        private final Button[] presetButtons = new Button[4];
        private final Button editButton;
        private final Button startButton;
        private final Button pauseButton;
        private final Button resetButton;
        private final long[] presets = new long[4];

        private int selectedPreset = 0;
        private long selectedMs;
        private long remainingMs;
        private long endElapsed;
        private boolean running;
        private boolean finishedAlarmPlayed;

        private final Runnable tickRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                updateRemainingFromClock();
                updateDisplay();
                if (remainingMs <= 0) {
                    running = false;
                    status.setText("Finished");
                    startButton.setText("Start");
                    updateControls();
                    if (!finishedAlarmPlayed) {
                        finishedAlarmPlayed = true;
                        alarmFinished();
                    }
                    return;
                }
                handler.postDelayed(this, 200);
            }
        };

        TimerController(int index, Bundle state) {
            this.index = index;
            this.statePrefix = "timer" + index + "_";

            long[] defaults = new long[]{
                    5 * 60_000L,
                    10 * 60_000L,
                    15 * 60_000L,
                    30 * 60_000L
            };
            for (int i = 0; i < presets.length; i++) {
                presets[i] = prefs.getLong(statePrefix + "preset" + i, defaults[i]);
            }

            selectedMs = presets[0];
            remainingMs = selectedMs;

            if (state != null) {
                selectedPreset = state.getInt(statePrefix + "selectedPreset", 0);
                selectedPreset = Math.max(0, Math.min(3, selectedPreset));
                selectedMs = state.getLong(statePrefix + "selectedMs", presets[selectedPreset]);
                remainingMs = state.getLong(statePrefix + "remainingMs", selectedMs);
                endElapsed = state.getLong(statePrefix + "endElapsed", 0L);
                running = state.getBoolean(statePrefix + "running", false);
                finishedAlarmPlayed = state.getBoolean(statePrefix + "finishedAlarmPlayed", false);
            }

            panel = new LinearLayout(MainActivity.this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(10), dp(8), dp(10), dp(8));
            panel.setBackground(cardBackground());

            LinearLayout header = new LinearLayout(MainActivity.this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(MainActivity.this);
            title.setText("Timer " + (index + 1));
            title.setTextSize(19);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setTextColor(Color.rgb(30, 30, 30));
            header.addView(title, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            status = new TextView(MainActivity.this);
            status.setText(running ? "Running" : "Ready");
            status.setTextSize(13);
            status.setTextColor(Color.rgb(90, 90, 90));
            header.addView(status);
            panel.addView(header);

            display = new TextView(MainActivity.this);
            display.setTextSize(38);
            display.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            display.setTextColor(Color.rgb(20, 20, 20));
            display.setGravity(Gravity.CENTER);
            display.setPadding(0, dp(2), 0, dp(3));
            panel.addView(display, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

            LinearLayout presetHeader = new LinearLayout(MainActivity.this);
            presetHeader.setOrientation(LinearLayout.HORIZONTAL);
            presetHeader.setGravity(Gravity.CENTER_VERTICAL);

            TextView presetTitle = new TextView(MainActivity.this);
            presetTitle.setText("Presets");
            presetTitle.setTextSize(14);
            presetTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            presetTitle.setTextColor(Color.rgb(50, 50, 50));
            presetHeader.addView(presetTitle, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            editButton = new Button(MainActivity.this);
            editButton.setText("Edit");
            editButton.setAllCaps(false);
            editButton.setTextSize(12);
            editButton.setMinHeight(0);
            editButton.setMinWidth(0);
            editButton.setPadding(dp(8), 0, dp(8), 0);
            editButton.setOnClickListener(v -> showEditPresetsDialog());
            presetHeader.addView(editButton, new LinearLayout.LayoutParams(dp(64), dp(36)));

            panel.addView(presetHeader, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

            for (int row = 0; row < 2; row++) {
                LinearLayout rowLayout = new LinearLayout(MainActivity.this);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);

                for (int col = 0; col < 2; col++) {
                    int presetIndex = row * 2 + col;
                    Button button = new Button(MainActivity.this);
                    button.setAllCaps(false);
                    button.setTextSize(13);
                    button.setMinHeight(0);
                    button.setMinWidth(0);
                    button.setPadding(dp(4), 0, dp(4), 0);
                    button.setOnClickListener(v -> selectPreset(presetIndex));
                    presetButtons[presetIndex] = button;

                    LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                            0, dp(45), 1f);
                    if (col == 0) {
                        buttonLp.setMargins(0, 0, dp(3), dp(3));
                    } else {
                        buttonLp.setMargins(dp(3), 0, 0, dp(3));
                    }
                    rowLayout.addView(button, buttonLp);
                }

                panel.addView(rowLayout, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            }

            LinearLayout controls = new LinearLayout(MainActivity.this);
            controls.setOrientation(LinearLayout.HORIZONTAL);

            startButton = new Button(MainActivity.this);
            startButton.setText("Start");
            startButton.setAllCaps(false);
            startButton.setTextSize(13);
            startButton.setTextColor(Color.WHITE);
            startButton.setMinHeight(0);
            startButton.setMinWidth(0);
            startButton.setBackground(actionBackground(
                    Color.rgb(46, 125, 50),
                    Color.rgb(27, 94, 32),
                    Color.rgb(165, 214, 167)));
            startButton.setOnClickListener(v -> start());

            pauseButton = new Button(MainActivity.this);
            pauseButton.setText("Pause");
            pauseButton.setAllCaps(false);
            pauseButton.setTextSize(13);
            pauseButton.setMinHeight(0);
            pauseButton.setMinWidth(0);
            pauseButton.setOnClickListener(v -> pause());

            resetButton = new Button(MainActivity.this);
            resetButton.setText("Reset");
            resetButton.setAllCaps(false);
            resetButton.setTextSize(13);
            resetButton.setTextColor(Color.WHITE);
            resetButton.setMinHeight(0);
            resetButton.setMinWidth(0);
            resetButton.setBackground(actionBackground(
                    Color.rgb(198, 40, 40),
                    Color.rgb(183, 28, 28),
                    Color.rgb(239, 154, 154)));
            resetButton.setOnClickListener(v -> reset());

            controls.addView(startButton, controlParams(true, false));
            controls.addView(pauseButton, controlParams(true, true));
            controls.addView(resetButton, controlParams(false, true));

            LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            controlsLp.setMargins(0, dp(2), 0, 0);
            panel.addView(controls, controlsLp);

            refreshPresetButtons();
            if (running) updateRemainingFromClock();
            updateDisplay();
            updateControls();
            if (running && remainingMs > 0) {
                scheduleTicker();
            } else if (running) {
                finishWithoutTicker();
            }
        }

        private LinearLayout.LayoutParams controlParams(boolean rightMargin, boolean leftMargin) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.setMargins(leftMargin ? dp(2) : 0, 0, rightMargin ? dp(2) : 0, 0);
            return lp;
        }

        private void selectPreset(int presetIndex) {
            if (running) return;
            selectedPreset = presetIndex;
            selectedMs = presets[presetIndex];
            remainingMs = selectedMs;
            finishedAlarmPlayed = false;
            status.setText("Ready");
            startButton.setText("Start");
            refreshPresetButtons();
            updateDisplay();
        }

        private void start() {
            if (running) return;
            if (remainingMs <= 0) remainingMs = selectedMs;
            if (remainingMs <= 0) return;

            finishedAlarmPlayed = false;
            running = true;
            endElapsed = SystemClock.elapsedRealtime() + remainingMs;
            status.setText("Running");
            startButton.setText("Running");
            updateControls();
            scheduleTicker();
        }

        private void pause() {
            if (!running) return;
            updateRemainingFromClock();
            running = false;
            stopTicker();
            status.setText("Paused");
            startButton.setText("Resume");
            updateDisplay();
            updateControls();
        }

        private void reset() {
            running = false;
            stopTicker();
            remainingMs = selectedMs;
            finishedAlarmPlayed = false;
            status.setText("Ready");
            startButton.setText("Start");
            updateDisplay();
            updateControls();
        }

        private void scheduleTicker() {
            stopTicker();
            handler.post(tickRunnable);
        }

        private void stopTicker() {
            handler.removeCallbacks(tickRunnable);
        }

        private void updateRemainingFromClock() {
            if (running) {
                remainingMs = Math.max(0L, endElapsed - SystemClock.elapsedRealtime());
            }
        }

        private void finishWithoutTicker() {
            running = false;
            remainingMs = 0;
            status.setText("Finished");
            startButton.setText("Start");
            updateDisplay();
            updateControls();
        }

        private void refreshAfterResume() {
            if (!running) return;
            updateRemainingFromClock();
            if (remainingMs <= 0) {
                running = false;
                stopTicker();
                status.setText("Finished");
                startButton.setText("Start");
                updateDisplay();
                updateControls();
                if (!finishedAlarmPlayed) {
                    finishedAlarmPlayed = true;
                    alarmFinished();
                }
            } else {
                scheduleTicker();
            }
        }

        private void updateControls() {
            startButton.setEnabled(!running);
            pauseButton.setEnabled(running);
            editButton.setEnabled(!running);
            for (Button button : presetButtons) {
                button.setEnabled(!running);
            }
        }

        private void updateDisplay() {
            display.setText(formatDuration(remainingMs, true));
        }

        private void refreshPresetButtons() {
            for (int i = 0; i < presetButtons.length; i++) {
                String marker = i == selectedPreset ? "✓ " : "";
                presetButtons[i].setText(
                        marker + "P" + (i + 1) + "\n" + formatDuration(presets[i], false));
            }
        }

        private void showEditPresetsDialog() {
            LinearLayout form = new LinearLayout(MainActivity.this);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(dp(18), dp(4), dp(18), 0);

            EditText[] fields = new EditText[4];
            for (int i = 0; i < 4; i++) {
                TextView label = new TextView(MainActivity.this);
                label.setText("Preset " + (i + 1) + "  (minutes, MM:SS, or HH:MM:SS)");
                label.setTextSize(13);
                label.setTextColor(Color.rgb(60, 60, 60));
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) labelLp.setMargins(0, dp(5), 0, 0);
                form.addView(label, labelLp);

                EditText field = new EditText(MainActivity.this);
                field.setSingleLine(true);
                field.setText(formatEditableDuration(presets[i]));
                field.setSelectAllOnFocus(true);
                field.setInputType(InputType.TYPE_CLASS_TEXT);
                fields[i] = field;
                form.addView(field, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
            }

            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Timer " + (index + 1) + " presets")
                    .setView(form)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", null)
                    .create();

            dialog.setOnShowListener(ignored ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                        long[] newValues = new long[4];
                        for (int i = 0; i < fields.length; i++) {
                            Long parsed = parseDuration(fields[i].getText().toString());
                            if (parsed == null || parsed <= 0) {
                                Toast.makeText(
                                        MainActivity.this,
                                        "Preset " + (i + 1) + " is not a valid time.",
                                        Toast.LENGTH_SHORT).show();
                                fields[i].requestFocus();
                                return;
                            }
                            newValues[i] = parsed;
                        }

                        SharedPreferences.Editor editor = prefs.edit();
                        for (int i = 0; i < newValues.length; i++) {
                            presets[i] = newValues[i];
                            editor.putLong(statePrefix + "preset" + i, presets[i]);
                        }
                        editor.apply();

                        selectedMs = presets[selectedPreset];
                        if (!running) remainingMs = selectedMs;
                        refreshPresetButtons();
                        updateDisplay();
                        status.setText("Ready");
                        startButton.setText("Start");
                        dialog.dismiss();
                    }));
            dialog.show();
        }

        private void saveState(Bundle out) {
            if (running) updateRemainingFromClock();
            out.putInt(statePrefix + "selectedPreset", selectedPreset);
            out.putLong(statePrefix + "selectedMs", selectedMs);
            out.putLong(statePrefix + "remainingMs", remainingMs);
            out.putLong(statePrefix + "endElapsed", endElapsed);
            out.putBoolean(statePrefix + "running", running);
            out.putBoolean(statePrefix + "finishedAlarmPlayed", finishedAlarmPlayed);
        }
    }

    private String formatDuration(long millis, boolean alwaysHours) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (alwaysHours || hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private String formatEditableDuration(long millis) {
        long totalSeconds = Math.max(1L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        if (seconds > 0) {
            return String.format(Locale.US, "%d:%02d", minutes, seconds);
        }
        return String.valueOf(minutes);
    }

    private Long parseDuration(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;

        try {
            long totalSeconds;
            if (value.contains(":")) {
                String[] parts = value.split(":");
                if (parts.length == 2) {
                    long minutes = Long.parseLong(parts[0].trim());
                    long seconds = Long.parseLong(parts[1].trim());
                    if (minutes < 0 || seconds < 0 || seconds > 59) return null;
                    totalSeconds = minutes * 60L + seconds;
                } else if (parts.length == 3) {
                    long hours = Long.parseLong(parts[0].trim());
                    long minutes = Long.parseLong(parts[1].trim());
                    long seconds = Long.parseLong(parts[2].trim());
                    if (hours < 0 || hours > 99 ||
                            minutes < 0 || minutes > 59 ||
                            seconds < 0 || seconds > 59) {
                        return null;
                    }
                    totalSeconds = hours * 3600L + minutes * 60L + seconds;
                } else {
                    return null;
                }
            } else {
                long minutes = Long.parseLong(value);
                if (minutes < 0 || minutes > 5999) return null;
                totalSeconds = minutes * 60L;
            }

            if (totalSeconds <= 0 ||
                    totalSeconds > (99L * 3600L + 59L * 60L + 59L)) {
                return null;
            }
            return totalSeconds * 1000L;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
