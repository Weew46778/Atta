package com.pixelcraft.studio

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.pow

/**
 * Procedural, seedable texture synthesis for PixelCraft Studio.
 *
 * Produces, per texture id, a base colour map, a tangent-space normal map and a
 * "bump-lit" shaded map (the per-pixel relief that makes blocks read as three
 * dimensional). The logic mirrors the browser engine so packs look identical.
 */

class Noise(val seed: Int) {
    private val perm = IntArray(512)

    init {
        val p = IntArray(256) { it }
        var state = seed.toLong() * 2654435761L
        fun rnd(): Float {
            state = (state * 1664525L + 1013904223L) and 0xffffffffL
            return (state shr 8).toFloat() / 16777216f
        }
        for (i in 255 downTo 1) {
            val j = (rnd() * (i + 1)).toInt().coerceIn(0, i)
            val t = p[i]; p[i] = p[j]; p[j] = t
        }
        for (i in 0 until 512) perm[i] = p[i and 255]
    }

    private fun grad(h: Int, x: Float, y: Float): Float = when (h and 7) {
        0 -> x + y
        1 -> -x + y
        2 -> x - y
        3 -> -x - y
        4 -> x
        5 -> -x
        6 -> y
        else -> -y
    }

    fun noise2(x: Float, y: Float): Float {
        val xi = Math.floor(x.toDouble()).toInt() and 255
        val yi = Math.floor(y.toDouble()).toInt() and 255
        val xf = x - Math.floor(x.toDouble()).toFloat()
        val yf = y - Math.floor(y.toDouble()).toFloat()
        fun fade(t: Float) = t * t * t * (t * (t * 6 - 15) + 10)
        fun lerp(a: Float, b: Float, t: Float) = a + t * (b - a)
        val u = fade(xf); val v = fade(yf)
        val A = perm[xi] + yi
        val B = perm[xi + 1] + yi
        return lerp(
            lerp(grad(perm[A], xf, yf), grad(perm[B], xf - 1, yf), u),
            lerp(grad(perm[A + 1], xf, yf - 1), grad(perm[B + 1], xf - 1, yf - 1), u),
            v
        )
    }

    fun fbm(x: Float, y: Float, octaves: Int, lac: Float, gain: Float): Float {
        var amp = 1f; var freq = 1f; var sum = 0f; var norm = 0f
        for (i in 0 until octaves) {
            sum += amp * noise2(x * freq, y * freq)
            norm += amp
            amp *= gain; freq *= lac
        }
        return sum / norm
    }

    fun ridged(x: Float, y: Float, octaves: Int, lac: Float, gain: Float): Float {
        var amp = 0.6f; var freq = 1f; var sum = 0f; var norm = 0f
        for (i in 0 until octaves) {
            val n = 1f - abs(noise2(x * freq, y * freq))
            sum += amp * n * n
            norm += amp
            amp *= gain; freq *= lac
        }
        return sum / norm
    }
}

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    fun norm(): Vec3 {
        val l = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat().coerceAtLeast(1e-6f)
        return Vec3(x / l, y / l, z / l)
    }
}

class TextureBundle(val color: Bitmap, val normal: Bitmap, val shaded: Bitmap,
                    val colorInt: IntArray, val height: FloatArray, val size: Int)

fun interface PixelGen {
    fun generate(x: Int, y: Int, size: Int, noise: Noise, rnd: () -> Float, rgb: IntArray, off: Int): Float
}

object TextureGenerator {

    private const val LIGHTX = -0.45f
    private const val LIGHTY = -0.72f
    private const val LIGHTZ = 0.55f

