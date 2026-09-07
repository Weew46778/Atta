/*
 * PixelCraft Studio — Bedrock resource pack builder.
 * Assembles a full texture pack (manifest + pack_icon + per-block PNGs, plus optional
 * PBR normal/roughness maps) and hands the file list back for zipping into a .mcpack.
 */

// Bedrock canonical texture filenames for each generated texture id.
const BEDROCK_PATH = {
  grass_top:    'textures/blocks/grass_top.png',
  grass_side:   'textures/blocks/grass_side.png',
  grass_bottom: 'textures/blocks/grass_bottom.png',
  dirt:         'textures/blocks/dirt.png',
  stone:        'textures/blocks/stone.png',
  cobblestone:  'textures/blocks/cobblestone.png',
  oak_log_side: 'textures/blocks/log_oak.png',
  oak_log_top:  'textures/blocks/log_oak_top.png',
  oak_planks:   'textures/blocks/planks_oak.png',
  sand:         'textures/blocks/sand.png',
  bricks:       'textures/blocks/brick.png',
  snow:         'textures/blocks/snow.png',
  oak_leaves:   'textures/blocks/leaves_oak.png',
  water:        'textures/blocks/water_still.png',
  coarse_dirt:  'textures/blocks/coarse_dirt.png',
  deepslate:    'textures/blocks/deepslate.png',
  tuff:         'textures/blocks/tuff.png',
  gravel:       'textures/blocks/gravel.png',
  red_sand:     'textures/blocks/red_sand.png',
  clay:         'textures/blocks/clay.png',
  ice:          'textures/blocks/ice.png',
  packed_ice:   'textures/blocks/packed_ice.png',
  netherrack:   'textures/blocks/netherrack.png',
  glowstone:    'textures/blocks/glowstone.png',
  obsidian:     'textures/blocks/obsidian.png',
  quartz_block: 'textures/blocks/quartz_block_top.png',
  end_stone:    'textures/blocks/end_stone.png',
  magma:        'textures/blocks/magma.png',
  sponge:       'textures/blocks/sponge.png',
  wool_blue:    'textures/blocks/wool_colored_blue.png',
  wool_purple:  'textures/blocks/wool_colored_purple.png',
  terracotta:   'textures/blocks/hardened_clay.png',
  mycelium_top: 'textures/blocks/mycelium_top.png',
  mycelium_side:'textures/blocks/mycelium_side.png',
  gold_ore:    'textures/blocks/gold_ore.png',
  iron_ore:    'textures/blocks/iron_ore.png',
  coal_ore:    'textures/blocks/coal_ore.png',
  diamond_ore: 'textures/blocks/diamond_ore.png',
  redstone_ore:'textures/blocks/redstone_ore.png',
  emerald_ore: 'textures/blocks/emerald_ore.png',
  lapis_ore:   'textures/blocks/lapis_ore.png',
  copper_ore:  'textures/blocks/copper_ore.png',
  gold_block:    'textures/blocks/gold_block.png',
  iron_block:    'textures/blocks/iron_block.png',
  diamond_block: 'textures/blocks/diamond_block.png',
  emerald_block: 'textures/blocks/emerald_block.png',
  redstone_block:'textures/blocks/redstone_block.png',
  lapis_block:   'textures/blocks/lapis_block.png',
  copper_block:  'textures/blocks/copper_block.png',
  netherite_block:'textures/blocks/netherite_block.png',
  sandstone:     'textures/blocks/sandstone.png',
  red_sandstone: 'textures/blocks/red_sandstone.png',
  mossy_cobblestone:'textures/blocks/cobblestone_mossy.png',
  moss_block:    'textures/blocks/moss_block.png',
  mud:           'textures/blocks/mud.png',
  packed_mud:    'textures/blocks/packed_mud.png',
  basalt_side:   'textures/blocks/basalt_side.png',
  blackstone:    'textures/blocks/blackstone.png',
  gilded_blackstone:'textures/blocks/gilded_blackstone.png',
  prismarine:        'textures/blocks/prismarine.png',
  dark_prismarine:   'textures/blocks/prismarine_dark.png',
  sea_lantern:       'textures/blocks/sea_lantern.png',
  // handful of official Bedrock asset names for the new palette; the rest fall back to a derived path below.
  granite:            'textures/blocks/stone_granite.png',
  polished_granite:   'textures/blocks/stone_granite_smooth.png',
  diorite:            'textures/blocks/stone_diorite.png',
  polished_diorite:   'textures/blocks/stone_diorite_smooth.png',
  andesite:           'textures/blocks/stone_andesite.png',
  polished_andesite:  'textures/blocks/stone_andesite_smooth.png',
  stone_bricks:       'textures/blocks/stonebrick.png',
  cracked_stone_bricks: 'textures/blocks/stonebrick_cracked.png',
  mossy_stone_bricks: 'textures/blocks/stonebrick_mossy.png',
  chiseled_stone_bricks: 'textures/blocks/stonebrick_carved.png',
  end_stone_bricks:   'textures/blocks/end_bricks.png',
  smooth_stone:       'textures/blocks/stone_smooth.png',
  smooth_sandstone:   'textures/blocks/sandstone_smooth.png',
  cut_sandstone:      'textures/blocks/sandstone_cut.png',
  chiseled_sandstone: 'textures/blocks/sandstone_carved.png',
  bookshelf:          'textures/blocks/bookshelf.png',
  hay_block:          'textures/blocks/hay_block_side.png',
  bone_block:         'textures/blocks/bone_block_side.png',
  honey_block:        'textures/blocks/honey_block.png',
  dried_kelp_block:   'textures/blocks/dried_kelp_block.png',
  slime_block:        'textures/blocks/slime.png',
  soul_sand:          'textures/blocks/soul_sand.png',
  soul_soil:          'textures/blocks/soul_soil.png',
  crimson_nylium:     'textures/blocks/crimson_nylium.png',
  warped_nylium:      'textures/blocks/warped_nylium.png',
  nether_wart_block:  'textures/blocks/nether_wart_block.png',
  warped_wart_block:  'textures/blocks/warped_wart_block.png',
  shroomlight:        'textures/blocks/shroomlight.png',
  nether_gold_ore:    'textures/blocks/nether_gold_ore.png',
  nether_quartz_ore:  'textures/blocks/quartz_ore.png',
  ancient_debris:     'textures/blocks/ancient_debris_side.png',
  purpur_block:       'textures/blocks/purpur_block.png',
  purpur_pillar:      'textures/blocks/purpur_pillar.png',
  calcite:            'textures/blocks/calcite.png',
  deepslate_bricks:   'textures/blocks/deepslate_bricks.png',
  cracked_deepslate_bricks: 'textures/blocks/deepslate_bricks_cracked.png',
  chiseled_deepslate: 'textures/blocks/deepslate_chiseled.png',
  polished_deepslate: 'textures/blocks/deepslate_polished.png',
};

