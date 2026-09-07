package com.pixelcraft.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Displays a rotating, textured block (top/side/bottom faces) in isometric-style
 * orthographic projection, with per-face lighting + baked relief.
 */

class BlockPreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var topBmp: Bitmap? = null
    private var sideBmp: Bitmap? = null
    private var bottomBmp: Bitmap? = null

    private var yaw = Math.PI.toFloat() / 6f
    private var pitch = 0.55f
    private var scale = 0f
    private var autoSpin = true

    private var dragging = false
    private var lastX = 0f
    private var lastY = 0f
    private var lastDist = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private val handler = Handler(Looper.getMainLooper())
    private val spin = object : Runnable {
        override fun run() {
            if (autoSpin && !dragging) {
                yaw += 0.01f
                invalidate()
            }
            handler.postDelayed(this, 16L)
        }
    }

    private val faces = arrayOf(
        Face(intArrayOf(-1,1,-1, 1,1,-1, 1,1,1, -1,1,1), floatArrayOf(0f,1f,0f), "top"),
        Face(intArrayOf(-1,-1,-1, 1,-1,-1, 1,-1,1, -1,-1,1), floatArrayOf(0f,-1f,0f), "bottom"),
        Face(intArrayOf(-1,-1,-1, 1,-1,-1, 1,1,-1, -1,1,-1), floatArrayOf(0f,0f,-1f), "side"),
        Face(intArrayOf(-1,-1,1, 1,-1,1, 1,1,1, -1,1,1), floatArrayOf(0f,0f,1f), "side"),
        Face(intArrayOf(1,-1,-1, 1,-1,1, 1,1,1, 1,1,-1), floatArrayOf(1f,0f,0f), "side"),
        Face(intArrayOf(-1,-1,-1, -1,-1,1, -1,1,1, -1,1,-1), floatArrayOf(-1f,0f,0f), "side"),
    )

    private class Face(val corners: IntArray, val normal: FloatArray, val tag: String)

    fun setFaces(top: Bitmap?, side: Bitmap?, bottom: Bitmap?) {
        topBmp = top; sideBmp = side; bottomBmp = bottom
        invalidate()
    }

    fun setAutoSpin(value: Boolean) { autoSpin = value }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(spin)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(spin)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scale = minOf(w, h) * 0.40f
    }

    private fun rotY(p: FloatArray): FloatArray {
        val c = cos(yaw); val s = sin(yaw)
        return floatArrayOf(p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c)
    }
    private fun rotX(p: FloatArray): FloatArray {
        val c = cos(pitch); val s = sin(pitch)
        return floatArrayOf(p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c)
    }
    private fun normalize(v: FloatArray): FloatArray {
        val l = hypot(v[0].toDouble(), v[1].toDouble(), v[2].toDouble()).toFloat().coerceAtLeast(1e-6f)
        return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    private fun project(v: FloatArray): FloatArray {
        var p = rotY(v)
        p = rotX(p)
        return floatArrayOf(width / 2f + p[0] * scale, height / 2f - p[1] * scale, p[2])
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // backdrop glow
        val cx = width / 2f
        val cy = height / 2f

        // gather visible faces
        val list = ArrayList<DrawFace>()
        for (f in faces) {
            val n = normalize(rotX(rotY(f.normal)))
            if (n[2] <= 0.05f) continue
            val pts = ArrayList<FloatArray>()
            var depth = 0f
            for (i in 0 until 4) {
                val p = project(floatArrayOf(
                    f.corners[i * 3].toFloat(),
                    f.corners[i * 3 + 1].toFloat(),
                    f.corners[i * 3 + 2].toFloat()))
                pts.add(p); depth += p[2]
            }
            list.add(DrawFace(f, n, pts, depth / 4f))
        }
        list.sortBy { it.depth }

        // ambient + light
        val ambient = 0.32f
        val light = normalize(floatArrayOf(0.5f, 0.82f, 0.45f))

        for (df in list) {
            val bmp = when (df.face.tag) {
                "top" -> topBmp
                "bottom" -> bottomBmp
                else -> sideBmp
            } ?: continue

            val srcQuad = floatArrayOf(0f, 0f, bmp.width.toFloat(), 0f,
                bmp.width.toFloat(), bmp.height.toFloat(), 0f, bmp.height.toFloat())

            val dst = floatArrayOf(
                df.pts[0][0], df.pts[0][1],
                df.pts[1][0], df.pts[1][1],
                df.pts[2][0], df.pts[2][1],
                df.pts[3][0], df.pts[3][1])

            val matrix = Matrix()
            if (matrix.setPolyToPoly(srcQuad, 0, dst, 0, 4)) {
                canvas.drawBitmap(bmp, matrix, paint)
            } else {
                // fallback: fill color
                path.reset()
                path.moveTo(dst[0], dst[1]); path.lineTo(dst[2], dst[3])
                path.lineTo(dst[4], dst[5]); path.lineTo(dst[6], dst[7]); path.close()
                canvas.drawPath(path, overlay)
            }

            // face lighting (use transformed normal so brightness follows rotation)
            val d = (df.n[0] * light[0] + df.n[1] * light[1] + df.n[2] * light[2])
                .coerceAtLeast(0f)
            val brightness = ambient + d * 0.75f
            val shadow = (1f - brightness).coerceIn(0f, 0.65f)
            if (shadow > 0.01f) {
                path.reset()
                path.moveTo(df.pts[0][0], df.pts[0][1])
                path.lineTo(df.pts[1][0], df.pts[1][1])
                path.lineTo(df.pts[2][0], df.pts[2][1])
                path.lineTo(df.pts[3][0], df.pts[3][1])
                path.close()
                overlay.color = 0x0A1420.toInt()
                overlay.alpha = (shadow * 255).toInt()
                canvas.drawPath(path, overlay)
            }
        }
        // draw a subtle highlight ring
        overlay.color = 0x144F8CFF
        overlay.alpha = 255
        overlay.style = Paint.Style.STROKE
        overlay.strokeWidth = 2f
        canvas.drawCircle(cx, cy, minOf(width, height) * 0.24f, overlay)
        overlay.style = Paint.Style.FILL
    }

    private class DrawFace(val face: Face, val n: FloatArray, val pts: ArrayList<FloatArray>, val depth: Float)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                lastX = event.x; lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val dx = event.getX(0) - event.getX(1)
                    val dy = event.getY(0) - event.getY(1)
                    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (lastDist > 0) {
                        scale *= (dist / lastDist).coerceIn(0.5f, 2f)
                        scale = scale.coerceIn(60f, minOf(width, height) * 1.2f)
                    }
                    lastDist = dist
                } else if (dragging) {
                    yaw += (event.x - lastX) * 0.012f
                    pitch += (event.y - lastY) * 0.012f
                    pitch = pitch.coerceIn(-1.3f, 1.3f)
                    lastX = event.x; lastY = event.y
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                lastDist = 0f
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
