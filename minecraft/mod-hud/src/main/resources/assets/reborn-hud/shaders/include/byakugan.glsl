// Byakugan : outils communs des deux passes du filtre (mod reborn-hud, lot 5 du Game Plan n° 17, étape B ; 7e tour, 11/11).
// Les paramètres sont lus une fois par sommet (post/byakugan.vsh) et arrivent aux pixels en variables plates :
//   etat    = (avancement du fondu, obscurité au joueur, phase des boucles d'encre, 1 à l'entrée / 0 à la sortie) ;
//   reglage = (boucles d'encre, banc, sous terre, distance de l'arrière du porteur le long du regard : 0 si inconnue) ;
//   monde   = (profondeur inversée de la distance de rendu : 0 si inconnue, bord du monde en blocs, hauteur des nuages
//              au-dessus de l'œil, nuages affichés 0 / 1) ;
//   rayon   = décalage du point vu, en blocs du monde, par bloc de distance le long du regard (interpolé : exact à chaque
//             pixel, balancement de la marche et nausée compris).

// plan proche du monde en 26.2 (GameRenderer, 0,05) ; profondeur inversée : 1 au plan proche, 0 à l'infini (ciel)
const float NEAR = 0.05;

// distance (en blocs, le long du regard) d'une profondeur inversée ; ciel au loin
float dist(float d) {
    return d <= 0.0 ? 1.0e4 : NEAR / d;
}

// Nuages : en 26.2 ils écrivent la profondeur à leur hauteur. Un point vu dont la hauteur tombe dans la couche des nuages
// (4 blocs d'épaisseur ; marge de 1,5 bloc plus 3 % de la distance dessous, 1,5 bloc plus 6 % dessus) est du ciel. f est la
// composante verticale du rayon du pixel : le point vu à la profondeur d est à NEAR × f / d au-dessus de l'œil (sans division).
bool nuage(vec4 m, float f, float d) {
    if (m.w < 0.5 || d <= 0.0) return false;
    float y = NEAR * f;
    return y > (m.z - 1.5) * d - 0.03 * NEAR && y < (m.z + 5.5) * d + 0.06 * NEAR;
}

// distance cylindrique d'un point relatif à l'œil : celle du brouillard de la distance de rendu (fog.glsl du jeu)
float cylindre(vec3 r) {
    return max(length(r.xz), abs(r.y));
}

// bord du monde : 0 en deçà de 95,5 % de la fin du brouillard du jeu (fin), 1 à la fin ; le terrain y passe au ton de
// l'horizon et l'encre s'éteint au même pas (distance cylindrique c)
float bord(float c, float fin) {
    return smoothstep(0.955 * fin, fin, c);
}