// Derive a sensible Bedrock path for any block not explicitly listed above:
// concrete_red -> textures/blocks/concrete_red.png, spruce_planks -> textures/blocks/planks_spruce.png,
// gamed ids that ship under a generic name get a stable fallback file name.
function bedrockPath(id) {
  if (BEDROCK_PATH[id]) return BEDROCK_PATH[id];
  const b = TEXTURE_REGISTRY[id] ? TEXTURE_REGISTRY[id].block : id;
  // Official vanilla family names that differ from our internal ids.
  const plankFamilies = ['spruce','birch','jungle','acacia','dark_oak','mangrove','cherry','crimson','warped'];
  if (b.endsWith('_planks')) {
    const fam = b.slice(0, -'_planks'.length);
    return `textures/blocks/planks_${fam}.png`;
  }
  if (b.endsWith('_log_side')) {
    const fam = b.slice(0, -'_log_side'.length);
    return `textures/blocks/log_${fam}.png`;
  }
  if (b.endsWith('_log_top')) {
    const fam = b.slice(0, -'_log_top'.length);
    return `textures/blocks/log_${fam}_top.png`;
  }
  if (b.startsWith('leaves_')) {
    const fam = b.slice('leaves_'.length);
    return `textures/blocks/leaves_${fam}.png`;
  }
  if (b.startsWith('wool_')) {
    const color = b.slice('wool_'.length);
    return `textures/blocks/wool_colored_${color}.png`;
  }
  if (b.startsWith('glazed_terracotta_')) {
    const color = b.slice('glazed_terracotta_'.length);
    return `textures/blocks/glazed_terracotta_${color}.png`;
  }
  if (b.startsWith('concrete_powder_')) {
    const color = b.slice('concrete_powder_'.length);
    return `textures/blocks/concrete_powder_${color}.png`;
  }
  if (b.startsWith('concrete_')) {
    const color = b.slice('concrete_'.length);
    return `textures/blocks/concrete_${color}.png`;
  }
  if (b.startsWith('terracotta_')) {
    const color = b.slice('terracotta_'.length);
    return `textures/blocks/hardened_clay_stained_${color}.png`;
  }
  if (b.startsWith('purpur_pillar')) return 'textures/blocks/purpur_pillar.png';
  return `textures/blocks/${b}.png`;
}

