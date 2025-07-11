package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Property;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ProfileActivity;

public class ProfileTransitionAvatarImageView extends BackupImageView {

    private static final String PROPERTY_CROSSFADE_PROGRESS = "crossfadeProgress";

    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final ImageReceiver foregroundImageReceiver = new ImageReceiver(this);
    private final ProfileTransitionAvatarBlurHelper blurHelper;

    private ProfileGalleryView avatarsViewPager;
    private ImageReceiver animateFromImageReceiver;
    private ImageReceiver.BitmapHolder drawableHolder;

    private boolean drawAvatar = true;
    private boolean drawForeground = true;
    private boolean hasStories;

    private float bounceScale = 1f;
    private float crossfadeProgress;
    private float foregroundAlpha;
    private float progressToExpand;
    private float progressToInsets = 1f;
    private float blurProgress;
    private float inset;
    private float radius;

    public ProfileTransitionAvatarImageView(Context context) {
        super(context);
        placeholderPaint.setColor(Color.BLACK);

        blurHelper = new ProfileTransitionAvatarBlurHelper(this) {
            @Override
            protected float getScaleY() {
                View parent = (View) getParent();
                return parent != null ? parent.getScaleY() : 1f;
            }

            @Override
            protected float getBottomOffset() {
                return getMeasuredHeight() - AndroidUtilities.dp(ProfileActivity.PROFILE_BUTTONS_DP + ProfileActivity.BUTTONS_MARGIN_DP * 2 + ProfileActivity.BUTTONS_FADE_DP)
                        / getScaleY() * blurProgress;
            }
        };
    }

    public static final Property<ProfileTransitionAvatarImageView, Float> CROSSFADE_PROGRESS =
            new AnimationProperties.FloatProperty<ProfileTransitionAvatarImageView>(PROPERTY_CROSSFADE_PROGRESS) {
                @Override
                public void setValue(ProfileTransitionAvatarImageView object, float value) {
                    object.setCrossfadeProgress(value);
                }

                @Override
                public Float get(ProfileTransitionAvatarImageView object) {
                    return object.crossfadeProgress;
                }
            };

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        blurHelper.onSizeChanged(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();

        inset = calculateInset();

        float scaleY = getParentScaleY();
        canvas.scale(bounceScale, bounceScale, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);

        path.rewind();
        float heightOffset = scaleY != 0 ? AndroidUtilities.dp(ProfileActivity.PROFILE_BUTTONS_DP + ProfileActivity.BUTTONS_MARGIN_DP * 2 + ProfileActivity.BUTTONS_FADE_DP) / scaleY * progressToExpand : 0;
        AndroidUtilities.rectTmp.set(inset, inset, getMeasuredWidth() - inset, getMeasuredHeight() - inset + heightOffset);
        path.addRoundRect(AndroidUtilities.rectTmp, radius, radius, Path.Direction.CW);
        canvas.clipPath(path);

        blurHelper.draw(this::drawAvatarLayer, canvas);
        canvas.restore();
    }

    private void drawAvatarLayer(Canvas canvas) {
        float alpha = 1.0f;
        float scaleY = getParentScaleY();
        int adjustedHeight = (int) (getMeasuredHeight() - AndroidUtilities.dp(ProfileActivity.PROFILE_BUTTONS_DP + ProfileActivity.BUTTONS_MARGIN_DP * 2) * blurProgress / scaleY);

        if (animateFromImageReceiver != null && crossfadeProgress > 0.0f) {
            alpha *= 1.0f - crossfadeProgress;
            drawAnimateFromImage(canvas, adjustedHeight);
        }

        if (imageReceiver != null && alpha > 0 && (foregroundAlpha < 1f || !drawForeground)) {
            drawMainAvatar(canvas, adjustedHeight, alpha);
        }

        if (foregroundAlpha > 0f && drawForeground && alpha > 0) {
            drawForegroundImage(canvas, adjustedHeight, alpha);
        }
    }

    private void drawAnimateFromImage(Canvas canvas, int height) {
        float fromAlpha = crossfadeProgress;
        float wasX = animateFromImageReceiver.getImageX();
        float wasY = animateFromImageReceiver.getImageY();
        float wasW = animateFromImageReceiver.getImageWidth();
        float wasH = animateFromImageReceiver.getImageHeight();
        float wasAlpha = animateFromImageReceiver.getAlpha();

        animateFromImageReceiver.setImageCoords(inset, inset, getMeasuredWidth() - inset * 2f, height - inset * 2f);
        animateFromImageReceiver.setAlpha(fromAlpha);
        animateFromImageReceiver.draw(canvas);
        animateFromImageReceiver.setImageCoords(wasX, wasY, wasW, wasH);
        animateFromImageReceiver.setAlpha(wasAlpha);
    }

