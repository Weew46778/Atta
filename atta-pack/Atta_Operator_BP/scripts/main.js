// ============================================================
// کنترل اپراتور Atta — پک رفتاری بدراک (تک‌نفره/سرور)
// نسخهٔ ۱٫۱ — بدراک‌محور
// قطب‌نمای داده‌شده را «استفاده» کن → منوی اپراتور باز می‌شود.
// هیچ نیازی به تایپ دستور در چت نیست.
// ============================================================
const { world, system, ItemStack } = require("@minecraft/server");
const { ActionFormData, ModalFormData } = require("@minecraft/server-ui");

const TRIGGER_ITEM = "minecraft:compass";
const MENU_NAME = "🧰 منوی اپراتور Atta";
const COMPASS_NAME = "🧰 منوی اپراتور Atta";

// ------------------------------------------------------------------
// ابزار
// ------------------------------------------------------------------
function giveCompass(p) {
  const inv = p.getComponent("inventory").container;
  if (!inv) return false;
  for (let i = 0; i < inv.size; i++) {
    const it = inv.getItem(i);
    if (it && it.typeId === TRIGGER_ITEM) return true; // دارد
  }
  try {
    const c = new ItemStack(TRIGGER_ITEM, 1);
    c.nameTag = COMPASS_NAME;
    inv.addItem(c);
    return true;
  } catch (e) {
    try { inv.addItem(new ItemStack(TRIGGER_ITEM, 1)); } catch (e2) { return false; }
    return true;
  }
}

function ensureCompass(p) {
  if (!p) return;
  try {
    if (!giveCompass(p)) return;
    if (p.hasTag("atta_welcomed")) return;
    p.addTag("atta_welcomed");
    p.onScreenDisplay.setActionBar("🧰 قطب‌نمای Atta داده شد — آن را «استفاده» کن تا منو باز شود");
  } catch (e) {}
}

// هنگام ورود/تولد
try {
  world.afterEvents.playerSpawn.subscribe((ev) => {
    const p = ev && ev.player;
    if (!p) return;
    try { system.runTimeout(() => ensureCompass(p), 30); } catch (e) {}
  });
} catch (e) {}

// نگهبانی: اگر قطبنما گم شد هر ۱۰ ثانیه دوباره بده
try {
  system.runInterval(() => {
    try {
      const ps = world.getAllPlayers();
      for (const p of ps) ensureCompass(p);
    } catch (e) {}
  }, 200);
} catch (e) {}

// باز شدن منو با «استفاده» از قطب‌نما
try {
  world.afterEvents.itemUse.subscribe((ev) => {
    const p = ev.source;
    const it = ev.itemStack;
    if (!p || !it) return;
    if (it.typeId === TRIGGER_ITEM) {
      try { mainMenu(p); } catch (e) {}
    }
  });
} catch (e) {}

// ------------------------------------------------------------------
// ابزار کمکی
// ------------------------------------------------------------------
function ok(p, label) {
  try { p.onScreenDisplay.setActionBar("✓ " + label); } catch (e) {}
}

function fail(p, label) {
  try { p.onScreenDisplay.setActionBar("✗ " + label + " (به اپراتور/دستور نیاز است)"); } catch (e) {}
}

function run(p, cmd, label) {
  try {
    p.runCommand(cmd);
    if (label) ok(p, label);
    return true;
  } catch (e) {
    if (label) fail(p, label);
    return false;
  }
}

function seq(p, cmds, label) {
  let okCount = 0;
  for (const c of cmds) {
    try { p.runCommand(c); okCount++; } catch (e) {}
  }
  if (okCount > 0) ok(p, label);
  else fail(p, label);
}

function open(form, p, onSel) {
  form.show(p).then((resp) => {
    if (!resp) return;
    let sel = null;
    if (typeof resp.selection === "number") sel = resp.selection;
    if (typeof resp.selection === "object" && resp.selection) sel = resp.selection;
    if (sel === null && resp.formValues) sel = resp.formValues;
    if (sel !== null && sel !== undefined) onSel(sel, resp);
  }).catch(() => {});
}

function back(p, fn) {
  try { fn(p); } catch (e) {}
}