function makeResourceManifest(name, uuid, minEngine) {
  return {
    format_version: 2,
    header: {
      name: name,
      description: "PixelCraft Studio — ultra realistic procedural texture pack",
      uuid: uuid,
      version: [1, 0, 0],
      min_engine_version: minEngine,
    },
    modules: [
      {
        type: "resources",
        uuid: ShaderGen.genUuid(),
        version: [1, 0, 0],
      },
    ],
    settings: {
      textures: { brightness: 1.0, num_mip_levels: 4 },
    },
  };
}

function imageDataToCanvas(img) {
  const c = document.createElement('canvas');
  c.width = img.width; c.height = img.height;
  c.getContext('2d').putImageData(img, 0, 0);
  return c;
}

function drawPackIcon(canvas, realism) {
  const s = 128;
  const c = document.createElement('canvas');
  c.width = s; c.height = s;
  const ctx = c.getContext('2d');
  ctx.fillStyle = '#0d1b2a'; ctx.fillRect(0, 0, s, s);
  ctx.fillStyle = `rgba(80,200,120,${0.12 + realism * 0.2})`;
  ctx.fillRect(0, 0, s, s);
  const grad = ctx.createLinearGradient(0, 0, s, s);
  grad.addColorStop(0, '#2a9d8f'); grad.addColorStop(1, '#264653');
  ctx.fillStyle = grad;
  ctx.fillRect(10, 10, s - 20, s - 20);
  // draw a simple block
  ctx.fillStyle = '#6b7b3a'; ctx.fillRect(30, 34, 68, 68);
  ctx.fillStyle = '#86a24b'; ctx.fillRect(38, 42, 52, 52);
  ctx.fillStyle = '#ffffff'; ctx.font = 'bold 20px system-ui'; ctx.textAlign = 'center';
  ctx.globalAlpha = 0.9;
  ctx.fillText('PC', s / 2, 30);
  ctx.globalAlpha = 1;
  return c;
}

/*
 * Build all files for a complete texture pack.
 * Returns { files, preview: [{id, colorCanvas, normalCanvas, shadedCanvas}] }
 */
function buildTexturePack(seed, size, realism, packName) {
  const entries = [];
  const preview = [];
  for (const id of Object.keys(TEXTURE_REGISTRY)) {
    const bundle = getTexture(id, seed, size, realism);
    // store per-face info for the preview panel
    preview.push({
      id,
      title: TEXTURE_REGISTRY[id].title,
      block: TEXTURE_REGISTRY[id].block,
      colorCanvas: imageDataToCanvas(bundle.color),
      normalCanvas: imageDataToCanvas(bundle.normal),
      shadedCanvas: imageDataToCanvas(bundle.shaded),
    });

    const rel = realism * 0.6 + 0.2; // pack relief clamping
    const relBundle = getTexture(id, seed, size, rel);
    const relPath = bedrockPath(id);
    entries.push({
      path: relPath,
      data: imageDataToCanvas(relBundle.color),
    });
    // optional PBR maps (documented — consumed when a matching shader wires them)
    entries.push({
      path: relPath.replace('.png', '_n.png'),
      data: imageDataToCanvas(relBundle.normal),
    });
    entries.push({
      path: relPath.replace('.png', '_roughness.png'),
      data: imageDataToCanvas(relBundle.shaded), // cheap roughness stand-in derived from shading
    });
  }

  const icon = drawPackIcon(document.createElement('canvas'), realism);
  const manifest = makeResourceManifest(packName, ShaderGen.genUuid(), [1, 26, 0]);
  const files = [
    { path: 'manifest.json', data: JSON.stringify(manifest, null, 2) },
    { path: 'pack_icon.png', data: icon },
    ...entries,
  ];

  return { files, preview, icon };
}

