#version 330

// Météo Reborn (mod reborn-hud) — passe plein écran par-dessus le brouillard en distance du jeu (FogRendererMeteoMixin).
// La pluie elle-même reste celle de Minecraft ; le filtre ajoute l'ambiance.
//
// Paramètres (texture MeteoClient 4x1) :
//   pixel 0 = (R type, G intensité, B/A temps bits 0-15)
//   pixel 1 = (R temps bits 16-23, G champ de vision / 180°)
//   pixel 2 = (R exposition au ciel, G/B soleil à l'écran codé sur [-0,5 ; 1,5], A visibilité du soleil)
//   pixel 3 = (R/G lacet de la caméra déroulé, 0..3600° sur 16 bits ; B/A tangage -90..90° sur 16 bits)
// Types : 1 pluie (bords mouillés, gouttes qui coulent sur l'objectif, voiles, nuit bleue, halo, grain),
//         2 sable (trois degrés : petit vent, vent moyen, grosse tempête) (nappes de poussière qui s'écoulent, grains), 3 brume (bords enveloppés, volutes, nappes au sol, faisceaux).

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

// Gouttes de pluie sur l'objectif : elles frappent l'objectif puis coulent vers le bas en accélérant, en laissant une
// trace mouillée, comme sous une vraie pluie. Hors mise au point : bord doux, la scène derrière déformée et floutée,
// petit éclat en haut, liseré clair en bas. Cellules trois fois plus hautes que larges, pour que chaque goutte ait de
// la place pour couler. Renvoie (décalage de lecture xy en uv, masque, lumière).
vec4 goutte(vec2 q, float t, float echelle, float densite, float aspect, float seed) {
    vec2 g = vec2(q.x * echelle, q.y * echelle / 3.0);
    g.x += hash(vec2(floor(g.y), seed)) * 0.5;                  // rangées décalées : pas de grille visible
    vec2 id = floor(g);
    vec2 st = fract(g) - 0.5;
    float h = hash(id + seed);
    if (h > densite) return vec4(0.0);
    float vie = fract(t * (0.05 + 0.07 * hash(id + seed + 9.1)) + hash(id + seed + 5.1));
    float impact = smoothstep(0.0, 0.02, vie);
    float fin = smoothstep(1.0, 0.92, vie);
    float descente = smoothstep(0.12, 1.0, vie);
    descente *= descente;                                        // accroche un instant, puis accélère
    float taille = 0.1 + 0.16 * hash(id + seed + 3.3);          // en largeurs de cellule
    float x = (hash(id + seed + 1.3) - 0.5) * 0.5 + sin(vie * 17.0 + h * 9.0) * 0.015 * descente;
    float y = 0.42 - descente * 0.9;                             // en hauteurs de cellule (uv : y = 0 en bas)
    vec2 p = vec2(st.x - x, (st.y - y) * 3.0);
    p.y /= 1.0 + descente * 0.45;                                // s'étire en coulant
    float d = length(p) / taille;
    float m = smoothstep(1.0, 0.35, d) * impact * fin;
    float trace = 0.0;
    if (st.y > y) {
        float k = (st.y - y) * 3.0;
        trace = smoothstep(taille * 0.4, 0.0, abs(st.x - x)) * smoothstep(1.4, 0.0, k) * 0.4 * descente * fin;
    }
    vec2 dec = -p * max(0.0, 1.0 - d) * 1.8 / echelle * m;
    dec.x /= aspect;
    float eclat = smoothstep(0.45, 0.0, length(p / taille - vec2(-0.28, 0.32))) * m;
    float lisere = smoothstep(0.4, 0.85, d) * smoothstep(0.1, -0.6, p.y / taille) * m;
    return vec4(dec, max(m, trace), eclat * 0.8 + lisere * 0.35);
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
        // ici le rendu « caméra sous la pluie » : bords mouillés, gouttes qui coulent, voiles, nuit bleue, halo, grain
        float dehors = p2.r;
        // eau qui ruisselle sur les bords de l'objectif : la scène y est floue et ondule
        float ruisselle = fbm3(vec2(q.x * 16.0, q.y * 1.2 + t * 0.9));
        float mouille = bordure * I * (0.35 + 0.65 * dehors);
        vec2 u = uv + vec2((ruisselle - 0.5) * 0.01, 0.0) * mouille;
        vec4 g = goutte(q, t, 9.0, 0.14 * I * dehors, aspect, 0.0);
        vec4 g2 = goutte(q + 0.37, t * 1.15, 16.0, 0.16 * I * dehors, aspect, 19.0);
        vec4 g3 = goutte(q + 0.71, t * 0.9, 28.0, 0.14 * I * dehors, aspect, 41.0);
        if (g2.z > g.z) g = g2;
        if (g3.z > g.z) g = g3;
        vec2 cd = uv - 0.5;
        vec2 ab = cd * 0.005 * I * dot(cd, cd) * 4.0;                                   // aberration vers les bords
        col = vec3(texture(InSampler, u + ab).r, texture(InSampler, u).g, texture(InSampler, u - ab).b);
        col = mix(col, flou5(u, px, 3.0), mouille * 0.85);                              // bords flous, mouillés
        if (g.z > 0.0) col = mix(col, flou5(uv + g.xy, px, 2.5) * 1.06, g.z * 0.9);    // la scène dans la goutte

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
        col += vec3(0.85, 0.9, 1.0) * g.w * 0.3 * I;                                   // éclat / liseré des gouttes
        col += (hash(uv * InSize + fract(t * 13.0) * 100.0) - 0.5) * 0.035 * I;        // grain de film
        vec2 d = uv - 0.5;
        col *= 1.0 - 0.45 * I * dot(d, d);
    } else if (type == 2) {
        // ---------------- sable (pays du Vent), trois degrés selon l'intensité :
        //   petit vent (~25 %) : du sable qui court au ras du sol, lumière chaude, l'horizon reste visible
        //   vent moyen (~55 %) : voile de poussière, nappes qui s'écoulent
        //   grosse tempête (~90 %) : mur de sable, nappes épaisses, rafales, grains, bords de l'écran envahis
        float moyen = smoothstep(0.25, 0.55, I);
        float tempete = smoothstep(0.6, 0.9, I);
        float vit = 0.5 + I;
        vec2 w = vec2(fbm(qm * 1.1 + vec2(-t * 0.3 * vit, 0.0)), fbm(qm * 1.1 + vec2(5.2, -t * 0.17)));
        float nappe = fbm(qm * vec2(1.3, 2.8) + w * 1.8 + vec2(-t * 0.8 * vit, 0.0));
        float rafale = fbm(qm * vec2(3.0, 9.0) + w * 1.0 + vec2(-t * 2.1 * vit, 0.0));
        float rase = fbm3(vec2(qm.x * 2.0 - t * (0.6 + 1.6 * I), qm.y * 18.0 + w.y * 3.0));
        float sol = smoothstep(1.1, 0.05, yv);
        float bas = smoothstep(0.42, 0.0, yv);
        float dens = I * 0.55 * bas * smoothstep(0.5, 0.8, rase)
                   + moyen * (0.08 + 0.3 * sol) * smoothstep(0.32, 0.68, nappe)
                   + tempete * (0.12 + 0.2 * sol) * smoothstep(0.3, 0.65, nappe)
                   + tempete * 0.25 * smoothstep(0.45, 0.75, rafale) * (0.4 + 0.6 * sol);
        vec3 sable = mix(vec3(0.62, 0.47, 0.3), vec3(0.93, 0.8, 0.6), clamp(nappe * 1.3 - 0.2, 0.0, 1.0));
        col *= mix(vec3(1.0), vec3(1.05, 0.95, 0.8), I * 0.6);                     // lumière chaude
        col = mix(col, sable, clamp(dens, 0.0, 0.92));
        // grains qui filent (courts traits flous, poussés par le vent)
        vec2 gg = vec2(qm.x * 26.0 - t * 48.0 * vit, qm.y * 220.0 + sin(qm.x * 4.0 + t) * 2.0);
        vec2 gid = floor(gg);
        float gx = fract(gg.x), gy = fract(gg.y) - 0.5;
        float grain = step(0.97, hash(gid)) * smoothstep(0.0, 0.7, gx) * smoothstep(1.0, 0.75, gx) * smoothstep(0.5, 0.1, abs(gy));
        col = mix(col, vec3(1.0, 0.9, 0.72), grain * 0.3 * (0.4 * moyen + 0.6 * tempete) * (0.5 + 0.5 * sol));
        // grosse tempête : le sable fouette la caméra, les bords de l'écran en sont envahis
        col = mix(col, sable * 0.92, bordure * tempete * 0.55 * (0.7 + 0.3 * rafale));
        vec2 d = uv - 0.5;
        col *= 1.0 - 0.35 * I * dot(d, d);
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
