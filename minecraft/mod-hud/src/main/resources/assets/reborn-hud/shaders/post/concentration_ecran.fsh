#version 330

// Concentration (mod reborn-hud, lot 4 du Game Plan n° 17) — effet d'écran du mode, une seule passe plein écran.
// Décisions du 07/11 : « le plus fort, comme chez Ketto » (sans un pixel de Ketto), puis précisé : coins plus sombres,
// saturation plus basse, image plus sombre, « presque noir et blanc, mais en gardant un peu de couleur » :
//   - désaturation de 80 % (luminance Rec. 709) : il reste un cinquième de la couleur ;
//   - nuance froide légère, surtout dans les ombres (moins de rouge, plus de bleu) ;
//   - assombrissement en profil elliptique : centre −20 %, milieu des bords −45 %, coins −72 %.
// Aucun flou, aucun grain, aucun motif, aucun cadre.
//
// Paramètres (texture ModeConcentration 4x1) : pixel 0, R = avancement de l'effet (0 à 1, courbe déjà appliquée).

uniform sampler2D InSampler;
uniform sampler2D ParamsSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

const float CENTRE = 0.20;
const float BORDS = 0.45;
const float COINS = 0.72;
const float DESAT = 0.80;
const float FROID = 1.3;
const vec3 NUANCE = vec3(-0.050, -0.012, 0.060);

void main() {
    vec3 p = texture(InSampler, texCoord).rgb;
    float n = texelFetch(ParamsSampler, ivec2(0, 0), 0).r;
    if (n <= 0.002) {
        fragColor = vec4(p, 1.0);
        return;
    }
    // distance elliptique au centre : 0 au centre, 1 au milieu des bords, racine de 2 aux coins
    float d = length((texCoord - 0.5) * 2.0);
    float profil = CENTRE + (BORDS - CENTRE) * smoothstep(0.30, 1.0, d) + (COINS - BORDS) * smoothstep(1.0, 1.4143, d);
    float l = dot(p, vec3(0.2126, 0.7152, 0.0722));
    vec3 q = vec3(l) + (p - vec3(l)) * (1.0 - DESAT * n);
    float w = 0.35 + 0.65 * (1.0 - l);
    q *= 1.0 + FROID * n * w * NUANCE;
    q *= 1.0 - n * profil;
    fragColor = vec4(clamp(q, 0.0, 1.0), 1.0);
}
