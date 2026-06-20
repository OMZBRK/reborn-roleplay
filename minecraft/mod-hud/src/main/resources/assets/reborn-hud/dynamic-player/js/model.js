// ============================================================
// MODEL — GLB/BBModel loading, animations, fog, shadows, mesh
// ============================================================
// Depends on: config.js, scene.js (via globals: scene, camera, renderer, modelGroup)

var lights = {};
var lightGizmos = {};

function buildLightsFromConfig() {
    var cfg = LIGHTING_CONFIG;

    lights.ambient = new THREE.AmbientLight(cfg.ambient.color, cfg.ambient.intensity);
    scene.add(lights.ambient);

    lights.key = new THREE.DirectionalLight(cfg.key.color, cfg.key.intensity);
    lights.key.position.set(cfg.key.position[0], cfg.key.position[1], cfg.key.position[2]);
    if (ENABLE_SHADOWS) {
        lights.key.castShadow = true;
        lights.key.shadow.mapSize.width = SHADOW_MAP_SIZE;
        lights.key.shadow.mapSize.height = SHADOW_MAP_SIZE;
        Object.assign(lights.key.shadow.camera, { near: 0.1, far: 20, left: -5, right: 5, top: 5, bottom: -5 });
        lights.key.shadow.bias = -0.001;
    }
    scene.add(lights.key);

    lights.fill = new THREE.DirectionalLight(cfg.fill.color, cfg.fill.intensity);
    lights.fill.position.set(cfg.fill.position[0], cfg.fill.position[1], cfg.fill.position[2]);
    scene.add(lights.fill);

    lights.rim = new THREE.DirectionalLight(cfg.rim.color, cfg.rim.intensity);
    lights.rim.position.set(cfg.rim.position[0], cfg.rim.position[1], cfg.rim.position[2]);
    scene.add(lights.rim);
}

buildLightsFromConfig();

async function loadHDRI() {
    if (!HDRI_ENABLED) return;

    var ext = HDRI_FILE.split('.').pop().toLowerCase();

    try {
        var texture;
        if (ext === 'hdr') {
            var loader = new THREE.RGBELoader();
            texture = await new Promise(function (resolve, reject) {
                loader.load(HDRI_FILE, resolve, undefined, reject);
            });
        } else {
            var loader = new THREE.TextureLoader();
            texture = await new Promise(function (resolve, reject) {
                loader.load(HDRI_FILE, resolve, undefined, reject);
            });
        }

        texture.mapping = THREE.EquirectangularReflectionMapping;
        texture.encoding = THREE.sRGBEncoding;

        if (HDRI_ROTATION_Y !== 0) {
            var rotationCanvas = document.createElement('canvas');
            rotationCanvas.width = texture.image.width;
            rotationCanvas.height = texture.image.height;
            var rotCtx = rotationCanvas.getContext('2d');
            var shift = Math.round((HDRI_ROTATION_Y / 360) * rotationCanvas.width);
            rotCtx.drawImage(texture.image, shift, 0);
            rotCtx.drawImage(texture.image, shift - rotationCanvas.width, 0);
            var newImg = new Image();
            await new Promise(function (res, rej) {
                newImg.onload = res;
                newImg.onerror = rej;
                newImg.src = rotationCanvas.toDataURL();
            });
            var newTex = new THREE.CanvasTexture(newImg);
            newTex.mapping = THREE.EquirectangularReflectionMapping;
            newTex.encoding = THREE.sRGBEncoding;
            texture = newTex;
        }

        scene.environment = texture;
        envMapTexture = texture;

        if (HDRI_AS_SKYBOX) {
            var bgTex = texture.clone();
            bgTex.mapping = THREE.EquirectangularReflectionMapping;
            bgTex.encoding = THREE.sRGBEncoding;
            scene.background = bgTex;
        }

        if (HDRI_INTENSITY !== 1.0) {
            renderer.toneMappingExposure = HDRI_INTENSITY;
        }

        var blend = HDRI_LIGHT_BLEND;
        Object.values(lights).forEach(function (light) {
            light.intensity *= (1 - blend);
        });

        log('HDRI loaded: ' + HDRI_FILE + ' (blend: ' + (blend * 100).toFixed(0) + '% manual' + (HDRI_AS_SKYBOX ? ', skybox on' : '') + ')');
    } catch (e) {
        log('HDRI load failed: ' + e.message, 'warn');
    }
}