/*
 * Async, incremental pack builder — generates one texture id at a time and yields
 * back to the browser so the UI stays responsive even at 512/1024/2048 resolution.
 * `onProgress(done, total)` is called after each id.
 */
async function buildTexturePackAsync(seed, size, realism, packName, onProgress) {
  const entries = [];
  const preview = [];
  const ids = Object.keys(TEXTURE_REGISTRY);
  const throttle = typeof onProgress === 'function';

  for (let idx = 0; idx < ids.length; idx++) {
    const id = ids[idx];
    const bundle = getTexture(id, seed, size, realism);
    preview.push({
      id,
      title: TEXTURE_REGISTRY[id].title,
      block: TEXTURE_REGISTRY[id].block,
      colorCanvas: imageDataToCanvas(bundle.color),
      normalCanvas: imageDataToCanvas(bundle.normal),
      shadedCanvas: imageDataToCanvas(bundle.shaded),
    });

    const rel = realism * 0.6 + 0.2;
    const relBundle = getTexture(id, seed, size, rel);
    const relPath = bedrockPath(id);
    entries.push({ path: relPath, data: imageDataToCanvas(relBundle.color) });
    entries.push({ path: relPath.replace('.png', '_n.png'), data: imageDataToCanvas(relBundle.normal) });
    entries.push({ path: relPath.replace('.png', '_roughness.png'), data: imageDataToCanvas(relBundle.shaded) });

    // yield to the event loop periodically so the page never freezes
    if (idx % 2 === 1) await new Promise((r) => setTimeout(r, 0));
    if (throttle) onProgress(idx + 1, ids.length);
  }

  const icon = drawPackIcon(document.createElement('canvas'), realism);
  const manifest = makeResourceManifest(packName, ShaderGen.genUuid(), [1, 26, 0]);
  const files = [
    { path: 'manifest.json', data: JSON.stringify(manifest, null, 2) },
    { path: 'pack_icon.png', data: icon },
    ...entries,
  ];

  return { files, preview, icon };
}

/*
 * Lightweight single-pass preview: generates each texture ONCE (no PBR duplicate)
 * and yields between batches, so the live atlas stays responsive. The 3D cube is
 * rendered separately from a full-res face, and export uses buildTexturePackAsync.
 */
async function buildPreviewPack(seed, size, realism, onProgress) {
  const preview = [];
  const ids = Object.keys(TEXTURE_REGISTRY);
  for (let idx = 0; idx < ids.length; idx++) {
    const id = ids[idx];
    const bundle = getTexture(id, seed, size, realism);
    preview.push({
      id,
      title: TEXTURE_REGISTRY[id].title,
      block: TEXTURE_REGISTRY[id].block,
      colorCanvas: imageDataToCanvas(bundle.color),
      normalCanvas: imageDataToCanvas(bundle.normal),
      shadedCanvas: imageDataToCanvas(bundle.shaded),
    });
    if (idx % 2 === 1) await new Promise((r) => setTimeout(r, 0));
    if (onProgress) onProgress(idx + 1, ids.length);
  }
  return { preview, icon: drawPackIcon(document.createElement('canvas'), realism) };
}

// Simple JSON pretty printer used by the preview "settings" card.
function prettyJson(o) { return JSON.stringify(o, null, 2); }

window.Pack = { buildTexturePack, buildTexturePackAsync, buildPreviewPack, BEDROCK_PATH, drawPackIcon };
