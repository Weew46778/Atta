#!/usr/bin/env node
/*
 * PixelCraft Studio — sample pack generator (Node, offline).
 * Regenerates the procedural textures and packs them into a real, importable
 * Bedrock `.mcpack` (ZIP of RGBA PNGs + manifest). Pure Node: no deps beyond
 * the built-in zlib. Run:  node tools/generate_sample_pack.js
 */
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

// ---- shared-global load of the browser engine ----
global.window = global;
class ImageData { constructor(w, h) { this.width = w; this.height = h; this.data = new Uint8ClampedArray(w * h * 4); } }
global.ImageData = ImageData;
const src = ['noise.js', 'texturegen.js'].map((f) => fs.readFileSync(path.join(__dirname, '..', 'web', f), 'utf8')).join('\n');
const PixelCraft = new Function(src + '\nreturn window.PixelCraft;')();

// ---- minimal RGBA PNG encoder ----
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(bytes) {
  let c = 0xFFFFFFFF;
  for (const b of bytes) c = CRC_TABLE[(c ^ b) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
}
function encodePNG(img) {
  const { width: w, height: h } = img;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  let o = 0;
  for (let y = 0; y < h; y++) {
    raw[o++] = 0; // filter: none
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4;
      raw[o++] = img.data[i]; raw[o++] = img.data[i + 1]; raw[o++] = img.data[i + 2]; raw[o++] = img.data[i + 3];
    }
  }
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0; // 8-bit RGBA
  const idat = zlib.deflateSync(raw);
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', idat), chunk('IEND', Buffer.alloc(0))]);
}