// ============================================================
// MODEL STATE
// ============================================================
var modelRoot = null;
var gltfScene = null;
var envMapTexture = null;
var bbmodelData = null;
var rigRoots = { playerStandard: null, playerSlim: null };
var animationClips = new Map();
var cachedGltfAnimations = [];
var animationMixer = null;
var activeAction = null;
var animationsStarted = false;
var boneMap = new Map();

var cameraAnimationMixer = null;
var cameraActiveAction = null;
var cameraAnimationsStarted = false;
var cameraAnimationClips = new Map();
var cameraBasePosition = new THREE.Vector3();
var cameraBaseRotation = new THREE.Euler(0, 0, 0, 'XYZ');

var gltfLoader = new THREE.GLTFLoader();

var modelReady = false;

// ============================================================
// CAMERA & VIEWPORT
// ============================================================
function applyCameraFromBBModel() {
    var cameraData = bbmodelData?.elements?.find(function (el) { return el.type === 'camera'; });
    if (!cameraData?.position) return false;

    var pos = cameraData.position;
    var rot = cameraData.rotation || [0, 0, 0];

    camera.position.set(pos[0] / SCALE, pos[1] / SCALE, pos[2] / SCALE);
    camera.rotation.order = 'XYZ';
    camera.rotation.set(
        THREE.MathUtils.degToRad(rot[0]),
        THREE.MathUtils.degToRad(rot[1]),
        THREE.MathUtils.degToRad(rot[2])
    );
    camera.fov = cameraData.fov || 40;

    if (cameraData.aspect_ratio?.length === 2) {
        viewportAspect = cameraData.aspect_ratio[0] / cameraData.aspect_ratio[1];
    }

    cameraBasePosition.copy(camera.position);
    cameraBaseRotation.copy(camera.rotation);

    updateViewportSize();
    log('Camera from BBModel: fov=' + camera.fov);
    return true;
}

function applyCameraFromGLB(camNode) {
    camNode.updateMatrixWorld(true);
    var worldPos = new THREE.Vector3();
    var worldQuat = new THREE.Quaternion();
    var worldScale = new THREE.Vector3();
    camNode.matrixWorld.decompose(worldPos, worldQuat, worldScale);

    camera.position.copy(worldPos);
    camera.quaternion.copy(worldQuat);
    camera.fov = camera.fov || 40;
    camera.updateProjectionMatrix();
    updateViewportSize();
    log('Camera from GLB node');
}

// ============================================================
// GLB MODEL
// ============================================================
function updateRigVisibility() {
    if (rigRoots.playerStandard) {
        rigRoots.playerStandard.visible = !skinManager.isSlim;
    }
    if (rigRoots.playerSlim) {
        rigRoots.playerSlim.visible = skinManager.isSlim;
    }
}

function isPlayerRigMesh(obj) {
    var current = obj;
    while (current) {
        if (current.name === 'playerStandard' || current.name === 'playerSlim') return true;
        current = current.parent;
    }
    return false;
}

function prepareMeshesForSkin(root) {
    if (!root) return;
    root.traverse(function (obj) {
        if (!obj.isMesh) return;
        if (!isPlayerRigMesh(obj)) return;
        var mats = Array.isArray(obj.material) ? obj.material : [obj.material];
        obj.material = mats.length > 1
            ? mats.map(function () { return createSkinMaterial(null); })
            : createSkinMaterial(null);
    });
}

