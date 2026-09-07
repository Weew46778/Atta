/*
 * PixelCraft Studio — main controller (browser preview).
 * Generates live textures, drives the 3D block preview and exports packs.
 */

(function () {
  const $ = (id) => document.getElementById(id);

  // Full resolution range the user asked for: 16 → 2048.
  const RESOLUTIONS = [16, 32, 64, 128, 256, 512, 1024, 2048];

  let texSize = 256;
  let seed = 1337;
  let realism = 0.82;   // 0..1 (drives shader settings)
  let relief = 0.6;     // 0..1 (bump strength)
  let autoSpin = true;
  let showGrid = true;
  let currentFilter = 'All';

  const state = {
    yaw: Math.PI / 6,
    pitch: 0.55,
    zoom: 1,
    mode: 'day',
    dragging: false,
    lastX: 0, lastY: 0,
  };

  const selectedBlock = { id: 'grass_top' };

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
    // wood variants
    'spruce_planks', 'birch_planks', 'jungle_planks', 'acacia_planks', 'dark_oak_planks', 'mangrove_planks', 'cherry_planks',
    'spruce_log_side', 'birch_log_side', 'jungle_log_side', 'acacia_log_side', 'dark_oak_log_side', 'cherry_log_side',
    'leaves_spruce', 'leaves_birch', 'leaves_cherry', 'leaves_mangrove', 'leaves_azalea',
    // colors (representative + power palette)
    'wool_white', 'wool_red', 'wool_black', 'wool_lime', 'wool_cyan',
    'concrete_red', 'concrete_blue', 'concrete_light_blue', 'concrete_green', 'concrete_white', 'concrete_orange', 'concrete_black',
    'concrete_powder_red', 'concrete_powder_yellow', 'concrete_powder_cyan',
    'terracotta_red', 'terracotta_orange', 'terracotta_cyan', 'terracotta_black',
    'glazed_terracotta_white', 'glazed_terracotta_blue', 'glazed_terracotta_red', 'glazed_terracotta_black',
    // stone family
    'granite', 'polished_granite', 'diorite', 'polished_diorite', 'andesite', 'polished_andesite', 'calcite',
    'stone_bricks', 'mossy_stone_bricks', 'cracked_stone_bricks', 'chiseled_stone_bricks',
    'deepslate_bricks', 'cracked_deepslate_bricks', 'polished_deepslate', 'smooth_stone',
    'smooth_sandstone', 'cut_sandstone', 'chiseled_sandstone', 'end_stone_bricks',
    // nether & end
    'soul_sand', 'soul_soil', 'crimson_nylium', 'warped_nylium', 'nether_wart_block', 'warped_wart_block',
    'shroomlight', 'nether_gold_ore', 'nether_quartz_ore', 'ancient_debris',
    'purpur_block', 'purpur_pillar',
    // misc
    'bookshelf', 'hay_block', 'bone_block', 'honey_block', 'dried_kelp_block', 'slime_block',
  ];

  let generatedPack = null;
  let currentFaces = { top: null, side: null, bottom: null };

  // ------- build resolution segmented control (16..2048) -------
  function buildResSeg() {
    const seg = $('resSeg');
    seg.innerHTML = '';
    RESOLUTIONS.forEach((v) => {
      const b = document.createElement('button');
      b.dataset.v = v;
      b.textContent = v >= 1024 ? (v / 1024) + 'K' : v + '';
      b.title = v + '×' + v + ' per block';
      if (v === texSize) b.classList.add('on');
      b.addEventListener('click', () => {
        seg.querySelectorAll('button').forEach((x) => x.classList.remove('on'));
        b.classList.add('on');
        texSize = v;
        debounceRegen();
      });
      seg.appendChild(b);
    });
    $('resVal').textContent = texSize + '×' + texSize;
  }

  function canvasOf(imgData) {
    const c = document.createElement('canvas');
    c.width = imgData.width; c.height = imgData.height;
    c.getContext('2d').putImageData(imgData, 0, 0);
    return c;
  }

  // ------- apply the advanced quality settings to the generator -------
  function applyConfig() {
    const cfg = PixelCraft.TEXTURE_CONFIG;
    cfg.detail = parseInt($('detail').value, 10) / 100;
    cfg.contrast = parseInt($('contrast').value, 10) / 100;
    cfg.saturation = parseInt($('sat').value, 10) / 100;
    cfg.ao = parseInt($('ao').value, 10) / 100;
    $('detailVal').textContent = Math.round(cfg.detail * 100) + '%';
    $('contrastVal').textContent = Math.round(cfg.contrast * 100) + '%';
    $('satVal').textContent = Math.round(cfg.saturation * 100) + '%';
    $('aoVal').textContent = Math.round(cfg.ao * 100) + '%';
  }

  function buildBlockList() {
    applyConfig();
    const list = $('blockList');
    list.innerHTML = '';
    BLOCK_CHIPS.forEach((id) => {
      const bundle = PixelCraft.getTexture(id, seed, 64, relief);
      const c = document.createElement('canvas');
      c.width = 16; c.height = 16;
      if (bundle.color) c.getContext('2d').drawImage(canvasOf(bundle.color), 0, 0, 16, 16);
      const chip = document.createElement('div');
      chip.className = 'chip on';
      chip.dataset.id = id;
      chip.innerHTML = `<span class="swatch"></span>${PixelCraft.TEXTURE_REGISTRY[id].title}`;
      chip.querySelector('.swatch').replaceWith(c);
      chip.addEventListener('click', () => selectForPreview(generatedPack, id));
      list.appendChild(chip);
    });
  }

  let regenTimer = null;
  let cubeTimer = null;
  function debounceRegen() {
    clearTimeout(regenTimer);
    regenTimer = setTimeout(() => rebuildAtlas(), 140);
  }
  function debounceCube() {
    clearTimeout(cubeTimer);
    cubeTimer = setTimeout(() => refreshCube(), 60);
  }

  // Cap the *live preview* generation size — the atlas tiles are drawn at 96px and
  // the cube is rendered from a separately generated face, so building all ~200
  // textures at 1024/2048 for the grid would freeze the page. The FULL selected
  // resolution is still used for the 3D cube and at export.
  const PREVIEW_CAP = 96;

  // ------- rebuild the light atlas (seed / resolution changes) -------
  let regenToken = 0;
  async function rebuildAtlas() {
    applyConfig();
    const token = ++regenToken;
    const previewSize = Math.min(texSize, PREVIEW_CAP);
    const pack = await Pack.buildPreviewPack(seed, previewSize, realism);
    if (token !== regenToken) return; // a newer rebuild started, discard this one
    generatedPack = pack;
    renderGrid(pack);
    refreshCube();
    renderShaderSettings();
    $('seedDisplay').textContent = seed;
    $('realismVal').textContent = Math.round(realism * 100) + '%';
    $('reliefVal').textContent = Math.round(relief * 100) + '%';
    $('resVal').textContent = texSize + '×' + texSize;
    $('overlayRes').textContent = texSize + '×' + texSize;
  }

  // ------- regenerate ONLY the crisp 3D cube + shaders (slider changes) -------
  function refreshCube() {
    applyConfig();
    selectForPreview(generatedPack, selectedBlock.id);
    renderShaderSettings();
    $('realismVal').textContent = Math.round(realism * 100) + '%';
    $('reliefVal').textContent = Math.round(relief * 100) + '%';
  }

  function onChangeQuality() {
    applyConfig();
    debounceCube();
  }

  // ------- filters at the top of the atlas -------
  const FILTERS = ['All', 'Nature', 'Stone', 'Wood', 'Color', 'Metal', 'Nether', 'End', 'Ore', 'Misc'];
  const FILTER_HINTS = {
    Nature: ['grass', 'dirt', 'sand', 'gravel', 'clay', 'snow', 'ice', 'water', 'mud', 'moss', 'coarse', 'mycelium', 'soil', 'leaves'],
    Stone: ['stone', 'tuff', 'deepslate', 'granite', 'diorite', 'andesite', 'calcite', 'brick', 'bricks', 'basalt', 'blackstone', 'purpur'],
    Wood: ['planks', 'log', 'leaves', 'cherry', 'mangrove', 'crimson', 'warped', 'book'],
    Color: ['wool', 'concrete', 'terracotta', 'glazed'],
    Metal: ['gold_block', 'iron_block', 'diamond_block', 'emerald_block', 'redstone_block', 'lapis_block', 'copper_block', 'netherite_block'],
    Nether: ['nether', 'soul', 'nylium', 'wart', 'shroom', 'crimson', 'warped', 'magma', 'glowstone', 'ancient', 'debris'],
    End: ['end', 'purpur'],
    Ore: ['ore'],
    Misc: ['book', 'hay', 'bone', 'honey', 'kelp', 'slime', 'sponge', 'lantern', 'prismarine'],
  };

  function buildFilters() {
    const el = $('filters');
    el.innerHTML = '';
    FILTERS.forEach((f) => {
      const b = document.createElement('button');
      b.textContent = f;
      b.dataset.f = f;
      if (f === currentFilter) b.classList.add('on');
      b.addEventListener('click', () => {
        currentFilter = f;
        el.querySelectorAll('button').forEach((x) => x.classList.toggle('on', x === b));
        renderGrid(generatedPack);
      });
      el.appendChild(b);
    });
  }

  function matchesFilter(id, block) {
    if (currentFilter === 'All') return true;
    const hints = FILTER_HINTS[currentFilter] || [];
    const text = id + ' ' + block;
    return hints.some((h) => text.includes(h));
  }

  function renderGrid(pack) {
    const grid = $('grid');
    grid.innerHTML = '';
    $('texCount').textContent = pack.preview.length + ' faces';
    pack.preview.forEach((p) => {
      if (!matchesFilter(p.id, p.block)) return;
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

  // Map a block name -> the three texture-id faces it owns.
  function faceIdsForBlock(blockName) {
    const ids = Object.keys(PixelCraft.TEXTURE_REGISTRY).filter((k) => PixelCraft.TEXTURE_REGISTRY[k].block === blockName);
    const single = ids.length === 1;
    const top = ids.find((i) => i.endsWith('_top')) || ids[0];
    const side = ids.find((i) => i.endsWith('_side')) || (single ? ids[0] : ids.find((i) => i.endsWith('_log')) || ids[0]);
    const bottom = ids.find((i) => i.endsWith('_bottom')) || top;
    return { top, side, bottom };
  }

  function selectForPreview(pack, id) {
    if (!pack) return;
    selectedBlock.id = id;
    const blockName = PixelCraft.TEXTURE_REGISTRY[id].block;

    // Render the 3D cube from a crisp generation of the SELECTED block only. This
    // is capped at 512 so slider changes stay instant; the atlas grid and the pack
    // export still honor the full selected resolution.
    const face = faceIdsForBlock(blockName);
    const full = Math.min(texSize, 512);
    const topB = PixelCraft.getTexture(face.top, seed, full, relief);
    const sideB = PixelCraft.getTexture(face.side, seed, full, relief);
    const bottomB = PixelCraft.getTexture(face.bottom, seed, full, relief);
    const mk = (b) => b ? canvasOf(b.shaded) : null;

    thumb('faceTop', mk(topB));
    thumb('faceSide', mk(sideB));
    thumb('faceBottom', mk(bottomB));

    currentFaces = {
      top: mk(topB),
      side: mk(sideB),
      bottom: mk(bottomB),
    };
    $('previewTitle').textContent = blockName.charAt(0).toUpperCase() + blockName.replace(/_/g, ' ');

    document.querySelectorAll('.tile').forEach((t) => t.classList.toggle('sel', t.dataset.id === id));
  }

  function thumb(id, canvas) {
    const c = $(id);
    const ctx = c.getContext('2d');
    ctx.clearRect(0, 0, c.width, c.height);
    if (!canvas) return;
    const sz = Math.min(c.width, c.height);
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
      ['Bloom', s.bloom.toFixed(2)],
      ['Tone mapping', s.toneMapping],
    ];
    $('shaderSettings').innerHTML = map.map(([k, v]) =>
      `<div class="setting"><div class="k">${k}</div><div class="val">${v}</div></div>`).join('');
  }

  // ------- 3D block animation -------
  function animate() {
    if (autoSpin && !state.dragging) state.yaw += 0.006;
    requestAnimationFrame(animate);
  }

  function drawBlock() {
    const canvas = $('blockCanvas');
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    if (!w || !h) return;
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
        zoom: state.zoom,
        mode: state.mode,
        grid: showGrid,
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
      debounceRegen();
    });
    $('seed').addEventListener('input', () => {
      seed = parseInt($('seed').value, 10) || 0;
      debounceRegen();
    });
    $('packName').addEventListener('input', debounceRegen);

    $('realism').addEventListener('input', () => {
      realism = parseInt($('realism').value, 10) / 100;
      onChangeQuality();
    });
    $('relief').addEventListener('input', () => {
      relief = parseInt($('relief').value, 10) / 100;
      onChangeQuality();
    });
    ['detail', 'contrast', 'sat', 'ao'].forEach((id) => {
      $(id).addEventListener('input', () => {
        applyConfig();
        onChangeQuality();
      });
    });

    $('toggleAuto').addEventListener('click', () => {
      autoSpin = !autoSpin;
      $('toggleAuto').classList.toggle('active', autoSpin);
    });
    $('toggleGrid').addEventListener('click', () => {
      showGrid = !showGrid;
      $('toggleGrid').classList.toggle('active', showGrid);
    });
    $('zoomIn').addEventListener('click', () => { state.zoom = Math.min(3, state.zoom * 1.15); });
    $('zoomOut').addEventListener('click', () => { state.zoom = Math.max(1, state.zoom / 1.15); });
    $('resetView').addEventListener('click', () => { state.zoom = 1; state.pitch = 0.55; state.yaw = Math.PI / 6; });

    // advanced settings collapsible
    $('advToggle').addEventListener('click', () => {
      $('advanced').classList.toggle('hidden');
      $('advToggle').classList.toggle('open');
    });

    document.querySelectorAll('#lightToggle button').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.mode = btn.dataset.mode;
        document.querySelectorAll('#lightToggle button').forEach((x) => x.classList.toggle('on', x === btn));
      });
    });

    // block search
    $('blockSearch').addEventListener('input', () => {
      const q = $('blockSearch').value.trim().toLowerCase();
      document.querySelectorAll('#blockList .chip').forEach((chip) => {
        chip.style.display = !q || chip.textContent.toLowerCase().includes(q) ? '' : 'none';
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
      state.zoom = Math.max(1, Math.min(3.0, state.zoom));
    }, { passive: false });
  }

  // ------- export -------
  async function exportPack(kind) {
    const name = ($('packName').value || 'pixelcraft').replace(/[^\w\- ]+/g, '').trim() || 'pixelcraft';

    // Texture export always generates at the FULL selected resolution, asynchronously,
    // so even 2048×2048 per-block won't freeze the page. Show progress while it runs.
    toast(`Rendering at ${texSize}×${texSize} …`);
    $('btnExportPack').disabled = true;
    const pack = await Pack.buildTexturePackAsync(seed, texSize, realism, $('packName').value, (done, total) => {
      $('btnExportPack').textContent = `Rendering ${done}/${total} …`;
    });

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

    const converted = await Promise.all(files.map(async (f) => {
      let data = f.data;
      if (data && data.getContext) data = await Zip.canvasToBytes(data);
      return { path: f.path, data };
    }));

    const blob = Zip.buildZip(converted);
    Zip.download(blob, filename);
    $('btnExportPack').disabled = false;
    $('btnExportPack').textContent = '⬇ Export Texture Pack (.mcpack)';
    toast(`Exported ${filename} (${texSize}×${texSize})`);
  }

  function toast(msg) {
    const t = document.createElement('div');
    t.className = 'toast';
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(() => t.classList.add('show'), 10);
    setTimeout(() => { t.classList.remove('show'); setTimeout(() => t.remove(), 300); }, 2600);
  }

  // ------- boot -------
  $('btnExportPack').addEventListener('click', () => exportPack('texture'));
  $('btnExportShader').addEventListener('click', () => exportPack('shader'));
  $('btnExportBoth').addEventListener('click', () => exportPack('both'));

  buildResSeg();
  buildFilters();
  buildBlockList();
  setupInput();
  rebuildAtlas();
  animate();
  loop();
  registerToastStyles();

  $('seedDisplay').textContent = seed;
  const blockSet = new Set(Object.values(PixelCraft.TEXTURE_REGISTRY).map((r) => r.block));
  $('blockCount').textContent = blockSet.size;

  function registerToastStyles() {
    if (document.querySelector('.toast-style')) return;
    const st = document.createElement('style');
    st.className = 'toast-style';
    st.textContent = `.toast{position:fixed;bottom:22px;left:50%;transform:translateX(-50%) translateY(20px);background:#121a30;border:1px solid var(--accent);color:#e8eefc;padding:11px 20px;border-radius:999px;box-shadow:var(--shadow);opacity:0;transition:.25s;z-index:999;font-size:14px;}.toast.show{opacity:1;transform:translateX(-50%) translateY(0);}.hidden{display:none!important;}`;
    document.head.appendChild(st);
  }
})();
