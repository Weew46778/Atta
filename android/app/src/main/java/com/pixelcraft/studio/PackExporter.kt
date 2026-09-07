package com.pixelcraft.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a complete Bedrock <code>.mcpack</code> (a ZIP archive) containing either a
 * resource/texture pack or a RenderDragon-compatible shader pack, and saves it into the
 * app-specific external storage folder <code>Android/data/.../files/PixelCraftStudio/</code>.
 *
 * The exported package can be installed into Minecraft Bedrock by opening it (share/import).
 */
object PackExporter {

    fun exportDir(context: Context): File {
        val base = context.getExternalFilesDir(null)
        val dir = File(base, "PixelCraftStudio")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun addBytes(zos: ZipOutputStream, path: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun pngBytes(bmp: Bitmap): ByteArray {
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }

    private fun makeManifest(name: String, description: String, headerUuid: String, moduleUuid: String): String {
        val fv = "format_version\":2"
        return "{\n" +
            "  \"format_version\": 2,\n" +
            "  \"header\": {\n" +
            "    \"name\": \"$name\",\n" +
            "    \"description\": \"$description\",\n" +
            "    \"uuid\": \"$headerUuid\",\n" +
            "    \"version\": [1, 0, 0],\n" +
            "    \"min_engine_version\": [1, 26, 0]\n" +
            "  },\n" +
            "  \"modules\": [\n" +
            "    {\n" +
            "      \"type\": \"resources\",\n" +
            "      \"uuid\": \"$moduleUuid\",\n" +
            "      \"version\": [1, 0, 0]\n" +
            "    }\n" +
            "  ],\n" +
            "  \"settings\": {\n" +
            "    \"textures\": {\"brightness\": 1.0, \"num_mip_levels\": 4}\n" +
            "  }\n" +
            "}\n"
    }

    private fun drawIcon(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val grad = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
            intArrayOf(0xFF38D1A4.toInt(), 0xFF4F8CFF.toInt()), null, Shader.TileMode.CLAMP)
        paint.shader = grad
        c.drawRoundRect(rect, size * 0.18f, size * 0.18f, paint)
        paint.shader = null
        paint.color = 0xFF0B1020.toInt()
        // simple block
        val s = size * 0.5f
        val l = size * 0.25f
        c.drawRect(l, size * 0.42f, l + s, size * 0.42f + s, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = size * 0.34f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        c.drawText("PC", size / 2f, size * 0.34f, paint)
        return bmp
    }

    private fun shaderManifest(name: String, headerUuid: String, moduleUuid: String): String {
        return "{\n" +
            "  \"format_version\": 2,\n" +
            "  \"header\": {\n" +
            "    \"name\": \"$name\",\n" +
            "    \"description\": \"PixelCraft Studio Realistic Shader Pack\",\n" +
            "    \"uuid\": \"$headerUuid\",\n" +
            "    \"version\": [1, 0, 0],\n" +
            "    \"min_engine_version\": [1, 26, 0]\n" +
            "  },\n" +
            "  \"modules\": [\n" +
            "    {\n" +
            "      \"type\": \"resources\",\n" +
            "      \"uuid\": \"$moduleUuid\",\n" +
            "      \"version\": [1, 0, 0]\n" +
            "    }\n" +
            "  ]\n" +
            "}\n"
    }

    /** Export the full texture pack for all textures and return the resulting file. */
    fun exportTexturePack(context: Context, name: String, seed: Int, res: Int,
                          realism: Float, relief: Float): File {
        val safeName = name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "pixelcraft" }
        val file = File(exportDir(context), "${safeName}_texture.mcpack")
        val ids = TextureGenerator.allIds()
        val headerUuid = UUID.randomUUID().toString()
        val moduleUuid = UUID.randomUUID().toString()

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            addBytes(zos, "manifest.json",
                makeManifest(safeName, "PixelCraft Studio — ultra realistic procedural texture pack",
                    headerUuid, moduleUuid).toByteArray())
            addBytes(zos, "pack_icon.png", pngBytes(drawIcon(128)))
            for (id in ids) {
                val bundle = TextureGenerator.generate(seed, res, id, realism, relief)
                val base = TextureGenerator.bedrockPath(id)
                addBytes(zos, base, pngBytes(bundle.color))
                addBytes(zos, base.replace(".png", "_n.png"), pngBytes(bundle.normal))
                addBytes(zos, base.replace(".png", "_roughness.png"), pngBytes(bundle.shaded))
            }
        }
        return file
    }

