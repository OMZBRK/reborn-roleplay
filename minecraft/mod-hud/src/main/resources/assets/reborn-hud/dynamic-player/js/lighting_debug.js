// ============================================================
// LIGHTING DEBUG — gizmos, panel, freecam, save
// ============================================================
// Depends on: config.js, scene.js, model.js (via globals)
// Only active when DEBUG_LIGHTING is true.

function initLightDebug() {
    if (!DEBUG_LIGHTING) return;
    buildLightGizmos();
    createLightDebugUI();
    document.addEventListener('mousedown', onLightGizmoMouseDown, true);
    document.addEventListener('mousemove', onLightGizmoMouseMove, true);
    document.addEventListener('mouseup', onLightGizmoMouseUp, true);
    log('Lighting debug enabled — drag gizmos or use sliders, click Save to download updated HTML');
}

// All code below is unreachable unless DEBUG_LIGHTING is true,
// but must be defined so initLightDebug() can reference it.
// ============================================================

if (DEBUG_LIGHTING) {

var GIZMO_COLORS = {
    ambient: 0xffcb5c,
    key: 0x1a6aea,
    fill: 0xffffff,
    rim: 0xff8844
};

var orbitControls = null;
var freecamActive = false;
var savedCameraState = null;

function toggleFreecam() {
    freecamActive = !freecamActive;
    var btn = document.getElementById('freecam-btn');

    if (freecamActive) {
        savedCameraState = {
            position: camera.position.clone(),
            rotation: camera.rotation.clone(),
            fov: camera.fov
        };

        if (!orbitControls) {
            orbitControls = new THREE.OrbitControls(camera, renderer.domElement);
            orbitControls.enableDamping = true;
            orbitControls.dampingFactor = 0.08;
            orbitControls.target.set(0, 0, 0);
        }
        orbitControls.enabled = true;
        if (btn) {
            btn.textContent = 'Exit Freecam';
            btn.style.background = '#dc2626';
        }
        log('Freecam enabled — drag to orbit, scroll to zoom');
    } else {
        if (orbitControls) orbitControls.enabled = false;
        if (savedCameraState) {
            camera.position.copy(savedCameraState.position);
            camera.rotation.copy(savedCameraState.rotation);
            camera.fov = savedCameraState.fov;
            camera.updateProjectionMatrix();
        }
        if (btn) {
            btn.textContent = 'Freecam';
            btn.style.background = '#2563eb';
        }
        log('Freecam disabled — camera restored');
    }
}

function createLightGizmo(name, light) {
    var group = new THREE.Group();
    group.name = 'gizmo_' + name;

    var color = GIZMO_COLORS[name] || 0xffffff;
    var size = name === 'ambient' ? 0.15 : 0.25;

    var geo = new THREE.OctahedronGeometry(size, 0);
    var mat = new THREE.MeshBasicMaterial({ color: color, wireframe: true });
    var mesh = new THREE.Mesh(geo, mat);
    mesh.userData.lightKey = name;
    group.add(mesh);

    var dotGeo = new THREE.SphereGeometry(size * 0.4, 8, 8);
    var dotMat = new THREE.MeshBasicMaterial({ color: color });
    var dot = new THREE.Mesh(dotGeo, dotMat);
    group.add(dot);

    if (light.isDirectionalLight) {
        var dirGeo = new THREE.ConeGeometry(0.08, 0.3, 6);
        var dirMesh = new THREE.Mesh(dirGeo, new THREE.MeshBasicMaterial({ color: color }));
        dirMesh.position.y = -size - 0.15;
        dirMesh.rotation.x = Math.PI;
        group.add(dirMesh);

        var axisLen = size + 0.6;
        var axisRadius = 0.03;
        var axisGeo = new THREE.CylinderGeometry(axisRadius, axisRadius, axisLen, 6);

        var xMat = new THREE.MeshBasicMaterial({ color: 0xff3333, transparent: true, opacity: 0.7 });
        var xAxis = new THREE.Mesh(axisGeo, xMat);
        xAxis.rotation.z = -Math.PI / 2;
        xAxis.position.x = axisLen / 2;
        xAxis.userData.axis = 'x';
        xAxis.userData.lightKey = name;
        group.add(xAxis);

        var yMat = new THREE.MeshBasicMaterial({ color: 0x33ff33, transparent: true, opacity: 0.7 });
        var yAxis = new THREE.Mesh(axisGeo, yMat);
        yAxis.position.y = axisLen / 2;
        yAxis.userData.axis = 'y';
        yAxis.userData.lightKey = name;
        group.add(yAxis);

        var zMat = new THREE.MeshBasicMaterial({ color: 0x3388ff, transparent: true, opacity: 0.7 });
        var zAxis = new THREE.Mesh(axisGeo, zMat);
        zAxis.rotation.x = Math.PI / 2;
        zAxis.position.z = axisLen / 2;
        zAxis.userData.axis = 'z';
        zAxis.userData.lightKey = name;
        group.add(zAxis);

        var tipGeo = new THREE.ConeGeometry(axisRadius * 2.5, axisRadius * 5, 6);
        var xTip = new THREE.Mesh(tipGeo, xMat.clone());
        xTip.rotation.z = -Math.PI / 2;
        xTip.position.x = axisLen + axisRadius * 2;
        xTip.userData.axis = 'x';
        xTip.userData.lightKey = name;
        xTip.userData.isTip = true;
        group.add(xTip);

        var yTip = new THREE.Mesh(tipGeo, yMat.clone());
        yTip.position.y = axisLen + axisRadius * 2;
        yTip.userData.axis = 'y';
        yTip.userData.lightKey = name;
        yTip.userData.isTip = true;
        group.add(yTip);

        var zTip = new THREE.Mesh(tipGeo, zMat.clone());
        zTip.rotation.x = Math.PI / 2;
        zTip.position.z = axisLen + axisRadius * 2;
        zTip.userData.axis = 'z';
        zTip.userData.lightKey = name;
        zTip.userData.isTip = true;
        group.add(zTip);
    }

    if (name !== 'ambient') {
        group.position.set(LIGHTING_CONFIG[name].position[0], LIGHTING_CONFIG[name].position[1], LIGHTING_CONFIG[name].position[2]);
    }

    scene.add(group);
    lightGizmos[name] = group;
    return group;
}

function buildLightGizmos() {
    var names = Object.keys(lights);
    for (var ni = 0; ni < names.length; ni++) {
        createLightGizmo(names[ni], lights[names[ni]]);
    }
}

function createLightDebugUI() {
    var panel = document.createElement('div');
    panel.id = 'light-debug-panel';
    panel.style.cssText = 'position:fixed;top:8px;right:8px;z-index:10000;font:12px/1.4 Consolas,monospace;background:rgba(0,0,0,0.85);color:#e8e8e8;padding:12px 14px;border-radius:6px;border:1px solid rgba(255,255,255,0.15);min-width:260px;max-height:90vh;overflow-y:auto;';

    var html = '<div style="font-weight:bold;margin-bottom:8px;color:#fff;font-size:13px;">Lighting Debug</div>';
    html += '<button id="freecam-btn" style="width:100%;padding:5px 0;background:#2563eb;color:#fff;border:none;border-radius:4px;cursor:pointer;font:inherit;font-size:11px;margin-bottom:8px;">Freecam</button>';

    var lightNames = ['ambient', 'key', 'fill', 'rim'];
    for (var li = 0; li < lightNames.length; li++) {
        var name = lightNames[li];
        var l = lights[name];
        var hex = '#' + GIZMO_COLORS[name].toString(16).padStart(6, '0');
        var isDir = l.isDirectionalLight;

        html += '<div style="margin-top:8px;padding-top:6px;border-top:1px solid #333;">';
        html += '<div style="display:flex;align-items:center;gap:6px;margin-bottom:4px;">';
        html += '<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:' + hex + ';"></span>';
        html += '<span style="font-weight:bold;color:#fff;">' + name + '</span></div>';

        html += '<div style="display:flex;align-items:center;gap:4px;margin-bottom:3px;">';
        html += '<span style="color:#888;font-size:10px;width:40px;">Color</span>';
        html += '<input type="color" id="lc-' + name + '" value="' + l.color.getStyle() + '" style="width:32px;height:18px;border:none;padding:0;cursor:pointer;background:transparent;"></div>';

        html += '<div style="display:flex;align-items:center;gap:4px;margin-bottom:3px;">';
        html += '<span style="color:#888;font-size:10px;width:40px;">Int</span>';
        html += '<input type="range" id="li-' + name + '" min="0" max="3" step="0.05" value="' + l.intensity + '" style="flex:1;height:14px;cursor:pointer;">';
        html += '<span id="liv-' + name + '" style="color:#aaa;font-size:10px;width:32px;text-align:right;">' + l.intensity.toFixed(2) + '</span></div>';

        if (isDir) {
            var pos = l.position;
            var axes = ['x', 'y', 'z'];
            for (var ai = 0; ai < axes.length; ai++) {
                var axis = axes[ai];
                var axisColor = axis === 'x' ? '#ff5555' : axis === 'y' ? '#55ff55' : '#5588ff';
                html += '<div style="display:flex;align-items:center;gap:4px;margin-bottom:2px;">';
                html += '<span style="color:' + axisColor + ';font-size:10px;width:40px;">' + axis.toUpperCase() + '</span>';
                html += '<input type="range" id="lp-' + name + '-' + axis + '" min="-10" max="10" step="0.1" value="' + pos[axis] + '" style="flex:1;height:14px;cursor:pointer;">';
                html += '<span id="lpv-' + name + '-' + axis + '" style="color:#aaa;font-size:10px;width:32px;text-align:right;">' + pos[axis].toFixed(1) + '</span></div>';
            }
        }

        html += '</div>';
    }

    html += '<button id="save-btn" style="margin-top:10px;width:100%;padding:6px 0;background:#2563eb;color:#fff;border:none;border-radius:4px;cursor:pointer;font:inherit;font-size:11px;">Save & Download</button>';
    html += '<button id="close-btn" style="margin-top:4px;width:100%;padding:6px 0;background:transparent;color:#888;border:1px solid #444;border-radius:4px;cursor:pointer;font:inherit;font-size:11px;">Close (no save)</button>';

    panel.innerHTML = html;
    document.body.appendChild(panel);

    document.getElementById('freecam-btn').addEventListener('click', toggleFreecam);

    for (var li = 0; li < lightNames.length; li++) {
        var name = lightNames[li];
        var l = lights[name];

        (function (lightName, lightObj) {
            document.getElementById('lc-' + lightName).addEventListener('input', function (e) {
                lightObj.color.set(e.target.value);
                LIGHTING_CONFIG[lightName].color = e.target.value;
                var gizmo = lightGizmos[lightName];
                if (gizmo) {
                    var hexVal = parseInt(e.target.value.replace('#', ''), 16);
                    gizmo.traverse(function (c) {
                        if (c.isMesh && c.material && !c.userData.isTip) c.material.color.setHex(hexVal);
                    });
                }
            });

            document.getElementById('li-' + lightName).addEventListener('input', function (e) {
                lightObj.intensity = parseFloat(e.target.value);
                document.getElementById('liv-' + lightName).textContent = lightObj.intensity.toFixed(2);
            });

            if (lightObj.isDirectionalLight) {
                var axes = ['x', 'y', 'z'];
                for (var ai = 0; ai < axes.length; ai++) {
                    (function (axisName) {
                        document.getElementById('lp-' + lightName + '-' + axisName).addEventListener('input', function (e) {
                            lightObj.position[axisName] = parseFloat(e.target.value);
                            document.getElementById('lpv-' + lightName + '-' + axisName).textContent = lightObj.position[axisName].toFixed(1);
                            var gizmo = lightGizmos[lightName];
                            if (gizmo) gizmo.position[axisName] = lightObj.position[axisName];
                        });
                    })(axes[ai]);
                }
            }
        })(name, l);
    }

    document.getElementById('save-btn').addEventListener('click', saveLightingConfig);
    document.getElementById('close-btn').addEventListener('click', disableLightDebug);
}

function saveLightingConfig() {
    var cfg = LIGHTING_CONFIG;

    // Intensity is halved at runtime by HDRI_LIGHT_BLEND (in loadHDRI).
    // Save the pre-blend value so reloading produces the same final intensity.
    var intensityFactor = HDRI_ENABLED ? (1 / (1 - HDRI_LIGHT_BLEND)) : 1;

    cfg.ambient.intensity = lights.ambient.intensity * intensityFactor;
    cfg.ambient.color = lights.ambient.color.getStyle();
    var lightNames = ['key', 'fill', 'rim'];
    for (var li = 0; li < lightNames.length; li++) {
        var name = lightNames[li];
        var l = lights[name];
        cfg[name].position = [l.position.x, l.position.y, l.position.z];
        cfg[name].intensity = l.intensity * intensityFactor;
        cfg[name].color = l.color.getStyle();
    }

    // Build compact inline JSON matching the original config style
    var cfgStr = formatLightingConfig(cfg);

    fetch('js/config.js')
        .then(function (r) { return r.text(); })
        .then(function (src) {
            var marker = 'const LIGHTING_CONFIG = ';
            var startIdx = src.indexOf(marker);
            if (startIdx === -1) { fallbackDownload(cfgStr); return; }
            var braceStart = src.indexOf('{', startIdx);
            var depth = 0, endIdx = braceStart;
            for (var i = braceStart; i < src.length; i++) {
                if (src[i] === '{') depth++;
                if (src[i] === '}') depth--;
                if (depth === 0) { endIdx = i + 1; break; }
            }
            while (endIdx < src.length && src[endIdx] === ';') endIdx++;

            var formatted = marker + cfgStr + ';';
            var newSrc = src.substring(0, startIdx) + formatted + src.substring(endIdx);

            // Set DEBUG_LIGHTING back to false so debug UI doesn't show on reload
            var debugMarker = 'DEBUG_LIGHTING = ';
            var debugIdx = newSrc.indexOf(debugMarker);
            if (debugIdx !== -1) {
                var valStart = debugIdx + debugMarker.length;
                var valEnd = valStart;
                while (valEnd < newSrc.length && /[a-zA-Z0-9]/.test(newSrc[valEnd])) valEnd++;
                newSrc = newSrc.substring(0, valStart) + 'false' + newSrc.substring(valEnd);
            }

            var blob = new Blob([newSrc], { type: 'text/javascript' });
            var url = URL.createObjectURL(blob);
            var a = document.createElement('a');
            a.href = url;
            a.download = 'config.js';
            a.click();
            URL.revokeObjectURL(url);
            log('Saved — replace config.js with the downloaded file');
            disableLightDebug();
        })
        .catch(function () { fallbackDownload(cfgStr); });
}

function formatLightingConfig(cfg) {
    var keys = Object.keys(cfg);
    var lines = ['{'];
    for (var ki = 0; ki < keys.length; ki++) {
        var k = keys[ki];
        var v = cfg[k];
        var inner = Object.keys(v).map(function (ik) {
            var iv = v[ik];
            var ivStr = typeof iv === 'string' ? '"' + iv + '"' : JSON.stringify(iv);
            return '"' + ik + '": ' + ivStr;
        }).join(', ');
        var comma = ki < keys.length - 1 ? ',' : '';
        lines.push('    "' + k + '": { ' + inner + ' }' + comma);
    }
    lines.push('}');
    return lines.join('\n');
}

function fallbackDownload(cfgStr) {
    var blob = new Blob([cfgStr], { type: 'application/json' });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = 'lighting_config.json';
    a.click();
    URL.revokeObjectURL(url);
    log('Could not fetch config.js — config downloaded as JSON. Paste the block into config.js replacing LIGHTING_CONFIG, and set DEBUG_LIGHTING = false');
}

function disableLightDebug() {
    if (freecamActive) toggleFreecam();
    var gizmoKeys = Object.keys(lightGizmos);
    for (var gi = 0; gi < gizmoKeys.length; gi++) {
        scene.remove(lightGizmos[gizmoKeys[gi]]);
    }
    var panel = document.getElementById('light-debug-panel');
    if (panel) panel.remove();
    document.removeEventListener('mousedown', onLightGizmoMouseDown);
    document.removeEventListener('mousemove', onLightGizmoMouseMove);
    document.removeEventListener('mouseup', onLightGizmoMouseUp);
}

var draggingGizmo = null;
var dragAxis = null;
var dragStartPos = new THREE.Vector3();
var dragStartMouse = new THREE.Vector2();
var raycaster = new THREE.Raycaster();

function getAxisMeshes() {
    var meshes = [];
    var gizmoKeys = Object.keys(lightGizmos);
    for (var gi = 0; gi < gizmoKeys.length; gi++) {
        lightGizmos[gizmoKeys[gi]].traverse(function (c) { if (c.isMesh && c.userData.axis) meshes.push(c); });
    }
    return meshes;
}

function getGizmoMeshes() {
    var meshes = [];
    var gizmoKeys = Object.keys(lightGizmos);
    for (var gi = 0; gi < gizmoKeys.length; gi++) {
        lightGizmos[gizmoKeys[gi]].traverse(function (c) { if (c.isMesh && !c.userData.axis) meshes.push(c); });
    }
    return meshes;
}

function onLightGizmoMouseDown(e) {
    if (e.button !== 0) return;
    var mouse = new THREE.Vector2(
        (e.clientX / window.innerWidth) * 2 - 1,
        -(e.clientY / window.innerHeight) * 2 + 1
    );
    raycaster.setFromCamera(mouse, camera);

    var axisHits = raycaster.intersectObjects(getAxisMeshes(), false);
    if (axisHits.length > 0) {
        var hit = axisHits[0];
        var key = hit.object.userData.lightKey;
        var axis = hit.object.userData.axis;
        var light = lights[key];
        if (light && light.isDirectionalLight) {
            draggingGizmo = lightGizmos[key];
            dragAxis = axis;
            dragStartPos.copy(light.position);
            dragStartMouse.set(e.clientX, e.clientY);
            e.preventDefault();
            e.stopPropagation();
            return;
        }
    }

    var gizmoHits = raycaster.intersectObjects(getGizmoMeshes(), false);
    if (gizmoHits.length > 0) {
        var hit = gizmoHits[0];
        var group = hit.object;
        while (group && !group.name.startsWith('gizmo_')) group = group.parent;
        if (group) {
            draggingGizmo = group;
            var key = group.name.replace('gizmo_', '');
            var light = lights[key];
            if (light && light.isDirectionalLight) {
                dragAxis = 'free';
                dragStartPos.copy(light.position);
                dragStartMouse.set(e.clientX, e.clientY);
                e.preventDefault();
            }
        }
    }
}

function onLightGizmoMouseMove(e) {
    if (!draggingGizmo) return;
    var key = draggingGizmo.name.replace('gizmo_', '');
    var light = lights[key];
    if (!light) return;

    var dx = (e.clientX - dragStartMouse.x) * 0.01;
    var dy = -(e.clientY - dragStartMouse.y) * 0.01;

    if (dragAxis === 'free') {
        var camRight = new THREE.Vector3();
        var camUp = new THREE.Vector3();
        camera.getWorldDirection(new THREE.Vector3());
        camRight.setFromMatrixColumn(camera.matrixWorld, 0);
        camUp.setFromMatrixColumn(camera.matrixWorld, 1);
        var move = new THREE.Vector3();
        move.addScaledVector(camRight, dx);
        move.addScaledVector(camUp, dy);
        light.position.copy(dragStartPos).add(move);
        draggingGizmo.position.copy(light.position);
    } else if (dragAxis) {
        var delta = dragAxis === 'x' ? dx : dragAxis === 'y' ? dy : -dx;
        light.position[dragAxis] = dragStartPos[dragAxis] + delta;
        draggingGizmo.position[dragAxis] = light.position[dragAxis];
    }

    updateAxisSliders(key);
}

function updateAxisSliders(name) {
    var light = lights[name];
    if (!light || !light.isDirectionalLight) return;
    var axes = ['x', 'y', 'z'];
    for (var ai = 0; ai < axes.length; ai++) {
        var slider = document.getElementById('lp-' + name + '-' + axes[ai]);
        var label = document.getElementById('lpv-' + name + '-' + axes[ai]);
        if (slider) slider.value = light.position[axes[ai]];
        if (label) label.textContent = light.position[axes[ai]].toFixed(1);
    }
}

function onLightGizmoMouseUp() {
    draggingGizmo = null;
    dragAxis = null;
}

} // end if (DEBUG_LIGHTING)
