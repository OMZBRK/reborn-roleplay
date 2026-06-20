================================================================================
  BBModel Viewer - Scene Setup & Usage Guide
================================================================================
This HTML page renders a 3D Minecraft player with HDRI lighting, fog, shadows,
pixelation, and screen-space gradient masking inside FancyMenu (or standalone).

================================================================================
  FILE STRUCTURE
================================================================================
  discoverymc/
    bbmodel_viewer.html    - Main page (load all JS, init)
    js/
      config.js            - All tunable settings, URL parameter parsing
      scene.js             - Renderer, camera, compositing, render loop, fade
      model.js             - GLB/BBModel loading, animations, fog, shadows, env map
      skin.js              - Skin texture loading, rig skinning
      lighting_debug.js    - Debug UI (gizmos, freecam, save)
    models/
      *.glb                - Geometry + animations (exported from Blockbench)
      *.bbmodel            - Metadata (camera, groups, outliner, resolution)
    textures/
      *.hdr / *.png / *.jpg - HDRI environment maps

================================================================================
  SETTING UP A SCENE
================================================================================
1. Place your files:
     models/your_model.glb       - The geometry export
     models/your_model.bbmodel   - The Blockbench project
     textures/your_hdri.hdr      - HDRI environment map

2. Edit js/config.js to point to your files:
     GLB_FILE      = 'models/your_model.glb'
     BBMODEL_FILE  = 'models/your_model.bbmodel'
     HDRI_FILE     = 'textures/your_hdri.hdr'

3. Open the HTML in a browser (or load through FancyMenu).

================================================================================
  WHY BOTH .BBMODEL AND .GLB?
================================================================================
Three.js r134 does not have a native Blockbench loader, so we use two files:

  .bbmodel (JSON)  - Provides camera position/FOV, group hierarchy, outliner
                      structure, element resolution, and embedded placeholder
                      textures. Also holds camera animation keyframes.

  .glb (binary)    - Provides the actual 3D geometry, materials, and all
                      skeletal animations (idle, walk, etc.). The GLB is
                      exported FROM Blockbench.

The BBModel is always loaded first for metadata. The GLB provides the mesh.
In bbmodel mode, the BBModel builds the scene geometry itself using the
resolution and texture UV data, while animations still come from the GLB file.

================================================================================
  ANIMATION SYSTEM
================================================================================
Animations are loaded from the .glb file's animation clips.

  - If only one clip exists, it loops forever.
  - If multiple clips exist, they play one after another (weighted random).
  - Weight suffix: append a two-digit number to the animation name in
    Blockbench (e.g. "idle_70", "wave_30") to control probability.
  - Camera animations are parsed from the .bbmodel file's animator keyframes
    (camera type). They play on top of model animations.
  - ?static=1 disables all animations entirely.

States:
  animationsStarted     - True once the first animation begins playing
  cameraAnimationsStarted - True once camera animation begins

================================================================================
  NAME SUFFIXES (object/group naming conventions)
================================================================================
These suffixes in the Blockbench outliner control rendering behavior:

  _f  - Apply distance fog (FogExp2) to this object or its group.
        Objects WITHOUT _f (player rig, foreground items) have mat.fog = false.
        Used for the screen-space gradient mask - fog objects fade to
        transparent at the bottom of the screen, revealing the HDRI skybox.
        Controls: DISTANCE_FOG_ENABLED, DISTANCE_FOG_COLOR, DISTANCE_FOG_DENSITY
                   GRADIENT_TOP_SOLID, GRADIENT_BOTTOM_TRANSPARENT

  _n  - Exclude from HDRI environment map reflections.
        Objects with _n get a neutral gray DataTexture as their envMap,
        so the HDRI does not reflect off them. Useful for eyes or other
        surfaces that should look flat regardless of the environment.
        Controls: ENV_MAP_EXCLUDE_SUFFIX

Suffixes can be on the object name directly or on any parent group name.
The system walks the parent chain to check.

================================================================================
  HDRI LIGHTING
================================================================================
The page loads a .hdr file (RGBE format) as the scene environment map.

  - Provides reflections on all meshes (via scene.environment).
  - Optionally renders as skybox background (HDRI_AS_SKYBOX).
  - Blended with manual Three.js lights via HDRI_LIGHT_BLEND:
        0.0 = full HDRI, 0.5 = half-and-half, 1.0 = manual lights only
  - Three manual lights are set up in LIGHTING_CONFIG:
        ambient - non-directional fill
        key     - main directional light (casts shadows)
        fill    - secondary directional light
        rim     - back/rim directional light
  - HDRI rotation: HDRI_ROTATION_Y rotates the map clockwise in degrees.
  - HDRI_INTENSITY controls overall exposure (toneMappingExposure).

