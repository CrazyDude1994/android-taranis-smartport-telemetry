package crazydude.com.telemetry.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.appcompat.widget.AppCompatTextView;

import crazydude.com.telemetry.R;

public class TextViewOutline extends AppCompatTextView {

    // constants
    private static final int DEFAULT_OUTLINE_SIZE = 0;
    private static final int DEFAULT_OUTLINE_COLOR = Color.TRANSPARENT;

    // data
    private int mOutlineSize;
    private int mOutlineColor;
    private int mTextColor;
    private float mShadowRadius;
    private float mShadowDx;
    private float mShadowDy;
    private int mShadowColor;

    public TextViewOutline(Context context) {
        this(context, null);
    }

    public TextViewOutline(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAttributes(attrs);
    }

    private void setAttributes(AttributeSet attrs) {
        // set defaults
        mOutlineSize = DEFAULT_OUTLINE_SIZE;
        mOutlineColor = DEFAULT_OUTLINE_COLOR;
        // text color   
        mTextColor = getCurrentTextColor();
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.TextViewOutline);
            // outline size
            if (a.hasValue(R.styleable.TextViewOutline_outlineSize)) {
                mOutlineSize = (int) a.getDimension(R.styleable.TextViewOutline_outlineSize, DEFAULT_OUTLINE_SIZE);
            }
            // outline color
            if (a.hasValue(R.styleable.TextViewOutline_outlineColor)) {
                mOutlineColor = a.getColor(R.styleable.TextViewOutline_outlineColor, DEFAULT_OUTLINE_COLOR);
            }

            a.recycle();
        }

    }

    private void setPaintToOutline() {
        Paint paint = getPaint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(mOutlineSize);
        super.setTextColor(mOutlineColor);
        super.setShadowLayer(mShadowRadius, mShadowDx, mShadowDy, mShadowColor);
    }

    private void setPaintToRegular() {
        Paint paint = getPaint();
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(0);
        super.setTextColor(mTextColor);
        super.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
    }

    private int measureDimension(int desiredSize, int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            result = specSize;
        } else {
            result = desiredSize;
            if (specMode == MeasureSpec.AT_MOST) {
                result = Math.min(result, specSize);
            }
        }
        return result;
    }

    public static int dpToPx(float dp, Context context) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setPaintToOutline();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int outlineWidth = this.dpToPx(mOutlineSize, getContext());

        this.setMeasuredDimension( this.measureDimension( getMeasuredWidth()+outlineWidth, widthMeasureSpec),
                this.measureDimension( getMeasuredHeight()+outlineWidth, heightMeasureSpec));
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        mTextColor = color;
    }

    @Override
    public void setShadowLayer(float radius, float dx, float dy, int color) {
        super.setShadowLayer(radius, dx, dy, color);
        mShadowRadius = radius;
        mShadowDx = dx;
        mShadowDy = dy;
        mShadowColor = color;
    }

    public void setOutlineSize(int size) {
        mOutlineSize = size;
    }

    public void setOutlineColor(int color) {
        mOutlineColor = color;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        setPaintToOutline();
        super.onDraw(canvas);
        setPaintToRegular();
        super.onDraw(canvas);
    }

}