// ============================================================
// FOG
// ============================================================
function applyFogFiltering() {
    if (!DISTANCE_FOG_ENABLED || !modelRoot) return;

    modelRoot.traverse(function (obj) {
        if (!obj.isMesh) return;

        var isPlayer = isPlayerRigMesh(obj);
        if (isPlayer) {
            var mats = Array.isArray(obj.material) ? obj.material : [obj.material];
            mats.forEach(function (mat) { if (mat) mat.fog = false; });
            return;
        }

        var inFogGroup = false;
        var current = obj.parent;
        while (current) {
            if (current.name && current.name.endsWith('_f')) {
                inFogGroup = true;
                break;
            }
            current = current.parent;
        }

        if (obj.name && obj.name.endsWith('_f')) {
            inFogGroup = true;
        }

        var mats = Array.isArray(obj.material) ? obj.material : [obj.material];
        mats.forEach(function (mat) {
            if (mat) {
                mat.fog = inFogGroup;
            }
        });
    });

    if (DEBUG) {
        var fogCount = 0, noFogCount = 0;
        modelRoot.traverse(function (obj) {
            if (!obj.isMesh) return;
            var mats = Array.isArray(obj.material) ? obj.material : [obj.material];
            if (mats[0]?.fog) fogCount++; else noFogCount++;
        });
        log('Fog filtering: ' + fogCount + ' fogged, ' + noFogCount + ' clear');
    }
}

var fogMeshes = [];
var fgMeshes = [];

function cacheMeshLayers() {
    fogMeshes.length = 0;
    fgMeshes.length = 0;
    if (!modelRoot) return;

    modelRoot.traverse(function (obj) {
        if (!obj.isMesh) return;
        if (isPlayerRigMesh(obj)) { fgMeshes.push(obj); return; }

        var inFog = false;
        var current = obj.parent;
        while (current) {
            if (current.name && current.name.endsWith('_f')) { inFog = true; break; }
            current = current.parent;
        }
        if (obj.name && obj.name.endsWith('_f')) inFog = true;

        (inFog ? fogMeshes : fgMeshes).push(obj);
    });
}

function showFogLayer() { fogMeshes.forEach(function (m) { m.visible = true; }); fgMeshes.forEach(function (m) { m.visible = false; }); }
function showFgLayer() { fogMeshes.forEach(function (m) { m.visible = false; }); fgMeshes.forEach(function (m) { m.visible = true; }); }
function showAllLayers() { fogMeshes.forEach(function (m) { m.visible = true; }); fgMeshes.forEach(function (m) { m.visible = true; }); }

// ============================================================
// SHADOWS
// ============================================================
function applyShadowSettings() {
    if (!ENABLE_SHADOWS || !modelRoot) return;
    modelRoot.traverse(function (obj) {
        if (!obj.isMesh) return;
        obj.castShadow = true;
        obj.receiveShadow = true;
        var mats = Array.isArray(obj.material) ? obj.material : [obj.material];
        mats.forEach(function (mat) {
            if (!mat) return;
            mat.alphaTest = ALPHA_TEST_THRESHOLD;
        });
        if (!Array.isArray(obj.material) && (obj.material.map || obj.material.alphaMap)) {
            obj.customDepthMaterial = new THREE.MeshDepthMaterial({
                depthPacking: THREE.RGBADepthPacking,
                map: obj.material.map || null,
                alphaMap: obj.material.alphaMap || null,
                alphaTest: ALPHA_TEST_THRESHOLD
            });
        }
    });
}

// ============================================================
// ENVIRONMENT MAP
// ============================================================
var neutralEnvMap = null;
function getNeutralEnvMap() {
    if (!neutralEnvMap) {
        var data = new Uint8Array([192, 192, 192]);
        neutralEnvMap = new THREE.DataTexture(data, 1, 1, THREE.RGBFormat);
        neutralEnvMap.mapping = THREE.EquirectangularReflectionMapping;
        neutralEnvMap.needsUpdate = true;
    }
    return neutralEnvMap;
}

