package org.telegram.ui.Components;

import android.graphics.*;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import android.graphics.Rect;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ProfileActivity;

import java.util.Arrays;

public class ProfileTransitionAvatarBlurHelper {

    private static final float CPU_SCALE_FACTOR = 20f;
    private static final float GPU_SCALE_FACTOR = 2f;
    private static final int DEFAULT_BLUR_RADIUS = 40;

    private final View view;
    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint colorPaint = new Paint();

    private Impl impl;
    private float progress = 0f;
    private boolean enabled = false;

    public ProfileTransitionAvatarBlurHelper(View view) {
        this.view = view;
        setupColorFilter();
        initializeImpl();
    }

    private void setupColorFilter() {
        ColorMatrix matrix = new ColorMatrix();
        // matrix.setSaturation(2f); // optionally uncomment
        colorPaint.setColorFilter(new ColorMatrixColorFilter(matrix));
    }

    private void initializeImpl() {
        int performance = SharedConfig.getDevicePerformanceClass();
        boolean isHighPerformance = performance >= SharedConfig.PERFORMANCE_CLASS_HIGH;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && performance >= SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
            impl = new GPUBlurImpl(performance == SharedConfig.PERFORMANCE_CLASS_HIGH ? 1f : 2f);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isHighPerformance) {
            impl = new CPUBlurImpl(false);
        } else {
            impl = new CPUBlurImpl(true);
        }

