#version 330

// Concentration (mod reborn-hud, lot 4 du Game Plan n° 17) — effet d'écran du mode, une seule passe plein écran.
// Décisions du 08/11 : « les coins et cet effet plus sombre », avec « ce petit effet de noir et blanc » et l'« effet pâle »,
// « pas aussi prononcé » qu'en 0.4.200. Recette K+ (choisie le 09/11), un cran plus sombre que les mesures de Ketto (_etude_ketto.md §2.1) :
//   - désaturation de 40 % (luminance Rec. 709) ;
//   - nuance froide très légère (rouge −2,5 %, vert −0,5 %, bleu +2 %) ;
//   - assombrissement en profil elliptique : centre −12 %, milieu des bords −35 %, coins −60 %.
// Aucun flou, aucun grain, aucun motif, aucun cadre.
//
// Paramètres (texture ModeConcentration 4x1), pixel 0 : R = avancement de l'effet (0 à 1, courbe déjà appliquée).

uniform sampler2D InSampler;
uniform sampler2D ParamsSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

// centre, milieu des bords, coins
const vec3 PROFIL = vec3(0.12, 0.35, 0.60);
const float DESAT = 0.40;
const vec3 NUANCE = vec3(-0.025, -0.005, 0.020);

void main() {
    vec3 p = texture(InSampler, texCoord).rgb;
    float n = texelFetch(ParamsSampler, ivec2(0, 0), 0).r;
    if (n <= 0.002) {
        fragColor = vec4(p, 1.0);
        return;
    }
    // distance elliptique au centre : 0 au centre, 1 au milieu des bords, racine de 2 aux coins
    float d = length((texCoord - 0.5) * 2.0);
    float profil = PROFIL.x + (PROFIL.y - PROFIL.x) * smoothstep(0.30, 1.0, d) + (PROFIL.z - PROFIL.y) * smoothstep(1.0, 1.4143, d);
    float l = dot(p, vec3(0.2126, 0.7152, 0.0722));
    vec3 q = vec3(l) + (p - vec3(l)) * (1.0 - DESAT * n);
    q *= 1.0 + n * NUANCE;
    q *= 1.0 - n * profil;
    fragColor = vec4(clamp(q, 0.0, 1.0), 1.0);
}
