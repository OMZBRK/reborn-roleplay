/* =============================================================================
 * Reborn — Compositeur de perso  (plugin Blockbench, desktop)
 * -----------------------------------------------------------------------------
 * Deux onglets dans un panneau latéral :
 *   • PERSO       — scanne ta bibliothèque modulaire (peau / sous-vêtement /
 *                   tenue / yeux / cheveux), tu choisis une pièce par catégorie,
 *                   elles s'empilent en 64×64 → aperçu Face/Dos + application
 *                   live sur le modèle 3D de Blockbench + export du skin.
 *   • RÉFÉRENCES  — épingle une image de réf dans la vue 3D (ReferenceImage
 *                   natif de Blockbench) pour peindre à la main à côté du modèle.
 *
 * V1 = composer / prévisualiser / exporter la bibliothèque existante.
 * Aucune donnée n'est modifiée sur disque ; on lit seulement les PNG.
 *
 * Installation : Blockbench → Fichier → Plugins → « Charger un plugin depuis un
 * fichier » → sélectionner ce .js.  (Bureau uniquement : accès disque requis.)
 * ============================================================================= */
(function () {
  "use strict";

  const fs = require("fs");
  const pathmod = require("path");

  // ---- Dossier bibliothèque par défaut (modifiable dans le panneau) ----------
  const DEFAULT_LIB = "D:/REBORN - PJ/Modélisation/Skin";

  // ---- Catégories : ordre d'empilement bas→haut ------------------------------
  const CATS = [
    { key: "peau",         label: "Peau",           names: ["peau", "skin", "peaux"],                          single: true,  order: 0 },
    { key: "sousvetement", label: "Sous-vêtement",  names: ["sousvetement", "sous-vetement", "underwear"],     single: true,  order: 1 },
    { key: "tenue",        label: "Tenue",          names: ["tenue", "tenues", "outfit", "clothes"],           single: false, order: 2 },
    { key: "yeux",         label: "Yeux",           names: ["yeux", "eyes"],                                   single: true,  order: 3 },
    { key: "cheveux",      label: "Cheveux",        names: ["hair", "cheveux", "coiffure"],                    single: true,  order: 4 },
  ];
  const REF_NAMES = ["ref", "refs", "reference", "references", "skin_naruto_reference"];

  // ---- UV du modèle joueur (dépliage boîte, 64×64, bras classiques 4px) -------
  function boxUV(u, v, W, H, D) {
    return {
      top: [u + D, v, W, D], bottom: [u + D + W, v, W, D],
      right: [u, v + D, D, H], front: [u + D, v + D, W, H],
      left: [u + D + W, v + D, D, H], back: [u + 2 * D + W, v + D, W, H],
    };
  }
  const IUV = {
    head: boxUV(0, 0, 8, 8, 8), body: boxUV(16, 16, 8, 12, 4),
    rArm: boxUV(40, 16, 4, 12, 4), lArm: boxUV(32, 48, 4, 12, 4),
    rLeg: boxUV(0, 16, 4, 12, 4), lLeg: boxUV(16, 48, 4, 12, 4),
  };
  const OUV = {
    head: boxUV(32, 0, 8, 8, 8), body: boxUV(16, 32, 8, 12, 4),
    rArm: boxUV(40, 32, 4, 12, 4), lArm: boxUV(48, 48, 4, 12, 4),
    rLeg: boxUV(0, 32, 4, 12, 4), lLeg: boxUV(0, 48, 4, 12, 4),
  };
  // Silhouette de face en texels (W=16, H=32)
  const SIL = [
    { k: "head", x: 4, y: 0, w: 8, h: 8 }, { k: "body", x: 4, y: 8, w: 8, h: 12 },
    { k: "rArm", x: 0, y: 8, w: 4, h: 12 }, { k: "lArm", x: 12, y: 8, w: 4, h: 12 },
    { k: "rLeg", x: 4, y: 20, w: 4, h: 12 }, { k: "lLeg", x: 8, y: 20, w: 4, h: 12 },
  ];
  function drawView(src, ctx, ox, oy, S, face) {
    for (const r of SIL) {
      const iu = IUV[r.k][face], ou = OUV[r.k][face];
      // en vue de dos on miroite horizontalement pour rester lisible
      const rx = face === "back" ? (16 - r.x - r.w) : r.x;
      ctx.drawImage(src, iu[0], iu[1], iu[2], iu[3], ox + rx * S, oy + r.y * S, r.w * S, r.h * S);
      ctx.drawImage(src, ou[0], ou[1], ou[2], ou[3], ox + rx * S, oy + r.y * S, r.w * S, r.h * S);
    }
  }

  // ---- Mannequin gris (fond de vignette pour lire la forme d'une pièce) -------
  const MANNEQUIN = (function () {
    const c = document.createElement("canvas"); c.width = 64; c.height = 64;
    const x = c.getContext("2d");
    const fillPart = (p, col) => { x.fillStyle = col; for (const k in p) x.fillRect(p[k][0], p[k][1], p[k][2], p[k][3]); };
    fillPart(IUV.head, "#6b6f7a"); fillPart(IUV.rArm, "#6b6f7a"); fillPart(IUV.lArm, "#6b6f7a");
    fillPart(IUV.body, "#565a63"); fillPart(IUV.rLeg, "#4c4f57"); fillPart(IUV.lLeg, "#4c4f57");
    return c;
  })();

  // ---- État -------------------------------------------------------------------
  const S = {
    libPath: localStorage.getItem("reborn_lib_path") || DEFAULT_LIB,
    cats: {},          // key -> [filepaths]
    refs: [],          // [filepaths]
    selection: {},     // key -> [selected filepaths]
    gender: "all",     // all | female | male  (filtre peau)
    filename: "perso_reborn",
    tab: "perso",
    addedRefs: [],     // ReferenceImage créées par le plugin
    refLimit: 60,
    refQuery: "",
  };
  CATS.forEach(c => (S.selection[c.key] = []));

  const imgCache = {};   // filepath -> HTMLImageElement (source 64×64)
  const thumbCache = {}; // filepath -> dataURL vignette

  // ---- Utilitaires disque -----------------------------------------------------
  function scanPngs(dir) {
    let out = [], entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return out; }
    for (const e of entries) {
      const full = pathmod.join(dir, e.name);
      if (e.isDirectory()) out = out.concat(scanPngs(full));
      else if (/\.png$/i.test(e.name)) out.push(full);
    }
    return out;
  }
  function findFolder(root, names) {
    let entries; try { entries = fs.readdirSync(root, { withFileTypes: true }); } catch (e) { return null; }
    const low = names.map(n => n.toLowerCase());
    for (const e of entries) if (e.isDirectory() && low.includes(e.name.toLowerCase())) return pathmod.join(root, e.name);
    return null;
  }
  function toDataURL(file) {
    try { return "data:image/png;base64," + fs.readFileSync(file).toString("base64"); }
    catch (e) { return null; }
  }
  function loadImg(file, cb) {
    if (imgCache[file]) { if (imgCache[file].complete) cb && cb(imgCache[file]); return imgCache[file]; }
    const url = toDataURL(file); if (!url) return null;
    const img = new Image();
    img.onload = () => cb && cb(img);
    img.src = url; imgCache[file] = img; return img;
  }

  // ---- Vignette d'une pièce (vue de face sur le mannequin) --------------------
  function thumbFor(file) {
    if (thumbCache[file]) return thumbCache[file];
    const img = imgCache[file];
    const t = document.createElement("canvas"); const sc = 3; t.width = 16 * sc; t.height = 32 * sc;
    const tc = t.getContext("2d"); tc.imageSmoothingEnabled = false;
    drawView(MANNEQUIN, tc, 0, 0, sc, "front");
    if (img && img.complete) drawView(img, tc, 0, 0, sc, "front");
    const url = t.toDataURL(); if (img && img.complete) thumbCache[file] = url;
    return url;
  }

  // ---- Compositing 64×64 ------------------------------------------------------
  function composite() {
    const cv = document.createElement("canvas"); cv.width = 64; cv.height = 64;
    const ctx = cv.getContext("2d"); ctx.imageSmoothingEnabled = false;
    for (const c of CATS.slice().sort((a, b) => a.order - b.order)) {
      for (const file of S.selection[c.key] || []) {
        const img = imgCache[file];
        if (img && img.complete) ctx.drawImage(img, 0, 0, 64, 64);
      }
    }
    return cv;
  }

  // ---- Scan de la bibliothèque -----------------------------------------------
  function rescan() {
    S.cats = {};
    for (const c of CATS) {
      const folder = findFolder(S.libPath, c.names);
      S.cats[c.key] = folder ? scanPngs(folder) : [];
    }
    S.refs = [];
    for (const n of REF_NAMES) { const f = findFolder(S.libPath, [n]); if (f) S.refs = S.refs.concat(scanPngs(f)); }
    // précharge les pièces de catégorie (peu nombreuses) pour les vignettes
    let total = 0;
    for (const c of CATS) for (const f of S.cats[c.key]) { total++; loadImg(f, () => scheduleRender()); }
    log(`Scan : ${total} pièces, ${S.refs.length} références — ${S.libPath}`);
    render();
  }

  // ---- Intégration Blockbench : appliquer au modèle 3D -----------------------
  function applyToModel() {
    const cv = composite(); const url = cv.toDataURL();
    try {
      let tex = (typeof Texture !== "undefined") ? (Texture.selected || (Texture.all && Texture.all[0])) : null;
      if (!tex) {
        tex = new Texture({ name: "Reborn Composite" }).fromDataURL(url).add();
        log("Nouvelle texture « Reborn Composite » créée et appliquée.");
      } else {
        if (typeof tex.edit === "function") {
          tex.edit((arg) => {
            const c = (arg && arg.getContext) ? arg.getContext("2d") : arg;
            c.clearRect(0, 0, 64, 64); c.imageSmoothingEnabled = false; c.drawImage(cv, 0, 0);
          }, { no_undo: false });
        } else if (typeof tex.updateSource === "function") {
          tex.updateSource(url);
        } else {
          tex.source = url; if (tex.img) tex.img.src = url;
        }
        log("Texture « " + tex.name + " » mise à jour.");
      }
      if (typeof Canvas !== "undefined" && Canvas.updateAll) Canvas.updateAll();
    } catch (e) { log("⚠ Erreur application 3D : " + e.message); }
  }

  // ---- Intégration Blockbench : références natives ----------------------------
  function addReference(file) {
    const url = toDataURL(file); if (!url) return;
    const img = new Image();
    img.onload = () => {
      try {
        if (typeof ReferenceImage === "undefined") { log("⚠ ReferenceImage indisponible dans cette version."); return; }
        const ri = new ReferenceImage({
          name: pathmod.basename(file),
          source: url,
          position: [Math.round(Preview.selected ? 260 : 260), 0],
          size: [img.width, img.height],
          layer: "viewport",
          visibility: true,
          opacity: 1,
        });
        ri.add(); S.addedRefs.push(ri);
        log("Référence épinglée : " + ri.name);
      } catch (e) { log("⚠ Erreur référence : " + e.message); }
    };
    img.src = url;
  }
  function clearReferences() {
    for (const ri of S.addedRefs) { try { ri.delete ? ri.delete() : (ri.remove && ri.remove()); } catch (e) {} }
    S.addedRefs = []; log("Références du plugin retirées.");
  }

  // ---- Export -----------------------------------------------------------------
  function exportPng() {
    const url = composite().toDataURL();
    const name = (S.filename || "perso_reborn");
    try {
      Blockbench.export({ type: "PNG", extensions: ["png"], name, content: url, savetype: "image" });
      log("Skin exporté : " + name + ".png");
    } catch (e) {
      const a = document.createElement("a"); a.href = url; a.download = name + ".png"; a.click();
      log("Skin téléchargé (fallback) : " + name + ".png");
    }
  }

  // =========================================================================
  //  UI
  // =========================================================================
  let root, previewFront, previewBack, logEl, renderTimer;
  function scheduleRender() { clearTimeout(renderTimer); renderTimer = setTimeout(render, 40); }
  function log(m) { console.log("[Reborn]", m); if (logEl) { logEl.textContent = ("• " + m + "\n" + logEl.textContent).slice(0, 4000); } }

  function el(tag, props, kids) {
    const n = document.createElement(tag);
    if (props) for (const k in props) {
      if (k === "style") n.style.cssText = props[k];
      else if (k === "class") n.className = props[k];
      else if (k.startsWith("on")) n.addEventListener(k.slice(2), props[k]);
      else n.setAttribute(k, props[k]);
    }
    (kids || []).forEach(c => n.appendChild(typeof c === "string" ? document.createTextNode(c) : c));
    return n;
  }

  function injectStyle() {
    if (document.getElementById("reborn-comp-style")) return;
    const css = `
    .rc-root{display:flex;flex-direction:column;height:100%;font-size:12px;color:var(--color-text)}
    .rc-tabs{display:flex;gap:2px;padding:6px 6px 0}
    .rc-tabs button{flex:1;background:var(--color-back);border:0;color:var(--color-text);
      padding:7px;border-radius:6px 6px 0 0;cursor:pointer;font-weight:600;opacity:.6}
    .rc-tabs button.on{background:var(--color-ui);opacity:1;color:var(--color-light)}
    .rc-body{flex:1;overflow:auto;padding:8px;background:var(--color-ui)}
    .rc-path{display:flex;gap:4px;margin-bottom:8px}
    .rc-path input{flex:1;background:var(--color-back);border:1px solid var(--color-border);
      color:var(--color-text);border-radius:5px;padding:5px 7px;font-family:monospace;font-size:11px}
    .rc-btn{background:var(--color-button);border:1px solid var(--color-border);color:var(--color-text);
      border-radius:5px;padding:6px 10px;cursor:pointer;font-weight:600}
    .rc-btn:hover{border-color:var(--color-accent)}
    .rc-btn.acc{background:var(--color-accent);color:var(--color-accent_text);border-color:transparent}
    .rc-cat{margin-bottom:10px}
    .rc-cat h4{margin:0 0 5px;font-size:11px;letter-spacing:.06em;text-transform:uppercase;
      color:var(--color-light);display:flex;justify-content:space-between;align-items:center}
    .rc-strip{display:flex;gap:5px;overflow-x:auto;padding-bottom:4px}
    .rc-item{flex:none;width:44px;border:2px solid transparent;border-radius:6px;cursor:pointer;
      background:var(--color-back);padding:2px;position:relative}
    .rc-item.on{border-color:var(--color-accent)}
    .rc-item img{width:100%;display:block;image-rendering:pixelated;border-radius:3px}
    .rc-item .cap{font-size:8px;line-height:1.1;text-align:center;color:var(--color-text);
      overflow:hidden;white-space:nowrap;text-overflow:ellipsis;margin-top:1px}
    .rc-none{flex:none;width:36px;display:grid;place-items:center;border:1px dashed var(--color-border);
      border-radius:6px;cursor:pointer;color:var(--color-text);font-size:10px}
    .rc-preview{display:flex;gap:10px;justify-content:center;background:var(--color-back);
      border-radius:8px;padding:10px;margin:6px 0}
    .rc-preview canvas{image-rendering:pixelated;border-radius:5px;
      background:repeating-conic-gradient(#0000 0 25%,#8884 0 50%) 0/12px 12px}
    .rc-preview figure{margin:0;text-align:center}
    .rc-preview figcaption{font-size:9px;letter-spacing:.08em;text-transform:uppercase;color:var(--color-text);opacity:.6;margin-top:3px}
    .rc-actions{display:flex;gap:6px;margin-top:6px}
    .rc-actions .rc-btn{flex:1}
    .rc-seg{display:inline-flex;background:var(--color-back);border-radius:5px;overflow:hidden}
    .rc-seg button{background:transparent;border:0;color:var(--color-text);padding:3px 8px;cursor:pointer;font-size:10px;opacity:.6}
    .rc-seg button.on{background:var(--color-accent);color:var(--color-accent_text);opacity:1}
    .rc-refgrid{display:grid;grid-template-columns:repeat(3,1fr);gap:5px}
    .rc-refgrid img{width:100%;image-rendering:auto;border-radius:5px;cursor:pointer;border:1px solid var(--color-border)}
    .rc-refgrid img:hover{border-color:var(--color-accent)}
    .rc-log{font-family:monospace;font-size:9.5px;white-space:pre-wrap;color:var(--color-text);opacity:.65;
      max-height:70px;overflow:auto;background:var(--color-back);border-radius:5px;padding:5px;margin-top:8px}
    .rc-hint{font-size:10.5px;opacity:.6;line-height:1.5;margin:2px 0 8px}
    `;
    document.head.appendChild(el("style", { id: "reborn-comp-style" }, [css]));
  }

  function buildUI(node) {
    injectStyle();
    root = node; root.className = "rc-root";
    render();
  }

  function render() {
    if (!root) return;
    root.innerHTML = "";
    // tabs
    const tabs = el("div", { class: "rc-tabs" }, [
      tabBtn("perso", "🧍 Perso"),
      tabBtn("refs", "🖼 Références"),
    ]);
    root.appendChild(tabs);
    const body = el("div", { class: "rc-body" });
    root.appendChild(body);

    // path row (shared)
    const pathInput = el("input", { type: "text", value: S.libPath, spellcheck: "false" });
    pathInput.addEventListener("change", () => { S.libPath = pathInput.value.trim(); localStorage.setItem("reborn_lib_path", S.libPath); });
    body.appendChild(el("div", { class: "rc-path" }, [
      pathInput,
      el("button", { class: "rc-btn", onclick: () => rescan() }, ["Scanner"]),
    ]));

    if (S.tab === "perso") renderPerso(body);
    else renderRefs(body);

    // log console
    logEl = el("div", { class: "rc-log" }, [""]);
    body.appendChild(el("div", { class: "rc-hint" }, ["Journal"]));
    body.appendChild(logEl);
  }

  function tabBtn(id, label) {
    const b = el("button", { class: S.tab === id ? "on" : "", onclick: () => { S.tab = id; render(); } }, [label]);
    return b;
  }

  function renderPerso(body) {
    const nCats = CATS.reduce((a, c) => a + ((S.cats[c.key] || []).length), 0);
    if (!nCats) {
      body.appendChild(el("div", { class: "rc-hint" }, [
        "Aucune pièce trouvée. Vérifie le chemin de la bibliothèque puis clique « Scanner ». " +
        "Sous-dossiers attendus : peau, sousvetement, tenue, yeux, hair.",
      ]));
      return;
    }

    for (const c of CATS) {
      let files = S.cats[c.key] || [];
      if (c.key === "peau" && S.gender !== "all")
        files = files.filter(f => f.toLowerCase().includes(S.gender));
      if (!files.length) continue;

      const head = el("h4", {}, [c.label + " ", el("span", { style: "opacity:.5;font-weight:400" }, [String(files.length)])]);
      if (c.key === "peau") {
        const seg = el("div", { class: "rc-seg" }, ["all", "female", "male"].map(g =>
          el("button", { class: S.gender === g ? "on" : "", onclick: () => { S.gender = g; render(); } },
            [g === "all" ? "Tous" : g === "female" ? "Femme" : "Homme"])));
        head.appendChild(seg);
      }
      const strip = el("div", { class: "rc-strip" });
      // "Aucun" pour vider la catégorie
      strip.appendChild(el("div", { class: "rc-none", title: "Aucun", onclick: () => { S.selection[c.key] = []; refreshPreview(); render(); } }, ["∅"]));

      for (const f of files) {
        const on = (S.selection[c.key] || []).includes(f);
        const item = el("div", { class: "rc-item" + (on ? " on" : ""), title: pathmod.basename(f) });
        const im = el("img", { alt: "" });
        loadImg(f, () => { im.src = thumbFor(f); });
        if (imgCache[f] && imgCache[f].complete) im.src = thumbFor(f);
        item.appendChild(im);
        item.appendChild(el("div", { class: "cap" }, [pathmod.basename(f).replace(/\.png$/i, "")]));
        item.addEventListener("click", () => {
          const sel = S.selection[c.key] || [];
          if (c.single) S.selection[c.key] = on ? [] : [f];
          else S.selection[c.key] = on ? sel.filter(x => x !== f) : sel.concat([f]);
          refreshPreview(); render();
        });
        strip.appendChild(item);
      }
      const cat = el("div", { class: "rc-cat" }, [head, strip]);
      body.appendChild(cat);
    }

    // preview
    previewFront = el("canvas", { width: 96, height: 192 });
    previewBack = el("canvas", { width: 96, height: 192 });
    body.appendChild(el("div", { class: "rc-preview" }, [
      el("figure", {}, [previewFront, el("figcaption", {}, ["Face"])]),
      el("figure", {}, [previewBack, el("figcaption", {}, ["Dos"])]),
    ]));
    refreshPreview();

    // filename + actions
    const nameInput = el("input", { type: "text", value: S.filename, style: "width:100%;background:var(--color-back);border:1px solid var(--color-border);color:var(--color-text);border-radius:5px;padding:5px 7px" });
    nameInput.addEventListener("change", () => S.filename = nameInput.value.trim() || "perso_reborn");
    body.appendChild(nameInput);
    body.appendChild(el("div", { class: "rc-actions" }, [
      el("button", { class: "rc-btn acc", onclick: () => applyToModel() }, ["Appliquer au 3D"]),
      el("button", { class: "rc-btn", onclick: () => exportPng() }, ["Exporter PNG"]),
    ]));
    body.appendChild(el("div", { class: "rc-hint" }, ["« Appliquer au 3D » met à jour la texture sélectionnée du modèle Blockbench en direct."]));
  }

  function refreshPreview() {
    if (!previewFront) return;
    const cv = composite();
    for (const [canvas, face] of [[previewFront, "front"], [previewBack, "back"]]) {
      const ctx = canvas.getContext("2d"); ctx.imageSmoothingEnabled = false;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      drawView(cv, ctx, 0, 0, canvas.width / 16, face);
    }
  }

  function renderRefs(body) {
    body.appendChild(el("div", { class: "rc-hint" }, [
      "Clique une référence pour l'épingler dans la vue 3D (déplaçable/redimensionnable). ",
    ]));
    const q = el("input", { type: "text", placeholder: "Filtrer…", value: S.refQuery, style: "width:100%;margin-bottom:6px;background:var(--color-back);border:1px solid var(--color-border);color:var(--color-text);border-radius:5px;padding:5px 7px" });
    q.addEventListener("input", () => { S.refQuery = q.value; S.refLimit = 60; render(); });
    body.appendChild(q);
    body.appendChild(el("div", { class: "rc-actions", style: "margin:0 0 8px" }, [
      el("button", { class: "rc-btn", onclick: () => clearReferences() }, ["Retirer les références épinglées"]),
    ]));

    const filtered = S.refs.filter(f => pathmod.basename(f).toLowerCase().includes(S.refQuery.toLowerCase()));
    if (!filtered.length) { body.appendChild(el("div", { class: "rc-hint" }, ["Aucune référence. Dossiers attendus : ref/, skin_naruto_reference/."])); return; }

    const grid = el("div", { class: "rc-refgrid" });
    filtered.slice(0, S.refLimit).forEach(f => {
      const im = el("img", { alt: "", loading: "lazy", title: pathmod.basename(f) });
      im.src = toDataURL(f);
      im.addEventListener("click", () => addReference(f));
      grid.appendChild(im);
    });
    body.appendChild(grid);
    if (filtered.length > S.refLimit) {
      body.appendChild(el("div", { class: "rc-actions" }, [
        el("button", { class: "rc-btn", onclick: () => { S.refLimit += 60; render(); } },
          [`Charger plus (${S.refLimit}/${filtered.length})`]),
      ]));
    }
  }

  // =========================================================================
  //  Plugin registration
  // =========================================================================
  let panel;
  Plugin.register("reborn_compositor", {
    title: "Reborn — Compositeur de perso",
    author: "Reborn RP",
    icon: "checkroom",
    description: "Compose des skins à partir d'une bibliothèque modulaire (peau/tenue/cheveux/…), aperçu 3D live + export, et épingle des références.",
    version: "1.0.0",
    variant: "desktop",
    tags: ["Skin", "Minecraft", "Texture"],
    onload() {
      panel = new Panel("reborn_compositor", {
        name: "Reborn Compositor",
        id: "reborn_compositor",
        icon: "checkroom",
        condition: () => true,
        default_position: { slot: "right_bar", float_position: [0, 0], float_size: [340, 720], height: 720 },
        component: {
          name: "reborn-compositor",
          template: '<div class="rc-mount"></div>',
          mounted() { buildUI(this.$el); setTimeout(rescan, 60); },
        },
        expand_button: true,
      });
      log && log("Plugin chargé.");
    },
    onunload() {
      try { clearReferences(); } catch (e) {}
      if (panel) panel.delete();
      const st = document.getElementById("reborn-comp-style"); if (st) st.remove();
    },
  });
})();
