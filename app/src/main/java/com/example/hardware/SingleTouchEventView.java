package com.example.hardware;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
public class SingleTouchEventView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseArray<Path> active = new SparseArray<>();
    private final ArrayList<Path> strokes = new ArrayList<>();
    private int points;
    public SingleTouchEventView(Context context, AttributeSet attrs) { super(context, attrs); setBackgroundColor(Color.WHITE); setContentDescription("Touch drawing area"); }
    public void clear() { strokes.clear(); active.clear(); points = 0; invalidate(); }
    public int pointCount() { return points; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setColor(Color.rgb(0, 121, 107)); paint.setStrokeWidth(5); paint.setStyle(Paint.Style.STROKE);
        for (Path path : strokes) canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL); paint.setTextSize(30); paint.setColor(Color.DKGRAY);
        canvas.drawText("Touch points: " + points, 20, 40, paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int i = event.getActionIndex(); Path path = new Path(); path.moveTo(event.getX(i), event.getY(i));
            active.put(event.getPointerId(i), path); strokes.add(path); points++;
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                Path path = active.get(event.getPointerId(i));
                if (path != null) { path.lineTo(event.getX(i), event.getY(i)); points++; }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            active.remove(event.getPointerId(event.getActionIndex())); performClick();
        } else if (action == MotionEvent.ACTION_CANCEL) active.clear();
        invalidate(); return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
