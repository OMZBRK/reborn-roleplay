#version 330

// Météo Reborn (mod reborn-hud) — passe plein écran par-dessus le brouillard en distance du jeu (FogRendererMeteoMixin).
// La pluie elle-même reste celle de Minecraft ; le filtre ajoute l'ambiance.
//
// Paramètres (texture MeteoClient 4x1) :
//   pixel 0 = (R type, G intensité, B/A temps bits 0-15)
//   pixel 1 = (R temps bits 16-23, G champ de vision / 180°)
//   pixel 2 = (R exposition au ciel, G/B soleil à l'écran codé sur [-0,5 ; 1,5], A visibilité du soleil)
//   pixel 3 = (R/G lacet de la caméra déroulé, 0..3600° sur 16 bits ; B/A tangage -90..90° sur 16 bits)
// Types : 1 pluie (bords mouillés, voiles, nuit bleue, halo, grain), 2 sable (lumière chaude ; le volume est en 3D
//         dans le monde), 3 brume (bords enveloppés, volutes, nappes au sol, faisceaux ; nappes 3D en plus).

uniform sampler2D InSampler;
uniform sampler2D ParamsSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

// (hash12 de Dave Hoskins : pas de motif répété sur les coordonnées entières)
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(11.7, 5.3);
        a *= 0.5;
    }
    return v;
}

float fbm3(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(11.7, 5.3);
        a *= 0.5;
    }
    return v / 0.875;
}

