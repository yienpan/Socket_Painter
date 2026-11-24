package com.example.socket_painter.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.socket_painter.model.Stroke;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PaintView extends View {
    private Paint paint;
    private List<Stroke> strokes = new ArrayList<>();
    private Stroke currentStroke;
    private String myUserId = "";
    private String myUserName = "";
    private String currentColor = "#FF0000";
    private boolean isEraserMode = false;

    public interface OnStrokeEventListener {
        void onStrokeFinished(Stroke stroke);
        void onStrokeDeleted(String strokeId);
    }

    private OnStrokeEventListener strokeEventListener;

    public PaintView(Context context) {
        super(context);
        init();
    }

    public PaintView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(8f);
    }

    public void setMyInfo(String userId, String userName, String color) {
        this.myUserId = userId;
        this.myUserName = userName;
        this.currentColor = color;
    }

    public void setCurrentColor(String color) {
        this.currentColor = color;
    }

    public void setEraserMode(boolean eraserMode) {
        this.isEraserMode = eraserMode;
    }

    public boolean isEraserMode() {
        return isEraserMode;
    }

    public void setStrokeEventListener(OnStrokeEventListener listener) {
        this.strokeEventListener = listener;
    }

    public void setStrokes(List<Stroke> strokes) {
        this.strokes = strokes != null ? strokes : new ArrayList<>();
        invalidate();
    }

    public void addStroke(Stroke stroke) {
        for (int i = 0; i < strokes.size(); i++) {
            if (strokes.get(i).getStrokeId().equals(stroke.getStrokeId())) {
                strokes.set(i, stroke);
                invalidate();
                return;
            }
        }
        strokes.add(stroke);
        invalidate();
    }

    public void deleteStroke(String strokeId) {
        strokes.removeIf(s -> s.getStrokeId().equals(strokeId));
        invalidate();
    }

    public void undoLast(String userId) {
        for (int i = strokes.size() - 1; i >= 0; i--) {
            Stroke s = strokes.get(i);
            if (userId.isEmpty() || s.getUserId().equals(userId)) {
                strokes.remove(i);
                break;
            }
        }
        invalidate();
    }

    public void clearAll() {
        strokes.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw all finished strokes
        for (Stroke stroke : strokes) {
            drawStroke(canvas, stroke);
        }

        // Draw current active stroke
        if (currentStroke != null) {
            drawStroke(canvas, currentStroke);
        }
    }

    private void drawStroke(Canvas canvas, Stroke stroke) {
        List<PointF> points = stroke.getPoints();
        if (points == null || points.isEmpty()) return;

        try {
            paint.setColor(Color.parseColor(stroke.getColor()));
        } catch (IllegalArgumentException e) {
            paint.setColor(Color.RED);
        }

        android.graphics.Path path = new android.graphics.Path();
        PointF first = points.get(0);
        path.moveTo(first.x, first.y);

        for (int i = 1; i < points.size(); i++) {
            PointF p = points.get(i);
            path.lineTo(p.x, p.y);
        }

        canvas.drawPath(path, paint);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (isEraserMode) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                performClick();
                // 檢查是否點擊到任意筆跡，進行整筆刪除（不限權限）
                String targetStrokeId = findStrokeNearPoint(x, y, 40f);
                if (targetStrokeId != null) {
                    deleteStroke(targetStrokeId);
                    if (strokeEventListener != null) {
                        strokeEventListener.onStrokeDeleted(targetStrokeId);
                    }
                }
            }
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                performClick();
                String strokeId = "s_" + UUID.randomUUID().toString().substring(0, 8);
                List<PointF> pts = new ArrayList<>();
                pts.add(new PointF(x, y));
                currentStroke = new Stroke(strokeId, myUserId, myUserName, currentColor, pts);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (currentStroke != null) {
                    currentStroke.addPoint(new PointF(x, y));
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (currentStroke != null) {
                    strokes.add(currentStroke);
                    if (strokeEventListener != null) {
                        strokeEventListener.onStrokeFinished(currentStroke);
                    }
                    currentStroke = null;
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * 尋找點擊位置附近的任意筆跡（不限權限）
     */
    private String findStrokeNearPoint(float x, float y, float threshold) {
        for (int i = strokes.size() - 1; i >= 0; i--) {
            Stroke s = strokes.get(i);
            for (PointF p : s.getPoints()) {
                float dx = p.x - x;
                float dy = p.y - y;
                if (Math.sqrt(dx * dx + dy * dy) <= threshold) {
                    return s.getStrokeId();
                }
            }
        }
        return null;
    }
}
