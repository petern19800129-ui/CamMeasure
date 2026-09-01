package com.pete.cammeasure;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

public class MainActivity extends Activity implements ArMeasureRenderer.Listener {
    private static final int CAMERA_PERMISSION_CODE = 1001;

    private GLSurfaceView arSurface;
    private ArMeasureRenderer renderer;
    private Session session;
    private boolean installRequested;
    private boolean depthSupported;

    private final MeasurementEngine engine = new MeasurementEngine();

    private TextView resultText;
    private TextView instructionText;
    private TextView depthStatusText;
    private Button widthButton;
    private Button heightButton;
    private Button depthButton;
    private Button boxButton;
    private Button markButton;
    private Button undoButton;
    private Button resetButton;
    private Button unitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultText = findViewById(R.id.resultText);
        instructionText = findViewById(R.id.instructionText);
        depthStatusText = findViewById(R.id.depthStatusText);
        widthButton = findViewById(R.id.widthButton);
        heightButton = findViewById(R.id.heightButton);
        depthButton = findViewById(R.id.depthButton);
        boxButton = findViewById(R.id.boxButton);
        markButton = findViewById(R.id.markButton);
        undoButton = findViewById(R.id.undoButton);
        resetButton = findViewById(R.id.resetButton);
        unitButton = findViewById(R.id.unitButton);

        arSurface = findViewById(R.id.arSurface);
        arSurface.setEGLContextClientVersion(2);
        arSurface.setPreserveEGLContextOnPause(true);
        arSurface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        renderer = new ArMeasureRenderer(this, this);
        arSurface.setRenderer(renderer);

        widthButton.setOnClickListener(v -> selectMode(MeasurementEngine.Mode.WIDTH));
        heightButton.setOnClickListener(v -> selectMode(MeasurementEngine.Mode.HEIGHT));
        depthButton.setOnClickListener(v -> selectMode(MeasurementEngine.Mode.DEPTH));
        boxButton.setOnClickListener(v -> selectMode(MeasurementEngine.Mode.BOX));
        markButton.setOnClickListener(v -> renderer.requestCapture());
        undoButton.setOnClickListener(v -> { engine.undo(); updateUi(); });
        resetButton.setOnClickListener(v -> { engine.reset(); updateUi(); });
        unitButton.setOnClickListener(v -> { engine.cycleUnit(); updateUi(); });

        selectMode(MeasurementEngine.Mode.WIDTH);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasCameraPermission()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            return;
        }
        startArSession();
    }

    private void startArSession() {
        try {
            if (session == null) {
                ArCoreApk.InstallStatus status = ArCoreApk.getInstance().requestInstall(this, !installRequested);
                if (status == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    installRequested = true;
                    return;
                }

                session = new Session(this);
                Config config = session.getConfig();
                config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
                depthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
                if (depthSupported) config.setDepthMode(Config.DepthMode.AUTOMATIC);
                session.configure(config);
                renderer.setSession(session);
            }

            session.resume();
            arSurface.onResume();
            depthStatusText.setText(depthSupported
                    ? "AR depth: ON — depth points preferred"
                    : "AR depth: unavailable — using planes/feature points");
        } catch (UnavailableArcoreNotInstalledException | UnavailableUserDeclinedInstallationException e) {
            showFatal("Google Play Services for AR is required.");
        } catch (UnavailableApkTooOldException e) {
            showFatal("Google Play Services for AR needs an update.");
        } catch (UnavailableSdkTooOldException e) {
            showFatal("This CamMeasure build needs to be updated.");
        } catch (UnavailableDeviceNotCompatibleException e) {
            showFatal("This device is not compatible with ARCore.");
        } catch (Exception e) {
            showFatal("Unable to start AR: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) {
            arSurface.onPause();
            session.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startArSession();
            } else {
                showFatal("Camera permission is required to measure objects.");
            }
        }
    }

    @Override
    public void onPointCaptured(MeasurementEngine.Point3 point, String source) {
        runOnUiThread(() -> {
            engine.addPoint(point);
            updateUi();
            Toast.makeText(this, "Point captured using " + source, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onPointCaptureFailed(String reason) {
        runOnUiThread(() -> Toast.makeText(this, reason, Toast.LENGTH_LONG).show());
    }

    private void selectMode(MeasurementEngine.Mode mode) {
        engine.setMode(mode);
        updateModeButtons();
        updateUi();
    }

    private void updateUi() {
        resultText.setText(engine.resultText());
        instructionText.setText(engine.instruction());
        unitButton.setText(engine.unitButtonLabel());
        undoButton.setEnabled(engine.getPointCount() > 0);
        resetButton.setEnabled(engine.getPointCount() > 0);
    }

    private void updateModeButtons() {
        widthButton.setAlpha(engine.getMode() == MeasurementEngine.Mode.WIDTH ? 1.0f : 0.65f);
        heightButton.setAlpha(engine.getMode() == MeasurementEngine.Mode.HEIGHT ? 1.0f : 0.65f);
        depthButton.setAlpha(engine.getMode() == MeasurementEngine.Mode.DEPTH ? 1.0f : 0.65f);
        boxButton.setAlpha(engine.getMode() == MeasurementEngine.Mode.BOX ? 1.0f : 0.65f);
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void showFatal(String message) {
        depthStatusText.setText(message);
        markButton.setEnabled(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
