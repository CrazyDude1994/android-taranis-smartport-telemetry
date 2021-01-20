package crazydude.com.telemetry.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.appcompat.widget.AppCompatTextView;

import crazydude.com.telemetry.R;

public class TextViewStableSize extends AppCompatTextView {

    //should fit this text
    private String mMinText = "";
    //width of icon and it's padding, if there is DrawableLeft icon.
    //It is to complex to calculate it, so we just provide explicit value here.
    private int iconWidthDp = 0;

    private int seenMaxWidth = 0;

    public TextViewStableSize(Context context) {
        this(context, null);
    }

    public TextViewStableSize(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAttributes(attrs);
    }

    private void setAttributes(AttributeSet attrs) {
        mMinText = "";
        iconWidthDp = 0;

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.TextViewStableSize);
            if (a.hasValue(R.styleable.TextViewStableSize_minText)) {
                mMinText = a.getString(R.styleable.TextViewStableSize_minText);
            }
            if (a.hasValue(R.styleable.TextViewStableSize_iconWidthDp)) {
                iconWidthDp = a.getInt(R.styleable.TextViewStableSize_iconWidthDp, 0);
            }
            a.recycle();
        }
    }

    public static int dpToPx(float dp, Context context) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int wPaddingAndIcon = this.getPaddingLeft() + this.getPaddingRight() + this.dpToPx(iconWidthDp, getContext());

        Rect bounds = new Rect();
        getPaint().getTextBounds(this.mMinText,0, mMinText.length(), bounds);
        int minTextWidth = bounds.width() + wPaddingAndIcon;

        this.seenMaxWidth = Math.max( this.seenMaxWidth, minTextWidth);
        this.seenMaxWidth = Math.max( this.seenMaxWidth, getMeasuredWidth());

        this.setMinWidth( this.seenMaxWidth );
    }


}