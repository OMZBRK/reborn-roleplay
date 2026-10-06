#version 330

// Météo Reborn (mod reborn-hud) — passe plein écran par-dessus le brouillard en distance du jeu (FogRendererMeteoMixin).
// La pluie elle-même reste celle de Minecraft ; le filtre ajoute l'ambiance.
//
// Paramètres (texture MeteoClient 4x1) :
//   pixel 0 = (R type, G intensité, B/A temps bits 0-15)
//   pixel 1 = (R temps bits 16-23)
//   pixel 2 = (R exposition au ciel, G/B soleil à l'écran codé sur [-0,5 ; 1,5], A visibilité du soleil)
// Types : 1 pluie (gouttes floues sur l'objectif, voiles de pluie, nuit bleue, halo, grain), 2 sable (nappes de poussière qui s'écoulent, grains), 3 brume (volutes, nappes au sol, faisceaux de lumière).

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

// Gouttes sur l'objectif, hors mise au point comme sur une vraie caméra : bord doux, la scène derrière déformée et
// floutée, petit éclat en haut, liseré clair en bas. Elles arrivent par un petit impact puis s'évaporent en
// rétrécissant ; avec glisse > 0, certaines (les plus grosses) coulent vers le bas en laissant une trace.
// Renvoie (décalage de lecture xy en uv, masque, lumière).
vec4 goutte(vec2 q, float t, float echelle, float densite, float aspect, float seed, float glisse) {
    vec2 g = q * echelle;
    g.x += hash(vec2(floor(g.y), seed)) * 0.5;                 // rangées décalées : pas de grille visible
    vec2 id = floor(g);
    vec2 st = fract(g) - 0.5;
    float h = hash(id + seed);
    if (h > densite) return vec4(0.0);
    float vie = fract(t * (0.035 + 0.03 * hash(id + seed + 9.1)) + hash(id + seed + 5.1));
    float impact = smoothstep(0.0, 0.025, vie);
    float taille = (0.07 + 0.16 * hash(id + seed + 3.3)) * mix(1.0, 0.45, smoothstep(0.55, 1.0, vie));
    vec2 c = (vec2(hash(id + seed + 1.3), hash(id + seed + 2.7)) - 0.5) * 0.45;
    float coule = glisse * step(0.55, hash(id + seed + 7.7)) * smoothstep(0.25, 1.0, vie);
    c.y -= coule * coule * 0.8;
    vec2 p = st - c;
    p.y /= 1.0 + coule * 0.35;                                  // s'étire un peu en coulant
    float d = length(p) / taille;
    float m = smoothstep(1.0, 0.35, d) * impact * smoothstep(1.0, 0.85, vie);
    // trace mouillée au-dessus d'une goutte qui coule
    float trace = 0.0;
    if (coule > 0.01 && st.y > c.y) {
        float k = st.y - c.y;
        trace = smoothstep(taille * 0.35, 0.0, abs(st.x - c.x)) * smoothstep(0.6, 0.0, k) * 0.35 * coule;
    }
    vec2 dec = -p * (1.0 - d) * 1.8 / echelle * m;
    dec.x /= aspect;
    float eclat = smoothstep(0.45, 0.0, length(p / taille - vec2(-0.28, 0.32))) * m;
    float lisere = smoothstep(0.4, 0.85, d) * smoothstep(0.1, -0.6, p.y / taille) * m;
    return vec4(dec, max(m, trace), eclat * 0.8 + lisere * 0.35);
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

    if (type == 1) {
        // ---------------- pluie (pays de la Pluie) : la pluie elle-même est celle de Minecraft ; ici, un rendu de
        // « film sous la pluie » : gouttes floues sur l'objectif, aberration chromatique, voiles de pluie, nuit bleue,
        // halo des lumières, grain
        float dehors = p2.r;
        vec4 g = goutte(q, t, 9.0, 0.10 * I * dehors, aspect, 0.0, 1.0);
        vec4 g2 = goutte(q + 0.37, t * 1.2, 17.0, 0.12 * I * dehors, aspect, 19.0, 0.0);
        vec4 g3 = goutte(q + 0.71, t * 0.9, 31.0, 0.10 * I * dehors, aspect, 41.0, 0.0);
        if (g2.z > g.z) g = g2;
        if (g3.z > g.z) g = g3;
        vec2 cd = uv - 0.5;
        vec2 ab = cd * 0.005 * I * dot(cd, cd) * 4.0;                                   // aberration vers les bords
        col = vec3(texture(InSampler, uv + ab).r, texture(InSampler, uv).g, texture(InSampler, uv - ab).b);
        // dans la goutte : la scène derrière, déformée et floutée (hors mise au point)
        if (g.z > 0.0) {
            vec2 u2 = uv + g.xy;
            vec3 flou = texture(InSampler, u2).rgb * 0.36;
            flou += texture(InSampler, u2 + vec2(2.5, 0.0) * px).rgb * 0.16;
            flou += texture(InSampler, u2 - vec2(2.5, 0.0) * px).rgb * 0.16;
            flou += texture(InSampler, u2 + vec2(0.0, 2.5) * px).rgb * 0.16;
            flou += texture(InSampler, u2 - vec2(0.0, 2.5) * px).rgb * 0.16;
            col = mix(col, flou * 1.06, g.z * 0.9);
        }

        // voiles de pluie qui descendent et dérivent avec le vent, bruine au ras du sol
        vec2 w = vec2(fbm(q * 0.9 + vec2(-t * 0.2, t * 0.3)), fbm(q * 0.9 + vec2(3.7, t * 0.25)));
        float voile = fbm(vec2(q.x * 2.2 + q.y * 0.35, q.y * 0.9) + w * 1.4 + vec2(-t * 0.25, t * 1.1));
        float bruine = fbm3(vec2(q.x * 1.5 - t * 0.3, q.y * 6.0 + t * 0.6));
        float sol = smoothstep(0.9, 0.0, uv.y);
        float dens = I * 0.04
                   + I * (0.1 + 0.14 * sol) * smoothstep(0.4, 0.75, voile)
                   + I * 0.1 * sol * smoothstep(0.5, 0.8, bruine);
        col = mix(col, vec3(0.5, 0.58, 0.72), clamp(dens, 0.0, 0.4));

        // étalonnage : nuit bleue, noirs bleutés ; les lumières (lanternes, fenêtres) gardent leur chaleur
        float l = lum(col);
        vec3 froid = vec3(l) * vec3(0.62, 0.78, 1.15) + vec3(0.01, 0.02, 0.06);
        col = mix(col, froid, 0.55 * I * (1.0 - smoothstep(0.55, 0.9, l)));
        col *= mix(1.0, 0.82, I);
        col += halo(uv, px) * vec3(1.0, 0.85, 0.6) * 0.6 * I;                          // halo des lumières
        col += vec3(0.85, 0.9, 1.0) * g.w * 0.3 * I;                                   // éclat / liseré des gouttes
        col += (hash(uv * InSize + fract(t * 13.0) * 100.0) - 0.5) * 0.035 * I;        // grain de film
        vec2 d = uv - 0.5;
        col *= 1.0 - 0.45 * I * dot(d, d);
    } else if (type == 2) {
        // ---------------- tempête de sable (pays du Vent) : nappes déformées qui s'écoulent avec le vent
        vec2 w = vec2(fbm(q * 1.1 + vec2(-t * 0.3, 0.0)), fbm(q * 1.1 + vec2(5.2, -t * 0.17)));
        float nappe = fbm(q * vec2(1.3, 2.8) + w * 1.8 + vec2(-t * 0.8, 0.0));
        float rafale = fbm(q * vec2(3.0, 9.0) + w * 1.0 + vec2(-t * 2.1, 0.0));
        float sol = smoothstep(1.1, 0.05, uv.y);
        float dens = I * 0.18
                   + I * (0.3 + 0.45 * sol) * smoothstep(0.32, 0.68, nappe)
                   + I * 0.25 * smoothstep(0.45, 0.75, rafale) * (0.4 + 0.6 * sol);
        vec3 sable = mix(vec3(0.62, 0.47, 0.3), vec3(0.93, 0.8, 0.6), clamp(nappe * 1.3 - 0.2, 0.0, 1.0));
        col *= mix(vec3(1.0), vec3(1.05, 0.95, 0.8), I * 0.6);                     // lumière chaude
        col = mix(col, sable, clamp(dens, 0.0, 0.9));
        // grains qui filent (courts traits flous, poussés par le vent)
        vec2 gg = vec2(q.x * 26.0 - t * 48.0, q.y * 220.0 + sin(q.x * 4.0 + t) * 2.0);
        vec2 gid = floor(gg);
        float gx = fract(gg.x), gy = fract(gg.y) - 0.5;
        float grain = step(0.97, hash(gid)) * smoothstep(0.0, 0.7, gx) * smoothstep(1.0, 0.75, gx) * smoothstep(0.5, 0.1, abs(gy));
        col = mix(col, vec3(1.0, 0.9, 0.72), grain * 0.3 * I * (0.5 + 0.5 * sol));
        vec2 d = uv - 0.5;
        col *= 1.0 - 0.35 * I * dot(d, d);
    } else if (type == 3) {
        // ---------------- brume (légère → dense de fou) : volutes, nappes au ras du sol, faisceaux de lumière
        float v1 = fbm(q * vec2(0.9, 1.8) + vec2(t * 0.025, t * 0.006));
        float v2 = fbm(q * vec2(2.0, 3.6) + vec2(-t * 0.04, 0.0) + v1 * 1.3);
        float sol = smoothstep(0.75, 0.0, uv.y);
        vec3 blanc = vec3(0.84, 0.86, 0.88);
        // voile général : s'épaissit vite vers 100 % (le brouillard du jeu fait déjà la distance)
        col = mix(col, blanc, I * I * 0.2);
        // volutes et nappes rampantes près du sol
        float nappe = fbm3(vec2(q.x * 1.2 + t * 0.05, q.y * 5.0 + v1 * 1.5));
        float dens = I * (0.1 + 0.4 * sol) * smoothstep(0.4, 0.78, v2 * 0.6 + v1 * 0.6)
                   + I * 0.3 * sol * smoothstep(0.45, 0.8, nappe);
        col = mix(col, blanc, clamp(dens, 0.0, 0.85));
        // faisceaux de lumière dans la brume, halo des sources claires
        float ray = rayons(uv, soleil, aspect) * soleilVis;
        col += vec3(1.0, 0.95, 0.85) * ray * 0.45 * I;
        col += halo(uv, px) * vec3(0.95, 0.95, 1.0) * 0.4 * I;
        col = mix(col, vec3(lum(col)), 0.25 * I);
    }

    fragColor = vec4(col, 1.0);
}
