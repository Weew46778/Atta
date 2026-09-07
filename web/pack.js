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
};

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
    entries.push({
      path: BEDROCK_PATH[id],
      data: imageDataToCanvas(relBundle.color),
    });
    // optional PBR maps (documented — consumed when a matching shader wires them)
    entries.push({
      path: BEDROCK_PATH[id].replace('.png', '_n.png'),
      data: imageDataToCanvas(relBundle.normal),
    });
    entries.push({
      path: BEDROCK_PATH[id].replace('.png', '_roughness.png'),
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

// Simple JSON pretty printer used by the preview "settings" card.
function prettyJson(o) { return JSON.stringify(o, null, 2); }

window.Pack = { buildTexturePack, BEDROCK_PATH, drawPackIcon };