// ------------------------------------------------------------------
// گیمرول‌ها — وضعیت را خودمان نگه می‌داریم (تک‌نفره)
// ------------------------------------------------------------------
const RULES = [
  { name: "keepInventory",     label: "📦 حفظ آیتم پس از مرگ", def: false },
  { name: "mobGriefing",       label: "🌿 آسیب موجودات به دنیا", def: true },
  { name: "doDaylightCycle",   label: "🕐 چرخهٔ روز و شب", def: true },
  { name: "doFireTick",        label: "🔥 گسترش آتش", def: true },
  { name: "doWeatherCycle",    label: "🌧 چرخهٔ آب‌وهوا", def: true },
  { name: "showCoordinates",   label: "🧭 نمایش مختصات", def: false },
];

function ruleKey(r) { return "atta_rule_" + r.name; }

function toggleRule(p, r) {
  const cur = world.getDynamicProperty(ruleKey(r));
  let val = r.def;
  if (typeof cur === "boolean") val = !cur;
  try { world.setDynamicProperty(ruleKey(r), val); } catch (e) {}
  run(p, "/gamerule " + r.name + " " + (val ? "true" : "false"), r.label + ": " + (val ? "روشن" : "خاموش"));
}

// ------------------------------------------------------------------
// منوی اصلی
// ------------------------------------------------------------------
function mainMenu(p) {
  const f = new ActionFormData();
  f.title(MENU_NAME);
  f.body("یک کلیک = اجرای مستقیم در دنیا (بدون تایپ در چت)");
  f.button("🎮 حالت بازی");
  f.button("🕐 زمان روز");
  f.button("🌦 آب‌وهوا");
  f.button("🌍 سختی دنیا");
  f.button("💚 سلامتی و درمان");
  f.button("✨ افکت‌ها");
  f.button("🎁 آیتم‌ها و کیت‌ها");
  f.button("🧭 گیمرول‌ها");
  f.button("⚡ اکشن‌های سریع");
  f.button("🗑 پاک‌سازی افکت‌ها");
  f.button("❌ بستن");
  open(f, p, (sel) => {
    switch (sel) {
      case 0: modeMenu(p); break;
      case 1: timeMenu(p); break;
      case 2: weatherMenu(p); break;
      case 3: diffMenu(p); break;
      case 4: healthMenu(p); break;
      case 5: effectMenu(p); break;
      case 6: itemMenu(p); break;
      case 7: gameruleMenu(p); break;
      case 8: quickMenu(p); break;
      case 9: run(p, "/effect @s clear", "افکت‌هایت پاک شد"); break;
    }
  });
}

// ------------------------------------------------------------------
// حالت بازی
// ------------------------------------------------------------------
function modeMenu(p) {
  const f = new ActionFormData();
  f.title("🎮 حالت بازی");
  f.button("🟢 خلاق");
  f.button("🔵 بقا");
  f.button("🟠 ماجراجویی");
  f.button("⚪ ناظر");
  f.button("↩️ بازگشت");
  open(f, p, (sel) => {
    const modes = ["creative", "survival", "adventure", "spectator"];
    if (sel < 4) run(p, "/gamemode " + modes[sel] + " @s", "حالت بازی: " + modes[sel]);
    else back(p, mainMenu);
  });
}

// ------------------------------------------------------------------
// زمان
// ------------------------------------------------------------------
function timeMenu(p) {
  const f = new ActionFormData();
  f.title("🕐 زمان روز");
  f.button("🌅 سپیده‌دم");
  f.button("☀️ ظهر");
  f.button("🌇 غروب");
  f.button("🌙 نیمه‌شب");
  f.button("⏸ توقف/ادامهٔ چرخه");
  f.button("↩️ بازگشت");
  open(f, p, (sel) => {
    const times = [0, 6000, 13000, 18000];
    if (sel < 4) run(p, "/time set " + times[sel], "زمان تنظیم شد");
    else if (sel === 4) toggleRule(p, RULES[2]);
    else back(p, mainMenu);
  });
}

