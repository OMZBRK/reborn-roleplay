#version 330

#moj_import <reborn-hud:byakugan.glsl>

// Byakugan, passe 2 sur 2 (mod reborn-hud, lot 5 du Game Plan n° 17, étape B ; 8e tour, 14/11 : « l'effet d'encre » de la
// référence, les traits seulement en fin de volume). La passe 1 (byakugan_traits) a tiré le trait de la profondeur seule.
// Ici, en pleine résolution :
//   - le trait, lu en tente 3 × 3 (quatre lectures bilinéaires) puis resserré : net, anticrénelé, ~2 px en 1080p ; blanc à
//     peine froid, posé en dernier (il cerne aussi les personnes et le porteur), bordé d'un liseré sombre très doux (deux
//     lectures à 2,5 px en diagonale) ; sur le papier (fond pâle), il passe à l'encre sombre, comme un trait de plume ; il arrive avant la couleur à l'entrée et
//     part avant elle à la sortie ;
//   - le papier et l'encre : le jour, le monde monte en gris pâle (un sol ou un mur éclairé vers 0,8), les feuillages
//     (pleins pendant la vision), les troncs et l'ombre profonde restent sombres : masses sombres cernées de blanc sur un
//     sol clair ; le ciel, gris moyen (0,46 à 0,66), est PLUS SOMBRE que le sol ; les nuages sont du ciel ;
//   - le lavis : avec la distance, tout pâlit et s'unit (brume claire), le trait s'éteint (passe 1) ; le bord du monde
//     (95,5 à 100 % de la fin du brouillard du jeu, en distance cylindrique comme lui) prend le ton que le ciel donne à
//     l'horizon (couleur du brouillard, pixel 18) ; profondeur aussi pour la lueur du proche la nuit ;
//   - les boucles d'encre : quatre grands anneaux au pinceau, fixés à l'écran, qui s'écartent lentement et assombrissent
//     un peu ce qu'ils couvrent ; jamais au centre, jamais sur le ciel ; calculés, sans texture ;
//   - personnes vues (couleurs-clés du mod) : corps cyan pâle, réseau cyan vif, tenketsu presque blancs, cernés de blanc
//     par l'encre comme le reste ; derrière un mur, le contour vanilla (clé du corps) devient un liseré cyan pâle, sauf
//     posé sur le porteur (troisième personne) ou la main : le décor voisin y revient.
// Lectures par pixel : couleur 1, profondeur 1, trait 6 (+ 4 couleurs sur un contour du porteur) ; les paramètres arrivent
// du sommet (post/byakugan.vsh, byakugan.glsl). Aucune allocation.

uniform sampler2D InSampler;
uniform sampler2D TraitsSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;
in vec3 rayon;
flat in vec4 etat;
flat in vec4 reglage;
flat in vec4 monde;
flat in vec3 horizon;
flat in vec4 boucleRayon;
flat in vec4 boucleForce;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 TraitsSize;
};

out vec4 fragColor;

const vec3 LUM = vec3(0.2126, 0.7152, 0.0722);
// part de couleur gardée, dominante froide
const float COULEUR = 0.08;
const vec3 FROID = vec3(-0.010, 0.0, 0.026);
const vec3 ENCRE = vec3(0.96, 0.975, 1.0);
const vec3 ENCRE_SOMBRE = vec3(0.24, 0.25, 0.29);

