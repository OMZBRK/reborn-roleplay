#version 330

// Byakugan — vision subjective « comme dans l'anime » : le monde en négatif noir et blanc légèrement bleuté.
// Les pixels orange pur (silhouettes/réseau/aura dessinés par le mod dans cette couleur « inverse ») deviennent du
// chakra cyan lumineux, avec un halo doux (petit flou sur le masque orange).

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

// 0..1 : à quel point ce pixel est l'orange « chakra », quelle que soit sa luminosité (les faces des modèles sont
// plus ou moins éclairées) : teinte orange pure (pas de bleu, vert ≈ 25..75 % du rouge) et pas trop sombre.
float chakra(vec3 c) {
    float r = max(c.r, 1e-3);
    float teinte = smoothstep(0.82, 0.95, (c.r - c.b) / r) * smoothstep(0.2, 0.3, c.g / r) * (1.0 - smoothstep(0.75, 0.85, c.g / r));
    return teinte * smoothstep(0.15, 0.3, c.r);
}

void main() {
    vec2 px = 1.0 / InSize;
    vec3 c = texture(InSampler, texCoord).rgb;

    // négatif noir et blanc, contraste « anime », légère dominante bleue
    float l = dot(1.0 - c, vec3(0.299, 0.587, 0.114));
    l = pow(smoothstep(0.1, 0.95, l), 1.7);            // plus sombre et contrasté (gris profonds, blancs rares)
    vec3 neg = mix(vec3(0.02, 0.03, 0.07), vec3(0.66, 0.71, 0.8), l);

    // chakra : cœur net + halo (moyenne du masque autour du pixel)
    float m = chakra(c);
    float halo = 0.0;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5235988;
        vec2 o = vec2(cos(a), sin(a));
        halo += chakra(texture(InSampler, texCoord + o * px * 1.5).rgb);
        halo += chakra(texture(InSampler, texCoord + o * px * 3.0).rgb) * 0.5;
    }
    halo = clamp(halo / 12.0, 0.0, 1.0);
    float vif = clamp(c.g / max(c.r, 1e-3), 0.0, 1.0);          // impulsions / tenketsu : orange plus jaune -> plus blanc
    vec3 cyan = mix(vec3(0.3, 0.78, 1.0), vec3(0.85, 0.97, 1.0), smoothstep(0.5, 0.7, vif)) * (0.75 + 0.35 * c.r);
    vec3 col = mix(neg, cyan, m);
    col += vec3(0.25, 0.65, 1.0) * halo * 0.35 * (1.0 - m);

    // léger vignettage
    vec2 d = texCoord - 0.5;
    col *= 1.0 - 0.35 * dot(d, d);
    fragColor = vec4(col, 1.0);
}
