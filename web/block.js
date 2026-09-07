/*
 * PixelCraft Studio — lightweight software 3D block renderer.
 * Renders a rotating, textured cube (top/side/bottom faces can each use a different
 * generated texture — e.g. a grass block's 3 textures) onto a 2D canvas using an
 * isometric-style orthographic projection with per-face lighting + baked relief.
 */

const CUBE_FACES = [
  { id: 'top',  corners: [[-1, 1, -1], [1, 1, -1], [1, 1, 1], [-1, 1, 1]],  normal: [0, 1, 0] },
  { id: 'bottom', corners: [[-1, -1, -1], [1, -1, -1], [1, -1, 1], [-1, -1, 1]], normal: [0, -1, 0] },
  { id: 'side', corners: [[-1, -1, -1], [1, -1, -1], [1, 1, -1], [-1, 1, -1]], normal: [0, 0, -1] },
  { id: 'side', corners: [[-1, -1, 1], [1, -1, 1], [1, 1, 1], [-1, 1, 1]],  normal: [0, 0, 1] },
  { id: 'side', corners: [[1, -1, -1], [1, -1, 1], [1, 1, 1], [1, 1, -1]],  normal: [1, 0, 0] },
  { id: 'side', corners: [[-1, -1, -1], [-1, -1, 1], [-1, 1, 1], [-1, 1, -1]], normal: [-1, 0, 0] },
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

/*
 * Draw one frame of a rotating block onto `ctx`.
 * textures: { top, side, bottom } canvases (the bump-lit shaded versions)
 * opts: { yaw, pitch, scale, cx, cy, spin, textureSize }
 */
function renderBlock(ctx, textures, opts) {
  const yaw = opts.yaw;
  const pitch = opts.pitch;
  const scale = opts.scale; // pixel half-size (corners are unit [-1,1])
  const ambient = 0.32;
  const light = norm([0.5, 0.82, 0.45]); // camera-space light

  const SArray = typeof textures.size !== 'undefined' ? textures.size : 256;

  // Transform a model-space corner to screen [x, y] and depth z.
  function project(p) {
    let v = rotY(p, yaw);
    v = rotX(v, pitch);
    return {
      x: opts.cx + v[0] * scale,
      y: opts.cy - v[1] * scale,
      z: v[2],
      n: norm(v), // rotated normal = normal transformed by same rotation
    };
  }

  const drawFaces = [];
  for (const face of CUBE_FACES) {
    const pts = face.corners.map(project);
    // back-face culling: normal transformed to camera space, visible if z > 0
    let n = norm(rotX(rotY(face.normal, yaw), pitch));
    if (n[2] <= 0.05) continue;

    const depth = pts.reduce((a, p) => a + p.z, 0) / pts.length;
    drawFaces.push({ face, pts, n, depth });
  }
  // painter's algorithm: farthest first (smallest camera-space z toward viewer)
  drawFaces.sort((a, b) => a.depth - b.depth);

  // save canvas state
  ctx.save();
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';

  for (const df of drawFaces) {
    const ftex =
      df.face.id === 'top' ? textures.top :
      df.face.id === 'bottom' ? textures.bottom :
      textures.side;

    const p0 = df.pts[0], p1 = df.pts[1], p2 = df.pts[2], p3 = df.pts[3];
    // affine mapping from source (0..S) to parallelogram p0,p1,p2,p3
    const invS = 1 / 256;
    ctx.setTransform(
      (p1.x - p0.x) * invS, (p1.y - p0.y) * invS,
      (p3.x - p0.x) * invS, (p3.y - p0.y) * invS,
      p0.x, p0.y
    );
    ctx.drawImage(ftex, 0, 0, 256, 256);
    ctx.setTransform(1, 0, 0, 1, 0, 0);

    // face-level lighting (darker when the block turns away from the light)
    const d = Math.max(0, df.n[0] * light[0] + df.n[1] * light[1] + df.n[2] * light[2]);
    const brightness = ambient + d * 0.75;
    const shadowAlpha = Math.max(0, Math.min(0.65, 1 - brightness));
    if (shadowAlpha > 0.005) {
      ctx.beginPath();
      ctx.moveTo(p0.x, p0.y);
      ctx.lineTo(p1.x, p1.y);
      ctx.lineTo(p2.x, p2.y);
      ctx.lineTo(p3.x, p3.y);
      ctx.closePath();
      ctx.fillStyle = `rgba(8,10,26,${shadowAlpha})`;
      ctx.fill();
    }
  }

  ctx.restore();
}

window.Block3D = { renderBlock, CUBE_FACES };
