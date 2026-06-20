// ============================================================
// SCENE — renderer, camera, compositing, HDRI, render loop, fade
// ============================================================
// Depends on: config.js (via globals)

const scene = new THREE.Scene();
scene.background = null;
if (DISTANCE_FOG_ENABLED) {
    scene.fog = new THREE.FogExp2(DISTANCE_FOG_COLOR, DISTANCE_FOG_DENSITY);
}

// DOM overlay for initial load fade-in (black screen → transparent)
const fadeOverlay = document.createElement('div');
fadeOverlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:#000;z-index:10001;pointer-events:none;opacity:1;transition:none;';
document.body.appendChild(fadeOverlay);

const canvas = document.createElement('canvas');
canvas.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;z-index:10000;';
document.body.appendChild(canvas);

// HDRI skybox background — transparent objects reveal this
let hdriBackground = null;

let fadeStarted = false;
let fadeStartTime = 0;
let loadStartTime = 0;

let pixelTarget = null;
let pixelQuad = null;
let fogTarget = null;
let fogQuad = null;
let bgTarget = null;
let bgQuad = null;
let pixelScene = null;
let pixelCamera = null;

function createGradientTexture() {
    var localCanvas = document.createElement('canvas');
    localCanvas.width = 2;
    localCanvas.height = 128;
    var ctx = localCanvas.getContext('2d');
    var grad = ctx.createLinearGradient(0, 0, 0, 128);
    grad.addColorStop(0.00, 'white');
    grad.addColorStop(GRADIENT_TOP_SOLID, 'white');
    grad.addColorStop(GRADIENT_BOTTOM_TRANSPARENT, 'black');
    grad.addColorStop(1.00, 'black');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, 2, 128);
    var tex = new THREE.CanvasTexture(localCanvas);
    tex.minFilter = THREE.LinearFilter;
    tex.magFilter = THREE.LinearFilter;
    tex.wrapS = THREE.ClampToEdgeWrapping;
    tex.wrapT = THREE.ClampToEdgeWrapping;
    return tex;
}

function initPixelation() {
    var filter = PIXELATION_ENABLED ? THREE.NearestFilter : THREE.LinearFilter;

    pixelTarget = new THREE.WebGLRenderTarget(1, 1, {
        minFilter: filter,
        magFilter: filter,
        format: THREE.RGBAFormat
    });

    fogTarget = new THREE.WebGLRenderTarget(1, 1, {
        minFilter: filter,
        magFilter: filter,
        format: THREE.RGBAFormat
    });

    bgTarget = new THREE.WebGLRenderTarget(1, 1, {
        minFilter: filter,
        magFilter: filter,
        format: THREE.RGBAFormat
    });

    pixelScene = new THREE.Scene();
    pixelCamera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);

    var geo = new THREE.PlaneGeometry(2, 2);

    var bgMat = new THREE.MeshBasicMaterial({
        map: bgTarget.texture,
        depthWrite: false,
        depthTest: false
    });
    bgQuad = new THREE.Mesh(geo, bgMat);
    bgQuad.renderOrder = 9997;
    pixelScene.add(bgQuad);

    var fogMat = new THREE.MeshBasicMaterial({
        map: fogTarget.texture,
        alphaMap: createGradientTexture(),
        transparent: true,
        depthWrite: false,
        depthTest: false
    });
    fogQuad = new THREE.Mesh(geo, fogMat);
    fogQuad.renderOrder = 9998;
    pixelScene.add(fogQuad);

    var fgMat = new THREE.MeshBasicMaterial({
        map: pixelTarget.texture,
        transparent: true,
        depthWrite: false,
        depthTest: false
    });
    pixelQuad = new THREE.Mesh(geo, fgMat);
    pixelQuad.renderOrder = 9999;
    pixelScene.add(pixelQuad);
}

function resizePixelTarget() {
    if (!pixelTarget) return;
    var scale = PIXELATION_ENABLED ? PIXELATION_SCALE : 1;
    var w = Math.max(1, Math.floor(window.innerWidth / scale));
    var h = Math.max(1, Math.floor(window.innerHeight / scale));
    pixelTarget.setSize(w, h);
    if (fogTarget) fogTarget.setSize(w, h);
    if (bgTarget) bgTarget.setSize(w, h);
}

