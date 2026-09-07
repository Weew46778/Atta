/*
 * PixelCraft Studio — procedural texture synthesizer
 * Generates a base colour map, a height field, a tangent-space normal map and a
 * "bump-lit" shaded map (the per-pixel relief that makes blocks look 3D).
 */

const LIGHT = vec3normalize(-0.45, -0.72, 0.55); // image-space light dir (top-left, toward viewer)

function vec3normalize(x, y, z) {
  const l = Math.hypot(x, y, z) || 1;
  return { x: x / l, y: y / l, z: z / l };
}

function makeImage(size) {
  return new ImageData(size, size);
}

// Build a full texture bundle. `genFn(px, py, size, noise2, rand)` writes RGBA into `rgba`,
// and returns a height value in [-1,1]. We use the returned height for the normal map.
function synthesizeTexture(seed, size, genFn, relief) {
  const noise2 = Perlin.make(seed);
  const rand = mulberry32((seed * 2654435761) >>> 0);

  const color = makeImage(size);
  const rgb = color.data;
  const height = new Float32Array(size * size);

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 4;
      const h = genFn(x, y, size, noise2, rand, rgb, i);
      height[y * size + x] = h;
    }
  }

  // Normal map + shaded map (tangent space; y axis points down in image coords)
  const normal = makeImage(size);
  const shaded = makeImage(size);
  const nrm = normal.data, shd = shaded.data;
  const ambient = 0.42;

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x);
      const xl = i === 0 ? i : i - 1;
      const xr = i === size * size - 1 ? i : i + 1;
      const yl = i - size < 0 ? i : i - size;
      const yr = i + size >= size * size ? i : i + size;

      const dhx = (height[xr] - height[xl]) * relief;
      const dhy = (height[yr] - height[yl]) * relief;
      const N = vec3normalize(-dhx, -dhy, 1);

      const i4 = i * 4;
      // encode normal (map [-1,1] -> [0,255])
      nrm[i4]     = Math.round((N.x * 0.5 + 0.5) * 255);
      nrm[i4 + 1] = Math.round((N.y * 0.5 + 0.5) * 255);
      nrm[i4 + 2] = Math.round((N.z * 0.5 + 0.5) * 255);
      nrm[i4 + 3] = 255;

      // bump-lit shading with a touch of specular
      const diff = Math.max(0, N.x * LIGHT.x + N.y * LIGHT.y + N.z * LIGHT.z);
      const spec = Math.pow(Math.max(0, diff), 14) * 0.35;
      const light = ambient + diff * 0.7 + spec;

      shd[i4]     = Math.max(0, Math.min(255, rgb[i4] * light));
      shd[i4 + 1] = Math.max(0, Math.min(255, rgb[i4 + 1] * light));
      shd[i4 + 2] = Math.max(0, Math.min(255, rgb[i4 + 2] * light));
      shd[i4 + 3] = 255;
    }
  }

  return { color, normal, shaded, height, size };
}

// ---------- individual block generators ----------

function genGrassTop(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const f = Perlin.fbm(n, x / s * 6, y / s * 6, 5, 2.0, 0.5);
    const f2 = Perlin.fbm(n, x / s * 14 + 50, y / s * 14 + 50, 3, 2.0, 0.5);
    const g = 0.5 + f * 0.32 + f2 * 0.14;
    rgb[i]     = Math.round(64 + g * 36 + r() * 10);
    rgb[i + 1] = Math.round(128 + g * 74 + r() * 16);
    rgb[i + 2] = Math.round(50 + g * 34);
    rgb[i + 3] = 255;
    return g * 0.9 + f2 * 0.3; // height: blades of grass
  }, relief);
}

function genGrassSide(seed, size, relief) {
  const dirt = genDirt(seed + 7, size, relief * 0.5);
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    // dirt region + hanging grass fringe at the top; grass weight fades to 0 toward the bottom
    const fringe = 1 - y / s;
    const top = Math.pow(fringe, 0.55);
    const blades = Perlin.fbm(n, x / s * 26, 0, 4, 2.0, 0.5);
    const ix = i;
    rgb[ix]     = dirt.color.data[ix]     * (1 - top) + (70 + blades * 90)  * top;
    rgb[ix + 1] = dirt.color.data[ix + 1] * (1 - top) + (128 + blades * 44) * top;
    rgb[ix + 2] = dirt.color.data[ix + 2] * (1 - top) + (58 + blades * 30)  * top;
    rgb[ix + 3] = 255;
    return dirt.height[y * s + x] * (1 - top) + top * (0.7 + blades * 0.5);
  }, relief);
}