    private fun pack(r: Int, g: Int, b: Int, a: Int): Int {
        val rr = r.coerceIn(0, 255); val gg = g.coerceIn(0, 255)
        val bb = b.coerceIn(0, 255); val aa = a.coerceIn(0, 255)
        return (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    fun generate(seed: Int, size: Int, id: String, realism: Float, relief: Float): TextureBundle {
        val noise = Noise(seed)
        var rstate = (seed * 2654435761L + 12345L) and 0xffffffffL
        val rnd: () -> Float = {
            rstate = (rstate * 1664525L + 1013904223L) and 0xffffffffL
            (rstate shr 9).toFloat() / 8388608f
        }

        // For grass_side we overlay grass over a precomputed dirt map (built once).
        val gen: PixelGen = if (id == "grass_side") {
            val dirtB = generate(seed + 7, size, "dirt", realism, relief)
            PixelGen { x, y, s, n, r, rgb, off ->
                grassSidePixel(x, y, s, n, r, rgb, off, dirtB.colorInt, dirtB.height)
            }
        } else registry(id)

        val color = IntArray(size * size)
        val height = FloatArray(size * size)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val off = y * size + x
                val h = gen.generate(x, y, size, noise, rnd, color, off)
                height[off] = h
            }
        }

        val normalI = IntArray(size * size)
        val shadedI = IntArray(size * size)
        val ambient = 0.42f
        val light = Vec3(LIGHTX, LIGHTY, LIGHTZ).norm()

        for (y in 0 until size) {
            for (x in 0 until size) {
                val i = y * size + x
                val xl = if (x == 0) i else i - 1
                val xr = if (x == size - 1) i else i + 1
                val yl = if (y == 0) i else i - size
                val yr = if (y == size - 1) i else i + size

                val dhx = (height[xr] - height[xl]) * relief
                val dhy = (height[yr] - height[yl]) * relief
                val n = Vec3(-dhx, -dhy, 1f).norm()

                val nR = ((n.x * 0.5f + 0.5f) * 255).toInt()
                val nG = ((n.y * 0.5f + 0.5f) * 255).toInt()
                val nB = ((n.z * 0.5f + 0.5f) * 255).toInt()
                normalI[i] = pack(nR, nG, nB, 255)

                val diff = (n.x * light.x + n.y * light.y + n.z * light.z).coerceAtLeast(0f)
                val spec = diff.pow(14) * 0.35f
                val lite = ambient + diff * 0.7f + spec
                val cr = ((color[i] shr 16) and 0xFF)
                val cg = ((color[i] shr 8) and 0xFF)
                val cb = (color[i] and 0xFF)
                shadedI[i] = pack((cr * lite).toInt(), (cg * lite).toInt(), (cb * lite).toInt(), 255)
            }
        }

        val colorBmp = Bitmap.createBitmap(color, size, size, Bitmap.Config.ARGB_8888)
        val normalBmp = Bitmap.createBitmap(normalI, size, size, Bitmap.Config.ARGB_8888)
        val shadedBmp = Bitmap.createBitmap(shadedI, size, size, Bitmap.Config.ARGB_8888)
        return TextureBundle(colorBmp, normalBmp, shadedBmp, color, height, size)
    }

    // registry of generators
    fun registry(id: String): PixelGen = when (id) {
        "grass_top" -> grassTop
        // grass_side is handled specially inside generate(); this path is never hit.
        "grass_side" -> stone
        "grass_bottom" -> dirt
        "dirt" -> dirt
        "stone" -> stone
        "cobblestone" -> cobble
        "oak_log_side" -> woodSide
        "oak_log_top" -> woodTop
        "oak_planks" -> plank
        "sand" -> sand
        "bricks" -> brick
        "snow" -> snow
        "oak_leaves" -> leaves
        "water" -> water
        else -> stone
    }

    fun allIds(): Array<String> = arrayOf(
        "grass_top", "grass_side", "grass_bottom", "dirt", "stone", "cobblestone",
        "oak_log_side", "oak_log_top", "oak_planks", "sand", "bricks", "snow",
        "oak_leaves", "water"
    )

    fun title(id: String): String = when (id) {
        "grass_top" -> "Grass Top"
        "grass_side" -> "Grass Side"
        "grass_bottom" -> "Grass Bottom"
        "dirt" -> "Dirt"
        "stone" -> "Stone"
        "cobblestone" -> "Cobblestone"
        "oak_log_side" -> "Oak Log Side"
        "oak_log_top" -> "Oak Log Top"
        "oak_planks" -> "Oak Planks"
        "sand" -> "Sand"
        "bricks" -> "Bricks"
        "snow" -> "Snow"
        "oak_leaves" -> "Oak Leaves"
        "water" -> "Water"
        else -> id
    }

    fun block(id: String): String = when (id) {
        "grass_top", "grass_side", "grass_bottom" -> "grass"
        "oak_log_side", "oak_log_top" -> "oak_log"
        else -> id.removeSuffix("_top").removeSuffix("_side").removeSuffix("_bottom")
    }

    // ---- generators ----
    private val grassTop = PixelGen { x, y, s, n, r, rgb, off ->
        val f = n.fbm(x / s * 6f, y / s * 6f, 5, 2f, 0.5f)
        val f2 = n.fbm(x / s * 14f + 50f, y / s * 14f + 50f, 3, 2f, 0.5f)
        val g = 0.5f + f * 0.32f + f2 * 0.14f
        rgb[off] = pack((64 + g * 36 + r() * 10).toInt(), (128 + g * 74 + r() * 16).toInt(),
            (50 + g * 34).toInt(), 255)
        g * 0.9f + f2 * 0.3f
    }