// ---- minimal ZIP writer (store) ----
const ZIP_CRC = CRC_TABLE;
function zipCrc(bytes) {
  let c = 0xFFFFFFFF;
  for (const b of bytes) c = ZIP_CRC[(c ^ b) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}
const u16 = (v) => [v & 0xFF, (v >>> 8) & 0xFF];
const u32 = (v) => [v & 0xFF, (v >>> 8) & 0xFF, (v >>> 16) & 0xFF, (v >>> 24) & 0xFF];
function buildZip(files) {
  const out = []; const central = []; let offset = 0;
  const dt = [0x21, 0x0E]; const dd = [0x21, 0xA9];
  for (const f of files) {
    const name = Buffer.from(f.path, 'utf8');
    const data = Buffer.from(f.data);
    const crc = zipCrc(data);
    const lfh = [0x50, 0x4B, 0x03, 0x04, ...u16(20), ...u16(0x0800), ...u16(0), ...dt, ...dd, ...u32(crc), ...u32(data.length), ...u32(data.length), ...u16(name.length), ...u16(0), ...name, ...data];
    out.push(Buffer.from(lfh));
    central.push({ name, crc, size: data.length, offset, nameLen: name.length });
    offset += lfh.length;
  }
  const cdStart = offset;
  let cd = [];
  for (const c of central) {
    cd = cd.concat([0x50, 0x4B, 0x01, 0x02, ...u16(20), ...u16(20), ...u16(0x0800), ...u16(0), ...dt, ...dd, ...u32(c.crc), ...u32(c.size), ...u32(c.size), ...u16(c.nameLen), ...u16(0), ...u16(0), ...u16(0), ...u16(0), ...u32(0), ...u32(c.offset), ...c.name]);
  }
  const cdLen = cd.length;
  const eocd = [0x50, 0x4B, 0x05, 0x06, ...u16(0), ...u16(0), ...u16(central.length), ...u16(central.length), ...u32(cdLen), ...u32(cdStart), ...u16(0)];
  return Buffer.concat([...out, Buffer.from(cd), Buffer.from(eocd)]);
}

// ---- build the sample texture pack ----
// Path overrides for ids whose Bedrock filename differs from `textures/blocks/<id>.png`.
const PATH_OVERRIDE = {
  oak_log_side: 'textures/blocks/log_oak.png',
  oak_log_top: 'textures/blocks/log_oak_top.png',
  oak_planks: 'textures/blocks/planks_oak.png',
  bricks: 'textures/blocks/brick.png',
  oak_leaves: 'textures/blocks/leaves_oak.png',
  water: 'textures/blocks/water_still.png',
  quartz_block: 'textures/blocks/quartz_block_top.png',
  wool_blue: 'textures/blocks/wool_colored_blue.png',
  wool_purple: 'textures/blocks/wool_colored_purple.png',
  terracotta: 'textures/blocks/hardened_clay.png',
  mycelium_top: 'textures/blocks/mycelium_top.png',
  mycelium_side: 'textures/blocks/mycelium_side.png',
  mossy_cobblestone: 'textures/blocks/cobblestone_mossy.png',
  dark_prismarine: 'textures/blocks/prismarine_dark.png',
  basalt_side: 'textures/blocks/basalt_side.png',
};
function pathOf(id) {
  if (PATH_OVERRIDE[id]) return PATH_OVERRIDE[id];
  const b = PixelCraft.TEXTURE_REGISTRY[id] ? PixelCraft.TEXTURE_REGISTRY[id].block : id;
  if (b.endsWith('_planks')) return `textures/blocks/planks_${b.slice(0, -7)}.png`;
  if (b.endsWith('_log_side')) return `textures/blocks/log_${b.slice(0, -9)}.png`;
  if (b.endsWith('_log_top')) return `textures/blocks/log_${b.slice(0, -8)}_top.png`;
  if (b.startsWith('leaves_')) return `textures/blocks/leaves_${b.slice(7)}.png`;
  if (b.startsWith('wool_')) return `textures/blocks/wool_colored_${b.slice(5)}.png`;
  if (b.startsWith('glazed_terracotta_')) return `textures/blocks/glazed_terracotta_${b.slice(19)}.png`;
  if (b.startsWith('concrete_powder_')) return `textures/blocks/concrete_powder_${b.slice(16)}.png`;
  if (b.startsWith('concrete_')) return `textures/blocks/concrete_${b.slice(9)}.png`;
  if (b.startsWith('terracotta_')) return `textures/blocks/hardened_clay_stained_${b.slice(11)}.png`;
  if (b.startsWith('purpur_pillar')) return 'textures/blocks/purpur_pillar.png';
  const single = {
    stone_bricks: 'stonebrick', cracked_stone_bricks: 'stonebrick_cracked',
    mossy_stone_bricks: 'stonebrick_mossy', chiseled_stone_bricks: 'stonebrick_carved',
    end_stone_bricks: 'end_bricks', smooth_stone: 'stone_smooth',
    smooth_sandstone: 'sandstone_smooth', cut_sandstone: 'sandstone_cut',
    chiseled_sandstone: 'sandstone_carved', quartz_block: 'quartz_block_top',
    bookshelf: 'bookshelf', hay_block: 'hay_block_side', bone_block: 'bone_block_side',
    honey_block: 'honey_block', dried_kelp_block: 'dried_kelp_block', slime_block: 'slime',
    polished_granite: 'stone_granite_smooth', polished_diorite: 'stone_diorite_smooth',
    polished_andesite: 'stone_andesite_smooth', granite: 'stone_granite',
    diorite: 'stone_diorite', andesite: 'stone_andesite',
  };
  return `textures/blocks/${single[b] || b}.png`;
}
const SEED = 1337; const SIZE = 128; const REL = 0.6;
const manifest = `{
  "format_version": 2,
  "header": {
    "name": "PixelCraft Sample Realistic Pack",
    "description": "PixelCraft Studio - ultra realistic procedural texture pack",
    "uuid": "2f7c9b1e-0c1f-4a9b-8c3d-5f1a2e4b6c7d",
    "version": [1, 0, 0],
    "min_engine_version": [1, 26, 0]
  },
  "modules": [
    { "type": "resources", "uuid": "0a6b2c3e-1d2f-4e9a-9b1c-3d2f4e5a6b7c", "version": [1, 0, 0] }
  ],
  "settings": { "textures": { "brightness": 1.0, "num_mip_levels": 4 } }
}
`;

const files = [{ path: 'manifest.json', data: manifest }];
const ids = Object.keys(PixelCraft.TEXTURE_REGISTRY);
for (const id of ids) {
  const b = PixelCraft.getTexture(id, SEED, SIZE, REL);
  const base = pathOf(id);
  files.push({ path: base, data: encodePNG(b.color) });
  files.push({ path: base.replace('.png', '_n.png'), data: encodePNG(b.normal) });
  files.push({ path: base.replace('.png', '_roughness.png'), data: encodePNG(b.shaded) });
  console.log(' +', id);
}
console.log('\nTotal blocks:', new Set(ids.map((i) => PixelCraft.TEXTURE_REGISTRY[i].block)).size);

const outDir = path.join(__dirname, '..', 'art', 'sample');
fs.mkdirSync(outDir, { recursive: true });
const blob = buildZip(files);
const outPath = path.join(outDir, 'PixelCraft_Sample_Texture.mcpack');
fs.writeFileSync(outPath, blob);
console.log('\nWrote', outPath, `(${blob.length} bytes, ${files.length} files)`);