function applyEnvMap() {
    if (!modelRoot) return;
    var suffix = ENV_MAP_EXCLUDE_SUFFIX || '';
    if (!suffix) return;
    var neutral = getNeutralEnvMap();
    modelRoot.traverse(function (obj) {
        if (!obj.isMesh) return;
        var excluded = obj.name && obj.name.endsWith(suffix);
        if (!excluded) {
            var current = obj.parent;
            while (current) {
                if (current.name && current.name.endsWith(suffix)) {
                    excluded = true;
                    break;
                }
                current = current.parent;
            }
        }
        if (!excluded) return;
        var materials = Array.isArray(obj.material) ? obj.material : [obj.material];
        materials.forEach(function (mat) {
            if (!mat) return;
            mat.envMap = neutral;
            mat.needsUpdate = true;
        });
    });
    log('envMap excluded for suffix: "' + suffix + '"');
}

// ============================================================
// ANIMATIONS
// ============================================================
var animationStartScheduled = false;

function scheduleAnimationStart() {
    if (animationStartScheduled || animationsStarted || STATIC_POSE) return;
    animationStartScheduled = true;
    requestAnimationFrame(function () {
        requestAnimationFrame(function () {
            animationStartScheduled = false;
            startAnimationsIfReady();
        });
    });
}

function startAnimationsIfReady() {
    if (animationsStarted || STATIC_POSE || !modelRoot || !skinManager.currentTexture) return;
    if (animationClips.size === 0) return;

    animationsStarted = true;
    if (animationClips.size === 1) {
        playClip(animationClips.values().next().value, true);
    } else {
        playClip(pickWeightedClip(Array.from(animationClips.values())), false);
    }

    startCameraAnimationsIfReady();
}

function parseAnimationWeight(name) {
    var match = name.match(/(\d{2})$/);
    return match ? parseInt(match[1], 10) : null;
}

function pickWeightedClip(clips) {
    if (clips.length === 0) return null;
    if (clips.length === 1) return clips[0];

    var parsed = clips.map(function (clip) { return { clip: clip, weight: parseAnimationWeight(clip.name) }; });
    var hasWeights = parsed.some(function (p) { return p.weight !== null; });
    var total = 0;
    var weights = parsed.map(function (p) {
        var w = hasWeights ? (p.weight !== null ? p.weight : 0) : 100 / clips.length;
        total += w;
        return w;
    });
    if (total <= 0) return clips[0];

    var r = Math.random() * total;
    for (var i = 0; i < clips.length; i++) {
        r -= weights[i];
        if (r <= 0) return clips[i];
    }
    return clips[clips.length - 1];
}

function playClip(clip, loop) {
    if (loop === undefined) loop = true;
    if (!animationMixer || !clip) return;

    if (activeAction) activeAction.stop();

    var action = animationMixer.clipAction(clip);
    if (loop) {
        action.setLoop(THREE.LoopRepeat, Infinity);
    } else {
        action.setLoop(THREE.LoopOnce, 1);
        action.clampWhenFinished = true;
    }
    action.reset();
    action.play();
    animationMixer.update(0);
    activeAction = action;
    log('Playing: ' + clip.name + (loop ? ' (loop)' : ''));
}

function onAnimationFinished() {
    var clips = Array.from(animationClips.values());
    if (clips.length <= 1) {
        if (activeAction) {
            activeAction.reset();
            activeAction.play();
        }
        return;
    }
    playClip(pickWeightedClip(clips), false);
}

function registerAnimations(clips) {
    animationClips.clear();
    if (!clips?.length) {
        log('No animations available', 'warn');
        return;
    }

    clips.forEach(function (clip) {
        animationClips.set(clip.name, clip);
        log('Animation: ' + clip.name + ' (' + clip.duration.toFixed(2) + 's)');
    });

    if (animationMixer) {
        animationMixer.stopAllAction();
        if (modelRoot) animationMixer.uncacheRoot(modelRoot);
    }
    animationMixer = new THREE.AnimationMixer(modelRoot);
    animationMixer.addEventListener('finished', onAnimationFinished);
    animationsStarted = false;

    if (STATIC_POSE) {
        log('Static pose (?static=1)');
        return;
    }
}