var renderer = new THREE.WebGLRenderer({
    canvas: canvas,
    antialias: true,
    alpha: true,
    preserveDrawingBuffer: true
});
renderer.setPixelRatio(window.devicePixelRatio);
renderer.outputEncoding = THREE.sRGBEncoding;
if (ENABLE_SHADOWS) {
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
}

var camera = new THREE.PerspectiveCamera(40, 16 / 9, 0.1, 1000);
var viewportAspect = 16 / 9;

var modelGroup = new THREE.Group();
scene.add(modelGroup);

function updateViewportSize() {
    var winW = window.innerWidth;
    var winH = window.innerHeight;
    var winAspect = winW / winH;

    renderer.setSize(winW, winH);
    renderer.setViewport(0, 0, winW, winH);
    camera.aspect = winAspect;
    camera.updateProjectionMatrix();
}

var clock = new THREE.Clock();

function tryShowAndFade() {
    if (!modelReady || !skinReady || fadeStarted) return;

    var elapsed = clock.getElapsedTime() - loadStartTime;
    if (elapsed < MIN_LOAD_DELAY) {
        setTimeout(tryShowAndFade, Math.max(0, (MIN_LOAD_DELAY - elapsed) * 1000));
        return;
    }

    if (gltfScene) {
        gltfScene.visible = true;
    }

    startFadeIn();
}

function startFadeIn() {
    if (fadeStarted) return;
    fadeStarted = true;
    fadeStartTime = clock.getElapsedTime();
    log('Fade in started (' + FADE_DURATION + 's)');
}

function renderLoop() {
    requestAnimationFrame(renderLoop);
    var delta = clock.getDelta();
    if (animationMixer) animationMixer.update(delta);
    if (cameraAnimationMixer) cameraAnimationMixer.update(delta);
    if (orbitControls && freecamActive) orbitControls.update();
    if (fadeStarted) {
        var elapsed = clock.getElapsedTime() - fadeStartTime;
        var t = Math.min(elapsed / FADE_DURATION, 1);
        var eased = 1 - Math.pow(1 - t, 3);
        fadeOverlay.style.opacity = (1 - eased).toFixed(4);
        if (t >= 1) {
            fadeOverlay.style.display = 'none';
        }
    }

    if (fogTarget && fogQuad && pixelTarget && pixelQuad && bgTarget && bgQuad) {
        var savedBg = scene.background;
        var savedFog = scene.fog;
        var savedClearColor = renderer.getClearColor(new THREE.Color()).clone();
        var savedClearAlpha = renderer.getClearAlpha();

        if (gltfScene) gltfScene.visible = true;
        if (modelRoot) modelRoot.visible = true;

        showFogLayer();
        scene.background = null;
        scene.fog = savedFog;
        renderer.setRenderTarget(fogTarget);
        renderer.setClearColor(0x000000, 0);
        renderer.clear(true, true, false);
        renderer.render(scene, camera);

        showFgLayer();
        renderer.setRenderTarget(pixelTarget);
        renderer.clear(true, true, false);
        renderer.render(scene, camera);

        showAllLayers();
        scene.background = savedBg;
        scene.fog = null;
        renderer.setRenderTarget(bgTarget);
        renderer.setClearColor(0x000000, 0);
        renderer.clear(true, true, false);
        modelGroup.visible = false;
        renderer.render(scene, camera);
        modelGroup.visible = true;

        scene.fog = savedFog;
        renderer.setClearColor(savedClearColor, savedClearAlpha);

        bgQuad.material.map = bgTarget.texture;
        fogQuad.material.map = fogTarget.texture;
        pixelQuad.material.map = pixelTarget.texture;

        renderer.setRenderTarget(null);
        renderer.clear(true, true, false);
        renderer.render(pixelScene, pixelCamera);
    } else {
        renderer.render(scene, camera);
    }
}

window.addEventListener('resize', function () {
    updateViewportSize();
    resizePixelTarget();
});
