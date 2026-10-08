package net.northplus.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * 可双指缩放 / 双击放大 / 拖拽的图片视图。
 *
 * <p>未放大时横向拖动会被识别为翻页手势，通过 {@link SwipeListener} 通知宿主，
 * 放大后拖动则用于平移查看细节。
 */
public class ZoomImageView extends AppCompatImageView {

    /** delta = +1 表示切到下一张，-1 表示上一张。 */
    public interface SwipeListener {
        void onSwipeOut(int delta);
    }

    public interface TapListener {
        void onTap();
    }

    private final Matrix matrix = new Matrix();
    private final float[] buf = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private final int touchSlop;

    private float baseScale = 1f;
    private float minScale = 1f;
    private float maxScale = 4f;
    private float curScale = 1f;

    private SwipeListener swipeListener;
    private TapListener tapListener;

    private float lastX;
    private float lastY;
    private boolean swiping;
    private float swipeDx;

    public ZoomImageView(Context c) {
        this(c, null);
    }

    public ZoomImageView(Context c, @Nullable AttributeSet attrs) {
        super(c, attrs);
        setScaleType(ScaleType.MATRIX);
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();

        scaleDetector = new ScaleGestureDetector(c,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        scaleBy(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                        return true;
                    }
                });

        gestureDetector = new GestureDetector(c,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (curScale > minScale * 1.15f) {
                            animateScaleTo(minScale, e.getX(), e.getY());
                        } else {
                            animateScaleTo(Math.min(maxScale, minScale * 2.5f),
                                    e.getX(), e.getY());
                        }
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (tapListener != null) tapListener.onTap();
                        return true;
                    }
                });
    }

    public void setSwipeListener(SwipeListener l) {
        swipeListener = l;
    }

    public void setTapListener(TapListener l) {
        tapListener = l;
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        post(this::reset);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (getDrawable() != null) reset();
    }

    /** 复位到「适应屏幕并居中」。 */
    public void reset() {
        Drawable d = getDrawable();
        int vw = getWidth();
        int vh = getHeight();
        if (d == null || vw <= 0 || vh <= 0) return;
        float dw = d.getIntrinsicWidth();
        float dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;

        baseScale = Math.min(vw / dw, vh / dh);
        if (baseScale <= 0f) baseScale = 1f;
        if (baseScale > 2f) baseScale = 2f;   // 小图不过度放大
        minScale = baseScale;
        maxScale = Math.max(baseScale * 4f, baseScale + 1f);

        matrix.reset();
        matrix.postScale(baseScale, baseScale);
        matrix.postTranslate((vw - dw * baseScale) / 2f, (vh - dh * baseScale) / 2f);
        curScale = baseScale;
        setImageMatrix(matrix);
        setTranslationX(0f);
    }

    private float currentScale() {
        matrix.getValues(buf);
        return buf[Matrix.MSCALE_X];
    }

    private void scaleBy(float factor, float px, float py) {
        float target = curScale * factor;
        if (target < minScale) target = minScale;
        if (target > maxScale) target = maxScale;
        if (Math.abs(target - curScale) < 0.0005f) return;
        float f = target / curScale;
        matrix.postScale(f, f, px, py);
        curScale = target;
        clamp();
        setImageMatrix(matrix);
    }

    private void animateScaleTo(final float target, final float px, final float py) {
        final float start = curScale;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(180);
        va.addUpdateListener(a -> {
            float p = (Float) a.getAnimatedValue();
            float want = start + (target - start) * p;
            if (curScale > 0f) {
                float f = want / curScale;
                matrix.postScale(f, f, px, py);
                curScale = want;
                clamp();
                setImageMatrix(matrix);
            }
        });
        va.start();
    }

    private void drag(float dx, float dy) {
        matrix.postTranslate(dx, dy);
        clamp();
        setImageMatrix(matrix);
    }

    /** 把图片约束在可视范围内。 */
    private void clamp() {
        Drawable d = getDrawable();
        if (d == null) return;
        matrix.getValues(buf);
        float scale = buf[Matrix.MSCALE_X];
        float tx = buf[Matrix.MTRANS_X];
        float ty = buf[Matrix.MTRANS_Y];
        float w = d.getIntrinsicWidth() * scale;
        float h = d.getIntrinsicHeight() * scale;
        float vw = getWidth();
        float vh = getHeight();

        float minX, maxX, minY, maxY;
        if (w <= vw) {
            minX = maxX = (vw - w) / 2f;
        } else {
            minX = vw - w;
            maxX = 0f;
        }
        if (h <= vh) {
            minY = maxY = (vh - h) / 2f;
        } else {
            minY = vh - h;
            maxY = 0f;
        }
        float nx = Math.min(Math.max(tx, minX), maxX);
        float ny = Math.min(Math.max(ty, minY), maxY);
        if (nx != tx || ny != ty) matrix.postTranslate(nx - tx, ny - ty);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        gestureDetector.onTouchEvent(e);
        if (getDrawable() == null) return true;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                swiping = false;
                swipeDx = 0f;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // 开始捏合，取消可能正在进行的翻页手势
                if (swiping) {
                    swiping = false;
                    swipeDx = 0f;
                    animate().translationX(0f).setDuration(120).start();
                }
                lastX = e.getX();
                lastY = e.getY();
                break;

            case MotionEvent.ACTION_MOVE: {
                if (scaleDetector.isInProgress()) break;
                float x = e.getX();
                float y = e.getY();
                float dx = x - lastX;
                float dy = y - lastY;
                if (currentScale() > minScale * 1.02f) {
                    drag(dx, dy);
                    lastX = x;
                    lastY = y;
                } else {
                    if (!swiping && Math.abs(dx) > touchSlop
                            && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        swiping = true;
                    }
                    if (swiping) {
                        swipeDx += dx;
                        setTranslationX(swipeDx);
                        lastX = x;
                        lastY = y;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (swiping) {
                    float threshold = getWidth() * 0.25f;
                    if (Math.abs(swipeDx) > threshold) {
                        final int delta = swipeDx < 0 ? 1 : -1;
                        animate().translationX(delta > 0 ? -getWidth() : getWidth())
                                .setDuration(140)
                                .withEndAction(() -> {
                                    setTranslationX(0f);
                                    if (swipeListener != null) swipeListener.onSwipeOut(delta);
                                })
                                .start();
                    } else {
                        animate().translationX(0f).setDuration(140).start();
                    }
                }
                swiping = false;
                swipeDx = 0f;
                break;
            }

            default:
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
