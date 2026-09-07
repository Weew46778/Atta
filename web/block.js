/*
 * PixelCraft Studio — software 3D block renderer (isometric).
 * Renders a rotating, textured cube (top/side/bottom faces may each use a different
 * generated texture — e.g. a grass block's 3 textures). The cube is auto-fitted and
 * centered inside the canvas, sitting on a subtle floor grid with a contact shadow,
 * so the preview reads as a clean product shot.
 */

const CUBE_FACES = [
  { id: 'top',    corners: [[-1, 1, -1], [1, 1, -1], [1, 1, 1], [-1, 1, 1]],  normal: [0, 1, 0] },
  { id: 'bottom', corners: [[-1, -1, -1], [1, -1, -1], [1, -1, 1], [-1, -1, 1]], normal: [0, -1, 0] },
  { id: 'side',   corners: [[-1, -1, -1], [1, -1, -1], [1, 1, -1], [-1, 1, -1]], normal: [0, 0, -1] },
  { id: 'side',   corners: [[-1, -1, 1], [1, -1, 1], [1, 1, 1], [-1, 1, 1]],  normal: [0, 0, 1] },
  { id: 'side',   corners: [[1, -1, -1], [1, -1, 1], [1, 1, 1], [1, 1, -1]],  normal: [1, 0, 0] },
  { id: 'side',   corners: [[-1, -1, -1], [-1, -1, 1], [-1, 1, 1], [-1, 1, -1]], normal: [-1, 0, 0] },
];

function rotX(p, b) {
  const c = Math.cos(b), s = Math.sin(b);
  return [p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c];
}
function rotY(p, b) {
  const c = Math.cos(b), s = Math.sin(b);
  return [p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c];
}
function norm(v) {
  const l = Math.hypot(v[0], v[1], v[2]) || 1;
  return [v[0] / l, v[1] / l, v[2] / l];
}

// Map a 3D point (model space) through rotation to camera space.
function toCam(p, yaw, pitch) {
  let v = rotY(p, yaw);
  v = rotX(v, pitch);
  return v;
}

// Day / night lighting presets.
const LIGHT_PRESETS = {
  day: {
    skyTop: '#7fb3ff', skyMid: '#a9ccff', skyBot: '#dff0ff',
    light: norm([0.42, 0.82, 0.42]),
    sunIntensity: 1.0, ambient: 0.34, contactAlpha: 0.34,
    glow: 'rgba(120,150,230,0.10)',
    grid: 'rgba(120,150,230,0.12)',
    edge: 'rgba(255,255,255,0.10)',
  },
  night: {
    skyTop: '#050a1c', skyMid: '#0a1228', skyBot: '#16233f',
    light: norm([-0.32, 0.72, 0.6]),   // cool moonlight from upper-left
    sunIntensity: 0.55, ambient: 0.5, contactAlpha: 0.5,
    glow: 'rgba(90,120,210,0.12)',
    grid: 'rgba(100,140,230,0.14)',
    edge: 'rgba(160,190,255,0.10)',
  },
};

/*
 * Draw one frame. `ctx` must already be scaled for DPR.
 * textures: { top, side, bottom } canvases (bump-lit shaded)
 * opts: { cx, cy, yaw, pitch, mode ('day'|'night'), zoom } — scale auto-fitted.
 */
