/*
 * PixelCraft Studio — main controller (browser preview).
 * Generates live textures, drives the 3D block preview and exports packs.
 */

(function () {
  const $ = (id) => document.getElementById(id);

  // base texture size; we always render at 256 and let the segment constrain display
  let texSize = 256;
  let seed = 1337;
  let realism = 0.75;   // 0..1
  let relief = 0.6;     // 0..1
  let autoSpin = true;

  const state = {
    yaw: Math.PI / 6,
    pitch: 0.55,
    zoom: 1,
    mode: 'day',
    dragging: false,
    lastX: 0, lastY: 0,
  };

  // selected block (determines the faces shown on the cube). Start with grass.
  const selectedBlock = { id: 'grass_top' };

  // ------- init UI -------
  // Curated order: each entry is a representative texture id; the "block" it belongs to
  // drives the 3-face cube preview. These map to the 30+ blocks in the pack.
  const BLOCK_CHIPS = [
    'grass_top', 'dirt', 'coarse_dirt', 'mycelium_top', 'moss_block', 'mud', 'packed_mud',
    'stone', 'cobblestone', 'mossy_cobblestone', 'deepslate', 'tuff', 'gravel', 'basalt_side', 'blackstone', 'gilded_blackstone',
    'sand', 'red_sand', 'clay', 'terracotta', 'sandstone', 'red_sandstone',
    'oak_log_side', 'oak_planks', 'oak_leaves',
    'bricks', 'snow', 'ice', 'packed_ice', 'water',
    'netherrack', 'glowstone', 'obsidian', 'quartz_block', 'end_stone', 'magma',
    'sponge', 'wool_blue', 'wool_purple', 'prismarine', 'dark_prismarine', 'sea_lantern',
    'gold_ore', 'iron_ore', 'coal_ore', 'diamond_ore', 'redstone_ore', 'emerald_ore', 'lapis_ore', 'copper_ore',
    'gold_block', 'iron_block', 'diamond_block', 'emerald_block', 'redstone_block', 'lapis_block', 'copper_block', 'netherite_block',
  ];

  function buildBlockList() {
    const list = $('blockList');
    list.innerHTML = '';
    BLOCK_CHIPS.forEach((id) => {
      const bundle = PixelCraft.getTexture(id, seed, 48, realism);
      const c = document.createElement('canvas');
      c.width = 14; c.height = 14;
      if (bundle.color) c.getContext('2d').drawImage(canvasOf(bundle.color), 0, 0, 14, 14);
      const chip = document.createElement('div');
      chip.className = 'chip on';
      chip.dataset.id = id;
      chip.innerHTML = `<span class="swatch"></span>${PixelCraft.TEXTURE_REGISTRY[id].title}`;
      chip.querySelector('.swatch').replaceWith(c);
      chip.addEventListener('click', () => {
        // selecting the block also puts it on the 3D cube
        selectForPreview(generatedPack, id);
      });
      list.appendChild(chip);
    });
  }

  function canvasOf(imgData) {
    const c = document.createElement('canvas');
    c.width = imgData.width; c.height = imgData.height;
    c.getContext('2d').putImageData(imgData, 0, 0);
    return c;
  }

  // Debounce for the expensive sliders (they rebuild all 14 textures).
  let regenTimer = null;
  function debounceRegen() {
    clearTimeout(regenTimer);
    regenTimer = setTimeout(() => regenerate(), 90);
  }

  // ------- regenerate everything -------
  function regenerate() {
    const res = $('resSeg').querySelector('button.on');
    texSize = parseInt(res ? res.dataset.v : '256', 10);
    const pack = Pack.buildTexturePack(seed, texSize, realism, $('packName').value);
    generatedPack = pack;
    renderGrid(pack);
    selectForPreview(pack, selectedBlock.id);
    renderShaderSettings();
    $('realismVal').textContent = Math.round(realism * 100) + '%';
    $('seedDisplay').textContent = seed;
    // scale the block-face preview thumbs
  }

  function renderGrid(pack) {
    const grid = $('grid');
    grid.innerHTML = '';
    $('texCount').textContent = pack.preview.length;
    pack.preview.forEach((p) => {
      const tile = document.createElement('div');
      tile.className = 'tile';
      tile.dataset.id = p.id;
      tile.dataset.block = p.block;
      const c = document.createElement('canvas');
      c.width = 96; c.height = 96;
      c.getContext('2d').drawImage(p.shadedCanvas, 0, 0, 96, 96);
      const name = document.createElement('div');
      name.className = 't-name';
      name.textContent = p.title;
      tile.appendChild(c);
      tile.appendChild(name);
      tile.addEventListener('click', () => selectForPreview(pack, p.id));
      grid.appendChild(tile);
    });
  }

  // Set the 3-face cube to the block that owns `id`, and highlight the tile.
  function selectForPreview(pack, id) {
    selectedBlock.id = id;
    const blockName = PixelCraft.TEXTURE_REGISTRY[id].block;
    const members = pack.preview.filter((p) => p.block === blockName);
    const single = members.length === 1;

    // For multi-face blocks use the _top/_side/_bottom variants; for single-face blocks
    // reuse the one texture on all three faces.
    const top = members.find((m) => m.id.endsWith('_top')) || members[0];
    const side = members.find((m) => m.id.endsWith('_side')) || (single ? members[0] : members.find((m) => m.id.endsWith('_log')) || members[0]);
    const bottom = members.find((m) => m.id.endsWith('_bottom')) || top;

    // fill face thumbs
    thumb('faceTop', top ? top.shadedCanvas : null);
    thumb('faceSide', side ? side.shadedCanvas : null);
    thumb('faceBottom', bottom ? bottom.shadedCanvas : null);

    currentFaces = {
      top: top ? top.shadedCanvas : null,
      side: side ? side.shadedCanvas : null,
      bottom: bottom ? bottom.shadedCanvas : null,
    };
    $('previewTitle').textContent = blockName.charAt(0).toUpperCase() + blockName.replace(/_/g, ' ') ;

    // highlight selected tile
    document.querySelectorAll('.tile').forEach((t) => t.classList.toggle('sel', t.dataset.id === id));
  }

  let currentFaces = { top: null, side: null, bottom: null };
  let generatedPack = null;

  function thumb(id, canvas) {
    const c = $(id);
    if (!canvas) {
      c.getContext('2d').clearRect(0, 0, c.width, c.height);
      return;
    }
    const ctx = c.getContext('2d');
    const sz = Math.min(c.width, c.height);
    // fit: draw centered
    ctx.clearRect(0, 0, c.width, c.height);
    const s = sz / Math.max(canvas.width, canvas.height);
    ctx.drawImage(canvas, (c.width - canvas.width * s) / 2, (c.height - canvas.height * s) / 2, canvas.width * s, canvas.height * s);
  }

  function renderShaderSettings() {
    const s = ShaderGen.buildShaderSettings(realism);
    const map = [
      ['Sun intensity', s.sunIntensity.toFixed(2)],
      ['Exposure', s.exposure.toFixed(2)],
      ['Fog density', s.fogDensity.toFixed(4)],
      ['Water reflection', s.waterReflect.toFixed(2)],
      ['AO strength', s.ambientOcclusion.toFixed(2)],
      ['Roughness scale', s.roughnessScale.toFixed(2)],
      ['Saturation', s.saturation.toFixed(2)],
      ['Contrast', s.contrast.toFixed(2)],
    ];
    const el = $('shaderSettings');
    el.innerHTML = map.map(([k, v]) =>
      `<div class="setting"><div class="k">${k}</div><div class="val">${v}</div></div>`).join('');
  }

  // ------- 3D block animation -------
  function animate() {
    if (autoSpin && !state.dragging) state.yaw += 0.008;
    requestAnimationFrame(animate);
  }

  function drawBlock() {
    const canvas = $('blockCanvas');
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    if (canvas.width !== w * dpr || canvas.height !== h * dpr) {
      canvas.width = w * dpr; canvas.height = h * dpr;
    }
    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);

    if (currentFaces.top && currentFaces.side) {
      window.Block3D.renderBlock(ctx, {
        top: currentFaces.top, side: currentFaces.side, bottom: currentFaces.bottom,
      }, {
        yaw: state.yaw, pitch: state.pitch,
        cx: w / 2, cy: h / 2,
        zoom: state.zoom,
        mode: state.mode,
      });
    }
  }

  function loop() {
    drawBlock();
    requestAnimationFrame(loop);
  }

  // ------- input -------
  function setupInput() {
    $('randomize').addEventListener('click', () => {
      seed = Math.floor(Math.random() * 1e9);
      $('seed').value = seed;
      regenerate();
    });
    $('seed').addEventListener('input', () => {
      seed = parseInt($('seed').value, 10) || 0;
      regenerate();
    });
    $('packName').addEventListener('input', debounceRegen);
    $('realism').addEventListener('input', () => {
      realism = parseInt($('realism').value, 10) / 100;
      debounceRegen();
    });
    $('relief').addEventListener('input', () => {
      relief = parseInt($('relief').value, 10) / 100;
      debounceRegen();
    });
    $('resSeg').querySelectorAll('button').forEach((b) => {
      b.addEventListener('click', () => {
        $('resSeg').querySelectorAll('button').forEach((x) => x.classList.remove('on'));
        b.classList.add('on');
        regenerate();
      });
    });
    $('toggleAuto').addEventListener('click', () => {
      autoSpin = !autoSpin;
      $('toggleAuto').classList.toggle('active', autoSpin);
    });

    // day / night light toggle
    document.querySelectorAll('#lightToggle button').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.mode = btn.dataset.mode;
        document.querySelectorAll('#lightToggle button').forEach((x) => x.classList.toggle('on', x === btn));
      });
    });

    // cube drag / zoom
    const canvas = $('blockCanvas');
    canvas.addEventListener('pointerdown', (e) => {
      state.dragging = true;
      state.lastX = e.clientX; state.lastY = e.clientY;
      canvas.setPointerCapture(e.pointerId);
    });
    canvas.addEventListener('pointermove', (e) => {
      if (!state.dragging) return;
      state.yaw += (e.clientX - state.lastX) * 0.01;
      state.pitch += (e.clientY - state.lastY) * 0.01;
      state.pitch = Math.max(-1.3, Math.min(1.3, state.pitch));
      state.lastX = e.clientX; state.lastY = e.clientY;
    });
    canvas.addEventListener('pointerup', () => { state.dragging = false; });
    canvas.addEventListener('wheel', (e) => {
      e.preventDefault();
      state.zoom *= (e.deltaY < 0 ? 1.06 : 0.94);
      state.zoom = Math.max(0.6, Math.min(2.0, state.zoom));
    }, { passive: false });
  }

  // ------- export -------
  async function exportPack(kind) {
    const name = ($('packName').value || 'pixelcraft').replace(/[^\w\- ]+/g, '').trim() || 'pixelcraft';
    const pack = generatedPack || Pack.buildTexturePack(seed, texSize, realism, $('packName').value);

    let files = [];
    let filename;
    if (kind === 'texture') {
      files = pack.files;
      filename = `${name}_texture.mcpack`;
    } else if (kind === 'shader') {
      const sh = ShaderGen.buildShaderFiles(name, realism, pack.icon);
      files = sh.files;
      filename = `${name}_shader.mcpack`;
    } else {
      const sh = ShaderGen.buildShaderFiles(name, realism, pack.icon);
      files = [...pack.files, ...sh.files.map((f) => ({ ...f, path: 'shader/' + f.path }))];
      filename = `${name}_texture_and_shader.zip`;
    }

    // Convert canvases to bytes
    const converted = await Promise.all(files.map(async (f) => {
      let data = f.data;
      if (data && data.getContext) {
        data = await Zip.canvasToBytes(data);
      }
      return { path: f.path, data };
    }));

    const blob = Zip.buildZip(converted);
    if (kind === 'both') {
      Zip.download(blob, filename);
    } else {
      // .mcpack is simply the zip; rename mime to video/mp4 is not needed — keep octet
      Zip.download(blob, filename);
    }
    toast(`Exported ${filename}`);
  }

  function toast(msg) {
    const t = document.createElement('div');
    t.className = 'toast';
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(() => { t.classList.add('show'); }, 10);
    setTimeout(() => { t.classList.remove('show'); setTimeout(() => t.remove(), 300); }, 2600);
  }

  // ------- boot -------
  $('btnExportPack').addEventListener('click', () => exportPack('texture'));
  $('btnExportShader').addEventListener('click', () => exportPack('shader'));
  $('btnExportBoth').addEventListener('click', () => exportPack('both'));

  buildBlockList();
  setupInput();
  regenerate();
  animate();
  loop();
  registerToastStyles();
  // initial seed display fix
  $('seedDisplay').textContent = seed;
  const blockSet = new Set(Object.values(PixelCraft.TEXTURE_REGISTRY).map((r) => r.block));
  $('blockCount').textContent = blockSet.size;

  function registerToastStyles() {
    const st = document.createElement('style');
    st.textContent = `.toast{position:fixed;bottom:22px;left:50%;transform:translateX(-50%) translateY(20px);background:#121a30;border:1px solid var(--accent);color:#e8eefc;padding:11px 20px;border-radius:999px;box-shadow:var(--shadow);opacity:0;transition:.25s;z-index:999;font-size:14px;}.toast.show{opacity:1;transform:translateX(-50%) translateY(0);}`;
    document.head.appendChild(st);
  }
})();
