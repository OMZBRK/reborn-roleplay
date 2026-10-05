#version 330

// Mode photo Reborn : étalonnage (matrice de couleur + saturation + teinte) et flou doux séparable.
// Passe 1 : étalonnage + flou horizontal ; passe 2 : flou vertical seul (matrice identité).
// Généré avec les JSON post_effect/photo_*.json (tools/ui-art/gen_photo_filters.py).

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform PhotoConfig {
    vec4 RedRow;     // xyz : ligne R de la matrice
    vec4 GreenRow;
    vec4 BlueRow;
    vec4 Params;     // x : saturation, y/z : direction du flou, w : rayon (texels)
    vec4 Tint;       // rgb : teinte multipliée, a : vignettage (0..1)
};

out vec4 fragColor;

vec3 grade(vec3 c) {
    vec3 o = vec3(dot(c, RedRow.xyz), dot(c, GreenRow.xyz), dot(c, BlueRow.xyz));
    float l = dot(o, vec3(0.299, 0.587, 0.114));
    o = mix(vec3(l), o, Params.x) * Tint.rgb;
    return clamp(o, 0.0, 1.0);
}

void main() {
    vec2 texel = 1.0 / InSize;
    float radius = Params.w;
    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    int r = int(ceil(radius));
    for (int i = -r; i <= r; i++) {
        float w = radius <= 0.0 ? (i == 0 ? 1.0 : 0.0) : exp(-float(i * i) / max(0.5, radius * radius * 0.5));
        acc += texture(InSampler, texCoord + Params.yz * texel * float(i)).rgb * w;
        wsum += w;
    }
    vec3 c = grade(acc / max(wsum, 1e-4));
    if (Tint.a > 0.0) {
        vec2 d = texCoord - 0.5;
        c *= 1.0 - Tint.a * smoothstep(0.35, 0.75, length(d * vec2(1.2, 1.0)));
    }
    fragColor = vec4(c, 1.0);
}