// ============================================================
// GLB LOADING
// ============================================================
async function loadGLB(filename) {
    log('Loading GLB: ' + filename);
    return new Promise(function (resolve, reject) {
        gltfLoader.load(
            filename,
            function (gltf) {
                try {
                    if (gltfScene) {
                        modelGroup.remove(gltfScene);
                    }

                    gltfScene = gltf.scene;
                    gltfScene.visible = false;
                    modelRoot = gltfScene;
                    skinTextureFlipY = false;

                    prepareMeshesForSkin(gltfScene);
                    cachedGltfAnimations = gltf.animations || [];

                    var camNode = gltfScene.getObjectByName('camera');
                    if (camNode) {
                        if (!applyCameraFromBBModel()) {
                            applyCameraFromGLB(camNode);
                        }
                        camNode.visible = false;
                    } else {
                        applyCameraFromBBModel();
                    }

                    parseCameraAnimations();

                    rigRoots.playerStandard = gltfScene.getObjectByName('playerStandard');
                    rigRoots.playerSlim = gltfScene.getObjectByName('playerSlim');

                    if (!rigRoots.playerStandard) {
                        log('playerStandard not found in GLB', 'warn');
                    }

                    updateRigVisibility();
                    registerAnimations(cachedGltfAnimations);
                    applySkinToRig();
                    applyFogFiltering();
                    cacheMeshLayers();
                    applyShadowSettings();
                    applyEnvMap();
                    gltfScene.visible = false;
                    modelGroup.add(gltfScene);

                    modelReady = true;
                    tryShowAndFade();

                    if (DEBUG) {
                        var box = new THREE.Box3().setFromObject(modelRoot);
                        var c = box.getCenter(new THREE.Vector3());
                        var s = box.getSize(new THREE.Vector3());
                        log('Bounds: center (' + c.x.toFixed(2) + ', ' + c.y.toFixed(2) + ', ' + c.z.toFixed(2) + ') size (' + s.x.toFixed(2) + ', ' + s.y.toFixed(2) + ', ' + s.z.toFixed(2) + ')');
                    }

                    log('GLB loaded');
                    resolve(gltf);
                } catch (err) {
                    log('GLB setup error: ' + err.message, 'error');
                    reject(err);
                }
            },
            undefined,
            function (err) {
                log('GLB load error: ' + err.message, 'error');
                reject(err);
            }
        );
    });
}

async function loadBBModelMetadata() {
    try {
        var response = await fetch(BBMODEL_FILE);
        if (!response.ok) return;
        bbmodelData = await response.json();
        log('BBModel metadata loaded (' + bbmodelData.name + ')');
    } catch (e) {
        log('BBModel metadata skipped: ' + e.message, 'warn');
    }
}

