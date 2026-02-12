package com.izzy2lost.psx2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.content.ContextCompat;

public class JoystickView extends View {
    public interface OnMoveListener {
        void onMove(float nx, float ny, int action);
    }

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float centerX, centerY, radius, knobX, knobY, knobRadius;
    private boolean isDragging = false;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private OnMoveListener listener;

    public JoystickView(Context ctx) { super(ctx); init(); }
    public JoystickView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(); }
    public JoystickView(Context ctx, AttributeSet attrs, int defStyle) { super(ctx, attrs, defStyle); init(); }

    private void init() {
        basePaint.setColor(0x22000000); // subtle fill
        basePaint.setStyle(Paint.Style.FILL);
        // Match Settings/Controls outline (brand primary blue)
        int brandBlue = ContextCompat.getColor(getContext(), R.color.brand_primary);
        ringPaint.setColor(brandBlue);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(2));
        // Knob uses the same brand blue
        knobPaint.setColor(brandBlue);
        knobPaint.setStyle(Paint.Style.FILL);
        setClickable(true);
    }

    public void setOnMoveListener(OnMoveListener l) { this.listener = l; }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        // Make the visible base circle smaller relative to the view size
        radius = Math.min(w, h) * 0.32f;
        knobRadius = radius * 0.30f;
        resetKnob();
    }

    private void resetKnob() {
        knobX = centerX;
        knobY = centerY;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Base circle
        canvas.drawCircle(centerX, centerY, radius, basePaint);
        // Outer thin ring
        canvas.drawCircle(centerX, centerY, radius, ringPaint);
        // Knob
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        final int actionIndex = event.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                activePointerId = event.getPointerId(0);
                // fallthrough to move
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    int pointerIndex = 0;
                    if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                        int idx = event.findPointerIndex(activePointerId);
                        if (idx >= 0) pointerIndex = idx;
                    }
                    updateFromPointer(event, pointerIndex);
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // Ignore additional pointers while dragging; if we aren't dragging, adopt the new pointer.
                if (!isDragging) {
                    isDragging = true;
                    activePointerId = event.getPointerId(actionIndex);
                    updateFromPointer(event, actionIndex);
                    return true;
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                // If the active pointer goes up, release.
                if (event.getPointerId(actionIndex) == activePointerId) {
                    activePointerId = MotionEvent.INVALID_POINTER_ID;
                    isDragging = false;
                    resetKnob();
                    if (listener != null) listener.onMove(0f, 0f, MotionEvent.ACTION_UP);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                resetKnob();
                if (listener != null) listener.onMove(0f, 0f, MotionEvent.ACTION_UP);
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateFromPointer(MotionEvent event, int pointerIndex) {
        float dx = event.getX(pointerIndex) - centerX;
        float dy = event.getY(pointerIndex) - centerY;
        // Clamp to circle
        float dist = (float) Math.hypot(dx, dy);
        if (dist > radius) {
            float scale = radius / dist;
            dx *= scale;
            dy *= scale;
        }
        knobX = centerX + dx;
        knobY = centerY + dy;
        invalidate();
        if (listener != null) {
            float nx = dx / radius;
            float ny = dy / radius;
            listener.onMove(clamp(nx), clamp(ny), MotionEvent.ACTION_MOVE);
        }
    }

    private static float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }

    private float dp(float d) {
        return d * getResources().getDisplayMetrics().density;
    }
}
