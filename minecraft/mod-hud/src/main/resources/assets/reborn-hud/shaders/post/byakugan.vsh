#version 330

#moj_import <reborn-hud:byakugan.glsl>

// Byakugan : sommets des deux passes du filtre (mod reborn-hud, lot 5 du Game Plan n° 17, étape B ; 7e tour, 11/11). Le
// triangle plein écran de minecraft:core/screenquad ; la texture de paramètres (21 × 1, ByakuganClient.vue, écrite pendant
// le rendu du monde avec la projection de l'image) est lue ici, trois fois par image au lieu d'une fois par pixel, et passée en variables plates (byakugan.glsl). Le rayon de chaque sommet
// (regard, plus le décalage de l'écran) est interpolé : il est linéaire sur l'écran, donc exact à chaque pixel.
//
// Texture de paramètres :
//   pixel 0 : R = avancement du fondu (0 à 1), G = obscurité au joueur (0 jour, 1 grotte noire), B = phase des boucles
//             d'encre, octet fort (un tour en 64 s ; octet faible au pixel 2) ;
//   pixel 1 : R = boucles d'encre (0 ou 1), G = banc (255 : bandes de distance ; 128 : traits seuls, blanc sur noir ; 32 : distance
//             brute), B = sous terre (0 ciel ouvert, 1 aucune lumière du ciel au joueur) ;
//   pixel 2 : R = distance de rendu en chunks (0 = inconnue), G = 255 à l'entrée (vision ouverte), 0 à la sortie, B = phase
//             des boucles, octet faible ;
//   pixel 3 : R, G = hauteur des nuages au-dessus de l'œil ((blocs + 2048) / 4096 × 65535), B = nuages affichés ;
//   pixel 4 : R, G = distance le long du regard de l'arrière du porteur (256es de bloc × 65535 ; troisième personne, 1 bloc
//             en première personne : la main), B = 255 si connue ;
//   pixel 5 : R, G = fin du brouillard de la distance de rendu (blocs / 4096 × 65535), le bord du monde ;
//   pixels 6 à 17 : douze flottants, un par pixel (bits IEEE, octet fort en R, faible en A) : regard, pas horizontal et pas
//             vertical de l'écran (par unité d'écran normalisé), en blocs du monde par bloc de distance ; puis l'œil dans son
//             bloc (balancement compris) ;
//   pixel 18 : couleur du brouillard du jeu (l'horizon) ;
//   pixels 19 et 20 : coins du rectangle de l'écran (0 à 1) qui contient le porteur, toute l'image en première personne
//             (R, G = x et B, A = y sur 16 bits) : seuil de trait plus fin et pas de contour vanilla dessus, plus près que son
//             arrière.

uniform sampler2D ParamsSampler;

out vec2 texCoord;
out vec3 rayon;
flat out vec4 etat;
flat out vec4 reglage;
flat out vec4 monde;
flat out vec3 horizon;
flat out vec4 porteurEcran;
// boucles d'encre : rayon et présence des quatre anneaux, les mêmes pour tous les pixels (un tour de phase en 64 s)
flat out vec4 boucleRayon;
flat out vec4 boucleForce;

vec4 px(int i) {
    return texelFetch(ParamsSampler, ivec2(i, 0), 0);
}

float u16(vec4 p) {
    return (floor(p.r * 255.0 + 0.5) * 256.0 + floor(p.g * 255.0 + 0.5)) / 65535.0;
}

float flottant(int i) {
    uvec4 o = uvec4(px(i) * 255.0 + 0.5);
    return uintBitsToFloat(o.r << 24u | o.g << 16u | o.b << 8u | o.a);
}

// deux valeurs de 0 à 1 sur 16 bits : (R, G) et (B, A)
vec2 paire(vec4 p) {
    vec4 o = floor(p * 255.0 + 0.5);
    return vec2(o.r * 256.0 + o.g, o.b * 256.0 + o.a) / 65535.0;
}

vec3 vecteur(int i) {
    return vec3(flottant(i), flottant(i + 1), flottant(i + 2));
}

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vec2 ndc = uv * 2.0 - 1.0;
    gl_Position = vec4(ndc, 0.0, 1.0);
    texCoord = uv;
    rayon = vecteur(6) + ndc.x * vecteur(9) + ndc.y * vecteur(12);
    vec4 p0 = px(0), p2 = px(2), p3 = px(3), p4 = px(4);
    etat = vec4(p0.rg, (floor(p0.b * 255.0 + 0.5) * 256.0 + floor(p2.b * 255.0 + 0.5)) / 65536.0, p2.g);
    reglage = vec4(px(1).rgb, p4.b > 0.5 ? u16(p4) * 256.0 : 0.0);
    horizon = px(18).rgb;
    vec4 vie = fract(etat.z * vec4(4.0, 5.0, 4.0, 5.0) + vec4(0.0, 0.27, 0.54, 0.81));
    boucleRayon = 0.22 + 0.52 * vie;
    boucleForce = sin(3.14159265 * vie);
    porteurEcran = vec4(paire(px(19)), paire(px(20)));
    float rd = p2.r * 255.0;
    float fin = u16(px(5)) * 4096.0;
    monde = vec4(rd > 0.5 ? NEAR / (rd * 16.0 + 24.0) : 0.0, fin > 1.0 ? fin : rd > 0.5 ? rd * 16.0 : 1.0e5,
        u16(p3) * 4096.0 - 2048.0, p3.b);
}