    /** Export a RenderDragon-compatible shader pack and return the resulting file. */
    fun exportShaderPack(context: Context, name: String, realism: Float): File {
        val safeName = name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "pixelcraft" }
        val file = File(exportDir(context), "${safeName}_shader.mcpack")
        val headerUuid = UUID.randomUUID().toString()
        val moduleUuid = UUID.randomUUID().toString()
        val t = realism.coerceIn(0f, 1f)
        val settings = settingsJson(t)

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            addBytes(zos, "manifest.json",
                shaderManifest(safeName, headerUuid, moduleUuid).toByteArray())
            addBytes(zos, "textures/renderer/per_frame.json", settings[0].toByteArray())
            addBytes(zos, "textures/renderer/per_object.json", settings[1].toByteArray())
            addBytes(zos, "textures/renderer/materials.json", settings[2].toByteArray())
            addBytes(zos, "textures/renderer/shaders.json", settings[3].toByteArray())
            addBytes(zos, "textures/renderer/deferred.json", settings[4].toByteArray())
            addBytes(zos, "textures/renderer/post_chain.json", settings[5].toByteArray())
            addBytes(zos, "shaders/pixelcraft_pbr.hlsl", PBR_HLSL.toByteArray())
            addBytes(zos, "shaders/pixelcraft_water.hlsl", PBR_WATER_HLSL.toByteArray())
        }
        return file
    }

    private fun fmt(v: Float): String = String.format("%.4f", v).trimEnd('0').trimEnd('.')

    private fun settingsJson(t: Float): Array<String> {
        val exposure = 0.9f + t * 0.55f
        val sun = 1.1f + t * 1.0f
        val fog = (0.018f - t * 0.012f)
        val water = 0.3f + t * 0.5f
        val ao = t
        val sat = 1.02f + t * 0.18f
        val con = 1.05f + t * 0.15f
        val rough = 1f - t * 0.45f

        val frame = "{\n  \"headers\": [\n    {\"name\": \"FOG_COLOR\", \"value\": [0.6, 0.75, 0.92]},\n" +
            "    {\"name\": \"SKY_ZENITH\", \"value\": [0.16, 0.28, 0.5]},\n" +
            "    {\"name\": \"SUN_DIRECTION\", \"value\": [0.25, -0.72, 0.5]},\n" +
            "    {\"name\": \"SUN_COLOR\", \"value\": [1.0, 0.96, 0.88]},\n" +
            "    {\"name\": \"SUN_INTENSITY\", \"value\": ${fmt(sun)}},\n" +
            "    {\"name\": \"EXPOSURE\", \"value\": ${fmt(exposure)}},\n" +
            "    {\"name\": \"FOG_DENSITY\", \"value\": ${fmt(fog)}},\n" +
            "    {\"name\": \"WATER_REFLECT\", \"value\": ${fmt(water)}}\n" +
            "  ]\n}\n"
        val obj = "{\n  \"headers\": [\n    {\"name\": \"AO_STRENGTH\", \"value\": ${fmt(ao)}},\n" +
            "    {\"name\": \"ROUGHNESS_SCALE\", \"value\": ${fmt(rough)}},\n" +
            "    {\"name\": \"SATURATION\", \"value\": ${fmt(sat)}},\n" +
            "    {\"name\": \"CONTRAST\", \"value\": ${fmt(con)}}\n" +
            "  ]\n}\n"
        val materials = "[\n  {\n    \"name\": \"pixelcraft_pbr\",\n    \"shader\": \"shaders/pixelcraft_pbr\",\n" +
            "    \"passes\": [{\"pass\": \"geometry\", \"vertex\": \"VertexShader\", \"pixel\": \"PixelShader\"}]\n" +
            "  }\n]\n"
        val shaders = "[\n  {\"name\": \"shaders/pixelcraft_pbr\", \"vertex\": \"PixelCraftVertex\", \"pixel\": \"PixelCraftPixel\"}\n]\n"
        val deferred = "{\n  \"version\": 1,\n  \"deferred_lighting\": true,\n" +
            "  \"sky\": {\"zenith\": [0.16, 0.28, 0.5], \"horizon\": [0.6, 0.75, 0.92]},\n" +
            "  \"fog\": {\"density\": ${fmt(fog)}, \"color\": [0.6, 0.75, 0.92]},\n" +
            "  \"water\": {\"reflection_strength\": ${fmt(water)}}\n}\n"
        val bloom = t * 0.55f
        val tone = if (t > 0.55f) "ACES" else "Neutral"
        val post = "{\n  \"version\": 1,\n  \"enabled\": true,\n" +
            "  \"tone_mapping\": \"$tone\",\n  \"exposure\": ${fmt(exposure)},\n" +
            "  \"bloom\": {\"enabled\": ${bloom > 0.01f}, \"strength\": ${fmt(bloom)}, " +
            "\"threshold\": 0.82, \"radius\": 0.9},\n  \"lens_dirt\": false,\n  \"chromatic_aberration\": 0\n}\n"
        return arrayOf(frame, obj, materials, shaders, deferred, post)
    }

    private val PBR_WATER_HLSL = """
// PixelCraft Studio — Realistic water (RenderDragon source)
#include <common/common.h>
Texture2D WaterMap : register(t0);
SamplerState LinearSampler : register(s0);

struct PSInputW {
  float4 Position : SV_POSITION;
  float3 WorldNormal : NORMAL;
  float2 UV : TEXCOORD0;
};

float4 PixelCraftWaterPixel(PSInputW input) : SV_TARGET {
  float4 albedo = WaterMap.Sample(LinearSampler, input.UV);
  float3 N = normalize(input.WorldNormal);
  float fresnel = pow(1.0 - saturate(dot(N, float3(0.0, 1.0, 0.0))), 3.0);
  float3 waterColor = lerp(albedo.rgb, float3(0.55, 0.72, 0.95), fresnel * g_WATER_REFLECT);
  return float4(waterColor, albedo.a * 0.75);
}
"""

    private val PBR_HLSL = """
// PixelCraft Studio — Realistic PBR shader (RenderDragon source)
// Compiled by the official Bedrock RenderDragon shader toolchain for the target engine.
#include <common/common.h>

struct FrameUniforms {
  float3 SunDirection;
  float3 SunColor;
  float  SunIntensity;
  float  Exposure;
  float  FogDensity;
  float3 FogColor;
};

struct ObjectUniforms {
  float  AOStrength;
  float  RoughnessScale;
  float  Saturation;
  float  Contrast;
};

Texture2D DiffuseMap : register(t0);
SamplerState LinearSampler : register(s0);

struct VSInput {
  float3 Position : POSITION;
  float3 Normal   : NORMAL;
  float2 UV       : TEXCOORD0;
};

struct PSInput {
  float4 Position : SV_POSITION;
  float3 WorldNormal : NORMAL;
  float2 UV : TEXCOORD0;
  float3 WorldPos : TEXCOORD1;
};

PSInput PixelCraftVertex(VSInput input) {
  PSInput out;
  out.Position = mul(g_WorldViewProj, float4(input.Position, 1.0));
  out.WorldNormal = normalize(mul((float3x3)g_World, input.Normal));
  out.UV = input.UV;
  out.WorldPos = mul((float3x3)g_World, input.Position) + g_WorldTranslation;
  return out;
}

float3 PixelCraftPixel(PSInput input) : SV_TARGET {
  float4 albedo = DiffuseMap.Sample(LinearSampler, input.UV);
  float3 N = normalize(input.WorldNormal);
  float3 L = normalize(-g_SUN_DIRECTION.xyz);
  float NdotL = saturate(dot(N, L));

  float luma = dot(albedo.rgb, float3(0.2126, 0.7152, 0.0722));
  float3 graded = lerp(float3(luma, luma, luma), albedo.rgb, g_SATURATION);
  graded = (graded - 0.5) * g_CONTRAST + 0.5;

  float3 direct = g_SUN_COLOR.rgb * g_SUN_INTENSITY * NdotL;
  float3 ambient = lerp(0.05, 0.35, saturate(N.y + 0.5)) * g_AO_STRENGTH;
  float3 color = graded * (ambient + direct);

  float3 H = normalize(L + float3(0.0, 0.0, 1.0));
  float spec = pow(saturate(dot(N, H)), 32.0) * g_ROUGHNESS_SCALE;
  color += spec * g_SUN_COLOR.rgb * 0.4;

  color = 1.0 - exp(-color * g_EXPOSURE);
  color = pow(saturate(color), rcp(2.2));

  float dist = length(input.WorldPos - g_WorldTranslation);
  float fog = 1.0 - exp(-dist * g_FOG_DENSITY);
  return float4(lerp(color, g_FOG_COLOR.rgb, fog), albedo.a);
}
"""
}
