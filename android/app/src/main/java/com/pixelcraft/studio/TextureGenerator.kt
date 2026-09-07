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

        // For grass_side / mycelium_side we overlay a fringe over a precomputed dirt map (built once).
        val gen: PixelGen = if (id == "grass_side" || id == "mycelium_side") {
            val dirtB = generate(seed + 7, size, "dirt", realism, relief)
            PixelGen { x, y, s, n, r, rgb, off ->
                grassSidePixel(x, y, s, n, r, rgb, off, dirtB.colorInt, dirtB.height, id)
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
        // grass_side / mycelium_side are handled specially inside generate(); never hit here.
        "grass_side" -> stone
        "grass_bottom" -> dirt
        "dirt" -> dirt
        "coarse_dirt" -> coarseDirt
        "stone" -> stone
        "cobblestone" -> cobble
        "deepslate" -> deepslate
        "tuff" -> tuff
        "gravel" -> gravel
        "sand" -> sand
        "red_sand" -> redSand
        "clay" -> clay
        "bricks" -> brick
        "oak_log_side" -> woodSide
        "oak_log_top" -> woodTop
        "oak_planks" -> plank
        "oak_leaves" -> leaves
        "snow" -> snow
        "ice" -> ice
        "packed_ice" -> packedIce
        "water" -> water
        "netherrack" -> netherrack
        "glowstone" -> glowstone
        "obsidian" -> obsidian
        "quartz_block" -> quartz
        "end_stone" -> endStone
        "magma" -> magma
        "sponge" -> sponge
        "wool_blue" -> blueWool
        "wool_purple" -> purpleWool
        "terracotta" -> terracotta
        "mycelium_top" -> myceliumTop
        "mycelium_side" -> stone
        "gold_ore" -> ore(232, 190, 70)
        "iron_ore" -> ore(214, 150, 112)
        "coal_ore" -> ore(40, 40, 46)
        "diamond_ore" -> ore(170, 232, 235)
        "redstone_ore" -> ore(226, 40, 40)
        "emerald_ore" -> ore(60, 220, 90)
        "lapis_ore" -> ore(50, 90, 220)
        "copper_ore" -> ore(214, 120, 90)
        "gold_block" -> metal(220f, 170f, 40f)
        "iron_block" -> metal(200f, 202f, 208f)
        "diamond_block" -> metal(140f, 220f, 232f)
        "emerald_block" -> metal(60f, 200f, 120f)
        "redstone_block" -> metal(200f, 40f, 40f)
        "lapis_block" -> metal(40f, 70f, 180f)
        "copper_block" -> metal(196f, 116f, 82f)
        "netherite_block" -> metal(70f, 64f, 74f)
        "sandstone" -> sand(190f, 176f, 120f)
        "red_sandstone" -> sand(168f, 86f, 54f)
        "mossy_cobblestone" -> mossyCobble
        "moss_block" -> springMoss
        "mud" -> mud
        "packed_mud" -> mud
        "basalt_side" -> basaltSide
        "blackstone" -> PixelGen { x, y, s, n, r, rgb, off -> blackstonePixel(x, y, s, n, r, rgb, off, false) }
        "gilded_blackstone" -> PixelGen { x, y, s, n, r, rgb, off -> blackstonePixel(x, y, s, n, r, rgb, off, true) }
        "prismarine" -> prism(false)
        "dark_prismarine" -> prism(true)
        "sea_lantern" -> seaLantern
        else -> stone
    }

    fun allIds(): Array<String> = arrayOf(
        "grass_top", "grass_side", "grass_bottom", "dirt", "coarse_dirt", "stone", "cobblestone",
        "deepslate", "tuff", "gravel", "sand", "red_sand", "clay", "bricks",
        "oak_log_side", "oak_log_top", "oak_planks", "oak_leaves", "snow", "ice", "packed_ice",
        "water", "netherrack", "glowstone", "obsidian", "quartz_block", "end_stone", "magma",
        "sponge", "wool_blue", "wool_purple", "terracotta", "mycelium_top", "mycelium_side",
        "gold_ore", "iron_ore", "coal_ore", "diamond_ore", "redstone_ore", "emerald_ore",
        "lapis_ore", "copper_ore", "gold_block", "iron_block", "diamond_block", "emerald_block",
        "redstone_block", "lapis_block", "copper_block", "netherite_block", "sandstone",
        "red_sandstone", "mossy_cobblestone", "moss_block", "mud", "packed_mud", "basalt_side",
        "blackstone", "gilded_blackstone", "prismarine", "dark_prismarine", "sea_lantern"
    )

    fun title(id: String): String = when (id) {
        "grass_top" -> "Grass Top"
        "grass_side" -> "Grass Side"
        "grass_bottom" -> "Grass Bottom"
        "dirt" -> "Dirt"
        "coarse_dirt" -> "Coarse Dirt"
        "stone" -> "Stone"
        "cobblestone" -> "Cobblestone"
        "deepslate" -> "Deepslate"
        "tuff" -> "Tuff"
        "gravel" -> "Gravel"
        "oak_log_side" -> "Oak Log Side"
        "oak_log_top" -> "Oak Log Top"
        "oak_planks" -> "Oak Planks"
        "sand" -> "Sand"
        "red_sand" -> "Red Sand"
        "clay" -> "Clay"
        "bricks" -> "Bricks"
        "snow" -> "Snow"
        "ice" -> "Ice"
        "packed_ice" -> "Packed Ice"
        "oak_leaves" -> "Oak Leaves"
        "water" -> "Water"
        "netherrack" -> "Netherrack"
        "glowstone" -> "Glowstone"
        "obsidian" -> "Obsidian"
        "quartz_block" -> "Quartz"
        "end_stone" -> "End Stone"
        "magma" -> "Magma"
        "sponge" -> "Sponge"
        "wool_blue" -> "Blue Wool"
        "wool_purple" -> "Purple Wool"
        "terracotta" -> "Terracotta"
        "mycelium_top" -> "Mycelium Top"
        "mycelium_side" -> "Mycelium Side"
        else -> id
    }

    fun block(id: String): String = when (id) {
        "grass_top", "grass_side", "grass_bottom" -> "grass"
        "oak_log_side", "oak_log_top" -> "oak_log"
        "mycelium_top", "mycelium_side" -> "mycelium"
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

    // Grass-side / mycelium-side use a precomputed dirt map provided by generate().
    // `kind` selects the fringe colour: "grass" (green blades) or "mycelium" (grey speckle).
    private fun grassSidePixel(x: Int, y: Int, s: Int, n: Noise, r: () -> Float,
                               rgb: IntArray, off: Int, dirtColor: IntArray, dirtHeight: FloatArray,
                               kind: String): Float {
        val fringe = 1f - y.toFloat() / s
        val top = fringe.pow(0.55f)
        val blades = n.fbm(x / s * 26f, 0f, 4, 2f, 0.5f)
        val dcr = (dirtColor[off] shr 16) and 0xFF
        val dcg = (dirtColor[off] shr 8) and 0xFF
        val dcb = dirtColor[off] and 0xFF
        val (fr, fg, fb) = if (kind == "mycelium")
            Triple(140f + blades * 60f, 132f + blades * 50f, 128f + blades * 46f)
        else
            Triple(70f + blades * 90f, 128f + blades * 44f, 58f + blades * 30f)
        rgb[off] = pack(
            (dcr * (1 - top) + fr * top).toInt(),
            (dcg * (1 - top) + fg * top).toInt(),
            (dcb * (1 - top) + fb * top).toInt(), 255)
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

    // ---- extra blocks (30 total) ----
    private val coarseDirt = PixelGen { x, y, s, n, r, rgb, off ->
        val peb = n.ridged(x / s * 9f, y / s * 9f, 3, 2f, 0.6f)
        val f = n.fbm(x / s * 11f, y / s * 11f, 5, 2f, 0.55f)
        val base = 0.45f + f * 0.2f
        val rock = if (peb > 0.55f) 1f else 0f
        rgb[off] = pack(((96 + base * 44) * (0.9f + peb * 0.25f) + r() * 8 + rock * 22).toInt(),
            ((60 + base * 30) * (0.9f + peb * 0.25f) + r() * 8 + rock * 20).toInt(),
            ((38 + base * 20) * (0.9f + peb * 0.22f) + rock * 18).toInt(), 255)
        base * 0.7f + peb * 0.9f
    }

    private val deepslate = PixelGen { x, y, s, n, r, rgb, off ->
        val ridged = n.ridged(x / s * 6f, y / s * 6f, 4, 2f, 0.55f)
        val strata = (sin(y / s * Math.PI.toFloat() * 6f + ridged * 2f) * 0.5f + 0.5f)
        val fine = n.fbm(x / s * 24f, y / s * 24f, 3, 2f, 0.5f)
        val v = 0.4f + ridged * 0.22f + strata * 0.14f + fine * 0.08f
        val q = v * 255
        rgb[off] = pack((q * 0.44f + r() * 6).toInt(), (q * 0.45f + r() * 6).toInt(),
            (q * 0.5f + r() * 5).toInt(), 255)
        ridged * 1.1f + strata * 0.6f + fine * 0.1f
    }

    private val tuff = PixelGen { x, y, s, n, r, rgb, off ->
        val f = n.fbm(x / s * 14f, y / s * 14f, 5, 2f, 0.5f)
        val spots = n.ridged(x / s * 22f, y / s * 22f, 2, 2f, 0.6f)
        val v = 0.5f + f * 0.18f + spots * 0.12f
        val q = v * 255
        rgb[off] = pack((q * 0.68f + r() * 8).toInt(), (q * 0.69f + r() * 8).toInt(),
            (q * 0.72f + r() * 6).toInt(), 255)
        f * 0.7f + spots * 0.6f
    }

    private val gravel = PixelGen { x, y, s, n, r, rgb, off ->
        val peb = n.ridged(x / s * 8f, y / s * 8f, 3, 2f, 0.7f)
        val f = n.fbm(x / s * 26f, y / s * 26f, 2, 2f, 0.5f)
        val grey = 0.5f + peb * 0.22f + f * 0.1f
        val warm = r() * 0.2f
        rgb[off] = pack(((120 + grey * 70) * (1 - warm) + (130 + grey * 50) * warm + r() * 14).toInt(),
            ((112 + grey * 64) * (1 - warm) + (104 + grey * 46) * warm + r() * 12).toInt(),
            ((104 + grey * 60) * (1 - warm) + (86 + grey * 44) * warm + r() * 10).toInt(), 255)
        peb * 1.3f + f * 0.2f
    }

    private val redSand = PixelGen { x, y, s, n, r, rgb, off ->
        val ripple = sin((x + n.fbm(x / s * 5f, y / s * 5f, 2, 2f, 0.5f) * 20f) / s * Math.PI.toFloat() * 8f)
        val f = n.fbm(x / s * 18f, y / s * 18f, 3, 2f, 0.5f)
        val v = 0.5f + ripple * 0.08f + f * 0.1f
        rgb[off] = pack((176 + v * 46 + r() * 8).toInt(), (92 + v * 30 + r() * 7).toInt(),
            (58 + v * 22 + r() * 6).toInt(), 255)
        ripple * 0.5f + f * 0.5f
    }

    private val clay = PixelGen { x, y, s, n, r, rgb, off ->
        val f = n.fbm(x / s * 8f, y / s * 8f, 4, 2f, 0.5f)
        val v = 0.5f + f * 0.1f
        rgb[off] = pack((176 + v * 32 + r() * 5).toInt(), (158 + v * 30 + r() * 5).toInt(),
            (146 + v * 28 + r() * 5).toInt(), 255)
        f * 0.4f
    }

    private val ice = PixelGen { x, y, s, n, r, rgb, off ->
        val streak = n.fbm(x / s * 6f, y / s * 2f, 4, 2f, 0.5f)
        val v = 0.5f + streak * 0.22f
        rgb[off] = pack((168 + v * 40 + r() * 8).toInt(), (214 + v * 30 + r() * 8).toInt(),
            (236 + v * 18 + r() * 6).toInt(), 200)
        streak * 0.8f
    }

    private val packedIce = PixelGen { x, y, s, n, r, rgb, off ->
        val f = n.fbm(x / s * 14f, y / s * 14f, 3, 2f, 0.5f)
        val v = 0.5f + f * 0.12f
        rgb[off] = pack((196 + v * 30 + r() * 6).toInt(), (226 + v * 24 + r() * 6).toInt(),
            (246 + v * 10 + r() * 5).toInt(), 230)
        f * 0.5f
    }

    private val netherrack = PixelGen { x, y, s, n, r, rgb, off ->
        val ridged = n.ridged(x / s * 9f, y / s * 9f, 4, 2f, 0.55f)
        val f = n.fbm(x / s * 20f, y / s * 20f, 3, 2f, 0.5f)
        val v = 0.5f + ridged * 0.26f + f * 0.1f
        rgb[off] = pack((128 + v * 60 + r() * 8).toInt(), (42 + v * 26 + r() * 6).toInt(),
            (34 + v * 20 + r() * 5).toInt(), 255)
        ridged * 1.2f + f * 0.2f
    }

    private val glowstone = PixelGen { x, y, s, n, r, rgb, off ->
        val glow = n.fbm(x / s * 10f, y / s * 10f, 3, 2f, 0.6f)
        val spots = n.ridged(x / s * 18f, y / s * 18f, 2, 2f, 0.7f)
        val v = 0.5f + glow * 0.24f
        val g = if (spots > 0.72f) 1f else 0f
        rgb[off] = pack((200 + v * 40 + r() * 16 + g * 30).toInt(), (168 + v * 36 + r() * 14 + g * 26).toInt(),
            (96 + v * 30 + r() * 12 + g * 20).toInt(), 255)
        glow * 0.7f + spots * 1.2f
    }

    private val obsidian = PixelGen { x, y, s, n, r, rgb, off ->
        val sheen = n.fbm(x / s * 8f, y / s * 8f, 3, 2f, 0.5f)
        val v = 0.18f + sheen * 0.14f
        rgb[off] = pack((14 + v * 60 + r() * 5).toInt(), (12 + v * 52 + r() * 5).toInt(),
            (28 + v * 74 + r() * 6).toInt(), 255)
        sheen * 0.7f
    }

    private val quartz = PixelGen { x, y, s, n, r, rgb, off ->
        val f = n.fbm(x / s * 10f, y / s * 10f, 3, 2f, 0.5f)
        val v = 0.5f + f * 0.09f
        rgb[off] = pack((224 + v * 24 + r() * 4).toInt(), (216 + v * 24 + r() * 4).toInt(),
            (204 + v * 24 + r() * 4).toInt(), 255)
        f * 0.4f
    }

    private val endStone = PixelGen { x, y, s, n, r, rgb, off ->
        val f = n.fbm(x / s * 10f, y / s * 10f, 4, 2f, 0.5f)
        val v = 0.5f + f * 0.13f
        rgb[off] = pack((204 + v * 34 + r() * 8).toInt(), (188 + v * 32 + r() * 8).toInt(),
            (134 + v * 30 + r() * 7).toInt(), 255)
        f * 0.7f
    }

    private val magma = PixelGen { x, y, s, n, r, rgb, off ->
        val crack = n.ridged(x / s * 8f, y / s * 8f, 3, 2f, 0.7f)
        val glow = if (crack > 0.62f) 1f else 0f
        val f = n.fbm(x / s * 20f, y / s * 20f, 3, 2f, 0.5f)
        val v = 0.5f + f * 0.12f
        rgb[off] = pack((96 + v * 34 + r() * 8 + glow * 90).toInt(), (30 + v * 18 + r() * 6 + glow * 46).toInt(),
            (22 + v * 12 + r() * 5 + glow * 22).toInt(), 255)
        f * 0.5f + crack * 1.5f
    }

    private val sponge = PixelGen { x, y, s, n, r, rgb, off ->
        val hole = n.ridged(x / s * 14f, y / s * 14f, 3, 2f, 0.7f)
        val f = n.fbm(x / s * 24f, y / s * 24f, 2, 2f, 0.5f)
        val v = 0.5f + f * 0.12f
        val isHole = if (hole > 0.62f) 0.6f else 1f
        rgb[off] = pack(((206 + v * 30 + r() * 12) * isHole).toInt(),
            ((176 + v * 28 + r() * 10) * isHole).toInt(),
            ((78 + v * 22 + r() * 8) * isHole).toInt(), 255)
        f * 0.5f + hole * 1.6f
    }

    private fun woolGen(seed: Int, r0: Int, r1: Int, r2: Int): PixelGen =
        PixelGen { x, y, s, n, r, rgb, off ->
            val weave = 0.5f + 0.5f * sin((x + y * 0.5f) / s * Math.PI.toFloat() * 26f +
                n.fbm(x / s * 20f, y / s * 20f, 2, 2f, 0.5f) * 4f)
            val f = n.fbm(x / s * 30f, y / s * 30f, 2, 2f, 0.5f)
            val v = 0.5f + weave * 0.18f + f * 0.1f
            rgb[off] = pack((r0 + v * (96 - r0) + r() * 12).toInt(),
                (r1 + v * (110 - r1) + r() * 12).toInt(),
                (r2 + v * (150 - r2) + r() * 10).toInt(), 255)
            weave * 1.1f + f * 0.2f
        }

    private val blueWool = woolGen(7, 40, 60, 150)
    private val purpleWool = woolGen(9, 110, 40, 150)

    private val terracotta = PixelGen { x, y, s, n, r, rgb, off ->
        val f = n.fbm(x / s * 8f, y / s * 8f, 3, 2f, 0.5f)
        val band = sin(y / s * Math.PI.toFloat() * 8f + f * 2f) * 0.5f + 0.5f
        val v = 0.5f + f * 0.1f + band * 0.08f
        rgb[off] = pack((150 + v * 44 + r() * 6).toInt(), (80 + v * 26 + r() * 5).toInt(),
            (56 + v * 20 + r() * 5).toInt(), 255)
        f * 0.5f + band * 0.4f
    }

    private val myceliumTop = PixelGen { x, y, s, n, r, rgb, off ->
        val speck = n.fbm(x / s * 20f, y / s * 20f, 4, 2f, 0.5f)
        val f = n.fbm(x / s * 10f, y / s * 10f, 4, 2f, 0.5f)
        val v = 0.5f + speck * 0.2f
        rgb[off] = pack((128 + v * 46 + r() * 10).toInt(), (118 + v * 40 + r() * 10).toInt(),
            (116 + v * 40 + r() * 10).toInt(), 255)
        f * 0.7f + speck * 0.5f
    }

    // ---- batch 2: ores, metals & more (photoreal) ----
    private fun ore(sr: Int, sg: Int, sb: Int): PixelGen = PixelGen { x, y, s, n, r, rgb, off ->
        val ridged = n.ridged(x / s * 5f, y / s * 5f, 4, 2f, 0.55f)
        val fine = n.fbm(x / s * 22f, y / s * 22f, 3, 2f, 0.5f)
        val v = 0.55f + ridged * 0.28f + fine * 0.12f
        val q = v * 255
        val cluster = n.ridged(x / s * 3f + 100f, y / s * 3f + 100f, 2, 2f, 0.7f)
        val spot = if (cluster > 0.7f) 1f else 0f
        val shade = 0.9f + ridged * 0.1f
        rgb[off] = pack((q * 0.82f * (1 - spot) * shade + sr * spot + r() * 6).toInt(),
            (q * 0.83f * (1 - spot) * shade + sg * spot + r() * 6).toInt(),
            (q * 0.86f * (1 - spot) * shade + sb * spot + r() * 5).toInt(), 255)
        ridged * 1.1f + fine * 0.2f + spot * 0.9f
    }

    private fun metal(m0: Float, m1: Float, m2: Float): PixelGen = PixelGen { x, y, s, n, r, rgb, off ->
        val brush = n.fbm(x / s * 30f, y / s * 3f, 4, 2f, 0.5f)
        val dent = n.ridged(x / s * 9f, y / s * 9f, 3, 2f, 0.7f)
        val v = 0.5f + brush * 0.22f - dent * 0.14f
        val sheen = Math.pow((Math.sin((x + y.toFloat()) / s * Math.PI.toFloat() * 3f + brush * 3f).toDouble()).coerceAtLeast(0.0), 6.0).toFloat() * 0.5f
        rgb[off] = pack((m0 * (0.62f + v * 0.5f) + sheen * 255 + r() * 8).toInt(),
            (m1 * (0.62f + v * 0.5f) + sheen * 250 + r() * 8).toInt(),
            (m2 * (0.62f + v * 0.5f) + sheen * 240 + r() * 8).toInt(), 255)
        brush * 0.9f + dent * 0.7f
    }

    private fun sand(rr: Float, rg: Float, rb: Float): PixelGen = PixelGen { x, y, s, n, r, rgb, off ->
        val band = (sin(y / s * Math.PI.toFloat() * 9f + n.fbm(x / s * 6f, y / s * 6f, 2, 2f, 0.5f) * 2f) * 0.5f + 0.5f)
        val f = n.fbm(x / s * 16f, y / s * 16f, 3, 2f, 0.5f)
        val v = 0.5f + band * 0.12f + f * 0.08f
        rgb[off] = pack((rr + v * 40 + r() * 6).toInt(), (rg + v * 36 + r() * 6).toInt(),
            (rb + v * 30 + r() * 5).toInt(), 255)
        band * 0.7f + f * 0.4f
    }

    private val mossyCobble = PixelGen { x, y, s, n, r, rgb, off ->
        val cell = n.fbm(x / s * 3.3f, y / s * 3.3f, 3, 2f, 0.5f)
        val ridge = n.ridged(x / s * 18f, y / s * 18f, 3, 2f, 0.6f)
        val mos = n.ridged(x / s * 12f, y / s * 12f, 3, 2f, 0.6f)
        val moss = mos > 0.42f
        val v = 0.5f + cell * 0.22f + ridge * 0.12f
        val q = v * 255
        rgb[off] = pack((if (moss) 70 + r() * 20 else q * 0.78f + r() * 12).toInt(),
            (if (moss) 104 + r() * 22 else q * 0.79f + r() * 12).toInt(),
            (if (moss) 58 + r() * 18 else q * 0.8f + r() * 10).toInt(), 255)
        ridge * 1.3f + cell * 0.2f + (if (moss) 0.8f else 0f)
    }

    private val springMoss = PixelGen { x, y, s, n, r, rgb, off ->
        val blades = n.fbm(x / s * 22f, y / s * 22f, 4, 2f, 0.5f)
        val hole = n.ridged(x / s * 12f, y / s * 12f, 2, 2f, 0.6f)
        val v = 0.5f + blades * 0.22f
        rgb[off] = pack((62 + v * 40 + r() * 10).toInt(), (96 + v * 60 + r() * 12).toInt(),
            (48 + v * 30 + r() * 8).toInt(), 255)
        blades * 0.9f + hole * 0.3f
    }

    private val mud = PixelGen { x, y, s, n, r, rgb, off ->
        val f = n.fbm(x / s * 9f, y / s * 9f, 5, 2f, 0.5f)
        val lumps = n.ridged(x / s * 22f, y / s * 22f, 2, 2f, 0.7f)
        val v = 0.5f + f * 0.2f + lumps * 0.14f
        rgb[off] = pack((92 + v * 40 + r() * 8).toInt(), (66 + v * 30 + r() * 7).toInt(),
            (48 + v * 22 + r() * 6).toInt(), 255)
        f * 0.8f + lumps * 0.9f
    }

    private val basaltSide = PixelGen { x, y, s, n, r, rgb, off ->
        val col = (sin(x / s * Math.PI.toFloat() * 10f + n.fbm(x / s * 20f, y / s * 2f, 3, 2f, 0.5f) * 2f) * 0.5f + 0.5f)
        val f = n.fbm(x / s * 16f, y / s * 16f, 3, 2f, 0.5f)
        val v = 0.4f + col * 0.26f + f * 0.12f
        val q = v * 255
        rgb[off] = pack((q * 0.42f + r() * 6).toInt(), (q * 0.42f + r() * 6).toInt(),
            (q * 0.46f + r() * 6).toInt(), 255)
        col * 1.2f + f * 0.3f
    }

    private fun blackstonePixel(x: Int, y: Int, s: Int, n: Noise, r: () -> Float,
                                rgb: IntArray, off: Int, gilded: Boolean): Float {
        val f = n.fbm(x / s * 10f, y / s * 10f, 4, 2f, 0.55f)
        val v = 0.24f + f * 0.2f
        val q = v * 255
        val gold = n.ridged(x / s * 6f + 30f, y / s * 6f + 30f, 3, 2f, 0.7f)
        val g = if (gilded && gold > 0.62f) 1f else 0f
        rgb[off] = pack((q * 0.5f + g * 200 + r() * 4).toInt(), (q * 0.5f + g * 175 + r() * 4).toInt(),
            (q * 0.56f + g * 70 + r() * 4).toInt(), 255)
        f * 0.9f + g * 1.4f
    }

    private fun prism(dark: Boolean): PixelGen = PixelGen { x, y, s, n, r, rgb, off ->
        val chip = n.fbm(x / s * 8f, y / s * 8f, 3, 2f, 0.6f)
        val v = 0.5f + chip * 0.2f
        val mult = if (dark) 0.72f else 1.0f
        rgb[off] = pack(((56 + chip * 30 + r() * 8) * mult).toInt(),
            ((120 + chip * 60 + r() * 10) * mult).toInt(),
            ((130 + chip * 60 + r() * 10) * mult).toInt(), 255)
        chip * 0.8f
    }

    private val seaLantern = PixelGen { x, y, s, n, r, rgb, off ->
        val grid = (Math.floor(x / (s / 6f)).toInt() % 2 == 0 && Math.floor(y / (s / 6f)).toInt() % 2 == 0)
        val f = n.fbm(x / s * 20f, y / s * 20f, 2, 2f, 0.5f)
        val v = 0.5f + f * 0.1f
        rgb[off] = pack((if (grid) 190 + v * 40 + r() * 8 else 40 + r() * 6).toInt(),
            (if (grid) 216 + v * 30 + r() * 8 else 42 + r() * 6).toInt(),
            (if (grid) 216 + v * 20 + r() * 8 else 46 + r() * 6).toInt(), 255)
        if (grid) 1.0f else f * 0.4f
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
        "sand" -> "textures/blocks/sand.png"
        "bricks" -> "textures/blocks/brick.png"
        "snow" -> "textures/blocks/snow.png"
        "oak_leaves" -> "textures/blocks/leaves_oak.png"
        "water" -> "textures/blocks/water_still.png"
        "quartz_block" -> "textures/blocks/quartz_block_top.png"
        "wool_blue" -> "textures/blocks/wool_colored_blue.png"
        "wool_purple" -> "textures/blocks/wool_colored_purple.png"
        "terracotta" -> "textures/blocks/hardened_clay.png"
        "mycelium_top" -> "textures/blocks/mycelium_top.png"
        "mycelium_side" -> "textures/blocks/mycelium_side.png"
        "mossy_cobblestone" -> "textures/blocks/cobblestone_mossy.png"
        "dark_prismarine" -> "textures/blocks/prismarine_dark.png"
        "basalt_side" -> "textures/blocks/basalt_side.png"
        else -> "textures/blocks/$id.png"
    }
}
