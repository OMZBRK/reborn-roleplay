#version 330

// KO / PainRP Reborn (mod reborn-hud, lot KO-6) — passe plein écran.
//
// Paramètres (texture KoClient 4x1) :
//   pixel 0 = (R mode, G force, B/A temps bits 0-15)
//   pixel 1 = (R temps bits 16-23, G épuisement de chakra 0/1, B phase cardiaque, A PV / PV max)
//   pixel 2 = (R temps restant de la phase, fraction)
// Modes : 1 à terre (monde qui se vide de ses couleurs, vignette rouge qui bat au rythme du cœur, vision qui
// tangue et se dédouble), 2 inconscient (noir et blanc, flou, paupières lourdes qui respirent), 3 ATA (douleur
// sourde : légère désaturation, pulsation discrète sur les bords). Chakra épuisé : bleu glacé au lieu du rouge.

uniform sampler2D InSampler;
uniform sampler2D ParamsSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float lum(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

// Battement « lub-dub » : 1 sur le premier coup, un second plus faible juste après.
float coeur(float p) {
    return min(1.0, exp(-pow(p / 0.07, 2.0)) + 0.55 * exp(-pow((p - 0.2) / 0.07, 2.0)));
}

// Flou en disque (spirale dorée), rayon en pixels.
vec3 flou(vec2 uv, vec2 px, float r) {
    vec3 acc = texture(InSampler, uv).rgb;
    for (int k = 1; k < 12; k++) {
        float a = float(k) * 2.39996;
        float rr = r * sqrt(float(k) / 11.0);
        acc += texture(InSampler, uv + vec2(cos(a), sin(a)) * rr * px).rgb;
    }
    return acc / 12.0;
}

void main() {
    vec4 p0 = texelFetch(ParamsSampler, ivec2(0, 0), 0);
    vec4 p1 = texelFetch(ParamsSampler, ivec2(1, 0), 0);
    vec4 p2 = texelFetch(ParamsSampler, ivec2(2, 0), 0);
    int mode = int(round(p0.r * 255.0));
    float S = p0.g;
    float t = (round(p0.b * 255.0) + round(p0.a * 255.0) * 256.0 + round(p1.r * 255.0) * 65536.0) / 60.0;
    bool chakra = p1.g > 0.5;
    float pulse = coeur(p1.b);
    float hp = p1.a;

    vec2 uv = texCoord;
    vec2 px = 1.0 / InSize;
    float aspect = InSize.x / InSize.y;
    vec2 d = (uv - 0.5) * vec2(aspect, 1.0);
    float dist = length(d) / length(vec2(aspect, 1.0) * 0.5);   // 0 centre, 1 coin
    vec3 orig = texture(InSampler, uv).rgb;
    vec3 col = orig;

    vec3 teinte = chakra ? vec3(0.16, 0.42, 0.85) : vec3(0.62, 0.02, 0.05);

    if (mode == 1) {
        // ---------------- à terre : la vision tangue et se dédouble, les couleurs s'en vont
        vec2 tangue = vec2(sin(t * 1.3) * 2.5, cos(t * 0.9) * 1.5) * px;
        vec2 ab = (uv - 0.5) * (0.004 + 0.006 * pulse) * dist;
        vec3 c = vec3(texture(InSampler, uv + tangue + ab).r,
                      texture(InSampler, uv + tangue).g,
                      texture(InSampler, uv + tangue - ab).b);
        vec3 double_ = texture(InSampler, uv + vec2(7.0 + 4.0 * sin(t * 0.7), 0.0) * px).rgb;
        c = mix(c, double_, 0.22);
        // bords flous
        c = mix(c, flou(uv, px, 6.0), smoothstep(0.35, 0.95, dist));
        // désaturation
        c = mix(c, vec3(lum(c)), 0.6);
        // vignette qui bat
        float v = smoothstep(0.28, 1.0, dist) * (0.55 + 0.45 * pulse);
        c = mix(c, teinte * (0.35 + 0.4 * pulse), clamp(v * 0.85, 0.0, 0.9));
        c *= 0.82 + 0.1 * pulse;
        col = c;
    } else if (mode == 2) {
        // ---------------- inconscient : noir et blanc flou, paupières lourdes qui s'ouvrent et se ferment
        vec3 c = flou(uv, px, 7.0 + 3.0 * sin(t * 0.6));
        float l = lum(c);
        c = vec3(l) * vec3(1.0, 0.97, 0.94);
        c = mix(vec3(0.18), c, 0.6);                                      // contraste écrasé
        c += teinte * 0.12 * pulse * (1.0 - dist);                        // le cœur, encore
        float ouverture = 0.16 + 0.07 * sin(t * 0.8) + 0.03 * pulse;      // paupières
        float yy = abs(uv.y - 0.5);
        float paupiere = smoothstep(ouverture, ouverture + 0.12, yy);
        c = mix(c, vec3(0.0), paupiere);
        c *= 1.0 - 0.75 * smoothstep(0.2, 0.9, dist);
        c += (hash(uv * InSize + fract(t * 7.0) * 100.0) - 0.5) * 0.05;   // grain
        col = c;
    } else if (mode == 3) {
        // ---------------- ATA : douleur sourde, plus forte quand les PV sont bas
        float douleur = 0.6 + 0.4 * (1.0 - hp);
        vec3 c = mix(orig, vec3(lum(orig)), 0.25 * douleur);
        float v = smoothstep(0.45, 1.05, dist) * (0.35 + 0.65 * pulse) * douleur;
        c = mix(c, teinte * 0.5, clamp(v * 0.6, 0.0, 0.6));
        col = c;
    }

    // Fondu d'entrée / de sortie (en ATA, la force plafonne à 0,45 : on la ramène sur 0..1).
    float w = mode == 3 ? S / 0.45 : S;
    fragColor = vec4(mix(orig, col, clamp(w, 0.0, 1.0)), 1.0);
}
