package com.agilie.agtimepicker.presenter

import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import com.agilie.agtimepicker.ui.animation.PickerPath
import com.agilie.agtimepicker.ui.view.TimePickerView
import com.agilie.agtimepicker.ui.view.TimePickerView.Companion.MAX_PULL_UP
import com.agilie.volumecontrol.calculateAngleWithTwoVectors
import com.agilie.volumecontrol.distance
import com.agilie.volumecontrol.getPointOnBorderLineOfCircle
import java.lang.Math.max
import java.lang.Math.min


abstract class BaseBehavior : TimePickerContract.Behavior {
    val view: TimePickerView
    val pickerPath: PickerPath
    var countOfValues = 24
    var maxLapCount = 2
    var colors: IntArray = intArrayOf(
            Color.parseColor("#0080ff"),
            Color.parseColor("#53FFFF"))

    constructor(view: TimePickerView, pickerPath: PickerPath) {
        this.view = view
        this.pickerPath = pickerPath
    }


    private companion object {
        val MIN_LAP_COUNT = 1
        val MIN_ANGLE = 0
        val MAX_ANGLE = 360
    }

    var valueChangeListener: TimePickerContract.Behavior.ValueChangeListener? = null

    var centeredText = ""

    var picker = true

    val pointCenter: PointF
        get() = pickerPath.center
    val radius: Float
        get() = pickerPath.radius

    var centeredTextSize = 50f
    var centeredStrokeWidth = 40f
    var centeredTypeface = Typeface.DEFAULT
    var centeredTextColor = Color.WHITE

    var textPaint = Paint().apply {
        color = centeredTextColor
        textSize = centeredTextSize
        typeface = centeredTypeface
        strokeWidth = centeredStrokeWidth
    }

    override fun onDraw(canvas: Canvas) {
        pickerPath.onDraw(canvas)
        drawText(canvas)
    }

    fun drawText(canvas: Canvas) {
        textPaint.color = centeredTextColor
        textPaint.textSize = centeredTextSize
        textPaint.typeface = centeredTypeface
        textPaint.strokeWidth = centeredStrokeWidth
        canvas.drawText(centeredText, (pointCenter.x - (textPaint.measureText(centeredText) / 2)),
                (pointCenter.y - ((textPaint.descent() + textPaint.ascent())) / 2), textPaint)
    }

    override fun onSizeChanged(width: Int, height: Int) {
        val center = PointF(width / 2f, height / 2f)
        val radius = max(min(width, height), 0) / 3f
        updatePaint(center, radius)
        drawShapes(center, radius)
    }

    var gradientAngle = 0

    fun updatePaint(center: PointF, radius: Float) {
        val startPoint = getPointOnBorderLineOfCircle(center, radius, 180 + gradientAngle)
        val endPoint = getPointOnBorderLineOfCircle(center, radius, 0 + gradientAngle)
        pickerPath.pickerPaint.apply {
            shader = LinearGradient(startPoint.x, startPoint.y, endPoint.x, endPoint.y, colors,
                    null,
                    Shader.TileMode.CLAMP)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onActionDown(PointF(event.x, event.y))
            }
            MotionEvent.ACTION_MOVE -> {
                onActionMove(PointF(event.x, event.y))
                view.onInvalidate()
            }
            MotionEvent.ACTION_UP -> {
                angleDelta = 0
                onActionUp(PointF(event.x, event.y))
                view.onInvalidate()
            }
        }
        return true
    }

    private fun drawShapes(center: PointF, radius: Float) {
        pickerPath.center = center
        pickerPath.radius = radius
        pickerPath.createPickerPath()
    }

    private fun onActionDown(pointF: PointF) {
        calculateAngleValue(pointF)
    }

    private fun onActionMove(pointF: PointF) {


        if (picker) {
            val currentAngle = calculateAngleWithTwoVectors(pointF, pickerPath.center).toInt()
            val angleChanged = previousAngle != currentAngle

            if (angleChanged) {
                if (overlappedClockwise(direction, previousAngle, currentAngle)) {
                    angleDelta += (MAX_ANGLE - previousAngle + currentAngle)
                } else if (overlappedCclockwise(direction, previousAngle, currentAngle)) {
                    angleDelta -= (MAX_ANGLE - currentAngle + previousAngle)
                } else if (previousAngle < currentAngle) {
                    direction = Direction.CLOCKWISE
                    angleDelta += (currentAngle - previousAngle)
                } else {
                    direction = Direction.CCLOCKWISE
                    angleDelta -= (previousAngle - currentAngle)
                }


                val angle = Math.max(Math.min(actionDownAngle + angleDelta, MAX_ANGLE), MIN_ANGLE)
                //when CLOCKWISE
                if (direction == Direction.CLOCKWISE && angle == MAX_ANGLE) {
                    if (lapCount >= maxLapCount) {
                        lapCount = MIN_LAP_COUNT

                    } else {
                        lapCount++
                    }
                    setMinAngleValue()
                }
                // when CCLOCKWISE
                if (direction == Direction.CCLOCKWISE && angle == MIN_ANGLE) {
                    if (lapCount == MIN_LAP_COUNT) {
                        lapCount = maxLapCount
                    } else {
                        lapCount--
                    }
                    actionDownAngle = MAX_ANGLE
                    angleDelta = MAX_ANGLE
                }

                if (angle == 360 || angle == 0) return
                previousAngle = currentAngle
                val distance = distance(pointF, pickerPath.center) - pickerPath.radius
                val pullUp = Math.min(MAX_PULL_UP, Math.max(distance, 0f))
                pickerPath.onActionMove(currentAngle.toFloat(), pullUp)
                value(calculateValue(((MAX_ANGLE * lapCount) - MAX_ANGLE) + currentAngle))
            }
        }

    }

    private var actionDownAngle = 0
    private var angleDelta = 0
    var lapCount = 1
    var direction: Direction = Direction.UNDEFINED
    private var previousAngle = 0

    enum class Direction {
        UNDEFINED, CLOCKWISE, CCLOCKWISE
    }

    private fun overlappedCclockwise(direction: Direction, previousAngle: Int, currentAngle: Int) = direction == Direction.CCLOCKWISE && (currentAngle - previousAngle) > 45

    private fun overlappedClockwise(direction: Direction, previousAngle: Int, currentAngle: Int) = direction == Direction.CLOCKWISE && (previousAngle - currentAngle) > 45

    private fun onActionUp(pointF: PointF) {
        setMinAngleValue()
        pickerPath.lockMove = true
    }


    fun calculateAngleValue(pointF: PointF) {
        if (picker) {
            pickerPath.lockMove = !picker
            val distance = distance(pointF, pickerPath.center) - pickerPath.radius
            val pullUp = Math.min(MAX_PULL_UP, Math.max(distance, 0f))
            actionDownAngle = calculateAngleWithTwoVectors(pointF, pickerPath.center).toInt()
            pickerPath.onActionMove(actionDownAngle.toFloat(), pullUp)
            previousAngle = actionDownAngle
            direction = Direction.UNDEFINED
            value(calculateValue(((MAX_ANGLE * lapCount) - MAX_ANGLE) + actionDownAngle))
            view.onInvalidate()
        }
    }

    private fun setMinAngleValue() {
        actionDownAngle = MIN_ANGLE
        angleDelta = MIN_ANGLE
    }

    abstract fun build()
}