    private void drawMainAvatar(Canvas canvas, int height, float alpha) {
        imageReceiver.setImageCoords(inset, inset, getMeasuredWidth() - inset * 2f, height - inset * 2f);
        float wasAlpha = imageReceiver.getAlpha();
        imageReceiver.setAlpha(wasAlpha * alpha);
        if (drawAvatar) {
            imageReceiver.draw(canvas);
        }
        imageReceiver.setAlpha(wasAlpha);
    }

    private void drawForegroundImage(Canvas canvas, int height, float alpha) {
        Drawable drawable = foregroundImageReceiver.getDrawable();
        if (drawable != null) {
            foregroundImageReceiver.setImageCoords(inset, inset, getMeasuredWidth() - inset * 2f, height - inset * 2f);
            foregroundImageReceiver.setAlpha(alpha * foregroundAlpha);
            foregroundImageReceiver.draw(canvas);
        } else {
            rect.set(0f, 0f, getMeasuredWidth(), getMeasuredHeight());
            placeholderPaint.setAlpha((int) (alpha * foregroundAlpha * 255f));
            canvas.drawRoundRect(rect, radius, radius, placeholderPaint);
        }
    }

    private float calculateInset() {
        float value = hasStories ? AndroidUtilities.dpf2(3.5f) : 0;
        return value * (1f - progressToExpand) * progressToInsets * (1f - foregroundAlpha);
    }

    private float getParentScaleY() {
        View parent = (View) getParent();
        return parent != null ? parent.getScaleY() : 1f;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (avatarsViewPager != null) {
            avatarsViewPager.invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        foregroundImageReceiver.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        blurHelper.onDetached();
        foregroundImageReceiver.onDetachedFromWindow();
        releaseDrawableHolder();
    }

    private void releaseDrawableHolder() {
        if (drawableHolder != null) {
            drawableHolder.release();
            drawableHolder = null;
        }
    }

    // Setters and utility methods

    public void setAnimateFromImageReceiver(ImageReceiver imageReceiver) {
        this.animateFromImageReceiver = imageReceiver;
    }

    public void setCrossfadeProgress(float progress) {
        this.crossfadeProgress = progress;
        invalidate();
    }

    public void setAvatarsViewPager(ProfileGalleryView avatarsViewPager) {
        this.avatarsViewPager = avatarsViewPager;
    }

    public void setHasStories(boolean hasStories) {
        if (this.hasStories != hasStories) {
            this.hasStories = hasStories;
            invalidate();
        }
    }

    public boolean isHasStories() {
        return hasStories;
    }

    public void setProgressToStoriesInsets(float value) {
        if (this.progressToInsets != value) {
            this.progressToInsets = value;
            invalidate();
        }
    }

    public void setProgressToExpand(float value) {
        if (this.progressToExpand != value) {
            this.progressToExpand = value;
            invalidate();
        }
    }

    public void setBlurEnabled(boolean enabled) {
        blurHelper.setBlurEnabled(enabled);
    }

    public void setBlurProgress(float value) {
        if (this.blurProgress != value) {
            this.blurProgress = value;
            blurHelper.setProgress(value);
        }
    }

    public void setRoundRadius(int value) {
        this.radius = value;
        invalidate();
    }

    public float getRadius() {
        return radius;
    }

    public void setForegroundImage(ImageLocation location, String filter, Drawable thumb) {
        foregroundImageReceiver.setImage(location, filter, thumb, 0, null, null, 0);
        releaseDrawableHolder();
    }

    public void setForegroundImageDrawable(ImageReceiver.BitmapHolder holder) {
        if (holder != null) {
            foregroundImageReceiver.setImageBitmap(holder.drawable);
        }
        releaseDrawableHolder();
        drawableHolder = holder;
    }

    public void clearForeground() {
        AnimatedFileDrawable drawable = foregroundImageReceiver.getAnimation();
        if (drawable != null) {
            drawable.removeSecondParentView(this);
        }
        foregroundImageReceiver.clearImage();
        releaseDrawableHolder();
        foregroundAlpha = 0f;
        invalidate();
    }

    public float getForegroundAlpha() {
        return foregroundAlpha;
    }

    public void setForegroundAlpha(float value) {
        this.foregroundAlpha = value;
        invalidate();
    }

    public void drawForeground(boolean draw) {
        this.drawForeground = draw;
    }

    public ChatActivityInterface getPrevFragment() {
        return null;
    }

    public void setBounceScale(float scale) {
        this.bounceScale = scale;
        invalidate();
    }

    public float getBounceScale() {
        return bounceScale;
    }

    public void setDrawAvatar(boolean drawAvatar) {
        this.drawAvatar = drawAvatar;
        invalidate();
    }

    public boolean isDrawAvatar() {
        return drawAvatar;
    }
}