// ------------------------------------------------------------------
// آب‌وهوا
// ------------------------------------------------------------------
function weatherMenu(p) {
  const f = new ActionFormData();
  f.title("🌦 آب‌وهوا");
  f.button("☀️ صاف");
  f.button("🌧 باران");
  f.button("⛈ رعدوبرق");
  f.button("↩️ بازگشت");
  open(f, p, (sel) => {
    const w = ["clear", "rain", "thunder"];
    if (sel < 3) run(p, "/weather " + w[sel], "آب‌وهوا: " + w[sel]);
    else back(p, mainMenu);
  });
}

// ------------------------------------------------------------------
// سختی
// ------------------------------------------------------------------
function diffMenu(p) {
  const f = new ActionFormData();
  f.title("🌍 سختی دنیا");
  f.button("🕊 صلح‌آمیز");
  f.button("🧒 آسان");
  f.button("⚔️ عادی");
  f.button("🔥 سخت");
  f.button("↩️ بازگشت");
  open(f, p, (sel) => {
    const d = ["peaceful", "easy", "normal", "hard"];
    if (sel < 4) run(p, "/difficulty " + d[sel], "سختی: " + d[sel]);
    else back(p, mainMenu);
  });
}

// ------------------------------------------------------------------
// سلامتی
// ------------------------------------------------------------------
function healthMenu(p) {
  const f = new ActionFormData();
  f.title("💚 سلامتی و درمان");
  f.button("⚡ شفای کامل");
  f.button("🍖 سیری کامل");
  f.button("🛡 مقاومت ۲ دقیقه");
  f.button("💨 سرعت ۲ دقیقه");
  f.button("👁 شب‌بینی ۱۰ دقیقه");
  f.button("↩️ بازگشت");
  open(f, p, (sel) => {
    if (sel === 0) run(p, "/effect @s instant_health 1 255", "شفای کامل");
    else if (sel === 1) run(p, "/effect @s saturation 60 10", "سیری کامل");
    else if (sel === 2) run(p, "/effect @s resistance 120 4", "مقاومت ۲ دقیقه");
    else if (sel === 3) run(p, "/effect @s speed 120 2", "سرعت ۲ دقیقه");
    else if (sel === 4) run(p, "/effect @s night_vision 600 0", "شب‌بینی ۱۰ دقیقه");
    else back(p, mainMenu);
  });
}

// ------------------------------------------------------------------
// افکت‌ها
// ------------------------------------------------------------------
function effectMenu(p) {
  const f = new ActionFormData();
  f.title("✨ افکت‌ها (روی خودت — ۲ دقیقه)");
  f.button("💪 قدرت");
  f.button("⚡ سرعت");
  f.button("🛡 مقاومت");
  f.button("💚 بازسازی");
  f.button("🦘 پرش");
  f.button("⛏ شتاب حفاری");
  f.button("🔥 نسوز");
  f.button("🌊 تنفس زیر آب");
  f.button("👻 نامرئی");
  f.button("↩️ بازگشت");
  open(f, p, (sel) => {
    const fx = [
      ["strength", "قدرت"],
      ["speed", "سرعت"],
      ["resistance", "مقاومت"],
      ["regeneration", "بازسازی"],
      ["jump_boost", "پرش"],
      ["haste", "شتاب حفاری"],
      ["fire_resistance", "نسوز"],
      ["water_breathing", "تنفس زیر آب"],
      ["invisibility", "نامرئی"],
    ];
    if (sel < 9) run(p, "/effect @s " + fx[sel][0] + " 120 1", fx[sel][1] + " فعال شد");
    else back(p, mainMenu);
  });
}

// ------------------------------------------------------------------
// گیمرول‌ها
// ------------------------------------------------------------------
function gameruleMenu(p) {
  const f = new ActionFormData();
  f.title("🧭 گیمرول‌ها");
  for (const r of RULES) f.button(r.label);
  f.button("↩️ بازگشت");
  open(f, p, (sel) => {
    if (sel >= 0 && sel < RULES.length) {
      toggleRule(p, RULES[sel]);
      system.runTimeout(() => gameruleMenu(p), 10);
    } else {
      back(p, mainMenu);
    }
  });
}

