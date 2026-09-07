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

// ---------- extra blocks (20+ total) ----------

function genCoarseDirt(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const peb = Perlin.ridged(n, x / s * 9, y / s * 9, 3, 2, 0.6);
    const f = Perlin.fbm(n, x / s * 11, y / s * 11, 5, 2, 0.55);
    const base = 0.45 + f * 0.2;
    const rock = peb > 0.55 ? 1 : 0;
    rgb[i]     = (96 + base * 44) * (0.9 + peb * 0.25) + r() * 8 + rock * 22;
    rgb[i + 1] = (60 + base * 30) * (0.9 + peb * 0.25) + r() * 8 + rock * 20;
    rgb[i + 2] = (38 + base * 20) * (0.9 + peb * 0.22) + rock * 18;
    rgb[i + 3] = 255;
    return base * 0.7 + peb * 0.9;
  }, relief);
}

function genDeepslate(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const ridged = Perlin.ridged(n, x / s * 6, y / s * 6, 4, 2, 0.55);
    const strata = Math.sin(y / s * Math.PI * 6 + ridged * 2) * 0.5 + 0.5;
    const fine = Perlin.fbm(n, x / s * 24, y / s * 24, 3, 2, 0.5);
    const v = 0.4 + ridged * 0.22 + strata * 0.14 + fine * 0.08;
    const q = v * 255;
    rgb[i]     = q * 0.44 + r() * 6;
    rgb[i + 1] = q * 0.45 + r() * 6;
    rgb[i + 2] = q * 0.5 + r() * 5;
    rgb[i + 3] = 255;
    return ridged * 1.1 + strata * 0.6 + fine * 0.1;
  }, relief);
}

function genTuff(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const f = Perlin.fbm(n, x / s * 14, y / s * 14, 5, 2, 0.5);
    const spots = Perlin.ridged(n, x / s * 22, y / s * 22, 2, 2, 0.6);
    const v = 0.5 + f * 0.18 + spots * 0.12;
    const q = v * 255;
    rgb[i]     = q * 0.68 + r() * 8;
    rgb[i + 1] = q * 0.69 + r() * 8;
    rgb[i + 2] = q * 0.72 + r() * 6;
    rgb[i + 3] = 255;
    return f * 0.7 + spots * 0.6;
  }, relief);
}

function genGravel(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const peb = Perlin.ridged(n, x / s * 8, y / s * 8, 3, 2, 0.7);
    const f = Perlin.fbm(n, x / s * 26, y / s * 26, 2, 2, 0.5);
    const grey = 0.5 + peb * 0.22 + f * 0.1;
    const warm = r() * 0.2;
    rgb[i]     = (120 + grey * 70) * (1 - warm) + (130 + grey * 50) * warm + r() * 14;
    rgb[i + 1] = (112 + grey * 64) * (1 - warm) + (104 + grey * 46) * warm + r() * 12;
    rgb[i + 2] = (104 + grey * 60) * (1 - warm) + (86 + grey * 44) * warm + r() * 10;
    rgb[i + 3] = 255;
    return peb * 1.3 + f * 0.2;
  }, relief);
}

function genRedSand(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const ripple = Math.sin((x + Perlin.fbm(n, x / s * 5, y / s * 5, 2, 2, 0.5) * 20) / s * Math.PI * 8);
    const f = Perlin.fbm(n, x / s * 18, y / s * 18, 3, 2, 0.5);
    const v = 0.5 + ripple * 0.08 + f * 0.1;
    rgb[i]     = 176 + v * 46 + r() * 8;
    rgb[i + 1] = 92 + v * 30 + r() * 7;
    rgb[i + 2] = 58 + v * 22 + r() * 6;
    rgb[i + 3] = 255;
    return ripple * 0.5 + f * 0.5;
  }, relief);
}

function genClay(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const f = Perlin.fbm(n, x / s * 8, y / s * 8, 4, 2, 0.5);
    const v = 0.5 + f * 0.1;
    rgb[i]     = 176 + v * 32 + r() * 5;
    rgb[i + 1] = 158 + v * 30 + r() * 5;
    rgb[i + 2] = 146 + v * 28 + r() * 5;
    rgb[i + 3] = 255;
    return f * 0.4;
  }, relief);
}

function genIce(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const streak = Perlin.fbm(n, x / s * 6, y / s * 2, 4, 2, 0.5);
    const v = 0.5 + streak * 0.22;
    rgb[i]     = 168 + v * 40 + r() * 8;
    rgb[i + 1] = 214 + v * 30 + r() * 8;
    rgb[i + 2] = 236 + v * 18 + r() * 6;
    rgb[i + 3] = 200;
    return streak * 0.8;
  }, relief);
}

function genPackedIce(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const f = Perlin.fbm(n, x / s * 14, y / s * 14, 3, 2, 0.5);
    const v = 0.5 + f * 0.12;
    rgb[i]     = 196 + v * 30 + r() * 6;
    rgb[i + 1] = 226 + v * 24 + r() * 6;
    rgb[i + 2] = 246 + v * 10 + r() * 5;
    rgb[i + 3] = 230;
    return f * 0.5;
  }, relief);
}