// ============================================================
// CAMERA ANIMATION
// ============================================================
function parseCameraAnimations() {
    if (!bbmodelData?.animations?.length) {
        log('No BBModel animations for camera parsing', 'warn');
        return;
    }

    var cameraData = bbmodelData.elements?.find(function (el) { return el.type === 'camera'; });
    if (!cameraData?.position) {
        log('No camera element found, skipping camera animation', 'warn');
        return;
    }

    var basePos = cameraData.position;
    var baseRot = cameraData.rotation || [0, 0, 0];

    for (var animIdx = 0; animIdx < bbmodelData.animations.length; animIdx++) {
        var anim = bbmodelData.animations[animIdx];
        var camAnimator = null;
        var animatorKeys = Object.keys(anim.animators || {});
        for (var ak = 0; ak < animatorKeys.length; ak++) {
            var a = anim.animators[animatorKeys[ak]];
            if (a.type === 'camera') { camAnimator = a; break; }
        }
        if (!camAnimator?.keyframes?.length) continue;

        var posKfs = camAnimator.keyframes.filter(function (k) { return k.channel === 'position'; }).sort(function (a, b) { return a.time - b.time; });
        var rotKfs = camAnimator.keyframes.filter(function (k) { return k.channel === 'rotation'; }).sort(function (a, b) { return a.time - b.time; });

        var times = [];
        var posValues = [];
        var rotValues = [];

        var timeSet = new Set();
        posKfs.forEach(function (k) { timeSet.add(k.time); });
        rotKfs.forEach(function (k) { timeSet.add(k.time); });
        var sortedTimes = Array.from(timeSet).sort(function (a, b) { return a - b; });

        for (var ti = 0; ti < sortedTimes.length; ti++) {
            var t = sortedTimes[ti];
            times.push(t);

            var posKf = null;
            for (var pk = 0; pk < posKfs.length; pk++) {
                if (posKfs[pk].time === t) { posKf = posKfs[pk]; break; }
            }
            if (posKf) {
                var dp = posKf.data_points[0];
                posValues.push(
                    (basePos[0] + (parseFloat(dp.x) || 0)) / SCALE,
                    (basePos[1] + (parseFloat(dp.y) || 0)) / SCALE,
                    (basePos[2] + (parseFloat(dp.z) || 0)) / SCALE
                );
            } else {
                posValues.push(cameraBasePosition.x, cameraBasePosition.y, cameraBasePosition.z);
            }

            var rotKf = null;
            for (var rk = 0; rk < rotKfs.length; rk++) {
                if (rotKfs[rk].time === t) { rotKf = rotKfs[rk]; break; }
            }
            if (rotKf) {
                var dp = rotKf.data_points[0];
                var ex = THREE.MathUtils.degToRad(baseRot[0] + (parseFloat(dp.x) || 0));
                var ey = THREE.MathUtils.degToRad(baseRot[1] + (parseFloat(dp.y) || 0));
                var ez = THREE.MathUtils.degToRad(baseRot[2] + (parseFloat(dp.z) || 0));
                var q = new THREE.Quaternion().setFromEuler(new THREE.Euler(ex, ey, ez, 'XYZ'));
                rotValues.push(q.x, q.y, q.z, q.w);
            } else {
                var bq = new THREE.Quaternion().setFromEuler(cameraBaseRotation);
                rotValues.push(bq.x, bq.y, bq.z, bq.w);
            }
        }

        var tracks = [];
        if (posValues.length > 0) {
            tracks.push(new THREE.VectorKeyframeTrack('.position', times, posValues));
        }
        if (rotValues.length > 0) {
            tracks.push(new THREE.QuaternionKeyframeTrack('.quaternion', times, rotValues));
        }

        if (tracks.length === 0) continue;

        var clip = new THREE.AnimationClip(anim.name, anim.length || 3, tracks);
        cameraAnimationClips.set(anim.name, clip);
        log('Camera animation: ' + anim.name + ' (' + clip.duration.toFixed(2) + 's, loop: ' + (anim.loop === 'loop') + ')');
    }

    if (cameraAnimationClips.size === 0) {
        log('No camera animation keyframes found', 'warn');
        return;
    }

    if (cameraAnimationMixer) {
        cameraAnimationMixer.stopAllAction();
    }
    cameraAnimationMixer = new THREE.AnimationMixer(camera);
    cameraAnimationMixer.addEventListener('finished', onCameraAnimationFinished);
    cameraAnimationsStarted = false;
}

function playCameraClip(clip, loop) {
    if (loop === undefined) loop = true;
    if (!cameraAnimationMixer || !clip) return;

    if (cameraActiveAction) cameraActiveAction.stop();

    var action = cameraAnimationMixer.clipAction(clip);
    if (loop) {
        action.setLoop(THREE.LoopRepeat, Infinity);
    } else {
        action.setLoop(THREE.LoopOnce, 1);
        action.clampWhenFinished = true;
    }
    action.reset();
    action.play();
    cameraAnimationMixer.update(0);
    cameraActiveAction = action;
    log('Camera playing: ' + clip.name + (loop ? ' (loop)' : ''));
}

