<div align="center">

# 🎨 PixelCraft Studio

**بسازِ پک تکستچر و شیدر فوق‌واقع‌گرایانه برای Minecraft Bedrock (نسخه ۱.۲۶.۴۵.۱)**

An advanced, modern **texture-pack & shader-pack generator** for Minecraft Bedrock.
It procedurally synthesizes ultra-realistic 3D-looking block textures (with true depth,
protrusion, roughness, wrinkles and natural thickness), previews them on a **rotating
isometric 3D block right in the app**, and exports a complete, Bedrock-compatible
**`.mcpack`** resource pack — plus a **RenderDragon-compatible shader pack** (realistic,
**without ray-tracing**, driven by baked relief, normal maps and procedural lighting).

</div>

---

## ✨ Features

| Feature | Web preview | Android app |
|--------|:-:|:-:|
| Procedural realistic block textures (**201 blocks / 214 faces** — grass, dirt, coarse dirt, stone, cobble, mossy cobble, deepslate, tuff, gravel, basalt, blackstone, gilded blackstone, sand, red sand, clay, terracotta, sandstone, red sandstone, logs/planks for **every wood** (oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, crimson, warped), leaves, moss, mud, brick, snow, ice, packed ice, water, netherrack, glowstone, obsidian, quartz, end stone, magma, sponge, wool, prismarine, sea lantern, **all ores** (gold, iron, coal, diamond, redstone, emerald, lapis, copper, nether gold, quartz, ancient debris), **all metal blocks**, **all 16 colours** of wool / concrete / concrete powder / terracotta / glazed terracotta, the full **stone family** (granite, diorite, andesite, calcite, smooth stone, stone/dark andesite bricks, mossy/cracked/chiselled, smooth/cut/chiselled sandstone, deepslate variants, end stone bricks), **nether** (soul sand/soil, nylium, wart blocks, shroomlight), **purpur** block & pillar, and **misc** (bookshelf, hay, bone, honey, dried kelp, slime) | ✅ | ✅ |
| **Day / night preview** toggle (warm sunlight vs cool moonlight) | ✅ | ✅ |
| Per-pixel **height map → normal map + bump-lit** shading (real depth & roughness) | ✅ | ✅ |
| **Rotating isometric 3D block preview** — a grass block shows all 3 of its textures (top / side / bottom) mapped onto the cube simultaneously | ✅ | ✅ |
| Realism & relief (bump strength) **sliders** | ✅ | ✅ |
| Resolution selector (16× / 32× / 64×) | ✅ | ✅ |
| Random seed | ✅ | ✅ |
| **Export `.mcpack`** (texture pack) | ✅ | ✅ |
| **Export `.mcpack`** (RenderDragon shader pack, **no ray**) | ✅ | ✅ |
| Export texture + shader combined `.zip` | ✅ | – |
| Share / open exported pack with Minecraft | – | ✅ |

The realism engine uses **only perceptually-true lighting**: baked per-pixel normal maps,
a physically-inspired sun + ambient + specular model, exposure/fog/water reflection settings.
No ray-tracing is required — the depth you see comes from the relief baked into the textures
themselves.

---

## 🖥️ Web preview (live)

Open `web/index.html` directly in a browser, or serve it:

```bash
cd web
python3 -m http.server 8080
```

- Left panel: pack name, seed, resolution, realism & relief sliders, texture toggles.
- Center: live **rotating 3D block** (drag to rotate, scroll/pinch to zoom). For a grass
  block, the grass**top**, grass**side** and grass**bottom** textures are installed onto the
  three faces as you watch.
- Right panel: the **texture atlas** (click any tile to put that block on the 3D cube) and
  the **shader settings** derived from your realism level.
- **Export** buttons produce real `.mcpack` files (they are ZIP archives) via a built-in,
  dependency-free ZIP writer.

---

## 📱 Android app

A complete **Android Studio project** is in `android/`.

Open it in **Android Studio** (or build on the command line):

```bash
cd android
./gradlew assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

> The sandbox has no Android SDK/JDK, so the APK must be built in Android Studio
> (or CI). The app source is complete and compiles against AGP 8.5 + Kotlin 1.9.

### What it does
1. **`TextureGenerator.kt`** — port of the JS synthesis engine (Perlin / fBm / ridged noise),
   producing colour + normal + bump-lit maps per block.
2. **`BlockPreviewView.kt`** — a custom `View` that renders the rotating isometric textured
   cube with per-face lighting, drag-to-rotate and pinch-to-zoom.
3. **`PackExporter.kt`** — writes complete Bedrock `.mcpack` ZIPs (texture and shader packs)
   into app storage and **shares** them so Minecraft can import them.
4. **`MainActivity.kt`** — slider-driven generator with block/preview selection and export.

---

## 🧩 Bedrock `.mcpack` structure

Exported texture packs contain:

```
manifest.json
pack_icon.png
textures/blocks/<block>.png
textures/blocks/<block>_n.png          # tangent-space normal map
textures/blocks/<block>_roughness.png  # PBR roughness (derived from shading)
```

Exported shader packs target the **RenderDragon** pipeline:

```
manifest.json
textures/renderer/per_frame.json
textures/renderer/per_object.json
textures/renderer/materials.json
textures/renderer/shaders.json
textures/renderer/deferred.json
textures/renderer/post_chain.json      # bloom + ACES tone mapping
shaders/pixelcraft_pbr.hlsl            # human-readable PBR source
shaders/pixelcraft_water.hlsl          # human-readable water shader
```

Install on Bedrock by opening the `.mcpack` (or launching Minecraft → Settings → Global
Resources → Import).

---

## 🔧 Default engine target

`min_engine_version: [1, 26, 0]` — compatible with **Minecraft Bedrock 1.26.45.1**.

---

Made with ❤️ — PixelCraft Studio.