    // Grass-side uses a precomputed dirt map provided by generate().
    private fun grassSidePixel(x: Int, y: Int, s: Int, n: Noise, r: () -> Float,
                               rgb: IntArray, off: Int, dirtColor: IntArray, dirtHeight: FloatArray): Float {
        val fringe = 1f - y.toFloat() / s
        val top = fringe.pow(0.55f)
        val blades = n.fbm(x / s * 26f, 0f, 4, 2f, 0.5f)
        val dcr = (dirtColor[off] shr 16) and 0xFF
        val dcg = (dirtColor[off] shr 8) and 0xFF
        val dcb = dirtColor[off] and 0xFF
        rgb[off] = pack(
            (dcr * (1 - top) + (70 + blades * 90) * top).toInt(),
            (dcg * (1 - top) + (128 + blades * 44) * top).toInt(),
            (dcb * (1 - top) + (58 + blades * 30) * top).toInt(), 255)
        return dirtHeight[off] * (1 - top) + top * (0.7f + blades * 0.5f)
    }

    private val dirt = PixelGen { x, y, s, n, r, rgb, off ->
        val f = n.fbm(x / s * 11f, y / s * 11f, 5, 2f, 0.55f)
        val specks = n.fbm(x / s * 30f + 90f, y / s * 30f + 90f, 2, 2f, 0.5f)
        val base = 0.45f + f * 0.2f + specks * 0.12f
        val crumb = n.ridged(x / s * 40f, y / s * 40f, 2, 2f, 0.6f)
        rgb[off] = pack(
            ((96 + base * 44) * (0.9f + crumb * 0.25f) + r() * 8).toInt(),
            ((60 + base * 30) * (0.9f + crumb * 0.25f) + r() * 8).toInt(),
            ((38 + base * 20) * (0.9f + crumb * 0.22f)).toInt(), 255)
        base * 0.7f + crumb * 0.5f
    }

    private val stone = PixelGen { x, y, s, n, r, rgb, off ->
        val ridged = n.ridged(x / s * 5f, y / s * 5f, 4, 2f, 0.55f)
        val fine = n.fbm(x / s * 22f, y / s * 22f, 3, 2f, 0.5f)
        val v = 0.55f + ridged * 0.28f + fine * 0.12f
        val q = v * 255
        rgb[off] = pack((q * 0.82f + r() * 10).toInt(), (q * 0.83f + r() * 10).toInt(),
            (q * 0.86f + r() * 8).toInt(), 255)
        ridged * 1.1f + fine * 0.2f
    }

    private val cobble = PixelGen { x, y, s, n, r, rgb, off ->
        val cell = n.fbm(x / s * 3.3f, y / s * 3.3f, 3, 2f, 0.5f)
        val ridge = n.ridged(x / s * 18f, y / s * 18f, 3, 2f, 0.6f)
        val v = 0.5f + cell * 0.22f + ridge * 0.12f
        val q = v * 255
        rgb[off] = pack((q * 0.78f + r() * 12).toInt(), (q * 0.79f + r() * 12).toInt(),
            (q * 0.8f + r() * 10).toInt(), 255)
        ridge * 1.3f + cell * 0.2f
    }

    private val woodSide = PixelGen { x, y, s, n, r, rgb, off ->
        val groove = n.fbm(y / s * 9f, x / s * 1.6f, 3, 2f, 0.5f)
        val lines = (sin(y / s * Math.PI.toFloat() * 10f + groove * 3f) * 0.5f + 0.5f)
        val grain = n.fbm(x / s * 40f, y / s * 4f, 3, 2f, 0.5f)
        val v = 0.5f + lines * 0.2f + grain * 0.14f
        rgb[off] = pack(((120 + v * 70) + r() * 14).toInt(), ((74 + v * 42) + r() * 12).toInt(),
            ((34 + v * 22) + r() * 10).toInt(), 255)
        lines * 1.1f + grain * 0.3f
    }

    private val woodTop = PixelGen { x, y, s, n, r, rgb, off ->
        val ix = x.toFloat() / s; val iy = y.toFloat() / s
        val cx = 0.5f + n.fbm(ix * 3f, iy * 3f, 2, 2f, 0.5f) * 0.08f
        val cy = 0.5f + n.fbm(ix * 3f + 30f, iy * 3f + 30f, 2, 2f, 0.5f) * 0.08f
        val d = hypot((ix - cx).toDouble(), (iy - cy).toDouble()).toFloat()
        val ring = 0.5f + 0.5f * sin(d * Math.PI.toFloat() * 26f)
        val v = 0.45f + ring * 0.3f + n.fbm(ix * 46f, iy * 46f, 2, 2f, 0.5f) * 0.12f
        rgb[off] = pack(((122 + v * 66) + r() * 14).toInt(), ((76 + v * 40) + r() * 12).toInt(),
            ((36 + v * 20) + r() * 10).toInt(), 255)
        ring * 1.1f + 0.2f
    }