function onCameraAnimationFinished() {
    var clips = Array.from(cameraAnimationClips.values());
    if (clips.length <= 1) {
        if (cameraActiveAction) {
            cameraActiveAction.reset();
            cameraActiveAction.play();
        }
        return;
    }
    playCameraClip(pickWeightedClip(clips), false);
}

function startCameraAnimationsIfReady() {
    if (cameraAnimationsStarted || STATIC_POSE) return;
    if (cameraAnimationClips.size === 0) return;

    cameraAnimationsStarted = true;
    if (cameraAnimationClips.size === 1) {
        playCameraClip(cameraAnimationClips.values().next().value, true);
    } else {
        playCameraClip(pickWeightedClip(Array.from(cameraAnimationClips.values())), false);
    }
}

// ============================================================
// BBMODEL SCENE (Blockbench layout matched to GLB export)
// ============================================================
function inflatedBounds(from, to, inflate) {
    if (!inflate) return { from: from.slice(), to: to.slice() };
    return {
        from: [from[0] - inflate, from[1] - inflate, from[2] - inflate],
        to: [to[0] + inflate, to[1] + inflate, to[2] + inflate]
    };
}

function faceUvCoords(uv, ox, oy, tw, th) {
    var u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];
    u1 += ox; u2 += ox; v1 += oy; v2 += oy;
    return [
        [u1 / tw, 1 - v1 / th],
        [u2 / tw, 1 - v1 / th],
        [u2 / tw, 1 - v2 / th],
        [u1 / tw, 1 - v2 / th]
    ];
}

function buildBBModelBoxGeometry(from, to, element, resolution) {
    var ox = element.uv_offset ? element.uv_offset[0] : 0;
    var oy = element.uv_offset ? element.uv_offset[1] : 0;
    var tw = resolution.width;
    var th = resolution.height;
    var positions = [];
    var normals = [];
    var uvs = [];
    var indices = [];

    var faceNames = Object.keys(BLOCKBENCH_FACES);
    for (var fi = 0; fi < faceNames.length; fi++) {
        var faceName = faceNames[fi];
        var def = BLOCKBENCH_FACES[faceName];
        var face = element.faces ? element.faces[faceName] : undefined;
        if (!face || !face.uv) continue;
        var corners = def.corners(from, to);
        var texCoords = faceUvCoords(face.uv, ox, oy, tw, th);
        var base = positions.length / 3;
        for (var ci = 0; ci < 4; ci++) {
            var c = corners[ci];
            positions.push(c[0] / SCALE, c[1] / SCALE, c[2] / SCALE);
            normals.push(def.normal[0], def.normal[1], def.normal[2]);
            uvs.push(texCoords[ci][0], texCoords[ci][1]);
        }
        indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
    }

    var geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
    geometry.setAttribute('normal', new THREE.Float32BufferAttribute(normals, 3));
    geometry.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
    geometry.setIndex(indices);
    return geometry;
}

function createBBModelCube(element, parentBone, resolution) {
    var bounds = inflatedBounds(element.from, element.to, element.inflate || 0);
    var origin = parentBone.userData.origin || [0, 0, 0];

    var geometry = buildBBModelBoxGeometry(bounds.from, bounds.to, element, resolution);

    var material = new THREE.MeshPhongMaterial({
        map: skinManager.currentTexture,
        transparent: true,
        alphaTest: ALPHA_TEST_THRESHOLD,
        side: THREE.FrontSide
    });
    if (element.inflate) {
        material.polygonOffset = true;
        material.polygonOffsetFactor = 1;
        material.polygonOffsetUnits = 1;
    }

    var mesh = new THREE.Mesh(geometry, material);
    mesh.name = element.name || element.uuid;
    mesh.position.set(-origin[0] / SCALE, -origin[1] / SCALE, -origin[2] / SCALE);
    parentBone.add(mesh);
    return mesh;
}