function genDirt(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const f = Perlin.fbm(n, x / s * 11, y / s * 11, 5, 2.0, 0.55);
    const specks = Perlin.fbm(n, x / s * 30 + 90, y / s * 30 + 90, 2, 2.0, 0.5);
    const base = 0.45 + f * 0.2 + specks * 0.12;
    const crumb = Perlin.ridged(n, x / s * 40, y / s * 40, 2, 2.0, 0.6);
    rgb[i]     = Math.round((96 + base * 44) * (0.9 + crumb * 0.25) + r() * 8);
    rgb[i + 1] = Math.round((60 + base * 30) * (0.9 + crumb * 0.25) + r() * 8);
    rgb[i + 2] = Math.round((38 + base * 20) * (0.9 + crumb * 0.22));
    rgb[i + 3] = 255;
    return base * 0.7 + crumb * 0.5;
  }, relief);
}

function genStone(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const ridged = Perlin.ridged(n, x / s * 5, y / s * 5, 4, 2.0, 0.55);
    const fine = Perlin.fbm(n, x / s * 22, y / s * 22, 3, 2.0, 0.5);
    const v = 0.55 + ridged * 0.28 + fine * 0.12;
    const q = v * 255;
    rgb[i]     = Math.round(q * 0.82 + r() * 10);
    rgb[i + 1] = Math.round(q * 0.83 + r() * 10);
    rgb[i + 2] = Math.round(q * 0.86 + r() * 8);
    rgb[i + 3] = 255;
    return ridged * 1.1 + fine * 0.2;
  }, relief);
}

function genCobble(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const cell = Perlin.fbm(n, x / s * 3.3, y / s * 3.3, 3, 2, 0.5);
    const ridge = Perlin.ridged(n, x / s * 18, y / s * 18, 3, 2, 0.6);
    const v = 0.5 + cell * 0.22 + ridge * 0.12;
    const q = v * 255;
    rgb[i]     = Math.round(q * 0.78 + r() * 12);
    rgb[i + 1] = Math.round(q * 0.79 + r() * 12);
    rgb[i + 2] = Math.round(q * 0.8 + r() * 10);
    rgb[i + 3] = 255;
    return ridge * 1.3 + cell * 0.2;
  }, relief);
}

function genWoodSide(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const groove = Perlin.fbm(n, y / s * 9, x / s * 1.6, 3, 2, 0.5);
    const lines = Math.sin(y / s * Math.PI * 10 + groove * 3) * 0.5 + 0.5;
    const grain = Perlin.fbm(n, x / s * 40, y / s * 4, 3, 2, 0.5);
    const v = 0.5 + lines * 0.2 + grain * 0.14;
    rgb[i]     = Math.round((120 + v * 70) + r() * 14);
    rgb[i + 1] = Math.round((74 + v * 42) + r() * 12);
    rgb[i + 2] = Math.round((34 + v * 22) + r() * 10);
    rgb[i + 3] = 255;
    return lines * 1.1 + grain * 0.3;
  }, relief);
}

function genWoodTop(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const id = x / s, id2 = y / s;
    let slope = 0; // distance to nearest ring (centered)
    const cx = 0.5 + Perlin.fbm(n, id * 3, id2 * 3, 2, 2, 0.5) * 0.08;
    const cy = 0.5 + Perlin.fbm(n, id * 3 + 30, id2 * 3 + 30, 2, 2, 0.5) * 0.08;
    const d = Math.hypot(id - cx, id2 - cy);
    const ring = 0.5 + 0.5 * Math.sin(d * Math.PI * 26);
    const v = 0.45 + ring * 0.3 + Perlin.fbm(n, id * 46, id2 * 46, 2, 2, 0.5) * 0.12;
    rgb[i]     = Math.round((122 + v * 66) + r() * 14);
    rgb[i + 1] = Math.round((76 + v * 40) + r() * 12);
    rgb[i + 2] = Math.round((36 + v * 20) + r() * 10);
    rgb[i + 3] = 255;
    return ring * 1.1 + 0.2;
  }, relief);
}

function genSand(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const ripple = Math.sin((x + Perlin.fbm(n, x / s * 5, y / s * 5, 2, 2, 0.5) * 20) / s * Math.PI * 8);
    const f = Perlin.fbm(n, x / s * 18, y / s * 18, 3, 2, 0.5);
    const v = 0.5 + ripple * 0.08 + f * 0.1;
    rgb[i]     = Math.round(204 + v * 46 + r() * 8);
    rgb[i + 1] = Math.round(176 + v * 40 + r() * 8);
    rgb[i + 2] = Math.round(116 + v * 30 + r() * 6);
    rgb[i + 3] = 255;
    return ripple * 0.5 + f * 0.5;
  }, relief);
}