function renderBlock(ctx, textures, opts) {
  const w = ctx.canvas.width;
  const h = ctx.canvas.height;
  const mode = opts.mode || 'day';
  const P = LIGHT_PRESETS[mode] || LIGHT_PRESETS.day;

  // auto-fit: cube half-extent is 1 → full cube spans 2*scale px.
  const scale = Math.min(w, h) * 0.46 * (opts.zoom || 1);
  const cx = opts.cx != null ? opts.cx : w / 2;
  const cy = opts.cy != null ? opts.cy : h / 2;

  const yaw = opts.yaw || 0;
  const pitch = opts.pitch || 0.5;
  const ambient = P.ambient;
  const light = P.light;

  ctx.save();
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';

  // ---- sky gradient (mood matches day/night) ----
  const sky = ctx.createLinearGradient(0, 0, 0, h);
  sky.addColorStop(0, P.skyTop);
  sky.addColorStop(0.5, P.skyMid);
  sky.addColorStop(1, P.skyBot);
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, w, h);

  // soft radial glow behind the block (a silhouette rim for night)
  const rim = ctx.createRadialGradient(cx, cy, scale * 0.2, cx, cy, scale * 1.8);
  rim.addColorStop(0, P.glow);
  rim.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = rim;
  ctx.fillRect(0, 0, w, h);

  // ---- floor grid (ground plane y = -1) ----
  drawFloor(ctx, w, h, yaw, pitch, scale, cx, cy, P.grid, P.glow);

  function project(p) {
    const v = toCam(p, yaw, pitch);
    return { x: cx + v[0] * scale, y: cy - v[1] * scale, z: v[2], n: norm(v) };
  }

  // ---- contact shadow (ellipse under the cube) ----
  const base = project([0, -1, 0]);
  ctx.beginPath();
  ctx.ellipse(base.x, base.y + scale * 0.16, scale * 1.02, scale * 0.34, 0, 0, Math.PI * 2);
  ctx.fillStyle = `rgba(5,8,20,${P.contactAlpha})`;
  ctx.fill();

  // ---- back-face cull + painter's sort ----
  const drawFaces = [];
  for (const face of CUBE_FACES) {
    const n = norm(toCam(face.normal, yaw, pitch));
    if (n[2] <= 0.06) continue;
    const pts = face.corners.map(project);
    const depth = pts.reduce((a, p) => a + p.z, 0) / pts.length;
    drawFaces.push({ face, pts, n, depth });
  }
  drawFaces.sort((a, b) => a.depth - b.depth);

  // outline helper (crisp edges) using the preset
  const edgeCss = P.edge;

  for (const df of drawFaces) {
    const ftex =
      df.face.id === 'top' ? textures.top :
      df.face.id === 'bottom' ? textures.bottom :
      textures.side;
    if (!ftex) continue;

    const p0 = df.pts[0], p1 = df.pts[1], p2 = df.pts[2], p3 = df.pts[3];
    const invS = 1 / 256;
    ctx.setTransform(
      (p1.x - p0.x) * invS, (p1.y - p0.y) * invS,
      (p3.x - p0.x) * invS, (p3.y - p0.y) * invS,
      p0.x, p0.y
    );
    ctx.drawImage(ftex, 0, 0, 256, 256);
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

    // subtle edge highlight for a crisp product look
    ctx.beginPath();
    ctx.moveTo(p0.x, p0.y); ctx.lineTo(p1.x, p1.y);
    ctx.lineTo(p2.x, p2.y); ctx.lineTo(p3.x, p3.y);
    ctx.closePath();
    ctx.strokeStyle = edgeCss;
    ctx.lineWidth = 1.2;
    ctx.stroke();
  }

  ctx.restore();
}

// Draw an isometric floor grid + a soft glow beneath the block.
function drawFloor(ctx, w, h, yaw, pitch, scale, cx, cy, gridCss, glowCss) {
  ctx.save();
  // soft radial glow centred under the block
  const g = ctx.createRadialGradient(cx, cy, scale * 0.1, cx, cy, scale * 1.6);
  g.addColorStop(0, glowCss);
  g.addColorStop(0.55, 'rgba(60,80,150,0.05)');
  g.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, w, h);

  // project ground-plane points (y = -1)
  function gp(x, z) { return toCam([x, -1, z], yaw, pitch); }
  const gridN = 4; // half-extent grid (lines from -gridN..gridN)
  ctx.strokeStyle = gridCss;
  ctx.lineWidth = 1;

  ctx.beginPath();
  for (let i = -gridN; i <= gridN; i++) {
    // lines parallel to X axis
    let a = gp(i, -gridN), b = gp(i, gridN);
    ctx.moveTo(cx + a[0] * scale, cy - (a[1] + 1) * scale);
    ctx.lineTo(cx + b[0] * scale, cy - (b[1] + 1) * scale);
    // lines parallel to Z axis
    a = gp(-gridN, i); b = gp(gridN, i);
    ctx.moveTo(cx + a[0] * scale, cy - (a[1] + 1) * scale);
    ctx.lineTo(cx + b[0] * scale, cy - (b[1] + 1) * scale);
  }
  ctx.stroke();
  ctx.restore();
}

window.Block3D = { renderBlock, CUBE_FACES, LIGHT_PRESETS };