        impl.setBlurRadius(DEFAULT_BLUR_RADIUS);
    }

    public void setBlurEnabled(boolean enabled) {
        this.enabled = enabled;
        view.invalidate();
    }

    public void setProgress(float progress) {
        this.progress = progress;
        onSizeChanged(view.getWidth(), view.getHeight());
        view.invalidate();
    }

    public void draw(Drawer drawer, @NonNull Canvas canvas) {
        if (!enabled) {
            drawer.draw(canvas);
        } else {
            impl.draw(drawer, canvas);
        }
    }

    public void invalidate() {
        impl.invalidateBlur();
    }

    public void onDetached() {
        impl.release();
    }

    protected float getBottomOffset() {
        return view.getHeight() - getBottomExtra() * progress;
    }

    protected float getScaleY() {
        return 1f;
    }

    private int getBottomExtra() {
        return (int) (AndroidUtilities.dp(ProfileActivity.BUTTONS_FADE_DP + ProfileActivity.PROFILE_BUTTONS_DP + ProfileActivity.BUTTONS_MARGIN_DP * 2) / getScaleY());
    }

    public void onSizeChanged(int w, int h) {
        impl.onSizeChanged(w, h);
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        fadePaint.setShader(new LinearGradient(
                w / 2f,
                AndroidUtilities.dp(ProfileActivity.BUTTONS_FADE_DP) / getScaleY(),
                w / 2f,
                0,
                new int[]{0x00FFFFFF, 0xFFFFFFFF},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        ));
        view.invalidate();
    }

    private final class CPUBlurImpl implements Impl {
        private final boolean oneShot;
        private boolean drawn;
        private float blurRadius;
        private float scaleFactor;

        private final Bitmap[] bitmaps = new Bitmap[1];
        private final Canvas[] bitmapCanvases = new Canvas[1];
        private final Paint bitmapPaint = new Paint(Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Rect visibleRect = new Rect();

        CPUBlurImpl(boolean oneShot) {
            this.oneShot = oneShot;
        }

        @Override
        public void setBlurRadius(float radius) {
            this.blurRadius = radius;
        }

        @Override
        public void onSizeChanged(int w, int h) {
            scaleFactor = CPU_SCALE_FACTOR / getScaleY();
            drawn = false;

            int dw = (int) Math.ceil(w / scaleFactor);
            int dh = (int) Math.ceil(getBottomExtra() / scaleFactor);

            if (bitmaps[0] != null && bitmaps[0].getWidth() >= dw && bitmaps[0].getHeight() >= dh) return;

            for (Bitmap bm : bitmaps) {
                if (bm != null) bm.recycle();
            }

            bitmaps[0] = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888);
            bitmapCanvases[0] = new Canvas(bitmaps[0]);
        }

        @Override
        public void draw(Drawer drawer, Canvas canvas) {
            if (bitmaps[0] == null) onSizeChanged(view.getWidth(), view.getHeight());

            if (!oneShot || !drawn) {
                bitmaps[0].eraseColor(0);
                bitmapCanvases[0].save();
                bitmapCanvases[0].scale(1f / scaleFactor, 1f / scaleFactor);
                bitmapCanvases[0].translate(0, getBottomExtra() * progress - getBottomOffset());
                drawer.draw(bitmapCanvases[0]);
                bitmapCanvases[0].restore();

                Utilities.stackBlurBitmap(bitmaps[0], (int) (blurRadius / 8));
                DisplayMetrics metrics = view.getResources().getDisplayMetrics();
                view.getLocalVisibleRect(visibleRect);

                if (visibleRect.right > 0 && visibleRect.left < metrics.widthPixels) {
                    drawn = true;
                }
            }

            // Draw blurred layer
            AndroidUtilities.rectTmp.set(0, getBottomOffset(), view.getWidth(), view.getHeight());
            canvas.saveLayer(AndroidUtilities.rectTmp, colorPaint, Canvas.ALL_SAVE_FLAG);
            canvas.translate(0, getBottomOffset());
            canvas.scale(scaleFactor, scaleFactor);
            canvas.drawBitmap(bitmaps[0], 0, 0, bitmapPaint);
            canvas.restore();

            // Draw original + fade
            float fadeStart = getBottomOffset() + AndroidUtilities.dp(ProfileActivity.BUTTONS_FADE_DP) / getScaleY() * (1f - progress);
            AndroidUtilities.rectTmp.set(0, 0, view.getWidth(), AndroidUtilities.lerp(fadeStart - 1, view.getHeight(), 1f - progress));

            canvas.saveLayer(AndroidUtilities.rectTmp, null, Canvas.ALL_SAVE_FLAG);
            drawer.draw(canvas);
            canvas.translate(0, fadeStart);
            canvas.drawRect(0, 0, view.getWidth(), AndroidUtilities.dp(ProfileActivity.BUTTONS_FADE_DP) / getScaleY() + 1, fadePaint);
            canvas.restore();
        }

        @Override
        public void invalidateBlur() {
            drawn = false;
        }

        @Override
        public void release() {
            for (Bitmap bm : bitmaps) {
                if (bm != null) bm.recycle();
            }
            Arrays.fill(bitmaps, null);
            Arrays.fill(bitmapCanvases, null);
            drawn = false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private final class GPUBlurImpl implements Impl {
        private final RenderNode node = new RenderNode("render");
        private final RenderNode blur = new RenderNode("blur");
        private final float factorMult;

        private float scaleFactor;

        GPUBlurImpl(float factorMult) {
            this.factorMult = factorMult;
        }

        @Override
        public void setBlurRadius(float radius) {
            blur.setRenderEffect(RenderEffect.createBlurEffect(radius / factorMult, radius / factorMult, Shader.TileMode.CLAMP));
        }

        @Override
        public void onSizeChanged(int w, int h) {
            scaleFactor = GPU_SCALE_FACTOR * factorMult / getScaleY();
            node.setPosition(0, 0, w, h);
            blur.setPosition(0, 0, (int) Math.ceil(w / scaleFactor), (int) Math.ceil(getBottomExtra() / scaleFactor));
        }

        @Override
        public void draw(Drawer drawer, Canvas canvas) {
            drawer.draw(node.beginRecording());
            node.endRecording();

            float y = getBottomOffset();
            Canvas c = blur.beginRecording();
            c.scale(1f / scaleFactor, 1f / scaleFactor);
            c.translate(0, getBottomExtra() * progress - y);
            c.drawRenderNode(node);
            blur.endRecording();

            canvas.saveLayer(0, 0, view.getWidth(), view.getHeight(), colorPaint);
            canvas.translate(0, y);
            canvas.scale(scaleFactor, scaleFactor);
            canvas.drawRenderNode(blur);
            canvas.restore();

            float fadeStart = y + AndroidUtilities.dp(ProfileActivity.BUTTONS_FADE_DP) / getScaleY() * (1f - progress);
            canvas.saveLayer(0, 0, view.getWidth(), AndroidUtilities.lerp(fadeStart - 1, view.getHeight(), 1f - progress), null);
            canvas.drawRenderNode(node);
            canvas.translate(0, fadeStart);
            canvas.drawRect(0, 0, view.getWidth(), AndroidUtilities.dp(ProfileActivity.BUTTONS_FADE_DP) / getScaleY() + 1, fadePaint);
            canvas.restore();
        }
    }

    private interface Impl {
        void setBlurRadius(float radius);
        void onSizeChanged(int w, int h);
        void draw(Drawer drawer, Canvas canvas);
        default void invalidateBlur() {}
        default void release() {}
    }

    public interface Drawer {
        void draw(Canvas canvas);
    }
}
