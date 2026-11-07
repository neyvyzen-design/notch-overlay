package com.notch.overlay;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

final class NotchView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float cx, cy, dotR, glowR;

    private int warna = 0xFF30D158;
    private int warnaTarget = 0xFF30D158;
    private int glowWarna = 0;
    private float kedip = 1f;
    private int berkedipMs = 0;
    private ValueAnimator animWarna;
    private ValueAnimator animKedip;
    private ValueAnimator animKembali;

    NotchView(Context c, int w, int h, int e, int mode, float dotR, float dotPos) {
        super(c);
        this.dotR = dotR;
        this.glowR = dotR * 2.9f;

        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);

        float r = h * 0.6f;
        path.moveTo(0, 0);
        path.quadTo(e, 0, e, e);
        path.lineTo(e, h - r);
        path.quadTo(e, h, e + r, h);
        path.lineTo(e + w - r, h);
        path.quadTo(e + w, h, e + w, h - r);
        path.lineTo(e + w, e);
        path.quadTo(e + w, 0, e + w + e, 0);
        path.close();

        float[] pt = { e + w * dotPos, h * 0.5f };
        Matrix m = new Matrix();
        float panjang = w + 2f * e;
        if (mode == 1) {
            m.postRotate(-90);
            m.postTranslate(0, panjang);
        } else if (mode == 2) {
            m.postRotate(90);
            m.postTranslate(h, 0);
        }
        path.transform(m);
        m.mapPoints(pt);
        this.cx = pt[0];
        this.cy = pt[1];

        dotPaint.setStyle(Paint.Style.FILL);
    }

    // Atur warna titik dan apakah berkedip. animasi=false: langsung tanpa transisi.
    void setStatus(int warnaBaru, int blinkMs, boolean animasi) {
        if (warnaBaru != warnaTarget || !animasi) {
            warnaTarget = warnaBaru;
            if (animWarna != null) {
                animWarna.cancel();
                animWarna = null;
            }
            if (!animasi) {
                warna = warnaBaru;
                invalidate();
            } else {
                animWarna = ValueAnimator.ofObject(new ArgbEvaluator(),
                        Integer.valueOf(warna), Integer.valueOf(warnaBaru));
                animWarna.setDuration(450);
                animWarna.setInterpolator(new AccelerateDecelerateInterpolator());
                animWarna.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator a) {
                        warna = (Integer) a.getAnimatedValue();
                        invalidate();
                    }
                });
                animWarna.start();
            }
        }

        if (blinkMs != berkedipMs || !animasi) {
            berkedipMs = blinkMs;
            if (animKedip != null) {
                animKedip.cancel();
                animKedip = null;
            }
            if (animKembali != null) {
                animKembali.cancel();
                animKembali = null;
            }
            if (blinkMs > 0) {
                animKedip = ValueAnimator.ofFloat(1f, 0.15f);
                animKedip.setDuration(blinkMs);
                animKedip.setRepeatCount(ValueAnimator.INFINITE);
                animKedip.setRepeatMode(ValueAnimator.REVERSE);
                animKedip.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator a) {
                        kedip = (Float) a.getAnimatedValue();
                        invalidate();
                    }
                });
                animKedip.start();
            } else if (!animasi || kedip >= 0.99f) {
                kedip = 1f;
                invalidate();
            } else {
                animKembali = ValueAnimator.ofFloat(kedip, 1f);
                animKembali.setDuration(250);
                animKembali.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator a) {
                        kedip = (Float) a.getAnimatedValue();
                        invalidate();
                    }
                });
                animKembali.start();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animWarna != null) animWarna.cancel();
        if (animKedip != null) animKedip.cancel();
        if (animKembali != null) animKembali.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(path, paint);

        if (glowWarna != warna) {
            glowWarna = warna;
            int rgb = warna & 0x00FFFFFF;
            glowPaint.setShader(new RadialGradient(0f, 0f, glowR,
                    new int[]{(150 << 24) | rgb, rgb},
                    new float[]{0.2f, 1f}, Shader.TileMode.CLAMP));
        }
        dotPaint.setColor(warna | 0xFF000000);
        int al = (int) (255 * kedip);
        glowPaint.setAlpha(al);
        dotPaint.setAlpha(al);

        canvas.save();
        canvas.translate(cx, cy);
        canvas.drawCircle(0f, 0f, glowR, glowPaint);
        canvas.restore();
        canvas.drawCircle(cx, cy, dotR, dotPaint);
    }
}
