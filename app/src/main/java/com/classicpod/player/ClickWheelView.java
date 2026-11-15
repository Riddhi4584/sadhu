package com.classicpod.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public final class ClickWheelView extends View {
    public interface Callback {
        void onMenu();

        void onSelect();

        void onPlay();

        void onNext();

        void onPrevious();

        void onWheel(int steps);
    }

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint center = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pressGlow = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Dual-layer fluid liquid paints
    private final Paint wavePaintBack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaintFront = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hoverGlow = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Liquid Glass (Glassmorphism) Paints
    private final Paint glassGlossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassGlarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassDiscSheen = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF oval = new RectF();
    private final RectF glassGlossBounds = new RectF();
    private final Path wavePathFront = new Path();
    private final Path wavePathBack = new Path();
    private final Path clipPath = new Path();

    private Callback callback;
    private float lastAngle;
    private float accum;
    private float downX;
    private float downY;
    private boolean dragging;
    private int hint;

    // Hover & Dual-layer Liquid wave animation states
    private int hoverRegion = 0;
    private float hoverAlpha = 0f;
    private float wavePhase1 = 0f;
    private float wavePhase2 = 0f;
    private float currentAmpScale = 1.0f;
    private float targetAmpScale = 1.0f;

    public ClickWheelView(Context context) {
        super(context);
        init();
    }

    public ClickWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ClickWheelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0xFF9A9A9A);
        disc.setStyle(Paint.Style.FILL);
        disc.setColor(0xFF1A1A1A);
        center.setStyle(Paint.Style.FILL);
        center.setColor(0xFF2E2E2E);
        accent.setStyle(Paint.Style.STROKE);
        accent.setColor(0xFFCCCCCC);
        pressGlow.setStyle(Paint.Style.FILL);
        pressGlow.setColor(0x35FFFFFF);

        // Fluid liquid wave paints
        wavePaintBack.setStyle(Paint.Style.FILL);
        wavePaintBack.setColor(0x3000B0FF); // Deep fluid blue back wave

        wavePaintFront.setStyle(Paint.Style.FILL);
        wavePaintFront.setColor(0x5500E5FF); // Vibrant cyan front wave

        // Smooth hover glow paint
        hoverGlow.setStyle(Paint.Style.STROKE);
        hoverGlow.setColor(0x60FFFFFF);

        // Liquid Glass Paints
        glassGlossPaint.setStyle(Paint.Style.FILL);
        glassRimPaint.setStyle(Paint.Style.STROKE);
        glassRimPaint.setStrokeWidth(3f);
        glassRimPaint.setColor(0x75FFFFFF); // Glass refraction edge highlight

        glassGlarePaint.setStyle(Paint.Style.FILL);
        glassGlarePaint.setColor(0x88FFFFFF); // Specular glass glare

        glassDiscSheen.setStyle(Paint.Style.FILL);

        text.setColor(0xFFEDEDED);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.SANS_SERIF);
        setClickable(true);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f - 4f;
        float cr = r * 0.36f;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        // Draw main disc wheel
        canvas.drawCircle(cx, cy, r, disc);
        ring.setStrokeWidth(r * 0.08f);
        canvas.drawCircle(cx, cy, r - ring.getStrokeWidth() / 2f, ring);

        // Glass sheen over upper disc
        glassDiscSheen.setShader(new LinearGradient(cx, cy - r, cx, cy,
                0x20FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r - 2f, glassDiscSheen);

        // Draw center button disc
        canvas.drawCircle(cx, cy, cr, center);
        accent.setStrokeWidth(2f);
        canvas.drawCircle(cx, cy, cr, accent);

        // --- Ultra-Smooth Dual-Layer Liquid Wave Animation ---
        canvas.save();
        clipPath.reset();
        clipPath.addCircle(cx, cy, cr - 2f, Path.Direction.CW);
        canvas.clipPath(clipPath);

        // Smooth wave phase progression & dynamic amplitude easing
        wavePhase1 += 0.06f;
        wavePhase2 += 0.09f;
        if (wavePhase1 > 6.28318f) wavePhase1 -= 6.28318f;
        if (wavePhase2 > 6.28318f) wavePhase2 -= 6.28318f;

        currentAmpScale += (targetAmpScale - currentAmpScale) * 0.1f;
        if (targetAmpScale > 1.0f) {
            targetAmpScale *= 0.96f;
        }

        float baseLevelY = cy + cr * 0.12f;
        float amp1 = cr * 0.18f * currentAmpScale;
        float amp2 = cr * 0.14f * currentAmpScale;

        // 1. Draw Back Liquid Wave Layer
        wavePathBack.reset();
        wavePathBack.moveTo(cx - cr, cy + cr);
        for (float x = cx - cr; x <= cx + cr; x += 3f) {
            float normX = (x - (cx - cr)) / (cr * 2f);
            float waveY = baseLevelY + (float) Math.sin(normX * 9.424f + wavePhase2) * amp2;
            wavePathBack.lineTo(x, waveY);
        }
        wavePathBack.lineTo(cx + cr, cy + cr);
        wavePathBack.close();
        canvas.drawPath(wavePathBack, wavePaintBack);

        // 2. Draw Front Liquid Wave Layer
        wavePathFront.reset();
        wavePathFront.moveTo(cx - cr, cy + cr);
        for (float x = cx - cr; x <= cx + cr; x += 3f) {
            float normX = (x - (cx - cr)) / (cr * 2f);
            float waveY = baseLevelY + (float) Math.sin(normX * 12.566f + wavePhase1) * amp1;
            wavePathFront.lineTo(x, waveY);
        }
        wavePathFront.lineTo(cx + cr, cy + cr);
        wavePathFront.close();
        canvas.drawPath(wavePathFront, wavePaintFront);

        // --- Liquid Glass Lens Gloss Reflection Overlay ---
        glassGlossBounds.set(cx - cr, cy - cr, cx + cr, cy + cr * 0.15f);
        glassGlossPaint.setShader(new LinearGradient(cx, cy - cr, cx, cy + cr * 0.15f,
                0x65FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawOval(glassGlossBounds, glassGlossPaint);

        canvas.restore();

        // Inner glass refraction rim & specular light glare point
        canvas.drawCircle(cx, cy, cr - 1f, glassRimPaint);
        canvas.drawCircle(cx - cr * 0.35f, cy - cr * 0.35f, cr * 0.12f, glassGlarePaint);

        // --- Smooth Hover Animation & Glow Effects ---
        if (hoverRegion != 0) {
            if (hoverAlpha < 1f) {
                hoverAlpha += 0.18f;
            }
            if (hoverRegion == 5) {
                pressGlow.setColor((int) (0x45 * hoverAlpha) << 24 | 0x00E5FF);
                canvas.drawCircle(cx, cy, cr - 2f, pressGlow);
                pressGlow.setColor(0x35FFFFFF);
            } else {
                hoverGlow.setStrokeWidth(r * 0.14f);
                hoverGlow.setColor((int) (0x65 * hoverAlpha) << 24 | 0x00E5FF);
                RectF arcBounds = new RectF(cx - (r - r * 0.12f), cy - (r - r * 0.12f),
                        cx + (r - r * 0.12f), cy + (r - r * 0.12f));
                canvas.drawArc(arcBounds, hintStart(hoverRegion), 60f, false, hoverGlow);
            }
        } else if (hoverAlpha > 0f) {
            hoverAlpha -= 0.18f;
        }

        // --- Tactile Press Highlight ---
        if (hint != 0) {
            if (hint == 5) {
                canvas.drawCircle(cx, cy, cr - 2f, pressGlow);
            } else {
                accent.setStrokeWidth(r * 0.14f);
                accent.setColor(0x60FFFFFF);
                RectF arcBounds = new RectF(cx - (r - r * 0.12f), cy - (r - r * 0.12f),
                        cx + (r - r * 0.12f), cy + (r - r * 0.12f));
                canvas.drawArc(arcBounds, hintStart(hint), 60f, false, accent);
                accent.setColor(0xFFCCCCCC);
            }
        }

        text.setTextSize(r * 0.11f);
        canvas.drawText("MENU", cx, cy - r * 0.72f, text);
        canvas.drawText("PLAY", cx, cy + r * 0.80f, text);
        canvas.drawText("I<<", cx - r * 0.72f, cy + text.getTextSize() / 3f, text);
        canvas.drawText(">>I", cx + r * 0.72f, cy + text.getTextSize() / 3f, text);

        // Continuously post smooth 60fps frame updates
        postInvalidateOnAnimation();
    }

    private float hintStart(int region) {
        if (region == 1) {
            return 240f;
        }
        if (region == 2) {
            return 150f;
        }
        if (region == 3) {
            return 60f;
        }
        if (region == 4) {
            return -30f;
        }
        return 0f;
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float dx = x - cx;
        float dy = y - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float r = Math.min(getWidth(), getHeight()) / 2f;
        float cr = r * 0.36f;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            int newHover = region(dist, cr, r, angle);
            if (newHover != hoverRegion) {
                hoverRegion = newHover;
                hoverAlpha = 0f;
                invalidate();
            }
            return true;
        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            hoverRegion = 0;
            invalidate();
            return true;
        }
        return super.onHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float dx = x - cx;
        float dy = y - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float r = Math.min(getWidth(), getHeight()) / 2f;
        float cr = r * 0.36f;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            downX = x;
            downY = y;
            lastAngle = angle;
            accum = 0f;
            dragging = false;
            hint = region(dist, cr, r, angle);
            hoverRegion = hint;
            targetAmpScale = 1.4f;
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            float move = (float) Math.hypot(x - downX, y - downY);
            if (dist > cr && dist < r) {
                float delta = angle - lastAngle;
                if (delta > 180f) {
                    delta -= 360f;
                }
                if (delta < -180f) {
                    delta += 360f;
                }
                lastAngle = angle;
                if (Math.abs(delta) < 40f) {
                    accum += delta;
                    int steps = (int) (accum / 18f);
                    if (steps != 0) {
                        dragging = true;
                        accum -= steps * 18f;
                        targetAmpScale = 1.8f;
                        if (callback != null) {
                            callback.onWheel(steps);
                        }
                    }
                }
            }
            if (move > 12f) {
                hint = 0;
                hoverRegion = region(dist, cr, r, angle);
                invalidate();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (!dragging && action == MotionEvent.ACTION_UP) {
                fire(dist, cr, r, angle);
            }
            hint = 0;
            hoverRegion = 0;
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private int region(float dist, float cr, float r, float angle) {
        if (dist <= cr) {
            return 5;
        }
        if (dist > r) {
            return 0;
        }
        if (angle > -135f && angle <= -45f) {
            return 1;
        }
        if (angle > 45f && angle <= 135f) {
            return 3;
        }
        if (angle > -45f && angle <= 45f) {
            return 4;
        }
        return 2;
    }

    private void fire(float dist, float cr, float r, float angle) {
        if (callback == null) {
            return;
        }
        int region = region(dist, cr, r, angle);
        if (region == 5) {
            callback.onSelect();
        } else if (region == 1) {
            callback.onMenu();
        } else if (region == 3) {
            callback.onPlay();
        } else if (region == 4) {
            callback.onNext();
        } else if (region == 2) {
            callback.onPrevious();
        }
    }
}
