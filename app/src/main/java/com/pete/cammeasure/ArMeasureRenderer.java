package com.pete.cammeasure;

import android.app.Activity;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.google.ar.core.Camera;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class ArMeasureRenderer implements GLSurfaceView.Renderer {
    interface Listener {
        void onPointCaptured(MeasurementEngine.Point3 point, String source);
        void onPointCaptureFailed(String reason);
    }

    private final Activity activity;
    private final Listener listener;
    private final CameraBackgroundRenderer backgroundRenderer = new CameraBackgroundRenderer();
    private final AtomicBoolean captureRequested = new AtomicBoolean(false);

    private volatile Session session;
    private Session textureBoundSession;
    private int cameraTextureId;
    private int surfaceWidth;
    private int surfaceHeight;

    ArMeasureRenderer(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    void setSession(Session session) {
        this.session = session;
        textureBoundSession = null;
    }

    void requestCapture() {
        captureRequested.set(true);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        cameraTextureId = backgroundRenderer.createOnGlThread();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        Session currentSession = session;
        if (currentSession == null || surfaceWidth <= 0 || surfaceHeight <= 0) return;

        try {
            if (textureBoundSession != currentSession) {
                currentSession.setCameraTextureName(cameraTextureId);
                textureBoundSession = currentSession;
            }

            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            currentSession.setDisplayGeometry(rotation, surfaceWidth, surfaceHeight);

            Frame frame = currentSession.update();
            Camera camera = frame.getCamera();
            backgroundRenderer.draw(frame);

            if (captureRequested.compareAndSet(true, false)) {
                if (camera.getTrackingState() != TrackingState.TRACKING) {
                    listener.onPointCaptureFailed("Tracking is not ready. Move the phone slowly and try again.");
                    return;
                }
                captureAtCentre(frame);
            }
        } catch (Exception e) {
            if (captureRequested.compareAndSet(true, false)) {
                listener.onPointCaptureFailed("AR error: " + e.getMessage());
            }
        }
    }

    private void captureAtCentre(Frame frame) {
        float x = surfaceWidth / 2f;
        float y = surfaceHeight / 2f;
        List<HitResult> hits = frame.hitTest(x, y);

        HitResult depthHit = null;
        HitResult planeHit = null;
        HitResult pointHit = null;

        for (HitResult hit : hits) {
            if (hit.getTrackable() instanceof DepthPoint) {
                depthHit = hit;
                break;
            }
            if (planeHit == null && hit.getTrackable() instanceof Plane) {
                Plane plane = (Plane) hit.getTrackable();
                if (plane.isPoseInPolygon(hit.getHitPose())) planeHit = hit;
            }
            if (pointHit == null && hit.getTrackable() instanceof Point) {
                pointHit = hit;
            }
        }

        HitResult chosen = depthHit != null ? depthHit : (planeHit != null ? planeHit : pointHit);
        if (chosen == null) {
            listener.onPointCaptureFailed("No surface found under the crosshair. Scan the area from another angle.");
            return;
        }

        Pose pose = chosen.getHitPose();
        float[] t = pose.getTranslation();
        String source = chosen.getTrackable() instanceof DepthPoint ? "Depth"
                : chosen.getTrackable() instanceof Plane ? "Plane" : "Feature point";
        listener.onPointCaptured(new MeasurementEngine.Point3(t[0], t[1], t[2]), source);
    }
}