function createBBModelBone(group, parentBone) {
    var bone = new THREE.Group();
    bone.name = group.name;
    bone.userData.bbUuid = group.uuid;
    bone.userData.groupName = group.name;
    var groupOrigin = group.origin || [0, 0, 0];
    var parentOrigin = (parentBone && parentBone.userData && parentBone.userData.origin) ? parentBone.userData.origin : [0, 0, 0];
    bone.userData.origin = groupOrigin;
    bone.userData.isBone = true;

    bone.position.set(
        (groupOrigin[0] - parentOrigin[0]) / SCALE,
        (groupOrigin[1] - parentOrigin[1]) / SCALE,
        (groupOrigin[2] - parentOrigin[2]) / SCALE
    );

    if (group.rotation) {
        bone.rotation.order = ROTATION_ORDER;
        bone.rotation.set(
            THREE.MathUtils.degToRad(group.rotation[0]),
            THREE.MathUtils.degToRad(group.rotation[1]),
            THREE.MathUtils.degToRad(group.rotation[2])
        );
    }

    boneMap.set(group.uuid, bone);
    if (group.name === 'playerStandard' || group.name === 'playerSlim') {
        rigRoots[group.name] = bone;
    }
    return bone;
}

function buildBBModelOutliner(node, parentBone, elementMap, groupMap) {
    if (typeof node === 'string') {
        var element = elementMap.get(node);
        if (!element || !element.export || element.type === 'camera') return;
        if (parentBone) createBBModelCube(element, parentBone, bbmodelData.resolution);
        return;
    }
    var group = groupMap.get(node.uuid);
    if (!group) return;
    var bone = createBBModelBone(group, parentBone);
    parentBone.add(bone);
    if (node.children) {
        for (var ci = 0; ci < node.children.length; ci++) {
            buildBBModelOutliner(node.children[ci], bone, elementMap, groupMap);
        }
    }
}

function buildBBModelScene() {
    modelGroup.clear();
    boneMap.clear();
    rigRoots.playerStandard = null;
    rigRoots.playerSlim = null;

    var elementMap = new Map();
    var groupMap = new Map();
    (bbmodelData.elements || []).forEach(function (el) { elementMap.set(el.uuid, el); });
    (bbmodelData.groups || []).forEach(function (g) { groupMap.set(g.uuid, g); });

    var root = new THREE.Group();
    root.name = 'bbmodelRoot';
    modelGroup.add(root);
    modelRoot = root;

    for (var oi = 0; oi < bbmodelData.outliner.length; oi++) {
        var item = bbmodelData.outliner[oi];
        if (typeof item === 'string') {
            var element = elementMap.get(item);
            if (!element || element.type === 'camera') continue;
            createBBModelCube(element, root, bbmodelData.resolution);
            continue;
        }
        buildBBModelOutliner(item, root, elementMap, groupMap);
    }

    updateRigVisibility();
}

async function fetchGLBAnimations() {
    if (cachedGltfAnimations.length) return cachedGltfAnimations;
    return new Promise(function (resolve, reject) {
        gltfLoader.load(
            GLB_FILE,
            function (gltf) {
                cachedGltfAnimations = gltf.animations || [];
                resolve(cachedGltfAnimations);
            },
            undefined,
            reject
        );
    });
}

async function loadBBModelScene() {
    log('Loading BBModel scene: ' + BBMODEL_FILE);
    skinTextureFlipY = true;

    if (!bbmodelData) {
        var response = await fetch(BBMODEL_FILE);
        if (!response.ok) throw new Error('HTTP ' + response.status);
        bbmodelData = await response.json();
    }

    buildBBModelScene();
    applyCameraFromBBModel();
    parseCameraAnimations();

    await fetchGLBAnimations();
    registerAnimations(cachedGltfAnimations);
    applySkinToRig();
    applyFogFiltering();
    cacheMeshLayers();
    applyShadowSettings();
    applyEnvMap();

    modelReady = true;
    tryShowAndFade();
    log('BBModel scene ready (animations from GLB export)');
}

async function loadModel() {
    if (MODEL_MODE === 'bbmodel') {
        return loadBBModelScene();
    }
    return loadGLB(GLB_FILE);
}