// ------------------------------------------------------------------
// اکشن‌های سریع
// ------------------------------------------------------------------
function quickMenu(p) {
  const f = new ActionFormData();
  f.title("⚡ اکشن‌های سریع");
  f.button("🌙 شب سخت (نیمه‌شب + رعدوبرق)");
  f.button("🌅 صبح آرام (سپیده + صاف)");
  f.button("💎 تجربه +۱۰۰");
  f.button("💚 شفا و سیری کامل");
  f.button("🧱 تخریب‌پذیری: همه‌چیز (هشدار!)");
  f.button("↩️ بازگشت");
  open(f, p, (sel) => {
    if (sel === 0) seq(p, ["/time set 13000", "/weather thunder"], "شب سخت");
    else if (sel === 1) seq(p, ["/time set 0", "/weather clear"], "صبح آرام");
    else if (sel === 2) run(p, "/xp 100 @s", "تجربه +۱۰۰");
    else if (sel === 3) seq(p, ["/effect @s instant_health 1 255", "/effect @s saturation 60 10"], "شفا و سیری");
    else if (sel === 4) {
      try { p.runCommand("/fill ~-3 ~-1 ~-3 ~3 ~0 ~3 air"); ok(p, "منطقهٔ اطراف تخریب شد"); }
      catch (e) { fail(p, "تخریب"); }
    } else back(p, mainMenu);
  });
}

// ------------------------------------------------------------------
// آیتم‌ها و کیت‌ها
// ------------------------------------------------------------------
function itemMenu(p) {
  const f = new ModalFormData();
  f.title("🎁 آیتم‌ها و کیت‌ها");
  const items = [
    "الماس", "ندرایت شمش", "زمرد", "شمش طلا", "آهن", "ابسیدین",
    "شمش ندرایت", "کلنگ ندرایت", "کمان", "سیب طلایی", "توتم", "ایلیترا",
    "مروارید اندر", "راکت آتش‌بازی", "تیر", "هویج طلایی",
    "زره کامل الماس", "زره کامل ندرایت", "کیت شروع", "کیت پی‌وی‌پی",
  ];
  const ids = [
    "diamond", "netherite_scrap", "emerald", "gold_ingot", "iron_ingot", "obsidian",
    "netherite_ingot", "netherite_pickaxe", "bow", "golden_apple", "totem_of_undying", "elytra",
    "ender_pearl", "firework_rocket", "arrow", "golden_carrot",
  ];
  f.dropdown("انتخاب", items);
  f.slider("تعداد", 1, 64, 1, 16);
  open(f, p, (sel, resp) => {
    if (!sel) return;
    const idx = sel[0];
    const qty = Math.max(1, Math.round(sel[1]));
    if (idx === 16) {
      seq(p, ["/give @s diamond_helmet 1", "/give @s diamond_chestplate 1",
              "/give @s diamond_leggings 1", "/give @s diamond_boots 1"], "زره کامل الماس");
    } else if (idx === 17) {
      seq(p, ["/give @s netherite_helmet 1", "/give @s netherite_chestplate 1",
              "/give @s netherite_leggings 1", "/give @s netherite_boots 1"], "زره کامل ندرایت");
    } else if (idx === 18) {
      seq(p, ["/give @s stone_pickaxe 1", "/give @s stone_axe 1",
              "/give @s bread 16", "/give @s torch 32"], "کیت شروع");
    } else if (idx === 19) {
      seq(p, ["/give @s netherite_sword 1", "/give @s golden_apple 16",
              "/give @s cooked_beef 32"], "کیت پی‌وی‌پی");
    } else {
      run(p, "/give @s " + ids[idx] + " " + qty, items[idx] + " ×" + qty);
    }
  });
}

// ------------------------------------------------------------------
// پیام هنگام بارگذاری
// ------------------------------------------------------------------
try {
  if (world.afterEvents.worldLoad) {
    world.afterEvents.worldLoad.subscribe(() => {
      try {
        system.run(() => {
          for (const p of world.getAllPlayers()) {
            p.onScreenDisplay.setActionBar("🧰 پک Atta فعال است — قطب‌نما را استفاده کن");
          }
        });
      } catch (e) {}
    });
  }
} catch (e) {}