================================================================================
  RENDER PIPELINE (multi-pass compositing)
================================================================================
The scene renders in three layers per frame:

  1. Fog pass:    Only _f objects rendered to fogTarget with fog enabled.
  2. FG pass:     Non-fog objects rendered to pixelTarget with fog disabled.
  3. Background:  modelGroup hidden, HDRI skybox rendered to screen.
  4. Compositing: fogTarget is drawn with a vertical gradient alpha mask
                  (fading to transparent at bottom), then pixelTarget is
                  drawn on top. Result is the final screen.

Pixelation: All render targets use PIXELATION_SCALE for retro/Minecraft look.
Set PIXELATION_ENABLED = false for smooth rendering.

================================================================================
  URL PARAMETERS (override config.js without editing)
================================================================================
  ?uuid=<uuid>          - Player UUID (FancyMenu replaces placeholder)
  ?player=<name>        - Dev player name (falls back to Mojang API)
  ?skinUrl=<url>        - Direct skin texture URL
  ?model=glb|bbmodel    - Which mode to use (default: glb)
  ?glb=<path>           - Custom GLB path
  ?bbmodel=<path>       - Custom BBModel path
  ?static=1             - Disable animations
  ?debug=1              - Show debug overlay (log messages on screen)
  ?debugLighting=1      - Show lighting debug panel (gizmos + save)
  ?shadows=0            - Disable shadows
  ?alphaTest=0          - Enable semitransparency (default 0.5 = hard cutout)
  ?flipY=0|1            - Override texture coordinate convention
  ?slim=0|1             - Force slim or standard arm model
  ?gradTop=<float>      - Gradient top solid point (default 0.4)
  ?gradBottom=<float>   - Gradient bottom transparent point (default 0.48)

================================================================================
  SKIN LOADING PRIORITY
================================================================================
The page tries these sources in order, stopping at the first success:

  1. URL UUID        - ?uuid parameter (FancyMenu replaces {playeruuid})
  2. Skin URL        - ?skinUrl parameter (direct link)
  3. Dev player      - ?player parameter (calls Mojang API)
  4. FancyMenu       - Polls window.fancymenu bridge for playeruuid
  5. Fallback        - BBModel embedded placeholder texture (data URL)
  6. Timeout         - Shows model with blank skin after SKIN_TIMEOUT ms

================================================================================
  LIGHTING DEBUG UI (?debugLighting=1)
================================================================================
When enabled, shows a draggable panel with:

  - Color pickers for each light (ambient, key, fill, rim)
  - Intensity sliders (0–3)
  - Position sliders (x/y/z for directional lights, range -10 to 10)
  - 3D gizmos: octahedron markers at light positions with colored
    axis arrows (red=x, green=y, blue=z) for click-and-drag editing
  - Freecam button: orbit/zoom with mouse (OrbitControls)
  - Save button: downloads a new config.js with your current settings
    (intensities are pre-multiplied to compensate for HDRI blending,
     and DEBUG_LIGHTING is set to false automatically)

================================================================================
  INTERNALS (for developers)
================================================================================
  SCALE = 16 - All Blockbench coordinates are divided by 16 to convert
               from Blockbench units to Three.js world units.

  skinTextureFlipY - Depends on mode:
      glb:     flipY = false (GLTF standard)
      bbmodel: flipY = true  (CanvasTexture drawn from Blockbench UVs)

  customDepthMaterial - Used for shadow maps on texture-alpha materials.
      Three.js r134 requires RGBADepthPacking + explicit map + alphaMap.

  fadeIn - 3-second cubic ease-out, starts after MIN_LOAD_DELAY (3s)
           from page load and requires both modelReady + skinReady.

================================================================================
  TROUBLESHOOTING
================================================================================
  "Scene is black"
    - Check file paths (GLB, BBModel, HDRI).
    - ?debug=1 to see log overlay.
    - Try ?shadows=0 in case shadow camera setup is wrong.

  "Model loading fails"
    - Ensure GLB is exported from Blockbench with animations.
    - Ensure BBModel JSON is valid.
    - Check for CORS issues if loading from file:// - use a local server.

  "Textures look wrong"
    - Try ?flipY=0 or ?flipY=1 to correct the coordinate convention.
    - Check alphaTest threshold (?alphaTest=0 for semi-transparency).

  "Shadows not appearing"
    - Key light is the only shadow-caster (ENABLE_SHADOWS must be true).
    - customDepthMaterial requires RGBADepthPacking in Three.js r134.

  "Performance issues"
    - Increase PIXELATION_SCALE (4 or 8) to render at lower resolution.
    - Disable HDRI (HDRI_ENABLED = false).
    - Reduce SHADOW_MAP_SIZE.

================================================================================