// Couleurs-clés du mod, vert NUL, le bleu dit la classe : corps et contour FF00FF, tenketsu FF00A6, réseau FF0066.
// Réseau et tenketsu (toujours purs, textures du mod) : vert sous 5 à 12 % du rouge, aucune matière ni aucun vêtement
// courant n'y entre (rouge, rose, carmin : vert ≥ 15 %). Corps et contour : lus par décomposition (couleur = a·clé +
// (1 − a)·gris, a = R − V), pour tenir sur le bord flou du contour vanilla derrière un mur, avec un vert sous la moitié du
// rouge (un cheveu ou un tissu violet, le purpur restent ce qu'ils sont) ; seul un magenta franc du décor (béton magenta)
// peut y entrer, teinté de cyan pâle. L'ombrage des faces garde les rapports.
// Retour : (corps, réseau, tenketsu), 0 à 1.
vec3 cles(vec3 c) {
    float r = max(c.r, 1e-4);
    float pur = (1.0 - smoothstep(0.05, 0.12, c.g / r)) * smoothstep(0.04, 0.08, c.r);
    float q = c.b / r;
    float noeud = smoothstep(0.52, 0.57, q) * (1.0 - smoothstep(0.76, 0.81, q)) * pur;
    float ligne = smoothstep(0.24, 0.29, q) * (1.0 - smoothstep(0.47, 0.52, q)) * pur;
    float d = c.r - c.g;
    float qd = (c.b - c.g) / max(d, 1e-4);
    float corps = smoothstep(0.82, 0.90, qd) * (1.0 - smoothstep(1.10, 1.18, qd)) * smoothstep(0.03, 0.22, d)
        * (1.0 - smoothstep(0.40, 0.55, c.g / r));
    return vec3(corps, ligne, noeud);
}

// ciel et nuages : le jour, gris moyen froid, plus sombre que le sol (bleu du ciel vers 0,5, nuages vers 0,66, jamais sous
// 0,46) ; la nuit (s = 1), sombre
vec3 tonCiel(vec3 c, float y, float s) {
    float yc = clamp(y, 0.0, 1.0);
    float tn = 0.05 + 0.95 * pow(yc, 0.9);
    float tj = clamp(1.04 * pow(yc, 1.6), 0.46, 0.52 + 0.14 * smoothstep(0.635, 1.0, yc));
    return vec3(mix(tj, tn, s)) + 0.12 * (c - y) + FROID;
}

// boucles d'encre : quatre grands anneaux tracés au pinceau (plus épais d'un côté), centrés près des bords gauche et droit,
// qui grandissent lentement puis s'effacent (rayon et présence de chacun calculés au sommet, byakugan.vsh) ; q = point de
// l'écran (centre à l'origine, hauteur 1). Retour : couverture, 0 à 1.
const vec2 BOUCLE_CENTRE[4] = vec2[4](vec2(-0.98, -0.22), vec2(-0.885, 0.362), vec2(0.841, -0.495), vec2(1.091, 0.433));
const vec2 BOUCLE_APPUI[4] = vec2[4](vec2(1.0, 0.0), vec2(-0.323, 0.946), vec2(-0.791, -0.612), vec2(0.835, -0.551));

float boucles(vec2 q) {
    float m = 0.0;
    for (int i = 0; i < 4; i++) {
        vec2 dq = q - BOUCLE_CENTRE[i];
        float l = length(dq);
        float w = 0.011 * (1.0 + 0.45 * dot(dq, BOUCLE_APPUI[i]) / max(l, 1.0e-4));
        m = max(m, (1.0 - smoothstep(0.3 * w, w, abs(l - boucleRayon[i]))) * boucleForce[i]);
    }
    return m;
}

// exposition : 1 − e^(−k·y), ramenée à 1 pour y = 1
float exposer(float y, float k) {
    return (1.0 - exp(-k * y)) / (1.0 - exp(-k));
}

// le papier et l'encre, de jour (y = luminance du jeu) : sous 0,18 (feuillages pleins, troncs, ombre profonde) une masse
// sombre presque unie (0,15 à 0,23 : le moucheté des feuilles s'efface) ; au-dessus de 0,5 (sol, mur, toit éclairés) le
// papier (0,82 et plus) ; entre les deux, la pierre à l'ombre et les faces de côté, en gris de lavis
float papier(float y) {
    return mix(0.15 + 0.45 * y, min(0.82 + 0.25 * (y - 0.5), 0.93), smoothstep(0.18, 0.50, y));
}

