#version 330

#moj_import <reborn-hud:byakugan.glsl>

// Byakugan, passe 1 sur 2 (mod reborn-hud, lot 5 du Game Plan n° 17, étape B ; 8e tour, 14/11) : l'ENCRE, lue dans la
// profondeur seule (jamais dans les textures). 7e tour rejeté : « des fils de partout […] ne les mettre que sur la fin des
// volumes, pour les contours de bâtiment, d'arbres ». Plus de grille ni de pli : UN SEUL trait, la FIN D'UN VOLUME.
// Pendant la vision, les feuilles sont pleines et les plantes basses cachées (FeuillagePlein) : la profondeur ne contient
// que de la géométrie de blocs.
// Sur chaque axe de l'écran, cinq profondeurs inversées (centre, ± k, ± 2k ; k = 1 px en 1080p, 2 en 2160p). Le plan du
// pixel est prolongé vers le voisin (pente prise du côté opposé) ; il y a trait si le voisin est derrière ce plan de plus
// de ~1,35 bloc (+ 2 % de la distance), l'écart étant compté PERPENDICULAIREMENT au plan du pixel :
//   - un tronc, un mur, une personne devant un fond plus loin : face vue de front, l'écart est celui du regard → trait ;
//   - le dessus d'une marche ou d'une terrasse d'un bloc vu en rasant : le fond n'est qu'à un bloc sous ce plan → rien ;
//   - deux blocs d'un même mur, un coin dont on voit les deux faces, un escalier vu de face : la surface continue → rien ;
//   - un bloc de feuilles qui dépasse d'un bloc devant d'autres feuilles : rien ; un houppier devant le ciel ou la
//     colline : trait.
// Contre le ciel, le bord d'un volume est toujours tracé. Marqué du côté proche seulement, sur 2 px de près, ~1,5 px au-delà
// de 24 à 48 blocs. Sur le porteur, 0,35 bloc suffit (sa silhouette reste cernée près d'un mur ; ses pieds posés au sol ne
// font pas de pointillés). LAVIS : le trait s'éteint de 48 à 130 blocs et au bord du monde (95,5 à 100 % de la fin du
// brouillard du jeu) ; le lointain n'est plus que des masses pâles. Rien sur le ciel ni sur les nuages.
// Sortie R = trait pesé de son opacité, lu en bilinéaire par la passe 2 (anticrénelage). Lectures par pixel : profondeur 9.

uniform sampler2D DepthSampler;

in vec2 texCoord;
in vec3 rayon;
flat in vec4 etat;
flat in vec4 reglage;
flat in vec4 monde;
flat in vec4 porteurEcran;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 DepthSize;
};

out vec4 fragColor;

// écart demandé entre un volume et ce qu'il cache, en blocs, à la distance du pixel (une fois par pixel)
float ecart;

// profondeur inversée au pixel p, 0 pour le ciel et ce qui passe la distance de rendu (les nuages ne sont écartés qu'au
// centre : un voisin pris dans un nuage, plus loin que le terrain, donne le même trait que le ciel)
float lire(ivec2 p) {
    float v = texelFetch(DepthSampler, clamp(p, ivec2(0), ivec2(DepthSize) - 1), 0).r;
    return v < monde.x ? 0.0 : v;
}

// le pixel peut-il être le porteur (ou la main) : plus près que son arrière, dans le rectangle de l'écran qui le contient
float surPorteur(float z) {
    return (z < reglage.w && all(greaterThanEqual(texCoord, porteurEcran.xy)) && all(lessThanEqual(texCoord, porteurEcran.zw)))
        ? 1.0 : 0.0;
}

// le point de profondeur n (du solide) est-il nettement derrière le plan du centre (c, pente s par pas) prolongé de j pas ?
// a = angle d'un pas d'écran. Sur le plan, la distance varie de |s| / c par pas : devant a, cela dit de combien le plan
// est vu en rasant, et ramène l'écart mesuré le long du regard à l'écart perpendiculaire au plan.
float derriere(float c, float s, float n, float j, float a) {
    float pr = c + s * j;
    if (pr <= 0.0 || n <= 0.0) return 0.0;  // le plan file au-delà de l'horizon, ou le ciel (voir bordCiel)
    float r = abs(s) / c;
    float perp = a * inversesqrt(a * a + r * r);
    return smoothstep(0.8, 1.2, (NEAR / n - NEAR / pr) * perp / ecart);
}

// le voisin à j pas est-il le ciel, le plan du centre (c, pente s) restant devant l'horizon ? (sinon : un sol qui file à
// l'horizon n'a pas de bord)
float bordCiel(float c, float s, float n, float j) {
    return (n <= 0.0 && c + s * j > 0.0) ? 1.0 : 0.0;
}

// un axe : m2, m1, c, p1, p2 = profondeurs à −2k, −k, 0, +k, +2k ; large = poids du 2e pas ; a = angle d'un pas.
// Fin de volume, du côté proche : la pente du plan du pixel est prise du côté opposé au voisin.
float axe(float m2, float m1, float c, float p1, float p2, float large, float a) {
    float sm = m1 > 0.0 ? c - m1 : 0.0;
    float sp = p1 > 0.0 ? p1 - c : 0.0;
    float t = max(derriere(c, sm, p1, 1.0, a), derriere(c, -sp, m1, 1.0, a));
    t = max(t, large * max(derriere(c, sm, p2, 2.0, a), derriere(c, -sp, m2, 2.0, a)));
    float b = max(max(bordCiel(c, sm, p1, 1.0), bordCiel(c, -sp, m1, 1.0)),
                  large * max(bordCiel(c, sm, p2, 2.0), bordCiel(c, -sp, m2, 2.0)));
    return max(t, b);
}

void main() {
    // angle d'un pixel sur chaque axe (le rayon est linéaire sur l'écran), pris avant tout branchement
    float ax = length(dFdx(rayon)), ay = length(dFdy(rayon));
    if (etat.x <= 0.002) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    ivec2 P = ivec2(gl_FragCoord.xy);
    float c = lire(P);
    if (c <= 0.0 || nuage(monde, rayon.y, c)) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    int k = int(max(1.0, floor(DepthSize.y / 1080.0)));
    float fk = float(k);
    ivec2 ox = ivec2(k, 0), oy = ivec2(0, k);
    float z = NEAR / c;
    float large = 1.0 - 0.5 * smoothstep(24.0, 48.0, z);
    ecart = mix(1.35, 0.35, surPorteur(z)) + 0.02 * z;
    float tx = axe(lire(P - 2 * ox), lire(P - ox), c, lire(P + ox), lire(P + 2 * ox), large, fk * ax);
    float ty = axe(lire(P - 2 * oy), lire(P - oy), c, lire(P + oy), lire(P + 2 * oy), large, fk * ay);
    // lavis : le trait s'éteint avec la distance, puis au bord du monde, au pas où le terrain passe au ton de l'horizon
    float opacite = (1.0 - smoothstep(48.0, 130.0, z)) * (1.0 - bord(cylindre(z * rayon), monde.y));
    fragColor = vec4(max(tx, ty) * opacite, 0.0, 0.0, 1.0);
}
