// ============================================================
// SKIN — texture loading, rig skinning, bbViewer API
// ============================================================
// Depends on: config.js, scene.js, model.js (via globals: modelRoot, rigRoots, etc.)

var skinReady = false;

function createSkinMaterial(tex) {
    return new THREE.MeshPhongMaterial({
        map: tex,
        transparent: true,
        alphaTest: ALPHA_TEST_THRESHOLD,
        side: THREE.FrontSide,
        depthWrite: true
    });
}

function applySkinToRig() {
    var tex = skinManager.currentTexture;
    if (!tex || !modelRoot) return;

    skinReady = true;

    tex.flipY = skinTextureFlipY;
    tex.needsUpdate = true;

    var rigList = [rigRoots.playerStandard, rigRoots.playerSlim].filter(Boolean);
    if (rigList.length === 0) {
        log('No rig roots found', 'warn');
        return;
    }

    rigList.forEach(function (root) {
        root.traverse(function (obj) {
            if (!obj.isMesh) return;
            var materials = Array.isArray(obj.material) ? obj.material : [obj.material];
            materials.forEach(function (mat) {
                if (!mat) return;
                mat.map = tex;
                mat.transparent = true;
                mat.alphaTest = ALPHA_TEST_THRESHOLD;
                mat.depthWrite = true;
                mat.needsUpdate = true;
                if (obj.customDepthMaterial) {
                    obj.customDepthMaterial.map = tex;
                    if (obj.customDepthMaterial.alphaMap) obj.customDepthMaterial.alphaMap = tex;
                    obj.customDepthMaterial.alphaTest = ALPHA_TEST_THRESHOLD;
                    obj.customDepthMaterial.needsUpdate = true;
                }
            });
        });
    });

    updateRigVisibility();
    scheduleAnimationStart();
    if (typeof applyEnvMap === 'function') applyEnvMap();
    tryShowAndFade();
}

// ============================================================
// SKIN MANAGER
// ============================================================
var skinManager = {
    currentTexture: null,
    isSlim: false,
    isLegacy: false,

    async fetchFromFancyMenu() {
        var bridge = window.fancymenu || window.FancyMenu;
        if (!bridge || !bridge.placeholders) return false;
        try {
            var uuid = await bridge.placeholders.get('playeruuid');
            if (!uuid || !uuid.trim()) return false;
            await this.loadFromUUID(uuid.trim());
            return !!this.currentTexture;
        } catch (e) {
            log('FancyMenu error: ' + e.message, 'error');
            return false;
        }
    },

    async loadFromUUID(uuid) {
        try {
            var response = await fetch(
                'https://sessionserver.mojang.com/session/minecraft/profile/' + uuid + '?t=' + Date.now()
            );
            if (!response.ok) throw new Error('HTTP ' + response.status);
            var profile = await response.json();
            var textureProp = null;
            for (var pi = 0; pi < (profile.properties || []).length; pi++) {
                if (profile.properties[pi].name === 'textures') { textureProp = profile.properties[pi]; break; }
            }
            if (!textureProp) throw new Error('No texture data');
            var textureData = JSON.parse(atob(textureProp.value));
            var skinUrl = textureData.textures && textureData.textures.SKIN ? textureData.textures.SKIN.url : null;
            var modelType = (textureData.textures && textureData.textures.SKIN && textureData.textures.SKIN.metadata && textureData.textures.SKIN.metadata.model) || 'standard';
            if (!skinUrl) throw new Error('No skin URL');
            this.isSlim = modelType === 'slim';
            await this.loadTextureFromUrl(skinUrl);
        } catch (e) {
            log('UUID fetch error: ' + e.message, 'error');
        }
    },

    async loadFromPlayerName(name) {
        if (!isFancyMenuContext()) {
            return this.loadDevFallbackSkin();
        }
        try {
            var response = await fetch(
                'https://api.mojang.com/users/profiles/minecraft/' + encodeURIComponent(name)
            );
            if (!response.ok) throw new Error('Player not found');
            var profile = await response.json();
            await this.loadFromUUID(profile.id);
        } catch (e) {
            log('Name lookup error: ' + e.message, 'error');
            await this.loadDevFallbackSkin();
        }
    },

    applyImageToTexture(img) {
        var canvas = img;
        this.isLegacy = img.height === 32 && img.width === 64;

        if (this.isLegacy) {
            var c = document.createElement('canvas');
            c.width = 64;
            c.height = 64;
            var ctx = c.getContext('2d');
            ctx.drawImage(img, 0, 0);
            ctx.save();
            ctx.translate(32, 48);
            ctx.scale(-1, 1);
            ctx.drawImage(img, 40, 16, 16, 16, -16, 0, 16, 16);
            ctx.restore();
            ctx.save();
            ctx.translate(16, 48);
            ctx.scale(-1, 1);
            ctx.drawImage(img, 0, 16, 16, 16, -16, 0, 16, 16);
            ctx.restore();
            canvas = c;
        }

        var texture = new THREE.CanvasTexture(canvas);
        texture.magFilter = THREE.NearestFilter;
        texture.minFilter = THREE.NearestFilter;
        texture.flipY = skinTextureFlipY;
        this.currentTexture = texture;

        var slimParam = params.get('slim');
        var wasSlim = this.isSlim;
        if (slimParam === 'true' || slimParam === '1') this.isSlim = true;
        if (slimParam === 'false' || slimParam === '0') this.isSlim = false;

        applySkinToRig();
        if (wasSlim !== this.isSlim) {
            log('Rig switched to ' + (this.isSlim ? 'slim' : 'standard'));
        }
        log('Skin applied (' + (this.isSlim ? 'slim' : 'standard') + (this.isLegacy ? ', legacy' : '') + ', flipY=' + skinTextureFlipY + ')');
    },

    async loadTextureFromDataUrl(dataUrl) {
        var img = new Image();
        await new Promise(function (res, rej) {
            img.onload = res;
            img.onerror = function () { rej(new Error('Failed to load image')); };
            img.src = dataUrl;
        });
        this.applyImageToTexture(img);
    },

    async loadDevFallbackSkin() {
        var embedded = null;
        if (bbmodelData && bbmodelData.textures) {
            for (var ti = 0; ti < bbmodelData.textures.length; ti++) {
                if (bbmodelData.textures[ti].source && bbmodelData.textures[ti].source.startsWith('data:')) {
                    embedded = bbmodelData.textures[ti];
                    break;
                }
            }
        }
        if (!embedded) {
            log('No placeholder texture available', 'warn');
            return false;
        }
        log('Using BBModel embedded placeholder skin');
        await this.loadTextureFromDataUrl(embedded.source);
        return true;
    },

    async loadTextureFromUrl(url) {
        try {
            var img = new Image();
            img.crossOrigin = 'anonymous';
            await new Promise(function (res, rej) {
                img.onload = res;
                img.onerror = function () { rej(new Error('Failed to load image')); };
                img.src = url + (url.indexOf('?') >= 0 ? '&' : '?') + 't=' + Date.now();
            });
            this.applyImageToTexture(img);
        } catch (e) {
            log('Texture error: ' + e.message, 'error');
            await this.loadDevFallbackSkin();
        }
    }
};