void main() {
    ivec2 P = ivec2(gl_FragCoord.xy);
    vec3 c = texelFetch(InSampler, P, 0).rgb;
    float n = etat.x;
    if (n <= 0.002) {
        fragColor = vec4(c, 1.0);
        return;
    }
    float s = etat.y;
    float db = texelFetch(DepthSampler, P, 0).r;
    // banc seulement : distance brute en blocs × 4096 sur 24 bits (ciel au maximum), étudiée hors du jeu
    if (reglage.y > 0.06 && reglage.y < 0.25) {
        uint zq = db <= 0.0 ? 16777215u : uint(min(NEAR / db, 4095.99) * 4096.0);
        fragColor = vec4(float(zq >> 16u), float((zq >> 8u) & 255u), float(zq & 255u), 255.0) / 255.0;
        return;
    }
    // distance de rendu : au-delà, la profondeur n'est que nuages (le terrain s'arrête là)
    float d0 = (db < monde.x || nuage(monde, rayon.y, db)) ? 0.0 : db;
    bool ciel = d0 <= 0.0;
    float z = dist(d0);

    // ── personnes : un contour vanilla (personne cachée) posé sur le porteur ou la main n'est pas vu au travers d'un mur :
    // le décor voisin y revient ────────────────────────────────────────────────────────────────────────────────────
    // (les couleurs-clés ont un vert nul : rien à lire ailleurs)
    vec3 kc = c.r > c.g + 0.02 ? cles(c) : vec3(0.0);
    if (kc.x > 0.0 && z < reglage.w) {
        for (int i = 0; i < 4; i++) {
            ivec2 o = ivec2(i == 0 ? 4 : i == 1 ? -4 : 0, i == 2 ? 4 : i == 3 ? -4 : 0) * int(max(1.0, floor(InSize.y / 1080.0)));
            vec3 cv = texelFetch(InSampler, clamp(P + o, ivec2(0), ivec2(InSize) - 1), 0).rgb;
            if (cv.r <= cv.g + 0.02 || cles(cv).x <= 0.0) {
                c = cv;
                break;
            }
        }
        kc = vec3(0.0);
    }

    // ── encre : tente 3 × 3 du trait (quatre lectures bilinéaires aux coins du pixel), resserrée ; le trait, déjà pesé de
    // son opacité par la passe 1, s'affine puis s'efface au loin ──────────────────────────────────────────────────────
    vec2 hp = 0.5 / TraitsSize;
    float tente = 0.25 * (texture(TraitsSampler, texCoord - hp).r + texture(TraitsSampler, texCoord + vec2(hp.x, -hp.y)).r
        + texture(TraitsSampler, texCoord + vec2(-hp.x, hp.y)).r + texture(TraitsSampler, texCoord + hp).r);
    float trait = smoothstep(0.12, 0.55, tente);
    // liseré sombre de part et d'autre du trait (deux lectures à 2,5 px en diagonale, plus la tente) : le trait blanc se lit
    // aussi sur un sol pâle, un mur clair ou une personne cyan pâle, sans assombrir le reste du monde
    vec2 hl = vec2(2.5, -2.5) / TraitsSize;
    float large = max(texture(TraitsSampler, texCoord - hl).r, texture(TraitsSampler, texCoord + hl).r);
    float halo = smoothstep(0.15, 0.6, max(large, tente)) * (1.0 - trait);

    // banc seulement : bandes de 8 blocs selon la distance (ciel en bleu), puis les traits seuls
    if (reglage.y > 0.75) {
        fragColor = ciel ? vec4(0.2, 0.4, 1.0, 1.0) : vec4(vec3(fract(z / 8.0)) * (1.0 - smoothstep(0.0, 160.0, z) * 0.5), 1.0);
        return;
    }
    if (reglage.y > 0.25) {
        fragColor = vec4(vec3(trait), 1.0);
        return;
    }

    // ── monde ───────────────────────────────────────────────────────────────────────────────────────────────────────
    float y = dot(c, LUM);
    vec3 col;
    if (ciel) {
        col = tonCiel(c, y, s);
    } else {
        // jour : le papier et l'encre (papier) ; dans le noir, une exposition qui monte près de soi (nuit k ≤ 4,2, sous
        // terre ≤ 6,2) et retombe au loin, avec un peu de S ; une faible lueur qui ne dépend que de la distance donne le
        // volume du proche
        float yc = clamp(y, 0.0, 1.0);
        float pres = 1.0 - 0.65 * smoothstep(6.0, 40.0, z);
        float tn = exposer(yc, 2.0 + pres * mix(2.2, 4.2, reglage.z));
        float t = mix(papier(yc), mix(tn, smoothstep(0.08, 0.95, tn), 0.28), s);
        float lueur = 0.12 * s * mix(0.5, 1.0, reglage.z) * pow(1.0 - smoothstep(2.0, 28.0, z), 1.6);
        t += (1.0 - t) * lueur;
        col = vec3(t) + COULEUR * (c - y) + FROID;
        // lavis : le jour, tout pâlit et s'unit avec la distance (moitié à ~75 blocs) ; la nuit, vers un gris bleuté sombre
        float hb = mix(0.50 * (1.0 - exp(-z / 110.0)), 0.6 * (1.0 - exp(-z / 70.0)), s);
        vec3 brume = mix(vec3(0.80, 0.82, 0.86), vec3(0.14, 0.16, 0.21), s);
        col = mix(col, brume, hb);
        // le bord du monde (95,5 à 100 % de la fin du brouillard du jeu, en distance cylindrique comme lui) passe au ton
        // que prend l'horizon (couleur du brouillard, traitée comme le ciel) : il s'y fond sans fantôme
        col = mix(col, tonCiel(horizon, dot(horizon, LUM), s), bord(cylindre(z * rayon), monde.y));
        // boucles d'encre : −17 % au plus, pas au centre de l'écran
        if (reglage.x > 0.5) {
            vec2 q = (texCoord - 0.5) * vec2(InSize.x / InSize.y, 1.0);
            col *= 1.0 - 0.17 * boucles(q) * smoothstep(0.16, 0.42, length(q));
        }
    }

    vec3 vu = mix(c, clamp(col, 0.0, 1.0), n);

    // ── personnes (couleurs-clés) ───────────────────────────────────────────────────────────────────────────────────
    float a = max(kc.x, max(kc.y, kc.z));
    float apparition = smoothstep(0.25, 0.6, n);
    if (a > 0.0) {
        float ombre = clamp(c.r, 0.0, 1.0);                  // ombrage des faces, gardé pour le volume
        float loinP = smoothstep(16.0, 40.0, z);             // de loin, la personne devient une tache cyan franche
        vec3 corps = mix(vec3(0.50, 0.74, 0.94), vec3(0.88, 0.96, 1.0), smoothstep(0.35, 1.0, ombre));
        corps = mix(corps, vec3(0.36, 0.76, 1.0), 0.55 * loinP);
        vec3 ligne = vec3(0.14, 0.68, 1.0) * (0.85 + 0.3 * ombre);
        vec3 noeud = vec3(0.82, 0.98, 1.0);
        vec3 cible = (corps * kc.x + ligne * kc.y + noeud * kc.z) / max(kc.x + kc.y + kc.z, 1e-4);
        cible = mix(vec3(0.82, 0.86, 0.9), cible, apparition);
        vu = mix(vu, cible, a);
    }

    // ── encre, en dernier : à l'entrée pleine dès 40 % du fondu (~80 ms), avant la couleur ; à la sortie partie sous 55 %
    // (~120 ms), avant elle ─────────────────────────────────────────────────────────────────────────────────────────
    float vif = etat.w > 0.5 ? smoothstep(0.02, 0.4, n) : smoothstep(0.55, 0.95, n);
    // sur une masse sombre ou moyenne, le trait est blanc, bordé de son liseré sombre ; sur le papier (un toit, un mur, un
    // sol pâle : fond au-dessus de 0,6 à 0,7), blanc sur blanc ne se lirait pas : le trait passe à l'encre sombre, sans liseré
    float clair = smoothstep(0.60, 0.70, dot(vu, LUM));
    vu *= 1.0 - 0.42 * halo * vif * (1.0 - clair);
    vu = mix(vu, mix(ENCRE, ENCRE_SOMBRE, clair), 0.95 * trait * vif);
    // tramage d'un demi-niveau : le dégradé du brouillard et du ciel, relevé, ne fait pas de cercles
    vu += (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    fragColor = vec4(clamp(vu, 0.0, 1.0), 1.0);
}
