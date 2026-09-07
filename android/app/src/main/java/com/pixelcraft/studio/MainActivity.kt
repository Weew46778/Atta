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
        BlockDef("Stone", "stone"),
        BlockDef("Cobblestone", "cobblestone"),
        BlockDef("Oak Log", "oak_log"),
        BlockDef("Oak Planks", "oak_planks"),
        BlockDef("Sand", "sand"),
        BlockDef("Bricks", "bricks"),
        BlockDef("Snow", "snow"),
        BlockDef("Oak Leaves", "oak_leaves"),
        BlockDef("Water", "water"),
    )

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
            override fun onItemSelected(p: android.view.View?, pos: Int, id: Long) {
                if (pos >= 0) onSelected(pos)
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

    private fun faceIds(key: String): Triple<String, String, String> = when (key) {
        "grass" -> Triple("grass_top", "grass_side", "grass_bottom")
        "oak_log" -> Triple("oak_log_top", "oak_log_side", "oak_log_top")
        else -> Triple(key, key, key)
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
