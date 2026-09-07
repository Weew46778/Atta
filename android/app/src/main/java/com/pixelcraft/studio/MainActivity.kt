package com.pixelcraft.studio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.pixelcraft.studio.databinding.ActivityMainBinding
import java.io.File

/**
 * PixelCraft Studio — Bedrock texture & shader pack generator.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private var seed: Int = 1337
    private var realism: Float = 0.75f
    private var relief: Float = 0.6f
    private var resolution: Int = 32
    private var currentBlock: String = "grass"

    private data class BlockDef(val label: String, val key: String)

    private val blocks = listOf(
        BlockDef("Grass Block", "grass"),
        BlockDef("Dirt", "dirt"),
        BlockDef("Coarse Dirt", "coarse_dirt"),
        BlockDef("Mycelium", "mycelium"),
        BlockDef("Moss Block", "moss_block"),
        BlockDef("Mud", "mud"),
        BlockDef("Stone", "stone"),
        BlockDef("Cobblestone", "cobblestone"),
        BlockDef("Mossy Cobble", "mossy_cobblestone"),
        BlockDef("Deepslate", "deepslate"),
        BlockDef("Tuff", "tuff"),
        BlockDef("Gravel", "gravel"),
        BlockDef("Basalt", "basalt"),
        BlockDef("Blackstone", "blackstone"),
        BlockDef("Sand", "sand"),
        BlockDef("Red Sand", "red_sand"),
        BlockDef("Clay", "clay"),
        BlockDef("Terracotta", "terracotta"),
        BlockDef("Sandstone", "sandstone"),
        BlockDef("Red Sandstone", "red_sandstone"),
        BlockDef("Oak Log", "oak_log"),
        BlockDef("Oak Planks", "oak_planks"),
        BlockDef("Oak Leaves", "oak_leaves"),
        BlockDef("Bricks", "bricks"),
        BlockDef("Snow", "snow"),
        BlockDef("Ice", "ice"),
        BlockDef("Packed Ice", "packed_ice"),
        BlockDef("Water", "water"),
        BlockDef("Netherrack", "netherrack"),
        BlockDef("Glowstone", "glowstone"),
        BlockDef("Obsidian", "obsidian"),
        BlockDef("Quartz", "quartz_block"),
        BlockDef("End Stone", "end_stone"),
        BlockDef("Magma", "magma"),
        BlockDef("Sponge", "sponge"),
        BlockDef("Blue Wool", "wool_blue"),
        BlockDef("Purple Wool", "wool_purple"),
        BlockDef("Prismarine", "prismarine"),
        BlockDef("Dark Prismarine", "dark_prismarine"),
        BlockDef("Sea Lantern", "sea_lantern"),
        BlockDef("Gold Ore", "gold_ore"),
        BlockDef("Iron Ore", "iron_ore"),
        BlockDef("Coal Ore", "coal_ore"),
        BlockDef("Diamond Ore", "diamond_ore"),
        BlockDef("Redstone Ore", "redstone_ore"),
        BlockDef("Emerald Ore", "emerald_ore"),
        BlockDef("Lapis Ore", "lapis_ore"),
        BlockDef("Copper Ore", "copper_ore"),
        BlockDef("Gold Block", "gold_block"),
        BlockDef("Iron Block", "iron_block"),
        BlockDef("Diamond Block", "diamond_block"),
        BlockDef("Emerald Block", "emerald_block"),
        BlockDef("Redstone Block", "redstone_block"),
        BlockDef("Lapis Block", "lapis_block"),
        BlockDef("Copper Block", "copper_block"),
        BlockDef("Netherite Block", "netherite_block"),
    ) + expandedBlocks()

    // Expanded block palette (batch 3): wood variants, colours, stone, nether & end.
    private fun expandedBlocks(): List<BlockDef> {
        val out = mutableListOf<BlockDef>()
        val woods = listOf(
            "spruce" to "Spruce", "birch" to "Birch", "jungle" to "Jungle", "acacia" to "Acacia",
            "dark_oak" to "Dark Oak", "mangrove" to "Mangrove", "cherry" to "Cherry",
            "crimson" to "Crimson", "warped" to "Warped"
        )
        for ((key, label) in woods) {
            out += BlockDef("$label Log", "${key}_log")
            out += BlockDef("$label Planks", "${key}_planks")
            if (key != "crimson" && key != "warped") out += BlockDef("$label Leaves", "leaves_$key")
        }
        val colors = listOf(
            "white" to "White", "orange" to "Orange", "magenta" to "Magenta", "light_blue" to "Light Blue",
            "yellow" to "Yellow", "lime" to "Lime", "pink" to "Pink", "gray" to "Gray",
            "light_gray" to "Light Gray", "cyan" to "Cyan", "purple" to "Purple", "blue" to "Blue",
            "brown" to "Brown", "green" to "Green", "red" to "Red", "black" to "Black"
        )
        for ((key, label) in colors) {
            out += BlockDef("$label Wool", "wool_$key")
            out += BlockDef("$label Concrete", "concrete_$key")
            out += BlockDef("$label Concrete Powder", "concrete_powder_$key")
            out += BlockDef("$label Terracotta", "terracotta_$key")
            out += BlockDef("$label Glazed Terracotta", "glazed_terracotta_$key")
        }
        out += BlockDef("Granite", "granite")
        out += BlockDef("Polished Granite", "polished_granite")
        out += BlockDef("Diorite", "diorite")
        out += BlockDef("Polished Diorite", "polished_diorite")
        out += BlockDef("Andesite", "andesite")
        out += BlockDef("Polished Andesite", "polished_andesite")
        out += BlockDef("Calcite", "calcite")
        out += BlockDef("Smooth Stone", "smooth_stone")
        out += BlockDef("Stone Bricks", "stone_bricks")
        out += BlockDef("Mossy Stone Bricks", "mossy_stone_bricks")
        out += BlockDef("Cracked Stone Bricks", "cracked_stone_bricks")
        out += BlockDef("Chiseled Stone Bricks", "chiseled_stone_bricks")
        out += BlockDef("Deepslate Bricks", "deepslate_bricks")
        out += BlockDef("Polished Deepslate", "polished_deepslate")
        out += BlockDef("Smooth Sandstone", "smooth_sandstone")
        out += BlockDef("Cut Sandstone", "cut_sandstone")
        out += BlockDef("Chiseled Sandstone", "chiseled_sandstone")
        out += BlockDef("End Stone Bricks", "end_stone_bricks")
        out += BlockDef("Soul Sand", "soul_sand")
        out += BlockDef("Soul Soil", "soul_soil")
        out += BlockDef("Crimson Nylium", "crimson_nylium")
        out += BlockDef("Warped Nylium", "warped_nylium")
        out += BlockDef("Nether Wart Block", "nether_wart_block")
        out += BlockDef("Warped Wart Block", "warped_wart_block")
        out += BlockDef("Shroomlight", "shroomlight")
        out += BlockDef("Nether Gold Ore", "nether_gold_ore")
        out += BlockDef("Nether Quartz Ore", "nether_quartz_ore")
        out += BlockDef("Ancient Debris", "ancient_debris")
        out += BlockDef("Purpur Block", "purpur_block")
        out += BlockDef("Purpur Pillar", "purpur_pillar")
        out += BlockDef("Bookshelf", "bookshelf")
        out += BlockDef("Hay Block", "hay_block")
        out += BlockDef("Bone Block", "bone_block")
        out += BlockDef("Honey Block", "honey_block")
        out += BlockDef("Dried Kelp Block", "dried_kelp_block")
        out += BlockDef("Slime Block", "slime_block")
        return out
    }

    private val resolutions = intArrayOf(16, 32, 64)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.packNameInput.setText("PixelCraft Realistic Pack")
        b.seedInput.setText("1337")

        // resolution spinner
        b.resolutionSpinner.adapter = spinnerAdapter(resolutions.map { "${it}x" })
        b.resolutionSpinner.setSelection(1) // 32x default
        b.resolutionSpinner.onItemSelectedListener = simpleSpinner {
            resolution = resolutions[it]
            regeneratePreview()
        }

        // block spinner
        b.blockSpinner.adapter = spinnerAdapter(blocks.map { it.label })
        b.blockSpinner.onItemSelectedListener = simpleSpinner {
            currentBlock = blocks[it].key
            regeneratePreview()
        }

        b.realismSeek.setOnSeekBarChangeListener(onSeek { v ->
            realism = v / 100f
            updateLabels()
            regeneratePreview()
        })
        b.reliefSeek.setOnSeekBarChangeListener(onSeek { v ->
            relief = v / 100f
            regeneratePreview()
        })

        b.randomizeBtn.setOnClickListener {
            seed = (Math.random() * 1_000_000_000).toInt() and 0x7FFFFFFF
            b.seedInput.setText(seed.toString())
            regeneratePreview()
        }
        b.seedInput.setOnEditorActionListener { _, _, _ ->
            seed = (b.seedInput.text.toString().toLongOrNull() ?: 0L).toInt()
            regeneratePreview()
            true
        }

        b.exportTextureBtn.setOnClickListener { export(kind = "texture") }
        b.exportShaderBtn.setOnClickListener { export(kind = "shader") }

        b.nightToggle.setOnCheckedChangeListener { _, isChecked ->
            b.blockPreview.setNightMode(isChecked)
        }

        updateLabels()
        regeneratePreview()
    }

    private fun spinnerAdapter(values: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            init {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

    private fun simpleSpinner(onSelected: (Int) -> Unit) =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (position >= 0) onSelected(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

    private fun onSeek(cb: (Int) -> Unit) =
        object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) cb(progress)
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        }

    private fun updateLabels() {
        b.realismLabel.text = "Realism / Detail  ·  ${(realism * 100).toInt()}%"
        b.reliefLabel.text = "Relief (bump strength)  ·  ${(relief * 100).toInt()}%"
    }

    private val logWoods = listOf("spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped")

    private fun faceIds(key: String): Triple<String, String, String> = when (key) {
        "grass" -> Triple("grass_top", "grass_side", "grass_bottom")
        "oak_log" -> Triple("oak_log_top", "oak_log_side", "oak_log_top")
        "mycelium" -> Triple("mycelium_top", "mycelium_side", "mycelium_top")
        else -> {
            val w = key.removeSuffix("_log")
            if (key.endsWith("_log") && w in logWoods) Triple("${w}_log_top", "${w}_log_side", "${w}_log_top")
            else Triple(key, key, key)
        }
    }

    private fun regeneratePreview() {
        val (topId, sideId, bottomId) = faceIds(currentBlock)
        val top = TextureGenerator.generate(seed, resolution, topId, realism, relief)
        val side = TextureGenerator.generate(seed, resolution, sideId, realism, relief)
        val bottom = TextureGenerator.generate(seed, resolution, bottomId, realism, relief)

        b.blockPreview.setFaces(top.shaded, side.shaded, bottom.shaded)
        b.faceTop.setImageBitmap(top.shaded)
        b.faceSide.setImageBitmap(side.shaded)
        b.faceBottom.setImageBitmap(bottom.shaded)
    }

    private fun export(kind: String) {
        val name = b.packNameInput.text.toString()
        Toast.makeText(this, "Generating pack…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val file = if (kind == "texture") {
                    PackExporter.exportTexturePack(this, name, seed, resolution, realism, relief)
                } else {
                    PackExporter.exportShaderPack(this, name, realism)
                }
                runOnUiThread {
                    Toast.makeText(this, "Saved: ${file.name}", Toast.LENGTH_LONG).show()
                    share(file)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun share(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(this, "com.pixelcraft.studio.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share / open with Minecraft"))
        } catch (e: Exception) {
            Toast.makeText(this, "Path: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
}