    private val plank = PixelGen { x, y, s, n, r, rgb, off ->
        val boards = 4
        val board = (y / (s / boards.toFloat())).toInt()
        val gap = (abs((y / (s / boards.toFloat())) - (board + 0.5f)) * 2f)
        val between = if (gap > 0.86f) 1f else 0f
        val grain = n.fbm(x / s * 34f, y / s * 6f + board, 3, 2f, 0.5f)
        val v = 0.5f + grain * 0.18f - between * 0.35f
        val shade = if (board % 2 == 0) 1f else 0.92f
        rgb[off] = pack(((134 + v * 60) * shade + r() * 12).toInt(), ((88 + v * 40) * shade + r() * 10).toInt(),
            ((46 + v * 22) * shade + r() * 8).toInt(), 255)
        grain * 0.7f + between * 1.4f
    }

    private val sand = PixelGen { x, y, s, n, r, rgb, off ->
        val ripple = sin((x + n.fbm(x / s * 5f, y / s * 5f, 2, 2f, 0.5f) * 20f) / s * Math.PI.toFloat() * 8f)
        val f = n.fbm(x / s * 18f, y / s * 18f, 3, 2f, 0.5f)
        val v = 0.5f + ripple * 0.08f + f * 0.1f
        rgb[off] = pack((204 + v * 46 + r() * 8).toInt(), (176 + v * 40 + r() * 8).toInt(),
            (116 + v * 30 + r() * 6).toInt(), 255)
        ripple * 0.5f + f * 0.5f
    }

    private val brick = PixelGen { x, y, s, n, r, rgb, off ->
        val rows = 4; val bw = s / 2f
        val row = (y / (s / rows.toFloat())).toInt()
        val offset = if (row % 2 == 0) 0f else bw / 2f
        val bx = (x + offset) % bw
        val mortar = if (bx < s / 26f || bx > bw - s / 26f || (y % (s / rows.toFloat())) < s / 26f) 1f else 0f
        val f = n.fbm(x / s * 14f, y / s * 14f + row, 3, 2f, 0.5f)
        val v = 0.5f + f * 0.14f - mortar * 0.5f
        rgb[off] = pack(((150 + v * 48) + r() * 10).toInt(), ((60 + v * 22) + r() * 8).toInt(),
            ((40 + v * 16) + r() * 6).toInt(), 255)
        f * 0.6f + mortar * 1.7f
    }

    private val snow = PixelGen { x, y, s, n, r, rgb, off ->
        val f = n.fbm(x / s * 20f, y / s * 20f, 3, 2f, 0.5f)
        val v = 235f + f * 16f + r() * 5f
        rgb[off] = pack(v.toInt(), v.toInt(), (v + 2).toInt().coerceAtMost(255), 255)
        f * 0.5f
    }

    private val leaves = PixelGen { x, y, s, n, r, rgb, off ->
        val blades = n.fbm(x / s * 24f, y / s * 24f, 4, 2f, 0.5f)
        val hole = n.ridged(x / s * 12f, y / s * 12f, 2, 2f, 0.6f)
        val v = 0.5f + blades * 0.2f
        var a = 235f + r() * 20f
        if (hole > 0.78f) a = 60f
        rgb[off] = pack((46 + v * 40).toInt(), (92 + v * 80).toInt(), (36 + v * 34).toInt(), a.toInt())
        blades * 0.9f + hole * 0.3f
    }

    private val water = PixelGen { x, y, s, n, r, rgb, off ->
        val wave = n.fbm(x / s * 7f, y / s * 7f, 4, 2f, 0.5f)
        val v = 0.5f + wave * 0.3f
        rgb[off] = pack((22 + v * 30).toInt(), (56 + v * 90).toInt(), (130 + v * 80).toInt(), 210)
        wave * 0.8f
    }

    // Expected Bedrock texture file path for an id.
    fun bedrockPath(id: String): String = when (id) {
        "grass_top" -> "textures/blocks/grass_top.png"
        "grass_side" -> "textures/blocks/grass_side.png"
        "grass_bottom" -> "textures/blocks/grass_bottom.png"
        "dirt" -> "textures/blocks/dirt.png"
        "stone" -> "textures/blocks/stone.png"
        "cobblestone" -> "textures/blocks/cobblestone.png"
        "oak_log_side" -> "textures/blocks/log_oak.png"
        "oak_log_top" -> "textures/blocks/log_oak_top.png"
        "oak_planks" -> "textures/blocks/planks_oak.png"
        "sand" -> "textures/blocks/sand.png"
        "bricks" -> "textures/blocks/brick.png"
        "snow" -> "textures/blocks/snow.png"
        "oak_leaves" -> "textures/blocks/leaves_oak.png"
        "water" -> "textures/blocks/water_still.png"
        else -> "textures/blocks/$id.png"
    }
}
