// ============================================================
// CONFIGURATION — All tunable settings in one place
// ============================================================
// Many settings support URL overrides: ?key=value
// Comments note which functions consume each setting.
//
// This file is loaded FIRST — all other .js files depend on it.

const params = new URLSearchParams(location.search);

// --- File Paths ---
const MODEL_MODE = (params.get('model') || 'glb').toLowerCase();
const GLB_FILE = params.get('glb') || 'models/idle_player.glb';
const BBMODEL_FILE = params.get('bbmodel') || 'models/idle_player.bbmodel';
const DEV_PLAYER = params.get('player') || params.get('skin');
const SKIN_URL = params.get('skinUrl');
const URL_UUID = params.get('uuid');                  // Resolved by FancyMenu before page load

// --- Display Options ---
const STATIC_POSE = params.get('static') === '1' || params.get('static') === 'true';
const DEBUG = params.get('debug') === '1' || params.get('debug') === 'true';
const DEBUG_LIGHTING = false;   // Set true for draggable gizmos + Save button; consumes loadHDRI()

// --- Texture Coordinate Convention ---
// glTF/GLB = flipY=false, BBModel-built meshes = flipY=true
// URL override: ?flipY=1 forces true, ?flipY=0 forces false
const flipYParam = params.get('flipY');
let skinTextureFlipY = flipYParam === '1' || flipYParam === 'true'
    ? true
    : flipYParam === '0' || flipYParam === 'false'
        ? false
        : MODEL_MODE === 'bbmodel';

// --- Fade-In Timing (seconds) ---
const FADE_DURATION = 3.0;      // 0→1 opacity transition; consumes startFadeIn()
const MIN_LOAD_DELAY = 1.2;     // Minimum wait before fade begins; consumes tryShowAndFade()

// --- Pixelation (retro/Minecraft-style low-res render) ---
// Scene renders at reduced resolution then scales up for pixelated look.
// Consumes initPixelation(), resizePixelTarget()
const PIXELATION_ENABLED = true;
const PIXELATION_SCALE = 1;     // 1 = full res, 4 = quarter, 8 = eighth

// --- Screen-Space Gradient Mask ---
// Fades _f-group objects to transparent at the bottom of the screen (HDRI shows through).
// URL overrides: ?gradTop=0.3&gradBottom=0.5
// Consumes createGradientTexture()
const GRADIENT_TOP_SOLID = parseFloat(params.get('gradTop')) || 0.4;
const GRADIENT_BOTTOM_TRANSPARENT = parseFloat(params.get('gradBottom')) || 0.48;

// --- Alpha Test Threshold ---
// Controls texture transparency cutoff. 0.5 = Minecraft-style hard cutout (default).
// Set to 0 for semitransparency: ?alphaTest=0
// Consumes applyShadowSettings(), createSkinMaterial(), applySkinToRig(), createBBModelCube()
const ALPHA_TEST_THRESHOLD = parseFloat(params.get('alphaTest')) || 0.1;

// --- HDRI Environment Map ---
// Supported: .hdr (RGBELoader), .png/.jpg (TextureLoader). Place file next to this HTML.
// On = provides reflection + optional skybox background.
// Consumes loadHDRI()
const HDRI_ENABLED = true;
const HDRI_FILE = 'textures/hdri.hdr';
const HDRI_INTENSITY = 0.90;         // Tone mapping exposure
const HDRI_ROTATION_Y = 0;           // Degrees clockwise
const HDRI_AS_SKYBOX = true;         // Show HDRI as scene background (reveals through transparency)
const HDRI_LIGHT_BLEND = 0.50;       // 0 = HDRI only, 1 = manual lights only

// --- Lighting (Three.js, blended with HDRI via HDRI_LIGHT_BLEND) ---
// Key light casts shadows. Edit here or via debug gizmos (?debugLighting=1 → Save button).
// Consumes buildLightsFromConfig()
const LIGHTING_CONFIG = {
    "ambient": { "color": "rgb(159,76,40)", "intensity": 0.7 },
    "key": { "color": "rgb(253,179,109)", "intensity": 0.9, "position": [0.44,2,-10] },
    "fill": { "color": "rgb(7,9,64)", "intensity": 1.7, "position": [-7.5,-0.7,7] },
    "rim": { "color": "rgb(179,118,61)", "intensity": 1.5, "position": [3.312156585646899,2.0896099376618564,-4.5] }
};