float lum(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

// ------------------------------------------------------------------ effets communs

// Rayons de lumière (« god rays ») : on remonte vers le soleil en accumulant la lumière du ciel visible ; les arbres,
// le relief et les personnages découpent des faisceaux dans la brume.
float rayons(vec2 uv, vec2 soleil, float aspect) {
    vec2 pas = (soleil - uv) / 28.0;
    vec2 p = uv + pas * hash(uv * InSize);                   // départ décalé : pas d'escalier visible
    float acc = 0.0, poids = 1.0;
    for (int k = 0; k < 28; k++) {
        float l = lum(texture(InSampler, p).rgb);
        acc += smoothstep(0.62, 0.95, l) * poids;
        poids *= 0.95;
        p += pas;
    }
    float d = length((uv - soleil) * vec2(aspect, 1.0));
    return acc / 14.0 * exp(-d * 1.1);
}

// Halo : les zones claires débordent doucement sur leur voisinage (comme une optique dans l'humidité).
vec3 halo(vec2 uv, vec2 px) {
    vec3 acc = vec3(0.0);
    for (int k = 0; k < 10; k++) {
        float a = float(k) * 2.39996;
        float r = 3.0 + 9.0 * fract(float(k) * 0.618);
        vec3 c = texture(InSampler, uv + vec2(cos(a), sin(a)) * r * px).rgb;
        acc += max(c - 0.55, 0.0);
    }
    return acc / 10.0;
}

// Flou doux (5 lectures) autour d'un point ; rayon en pixels.
vec3 flou5(vec2 u, vec2 px, float r) {
    return texture(InSampler, u).rgb * 0.36
         + (texture(InSampler, u + vec2(r, 0.0) * px).rgb + texture(InSampler, u - vec2(r, 0.0) * px).rgb
          + texture(InSampler, u + vec2(0.0, r) * px).rgb + texture(InSampler, u - vec2(0.0, r) * px).rgb) * 0.16;
}

void main() {
    vec4 p0 = texelFetch(ParamsSampler, ivec2(0, 0), 0);
    vec4 p1 = texelFetch(ParamsSampler, ivec2(1, 0), 0);
    vec4 p2 = texelFetch(ParamsSampler, ivec2(2, 0), 0);
    int type = int(round(p0.r * 255.0));
    float I = p0.g;
    float t = (round(p0.b * 255.0) + round(p0.a * 255.0) * 256.0 + round(p1.r * 255.0) * 65536.0) / 60.0;
    vec2 soleil = p2.gb * 2.0 - 0.5;
    float soleilVis = p2.a;

    vec2 uv = texCoord;
    float aspect = InSize.x / InSize.y;
    vec2 q = uv * vec2(aspect, 1.0);
    vec2 px = 1.0 / InSize;
    vec3 col = texture(InSampler, uv).rgb;
    // caméra (pixel 3 : lacet et tangage, pixel 1 G : champ de vision) — les nappes, voiles et volutes sont accrochés au
    // monde : en tournant la tête, ils défilent comme le décor ; la hauteur « sol » suit l'horizon (yv)
    vec4 p3 = texelFetch(ParamsSampler, ivec2(3, 0), 0);
    float fov = radians(max(30.0, p1.g * 180.0));
    float lacet = radians((round(p3.r * 255.0) * 256.0 + round(p3.g * 255.0)) / 65535.0 * 3600.0);
    float tangage = radians((round(p3.b * 255.0) * 256.0 + round(p3.a * 255.0)) / 65535.0 * 180.0 - 90.0);
    vec2 qm = q + vec2(lacet, -tangage) / fov;
    float yv = clamp(uv.y - tangage / fov, -1.0, 2.0);
    // bords de l'écran : 0 au centre, 1 vers les coins
    float bordure = smoothstep(0.28, 0.85, length((uv - 0.5) * vec2(aspect * 0.75, 1.0)));

    if (type == 1) {
        // ---------------- pluie (pays de la Pluie) : la pluie elle-même est celle de Minecraft (texture plus pâle) ;
        // ici le rendu « caméra sous la pluie » : bords mouillés, voiles, nuit bleue, halo, grain
        float dehors = p2.r;
        // eau qui ruisselle sur les bords de l'objectif : la scène y est floue et ondule
        float ruisselle = fbm3(vec2(q.x * 16.0, q.y * 1.2 + t * 0.9));
        float mouille = bordure * I * (0.35 + 0.65 * dehors);
        vec2 u = uv + vec2((ruisselle - 0.5) * 0.01, 0.0) * mouille;
        vec2 cd = uv - 0.5;
        vec2 ab = cd * 0.005 * I * dot(cd, cd) * 4.0;                                   // aberration vers les bords
        col = vec3(texture(InSampler, u + ab).r, texture(InSampler, u).g, texture(InSampler, u - ab).b);
        col = mix(col, flou5(u, px, 3.0), mouille * 0.85);                              // bords flous, mouillés

        // voiles de pluie qui descendent et dérivent avec le vent, bruine au ras du sol
        vec2 w = vec2(fbm(qm * 0.9 + vec2(-t * 0.2, t * 0.3)), fbm(qm * 0.9 + vec2(3.7, t * 0.25)));
        float voile = fbm(vec2(qm.x * 2.2 + qm.y * 0.35, qm.y * 0.9) + w * 1.4 + vec2(-t * 0.25, t * 1.1));
        float bruine = fbm3(vec2(qm.x * 1.5 - t * 0.3, qm.y * 6.0 + t * 0.6));
        float sol = smoothstep(0.9, 0.0, yv);
        float dens = I * 0.04
                   + I * (0.1 + 0.14 * sol) * smoothstep(0.4, 0.75, voile)
                   + I * 0.1 * sol * smoothstep(0.5, 0.8, bruine);
        col = mix(col, vec3(0.5, 0.58, 0.72), clamp(dens, 0.0, 0.4));

        // étalonnage : nuit bleue, noirs bleutés ; les lumières (lanternes, fenêtres) gardent leur chaleur
        float l = lum(col);
        vec3 froid = vec3(l) * vec3(0.62, 0.78, 1.15) + vec3(0.01, 0.02, 0.06);
        col = mix(col, froid, 0.55 * I * (1.0 - smoothstep(0.55, 0.9, l)));
        col *= mix(1.0, 0.82, I);
        col = mix(col, col * vec3(0.78, 0.9, 1.18) + vec3(0.01, 0.02, 0.05), mouille * 0.6);   // bords plus bleus
        col += halo(uv, px) * vec3(1.0, 0.85, 0.6) * 0.6 * I;                          // halo des lumières
        col += (hash(uv * InSize + fract(t * 13.0) * 100.0) - 0.5) * 0.035 * I;        // grain de film
        vec2 d = uv - 0.5;
        col *= 1.0 - 0.45 * I * dot(d, d);
    } else if (type == 2) {
        // ---------------- sable (pays du Vent) : le volume (nuages de poussière, grains) est en 3D dans le monde
        // (MeteoClient) et le mur de sable dans le brouillard du jeu ; ici seulement la lumière chaude et terne et un
        // léger voile uniforme, plus marqués avec l'intensité (petit vent, vent moyen, grosse tempête)
        col *= mix(vec3(1.0), vec3(1.05, 0.95, 0.8), I * 0.6);
        col = mix(col, vec3(lum(col)) * vec3(1.1, 0.96, 0.78), 0.25 * I);
        col = mix(col, vec3(0.86, 0.72, 0.52), 0.12 * I * I);
        vec2 d = uv - 0.5;
        col *= 1.0 - 0.3 * I * dot(d, d);
    } else if (type == 3) {
        // ---------------- brume (légère → dense de fou) : bords enveloppés, volutes, nappes au sol, faisceaux
        // bords de l'écran : la brume s'y épaissit et y adoucit tout, avec des volutes qui dérivent lentement
        float vb = fbm(q * 1.6 + vec2(t * 0.03, -t * 0.01));
        float enveloppe = bordure * I * (0.55 + 0.45 * smoothstep(0.3, 0.7, vb));
        col = mix(col, flou5(uv, px, 4.0 + 4.0 * I), enveloppe * 0.8);
        float v1 = fbm(qm * vec2(0.9, 1.8) + vec2(t * 0.025, t * 0.006));
        float v2 = fbm(qm * vec2(2.0, 3.6) + vec2(-t * 0.04, 0.0) + v1 * 1.3);
        float sol = smoothstep(0.75, 0.0, yv);
        vec3 blanc = vec3(0.84, 0.86, 0.88);
        // voile général : s'épaissit vite vers 100 % (le brouillard du jeu fait déjà la distance)
        col = mix(col, blanc, I * I * 0.2);
        // volutes et nappes rampantes près du sol
        float nappe = fbm3(vec2(qm.x * 1.2 + t * 0.05, qm.y * 5.0 + v1 * 1.5));
        float dens = I * (0.1 + 0.4 * sol) * smoothstep(0.4, 0.78, v2 * 0.6 + v1 * 0.6)
                   + I * 0.3 * sol * smoothstep(0.45, 0.8, nappe);
        col = mix(col, blanc, clamp(dens, 0.0, 0.85));
        col = mix(col, blanc, enveloppe * 0.5);                                         // bords plus blancs
        // faisceaux de lumière dans la brume, halo des sources claires
        float ray = rayons(uv, soleil, aspect) * soleilVis;
        col += vec3(1.0, 0.95, 0.85) * ray * 0.45 * I;
        col += halo(uv, px) * vec3(0.95, 0.95, 1.0) * 0.4 * I;
        col = mix(col, vec3(lum(col)), 0.25 * I);
    }

    fragColor = vec4(col, 1.0);
}
