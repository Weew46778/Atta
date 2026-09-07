/*
 * PixelCraft Studio — software 3D block renderer (isometric).
 *
 * Draws a rotating, textured cube (top / side / bottom faces may each use a
 * different generated texture). The cube is AUTO-FITTED every frame from its
 * true projected corner bounding box, so it always sits fully inside the
 * canvas, centered, on a subtle floor grid with a soft contact shadow.
 */

// The 8 corners of a unit cube (half-extent = 1).
const CUBE_CORNERS = [
  [-1, -1, -1], [1, -1, -1], [1, -1, 1], [-1, -1, 1],
  [-1, 1, -1], [1, 1, -1], [1, 1, 1], [-1, 1, 1],
];

const CUBE_FACES = [
  { id: 'top',    corners: [[-1, 1, -1], [1, 1, -1], [1, 1, 1], [-1, 1, 1]],  normal: [0, 1, 0] },
  { id: 'bottom', corners: [[-1, -1, -1], [1, -1, -1], [1, -1, 1], [-1, -1, 1]], normal: [0, -1, 0] },
  { id: 'side',   corners: [[-1, -1, -1], [1, -1, -1], [1, 1, -1], [-1, 1, -1]], normal: [0, 0, -1] },
  { id: 'side',   corners: [[-1, -1, 1], [1, -1, 1], [1, 1, 1], [-1, 1, 1]],  normal: [0, 0, 1] },
  { id: 'side',   corners: [[1, -1, -1], [1, -1, 1], [1, 1, 1], [1, 1, -1]],  normal: [1, 0, 0] },
  { id: 'side',   corners: [[-1, -1, -1], [-1, -1, 1], [-1, 1, 1], [-1, 1, -1]], normal: [-1, 0, 0] },
];

function rotX(p, b) { const c = Math.cos(b), s = Math.sin(b); return [p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c]; }
function rotY(p, b) { const c = Math.cos(b), s = Math.sin(b); return [p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c]; }
function norm(v) { const l = Math.hypot(v[0], v[1], v[2]) || 1; return [v[0] / l, v[1] / l, v[2] / l]; }

function toCam(p, yaw, pitch) { return rotX(rotY(p, yaw), pitch); }

// Projected 2D bounding box (at scale = 1) of the rotated cube corners.
function cubeBounds(yaw, pitch) {
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  for (const c of CUBE_CORNERS) {
    const v = toCam(c, yaw, pitch);
    if (v[0] < minX) minX = v[0];
    if (v[0] > maxX) maxX = v[0];
    if (v[1] < minY) minY = v[1];
    if (v[1] > maxY) maxY = v[1];
  }
  return { minX, maxX, minY, maxY };
}

// Day / night lighting presets.
const LIGHT_PRESETS = {
  day: {
    skyTop: '#5b93e6', skyMid: '#8fb6f0', skyBot: '#d7e8fb',
    light: norm([0.42, 0.82, 0.42]),
    sunIntensity: 1.0, ambient: 0.34, contactAlpha: 0.34,
    glow: 'rgba(80,130,230,0.10)', grid: 'rgba(110,150,235,0.13)',
    edge: 'rgba(255,255,255,0.12)',
  },
  night: {
    skyTop: '#04091a', skyMid: '#081227', skyBot: '#14233f',
    light: norm([-0.32, 0.72, 0.6]),
    sunIntensity: 0.55, ambient: 0.52, contactAlpha: 0.52,
    glow: 'rgba(90,120,210,0.13)', grid: 'rgba(110,150,235,0.16)',
    edge: 'rgba(170,200,255,0.12)',
  },
};

/**
 * Draw one frame onto `ctx`.
 * textures: { top, side, bottom } canvases (bump-lit shaded).
 * opts: {
 *   mode: 'day'|'night',
 *   zoom: number (>=1 keeps the block fully visible; >1 lets the user push in),
 *   yaw, pitch,
 *   grid: boolean, bg: css color override
 * }
 */