// --- Environment Map Exclusions ---
// Meshes whose name (or any parent group name) ends with this suffix
// will NOT receive HDR environment map reflections. All other meshes
// get the HDRI as their envMap.
// Consumes applyEnvMap()
const ENV_MAP_EXCLUDE_SUFFIX = '_n';

// --- Shadows ---
// URL override: ?shadows=0 disables shadows, enabled by default.
// Consumes applyShadowSettings(), buildLightsFromConfig(), renderer setup
const ENABLE_SHADOWS = params.get('shadows') !== '0';
const SHADOW_MAP_SIZE = 2048;

// --- Distance Fog (exponential, only affects _f-group objects) ---
// Consumes scene.fog, applyFogFiltering(), renderLoop fog pass
const DISTANCE_FOG_ENABLED = true;
const DISTANCE_FOG_COLOR = 0xFF85AD;
const DISTANCE_FOG_DENSITY = 0.015;

// --- Skin Loading ---
const SKIN_POLL_INTERVAL = 500;   // ms between FancyMenu bridge polls; consumes pollFancyMenu()
const MAX_SKIN_POLLS = 20;         // total poll attempts before fallback
const SKIN_TIMEOUT = 12000;        // ms before showing model without skin; consumes init()
// Loading priority: URL UUID → explicit skin URL → dev player name → FancyMenu poll → fallback

// ============================================================
// INTERNAL CONSTANTS — not user-configurable
// ============================================================
const SCALE = 16;
const ROTATION_ORDER = 'XYZ';
const BLOCKBENCH_FACES = {
    north: { normal: [0, 0, -1], corners: (f, t) => [[f[0], f[1], f[2]], [t[0], f[1], f[2]], [t[0], t[1], f[2]], [f[0], t[1], f[2]]] },
    south: { normal: [0, 0, 1], corners: (f, t) => [[t[0], f[1], t[2]], [f[0], f[1], t[2]], [f[0], t[1], t[2]], [t[0], t[1], t[2]]] },
    east: { normal: [1, 0, 0], corners: (f, t) => [[t[0], f[1], f[2]], [t[0], f[1], t[2]], [t[0], t[1], t[2]], [t[0], t[1], f[2]]] },
    west: { normal: [-1, 0, 0], corners: (f, t) => [[f[0], f[1], t[2]], [f[0], f[1], f[2]], [f[0], t[1], f[2]], [f[0], t[1], t[2]]] },
    up: { normal: [0, 1, 0], corners: (f, t) => [[f[0], t[1], t[2]], [t[0], t[1], t[2]], [t[0], t[1], f[2]], [f[0], t[1], f[2]]] },
    down: { normal: [0, -1, 0], corners: (f, t) => [[f[0], f[1], f[2]], [t[0], f[1], f[2]], [t[0], f[1], t[2]], [f[0], f[1], t[2]]] }
};

const debugOverlay = document.getElementById('debug-overlay');
const debugLines = [];
const DEBUG_MAX_LINES = 24;

function log(msg, level) {
    if (level === undefined) level = 'info';
    const tag = level === 'error' ? 'error' : level === 'warn' ? 'warn' : 'log';
    console[tag](`[BBViewer] ${msg}`);
    if (!DEBUG) return;
    const time = new Date().toLocaleTimeString();
    debugLines.push({ time, msg, level });
    if (debugLines.length > DEBUG_MAX_LINES) debugLines.shift();
    debugOverlay.innerHTML = debugLines.map(function (line) {
        const cls = line.level === 'error' ? 'line-error' : line.level === 'warn' ? 'line-warn' : '';
        return '<div class="' + cls + '">[' + line.time + '] ' + line.msg + '</div>';
    }).join('');
    debugOverlay.classList.add('visible');
    debugOverlay.scrollTop = debugOverlay.scrollHeight;
}

if (DEBUG) {
    debugOverlay.classList.add('visible');
    log('Debug overlay on (?debug=1)');
}

function isFancyMenuContext() {
    return !!(window.fancymenu || window.FancyMenu);
}