function genNetherrack(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const ridged = Perlin.ridged(n, x / s * 9, y / s * 9, 4, 2, 0.55);
    const f = Perlin.fbm(n, x / s * 20, y / s * 20, 3, 2, 0.5);
    const v = 0.5 + ridged * 0.26 + f * 0.1;
    rgb[i]     = 128 + v * 60 + r() * 8;
    rgb[i + 1] = 42 + v * 26 + r() * 6;
    rgb[i + 2] = 34 + v * 20 + r() * 5;
    rgb[i + 3] = 255;
    return ridged * 1.2 + f * 0.2;
  }, relief);
}

function genGlowstone(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const glow = Perlin.fbm(n, x / s * 10, y / s * 10, 3, 2, 0.6);
    const glowSpots = Perlin.ridged(n, x / s * 18, y / s * 18, 2, 2, 0.7);
    const v = 0.5 + glow * 0.24;
    const g = glowSpots > 0.72 ? 1 : 0; // bright glowing nodes
    rgb[i]     = 200 + v * 40 + r() * 16 + g * 30;
    rgb[i + 1] = 168 + v * 36 + r() * 14 + g * 26;
    rgb[i + 2] = 96 + v * 30 + r() * 12 + g * 20;
    rgb[i + 3] = 255;
    return glow * 0.7 + glowSpots * 1.2;
  }, relief);
}

function genObsidian(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const sheen = Perlin.fbm(n, x / s * 8, y / s * 8, 3, 2, 0.5);
    const v = 0.18 + sheen * 0.14;
    rgb[i]     = 14 + v * 60 + r() * 5;
    rgb[i + 1] = 12 + v * 52 + r() * 5;
    rgb[i + 2] = 28 + v * 74 + r() * 6;
    rgb[i + 3] = 255;
    return sheen * 0.7;
  }, relief);
}

function genQuartz(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const f = Perlin.fbm(n, x / s * 10, y / s * 10, 3, 2, 0.5);
    const v = 0.5 + f * 0.09;
    rgb[i]     = 224 + v * 24 + r() * 4;
    rgb[i + 1] = 216 + v * 24 + r() * 4;
    rgb[i + 2] = 204 + v * 24 + r() * 4;
    rgb[i + 3] = 255;
    return f * 0.4;
  }, relief);
}

function genEndStone(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const f = Perlin.fbm(n, x / s * 10, y / s * 10, 4, 2, 0.5);
    const v = 0.5 + f * 0.13;
    rgb[i]     = 204 + v * 34 + r() * 8;
    rgb[i + 1] = 188 + v * 32 + r() * 8;
    rgb[i + 2] = 134 + v * 30 + r() * 7;
    rgb[i + 3] = 255;
    return f * 0.7;
  }, relief);
}

function genMagma(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const crack = Perlin.ridged(n, x / s * 8, y / s * 8, 3, 2, 0.7);
    const glow = crack > 0.62 ? 1 : 0; // glowing cracks
    const f = Perlin.fbm(n, x / s * 20, y / s * 20, 3, 2, 0.5);
    const v = 0.5 + f * 0.12;
    rgb[i]     = 96 + v * 34 + r() * 8 + glow * 90;
    rgb[i + 1] = 30 + v * 18 + r() * 6 + glow * 46;
    rgb[i + 2] = 22 + v * 12 + r() * 5 + glow * 22;
    rgb[i + 3] = 255;
    return f * 0.5 + crack * 1.5;
  }, relief);
}

function genSponge(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const hole = Perlin.ridged(n, x / s * 14, y / s * 14, 3, 2, 0.7);
    const f = Perlin.fbm(n, x / s * 24, y / s * 24, 2, 2, 0.5);
    const v = 0.5 + f * 0.12;
    const isHole = hole > 0.62;
    rgb[i]     = (206 + v * 30 + r() * 12) * (isHole ? 0.6 : 1);
    rgb[i + 1] = (176 + v * 28 + r() * 10) * (isHole ? 0.6 : 1);
    rgb[i + 2] = (78 + v * 22 + r() * 8) * (isHole ? 0.6 : 1);
    rgb[i + 3] = 255;
    return f * 0.5 + hole * 1.6;
  }, relief);
}

function genWool(seed, size, relief, rgb0, rgb1, rgb2) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const weave = 0.5 + 0.5 * Math.sin((x + y * 0.5) / s * Math.PI * 26 + Perlin.fbm(n, x / s * 20, y / s * 20, 2, 2, 0.5) * 4);
    const f = Perlin.fbm(n, x / s * 30, y / s * 30, 2, 2, 0.5);
    const v = 0.5 + weave * 0.18 + f * 0.1;
    rgb[i]     = rgb0 + v * (96 - rgb0) + r() * 12;
    rgb[i + 1] = rgb1 + v * (110 - rgb1) + r() * 12;
    rgb[i + 2] = rgb2 + v * (150 - rgb2) + r() * 10;
    rgb[i + 3] = 255;
    return weave * 1.1 + f * 0.2;
  }, relief);
}
function genBlueWool(seed, size, relief) { return genWool(seed, size, relief, 40, 60, 150); }
function genPurpleWool(seed, size, relief) { return genWool(seed, size, relief, 110, 40, 150); }

