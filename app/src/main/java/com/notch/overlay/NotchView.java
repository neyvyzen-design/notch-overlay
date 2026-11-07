package com.notch.overlay;

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

final class NotchView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float cx, cy, dotR, glowR;
    private float kedip = 1f;
    private boolean dipakai = false;
    private ValueAnimator animKedip;

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

        int hijau = 0xFF30D158;
        dotPaint.setColor(hijau);
        dotPaint.setStyle(Paint.Style.FILL);
        int rgb = hijau & 0x00FFFFFF;
        glowPaint.setShader(new RadialGradient(0f, 0f, glowR,
                new int[]{(150 << 24) | rgb, rgb},
                new float[]{0.2f, 1f}, Shader.TileMode.CLAMP));
    }

    void setSedangDipakai(boolean b) {
        if (b == dipakai) return;
        dipakai = b;
        if (animKedip != null) {
            animKedip.cancel();
            animKedip = null;
        }
        if (b) {
            animKedip = ValueAnimator.ofFloat(1f, 0.15f);
            animKedip.setDuration(550);
            animKedip.setRepeatCount(ValueAnimator.INFINITE);
            animKedip.setRepeatMode(ValueAnimator.REVERSE);
            animKedip.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    kedip = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            animKedip.start();
        } else {
            kedip = 1f;
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animKedip != null) animKedip.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(path, paint);
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
