package crazydude.com.telemetry.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class HorizonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var roll: Float = 0f
    private var pitch: Float = 0f
    private var size: Float = 0f
    private var center: Float = 0f

    private val leftLinePath = Path()
    private val rightLinePath = Path()
    private val circlePath = Path()

    private val planeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        .apply {
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        size = w.toFloat() //336
        center = size / 2 //128

        planeLinePaint.strokeWidth = size / 67

        leftLinePath.apply {
            moveTo(center - (size / 3), center)
            lineTo(center - (size / 20), center)
            lineTo(center - (size / 20), center + (size / 33))
        }
        rightLinePath.apply {
            moveTo(center + (size / 3), center)
            lineTo(center + (size / 20), center)
            lineTo(center + (size / 20), center + (size / 33))
        }
        circlePath.apply {
            addCircle(center, center, center, Path.Direction.CW)
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        canvas?.let {
            it.save()
            it.clipPath(circlePath, Region.Op.INTERSECT)
            it.rotate(-roll, center, center)
            paint.color = Color.parseColor("#5BCBD3")
            it.drawRect(0f, 0f, size, center - pitch * (size / 67), paint)
            paint.color = Color.parseColor("#D38700")
            it.drawRect(0f, center - pitch * (size / 67), size, size, paint)
            paint.strokeWidth = size / 84
            paint.color = Color.BLACK
            for (i in 1..18) {
                val lineLength = if (i.rem(2) == 0) size / 6 else size / 12
                it.drawLine(
                    center - lineLength,
                    (center - pitch * (size / 67)) + i * (size / 13),
                    center + lineLength,
                    (center - pitch * (size / 67)) + i * (size / 13),
                    paint
                )
                it.drawLine(
                    center - lineLength,
                    (center - pitch * (size / 67)) - i * (size / 13),
                    center + lineLength,
                    (center - pitch * (size / 67)) - i * (size / 13),
                    paint
                )
            }
            it.restore()
            it.drawPath(leftLinePath, planeLinePaint)
            it.drawPath(rightLinePath, planeLinePaint)
        }
    }

    fun setRoll(roll: Float) {
        this.roll = roll
        invalidate()
    }

    fun setPitch(pitch: Float) {
        this.pitch = pitch
        invalidate()
    }
}