function genPlank(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const boards = 4;
    const board = Math.floor(y / (s / boards));
    const gap = Math.abs(y / (s / boards) - (board + 0.5)) * 2;
    const betweenGap = gap > 0.86 ? 1 : 0; // dark separation line
    const grain = Perlin.fbm(n, x / s * 34, y / s * 6 + board, 3, 2, 0.5);
    const v = 0.5 + grain * 0.18 - betweenGap * 0.35;
    const shade = board % 2 === 0 ? 1 : 0.92;
    rgb[i]     = Math.round((134 + v * 60) * shade + r() * 12);
    rgb[i + 1] = Math.round((88 + v * 40) * shade + r() * 10);
    rgb[i + 2] = Math.round((46 + v * 22) * shade + r() * 8);
    rgb[i + 3] = 255;
    return grain * 0.7 + betweenGap * 1.4;
  }, relief);
}

function genBrick(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const rows = 4, bw = s / 2;
    const row = Math.floor(y / (s / rows));
    const offset = row % 2 === 0 ? 0 : bw / 2;
    const bx = (x + offset) % bw;
    const mortar = (bx < s / 26 || bx > bw - s / 26 || (y % (s / rows)) < s / 26) ? 1 : 0;
    const f = Perlin.fbm(n, x / s * 14, y / s * 14 + row, 3, 2, 0.5);
    const v = 0.5 + f * 0.14 - mortar * 0.5;
    rgb[i]     = Math.round((150 + v * 48) + r() * 10);
    rgb[i + 1] = Math.round((60 + v * 22) + r() * 8);
    rgb[i + 2] = Math.round((40 + v * 16) + r() * 6);
    rgb[i + 3] = 255;
    return f * 0.6 + mortar * 1.7;
  }, relief);
}

function genSnow(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const f = Perlin.fbm(n, x / s * 20, y / s * 20, 3, 2, 0.5);
    const v = 235 + f * 16 + r() * 5;
    rgb[i]     = Math.round(v);
    rgb[i + 1] = Math.round(v);
    rgb[i + 2] = Math.round(Math.min(255, v + 2));
    rgb[i + 3] = 255;
    return f * 0.5;
  }, relief);
}

function genLeaves(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const blades = Perlin.fbm(n, x / s * 24, y / s * 24, 4, 2, 0.5);
    const hole = Perlin.ridged(n, x / s * 12, y / s * 12, 2, 2, 0.6);
    const v = 0.5 + blades * 0.2;
    let a = 235 + r() * 20;
    if (hole > 0.78) a = 60; // some translucent holes
    rgb[i]     = Math.round((46 + v * 40));
    rgb[i + 1] = Math.round((92 + v * 80));
    rgb[i + 2] = Math.round((36 + v * 34));
    rgb[i + 3] = a;
    return blades * 0.9 + hole * 0.3;
  }, relief);
}

function genWater(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const wave = Perlin.fbm(n, x / s * 7, y / s * 7, 4, 2, 0.5);
    const v = 0.5 + wave * 0.3;
    rgb[i]     = Math.round((22 + v * 30));
    rgb[i + 1] = Math.round((56 + v * 90));
    rgb[i + 2] = Math.round((130 + v * 80));
    rgb[i + 3] = 210;
    return wave * 0.8;
  }, relief);
}

// register of generators keyed by texture id
const TEXTURE_REGISTRY = {
  grass_top:   { title: 'Grass Top',   gen: genGrassTop,  block: 'grass' },
  grass_side:  { title: 'Grass Side',  gen: genGrassSide, block: 'grass' },
  grass_bottom:{ title: 'Grass Bottom',gen: (s, sz, r) => genDirt(s, sz, r), block: 'grass' },
  dirt:        { title: 'Dirt',        gen: genDirt,   block: 'dirt' },
  stone:       { title: 'Stone',       gen: genStone,  block: 'stone' },
  cobblestone: { title: 'Cobblestone', gen: genCobble, block: 'cobblestone' },
  oak_log_side:{ title: 'Oak Log Side',gen: genWoodSide, block: 'oak_log' },
  oak_log_top: { title: 'Oak Log Top', gen: genWoodTop, block: 'oak_log' },
  oak_planks:  { title: 'Oak Planks',  gen: genPlank,  block: 'oak_planks' },
  sand:        { title: 'Sand',        gen: genSand,   block: 'sand' },
  bricks:      { title: 'Bricks',      gen: genBrick,  block: 'bricks' },
  snow:        { title: 'Snow',        gen: genSnow,   block: 'snow' },
  oak_leaves:  { title: 'Oak Leaves',  gen: genLeaves, block: 'oak_leaves' },
  water:       { title: 'Water',       gen: genWater,  block: 'water' },
};

function getTexture(id, seed, size, relief) {
  return TEXTURE_REGISTRY[id].gen(seed, size, relief);
}

// Expose to browser global scope (classic scripts load in order).
window.PixelCraft = window.PixelCraft || {};
Object.assign(window.PixelCraft, {
  synthesizeTexture, getTexture, TEXTURE_REGISTRY,
  Perlin, mulberry32, vec3normalize,
  genGrassTop, genGrassSide, genDirt, genStone, genCobble,
  genWoodSide, genWoodTop, genSand, genPlank, genBrick,
  genSnow, genLeaves, genWater,
});