function renderBlock(ctx, textures, opts) {
  const w = ctx.canvas.width;
  const h = ctx.canvas.height;
  const mode = opts.mode || 'day';
  const P = LIGHT_PRESETS[mode] || LIGHT_PRESETS.day;

  const yaw = opts.yaw || 0;
  const pitch = opts.pitch != null ? opts.pitch : 0.52;

  ctx.save();
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';

  // ---- background sky ----
  const sky = ctx.createLinearGradient(0, 0, 0, h);
  sky.addColorStop(0, P.skyTop);
  sky.addColorStop(0.5, P.skyMid);
  sky.addColorStop(1, P.skyBot);
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, w, h);

  // ---- measure the cube to AUTO-FIT inside the frame ----
  const b = cubeBounds(yaw, pitch);
  const spanX = Math.max(1e-4, b.maxX - b.minX);
  const spanY = Math.max(1e-4, b.maxY - b.minY);

  // Reserve margins; more room at the bottom for floor + contact shadow.
  const marginX = w * 0.12;
  const marginTop = h * 0.10;
  const marginBottom = h * 0.26;
  const availW = w - marginX * 2;
  const availH = h - marginTop - marginBottom;

  // At zoom=1 the cube fits perfectly; zoom lets the user push in but never less than fit.
  const zoom = opts.zoom != null ? Math.max(0.65, opts.zoom) : 1;
  const scale = Math.min(availW / spanX, availH / spanY) * zoom;

  const midX = (b.minX + b.maxX) / 2;
  const midY = (b.minY + b.maxY) / 2;
  // screen: x = cx + v[0]*scale ; y = cy - v[1]*scale
  const cx = w / 2 - midX * scale;
  const cy = marginTop + availH / 2 + midY * scale;

  // ---- soft radial glow behind the block ----
  const rim = ctx.createRadialGradient(cx, cy - scale * 0.2, scale * 0.2, cx, cy - scale * 0.2, scale * 2.2);
  rim.addColorStop(0, P.glow);
  rim.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = rim;
  ctx.fillRect(0, 0, w, h);

  // ---- floor grid (ground plane y = -1), clipped to a tidy platform ----
  if (opts.grid !== false) drawFloor(ctx, w, h, yaw, pitch, scale, cx, cy, P.grid, P.glow);

  function project(p) {
    const v = toCam(p, yaw, pitch);
    return { x: cx + v[0] * scale, y: cy - v[1] * scale, z: v[2], n: norm(v) };
  }

  // ---- contact shadow under the cube ----
  const base = project([0, -1, 0]);
  ctx.beginPath();
  ctx.ellipse(base.x, base.y + scale * 0.12, scale * 1.05, scale * 0.30, 0, 0, Math.PI * 2);
  ctx.fillStyle = `rgba(4,7,18,${P.contactAlpha})`;
  ctx.fill();

  // ---- back-face cull + painter's sort ----
  const drawFaces = [];
  for (const face of CUBE_FACES) {
    const n = norm(toCam(face.normal, yaw, pitch));
    if (n[2] <= 0.05) continue;
    const pts = face.corners.map(project);
    const depth = pts.reduce((a, p) => a + p.z, 0) / pts.length;
    drawFaces.push({ face, pts, n, depth });
  }
  drawFaces.sort((a, b2) => a.depth - b2.depth);

  const light = P.light;
  const ambient = P.ambient;

  for (const df of drawFaces) {
    const ftex =
      df.face.id === 'top' ? textures.top :
      df.face.id === 'bottom' ? textures.bottom :
      textures.side;
    if (!ftex) continue;

    const p0 = df.pts[0], p1 = df.pts[1], p2 = df.pts[2], p3 = df.pts[3];
    const srcW = ftex.width || 256;
    const srcH = ftex.height || 256;
    const invS = 1 / srcW;

    ctx.setTransform(
      (p1.x - p0.x) * invS, (p1.y - p0.y) * invS,
      (p3.x - p0.x) * invS, (p3.y - p0.y) * invS,
      p0.x, p0.y
    );
    // Slightly crisp when idle, but keep quality high.
    ctx.imageSmoothingEnabled = true;
    ctx.drawImage(ftex, 0, 0, srcW, srcH);
    ctx.setTransform(1, 0, 0, 1, 0, 0);

    // face-level directional shading that follows rotation
    const d = Math.max(0, df.n[0] * light[0] + df.n[1] * light[1] + df.n[2] * light[2]);
    const brightness = ambient + d * 0.78 * P.sunIntensity;
    const shadowAlpha = Math.max(0, Math.min(0.62, 1 - brightness));
    if (shadowAlpha > 0.004) {
      ctx.beginPath();
      ctx.moveTo(p0.x, p0.y); ctx.lineTo(p1.x, p1.y);
      ctx.lineTo(p2.x, p2.y); ctx.lineTo(p3.x, p3.y);
      ctx.closePath();
      ctx.fillStyle = `rgba(8,10,26,${shadowAlpha})`;
      ctx.fill();
    }

    // crisp edge highlight
    ctx.beginPath();
    ctx.moveTo(p0.x, p0.y); ctx.lineTo(p1.x, p1.y);
    ctx.lineTo(p2.x, p2.y); ctx.lineTo(p3.x, p3.y);
    ctx.closePath();
    ctx.strokeStyle = P.edge;
    ctx.lineWidth = 1.2;
    ctx.stroke();
  }

  ctx.restore();
}

// Draw an isometric floor grid + soft glow beneath the block.
function drawFloor(ctx, w, h, yaw, pitch, scale, cx, cy, gridCss, glowCss) {
  ctx.save();

  const g = ctx.createRadialGradient(cx, cy, scale * 0.1, cx, cy, scale * 1.7);
  g.addColorStop(0, glowCss);
  g.addColorStop(0.55, 'rgba(60,80,150,0.05)');
  g.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, w, h);

  function gp(x, z) { return toCam([x, -1, z], yaw, pitch); }

  // A tidy platform slightly larger than the cube.
  const gridN = 2;
  ctx.strokeStyle = gridCss;
  ctx.lineWidth = 1;
  ctx.beginPath();
  for (let i = -gridN * 2; i <= gridN * 2; i++) {
    // lines parallel to X axis
    let a = gp(i / 2, -gridN), b = gp(i / 2, gridN);
    ctx.moveTo(cx + a[0] * scale, cy - a[1] * scale);
    ctx.lineTo(cx + b[0] * scale, cy - b[1] * scale);
    // lines parallel to Z axis
    a = gp(-gridN, i / 2); b = gp(gridN, i / 2);
    ctx.moveTo(cx + a[0] * scale, cy - a[1] * scale);
    ctx.lineTo(cx + b[0] * scale, cy - b[1] * scale);
  }
  ctx.stroke();
  ctx.restore();
}

window.Block3D = { renderBlock, CUBE_FACES, CUBE_CORNERS, LIGHT_PRESETS, cubeBounds };
