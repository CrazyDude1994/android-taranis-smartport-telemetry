package crazydude.com.telemetry.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class RCWidget @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var height: Float = 0f


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val width: Int
        val height: Int

        val desiredWidth =  Math.ceil(0.1*heightSize + 0.3 * heightSize*15 + 0.2 * heightSize).toInt();
        val desiredHeight = heightSize

        //Measure Width
        width = if (widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            widthSize
        } else if (widthMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            Math.min(desiredWidth, widthSize)
        } else {
            //Be whatever you want
            desiredWidth
        }

        //Measure Height
        height = if (heightMode == MeasureSpec.EXACTLY) {
            //Must be this size
            heightSize
        } else if (heightMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            Math.min(desiredHeight, heightSize)
        } else {
            //Be whatever you want
            desiredHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        height = h.toFloat()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        //var barHeight : Float = this.size * 0.6;


        canvas?.let {

/*
            val paint2 = Paint()
            paint2.color = Color.parseColor("#909090")
            paint2.style = Paint.Style.FILL
            canvas.drawPaint(paint2)

 */

            for( ch  in 0..15)
            {
                var  x = height * 0.1f + ch*height*0.3f;
                var x2 = x + height * 0.15f
                var top = height *0.1f;
                var bottom = height*0.75f;
                var middleY = (top+bottom)/2.0f

                paint.color = Color.rgb(34, 177,76)
                paint.style=Paint.Style.FILL;
                it.drawRect(x, top, x2, middleY, paint)

                paint.color = Color.parseColor("#FFFFFF")
                paint.style = Paint.Style.STROKE;
                paint.strokeWidth = this.height / 20;

                it.drawRect(x, top, x2, height*0.75f, paint)
                it.drawLine(x, middleY, x2, middleY,paint )
            }

            paint.style = Paint.Style.FILL;
            paint.textSize = this.height / 5.0f
            paint.textAlign = Paint.Align.CENTER
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            for( ch  in 0..15)
            {
                var  x = height * 0.1f + ch*height*0.3f + height*0.15f / 2.0f;
                canvas.drawText((ch+1).toString(), x, height*0.95f, paint);
            }

        }
    }

}