function genTerracotta(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const f = Perlin.fbm(n, x / s * 8, y / s * 8, 3, 2, 0.5);
    const band = Math.sin(y / s * Math.PI * 8 + f * 2) * 0.5 + 0.5;
    const v = 0.5 + f * 0.1 + band * 0.08;
    rgb[i]     = 150 + v * 44 + r() * 6;
    rgb[i + 1] = 80 + v * 26 + r() * 5;
    rgb[i + 2] = 56 + v * 20 + r() * 5;
    rgb[i + 3] = 255;
    return f * 0.5 + band * 0.4;
  }, relief);
}

function genMyceliumTop(seed, size, relief) {
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const speck = Perlin.fbm(n, x / s * 20, y / s * 20, 4, 2, 0.5);
    const f = Perlin.fbm(n, x / s * 10, y / s * 10, 4, 2, 0.5);
    const v = 0.5 + speck * 0.2;
    rgb[i]     = 128 + v * 46 + r() * 10;
    rgb[i + 1] = 118 + v * 40 + r() * 10;
    rgb[i + 2] = 116 + v * 40 + r() * 10;
    rgb[i + 3] = 255;
    return f * 0.7 + speck * 0.5;
  }, relief);
}

function genMyceliumSide(seed, size, relief) {
  const dirt = genDirt(seed + 7, size, relief * 0.5);
  return synthesizeTexture(seed, size, (x, y, s, n, r, rgb, i) => {
    const fringe = 1 - y / s;
    const top = Math.pow(fringe, 0.6);
    const speck = Perlin.fbm(n, x / s * 26, 0, 3, 2, 0.5);
    const ix = i;
    rgb[ix]     = dirt.color.data[ix]     * (1 - top) + (140 + speck * 60) * top;
    rgb[ix + 1] = dirt.color.data[ix + 1] * (1 - top) + (132 + speck * 50) * top;
    rgb[ix + 2] = dirt.color.data[ix + 2] * (1 - top) + (128 + speck * 46) * top;
    rgb[ix + 3] = 255;
    return dirt.height[y * s + x] * (1 - top) + top * (0.7 + speck * 0.5);
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
  coarse_dirt: { title: 'Coarse Dirt', gen: genCoarseDirt, block: 'coarse_dirt' },
  deepslate:   { title: 'Deepslate',   gen: genDeepslate,   block: 'deepslate' },
  tuff:        { title: 'Tuff',        gen: genTuff,        block: 'tuff' },
  gravel:      { title: 'Gravel',      gen: genGravel,      block: 'gravel' },
  red_sand:    { title: 'Red Sand',    gen: genRedSand,     block: 'red_sand' },
  clay:        { title: 'Clay',        gen: genClay,        block: 'clay' },
  ice:         { title: 'Ice',         gen: genIce,         block: 'ice' },
  packed_ice:  { title: 'Packed Ice',  gen: genPackedIce,   block: 'packed_ice' },
  netherrack:  { title: 'Netherrack',  gen: genNetherrack,  block: 'netherrack' },
  glowstone:   { title: 'Glowstone',   gen: genGlowstone,   block: 'glowstone' },
  obsidian:    { title: 'Obsidian',    gen: genObsidian,    block: 'obsidian' },
  quartz_block:{ title: 'Quartz',      gen: genQuartz,      block: 'quartz_block' },
  end_stone:   { title: 'End Stone',   gen: genEndStone,    block: 'end_stone' },
  magma:       { title: 'Magma',       gen: genMagma,       block: 'magma' },
  sponge:      { title: 'Sponge',      gen: genSponge,      block: 'sponge' },
  wool_blue:   { title: 'Blue Wool',   gen: genBlueWool,    block: 'wool_blue' },
  wool_purple: { title: 'Purple Wool', gen: genPurpleWool,  block: 'wool_purple' },
  terracotta:  { title: 'Terracotta',  gen: genTerracotta,  block: 'terracotta' },
  mycelium_top:{ title: 'Mycelium Top',   gen: genMyceliumTop,   block: 'mycelium' },
  mycelium_side:{title: 'Mycelium Side',  gen: genMyceliumSide,  block: 'mycelium' },
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
  genCoarseDirt, genDeepslate, genTuff, genGravel, genRedSand, genClay, genIce,
  genPackedIce, genNetherrack, genGlowstone, genObsidian, genQuartz, genEndStone,
  genMagma, genSponge, genBlueWool, genPurpleWool, genTerracotta, genMyceliumTop,
  genMyceliumSide,
});
