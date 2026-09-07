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
    private var cx = 0f
    private var cy = 0f
    private var zoom = 1f
    private var autoSpin = true
    private var night = false
    private var showGrid = true

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

    fun setNightMode(value: Boolean) {
        night = value
        invalidate()
    }

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
        val l = Math.sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble()).toFloat().coerceAtLeast(1e-6f)
        return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    // rotate a model-space point to camera space
    private fun toCam(v: FloatArray): FloatArray = rotX(rotY(v))

    private fun project(v: FloatArray): FloatArray {
        val p = toCam(v)
        return floatArrayOf(cx + p[0] * scale, cy - p[1] * scale, p[2])
    }

    // Projected 2D bounds of the rotated unit cube corners (at scale = 1).
    private fun cubeBounds(): FloatArray {
        val corners = arrayOf(
            floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
            floatArrayOf(1f, -1f, 1f), floatArrayOf(-1f, -1f, 1f),
            floatArrayOf(-1f, 1f, -1f), floatArrayOf(1f, 1f, -1f),
            floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f))
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (c in corners) {
            val p = toCam(c)
            if (p[0] < minX) minX = p[0]
            if (p[0] > maxX) maxX = p[0]
            if (p[1] < minY) minY = p[1]
            if (p[1] > maxY) maxY = p[1]
        }
        return floatArrayOf(minX, maxX, minY, maxY)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeFit()
    }

    private fun computeFit() {
        if (width == 0 || height == 0) return
        val b = cubeBounds()
        val spanX = (b[1] - b[0]).coerceAtLeast(1e-4f)
        val spanY = (b[3] - b[2]).coerceAtLeast(1e-4f)
        val marginX = width * 0.12f
        val marginTop = height * 0.10f
        val marginBottom = height * 0.26f
        val availW = width - marginX * 2
        val availH = height - marginTop - marginBottom
        scale = minOf(availW / spanX, availH / spanY) * zoom
        val midX = (b[0] + b[1]) / 2f
        val midY = (b[2] + b[3]) / 2f
        cx = width / 2f - midX * scale
        cy = marginTop + availH / 2f + midY * scale
    }

    fun setShowGrid(value: Boolean) { showGrid = value; invalidate() }

    private fun drawFloor(canvas: Canvas) {
        val gridPaint = overlay.apply {
            color = if (night) 0x2A70E6.toInt() else 0x3A6BB8.toInt()
            alpha = if (night) 70 else 60
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        // ground plane y = -1
        fun gp(x: Float, z: Float): FloatArray = toCam(floatArrayOf(x, -1f, z))
        val n = 4
        for (i in -n..n) {
            // lines parallel to X
            var a = gp(i / 2f, -n.toFloat()); var b = gp(i / 2f, n.toFloat())
            canvas.drawLine(cx + a[0] * scale, cy - a[1] * scale, cx + b[0] * scale, cy - b[1] * scale, gridPaint)
            // lines parallel to Z
            a = gp(-n.toFloat(), i / 2f); b = gp(n.toFloat(), i / 2f)
            canvas.drawLine(cx + a[0] * scale, cy - a[1] * scale, cx + b[0] * scale, cy - b[1] * scale, gridPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        computeFit() // recompute each frame (yaw/pitch change with spin/drag)

        // backdrop gradient
        val bg = android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(),
            if (night) intArrayOf(0xFF04091A.toInt(), 0xFF081227.toInt(), 0xFF14233F.toInt())
            else intArrayOf(0xFF5B93E6.toInt(), 0xFF8FB6F0.toInt(), 0xFFD7E8FB.toInt()),
            floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP)
        val bgPaint = Paint().apply { shader = bg }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (showGrid) drawFloor(canvas)

        // contact shadow under the cube
        val base = project(floatArrayOf(0f, -1f, 0f))
        overlay.color = 0x060B18.toInt()
        overlay.alpha = if (night) 130 else 96
        overlay.style = Paint.Style.FILL
        canvas.drawOval(base[0] - scale * 1.05f, base[1] + scale * 0.12f,
            base[0] + scale * 1.05f, base[1] + scale * 0.44f, overlay)

        // gather visible faces
        val list = ArrayList<DrawFace>()
        for (f in faces) {
            val n = normalize(toCam(f.normal))
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

        // ambient + light (adjust for night = cool moonlight, brighter block ambient)
        val ambient = if (night) 0.5f else 0.32f
        val light = if (night) normalize(floatArrayOf(-0.32f, 0.72f, 0.6f))
                    else normalize(floatArrayOf(0.5f, 0.82f, 0.45f))
        val sunIntensity = if (night) 0.55f else 1.0f

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
                path.reset()
                path.moveTo(dst[0], dst[1]); path.lineTo(dst[2], dst[3])
                path.lineTo(dst[4], dst[5]); path.lineTo(dst[6], dst[7]); path.close()
                canvas.drawPath(path, overlay)
            }

            // face lighting (use transformed normal so brightness follows rotation)
            val d = (df.n[0] * light[0] + df.n[1] * light[1] + df.n[2] * light[2])
                .coerceAtLeast(0f)
            val brightness = ambient + d * 0.75f * sunIntensity
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
                        zoom *= (dist / lastDist).coerceIn(0.85f, 1.18f)
                        zoom = zoom.coerceIn(1f, 3f)
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
