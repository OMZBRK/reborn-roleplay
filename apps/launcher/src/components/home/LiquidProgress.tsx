import { useEffect, useRef, useState } from "react";

// Barre de progression « liquide » (style slosh) : un front de liquide
// crimson qui ondule, penche quand la progression accelere (inertie du
// ressort), laisse un front fantome en retard (echo), avec halo, trainees
// et etincelles sur le front. Shader WebGL1 maison, un seul quad plein
// cadre ; retombe sur une barre CSS si WebGL est indisponible.
//
// La progression affichee n'est jamais la valeur brute : elle suit la
// cible via un ressort amorti, ce qui lisse les sauts d'evenements
// (manifest:progress arrive par paquets) et nourrit le ballottement.

type Props = {
  /** 0..1 */
  progress: number;
  title: string;
  /** Affiche le pourcentage a droite (defaut : oui). */
  showPercent?: boolean;
  width?: number;
  height?: number;
};

// Palette Reborn (tokens --color-* de globals.css), du fond au reflet.
const PALETTE = {
  bg: [0x0d, 0x07, 0x09],
  c1: [0x14, 0x05, 0x0a],
  c2: [0x3d, 0x0c, 0x14],
  c3: [0x84, 0x14, 0x25],
  c4: [0xe6, 0x39, 0x50],
  c5: [0xff, 0xd0, 0xd6],
} as const;

const VERT = `
attribute vec2 aPos;
void main() { gl_Position = vec4(aPos, 0.0, 1.0); }
`;

const FRAG = `
precision highp float;
uniform vec2 uRes;
uniform float uTime;
uniform float uProgress;
uniform float uEcho;
uniform float uSlosh;
uniform vec3 uBg, uC1, uC2, uC3, uC4, uC5;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}
float noise(vec2 p) {
  vec2 i = floor(p), f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
             mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
  float v = 0.0, a = 0.5;
  for (int i = 0; i < 4; i++) { v += a * noise(p); p *= 2.03; a *= 0.5; }
  return v;
}

// Position du front (espace X = uv.x * aspect) a la hauteur y.
float frontAt(float p, float y, float t, float aspect, float phase) {
  float wave = sin(y * 6.9 + t * 2.2 + phase) * 0.6
             + sin(y * 14.1 - t * 3.1 + 1.3 + phase) * 0.28
             + (fbm(vec2(y * 3.0, t * 0.6 + phase)) - 0.5) * 0.9;
  float tilt = (y - 0.5) * uSlosh * 1.4;
  float amp = 0.07 + abs(uSlosh) * 0.25;
  return mix(-0.25, aspect + 0.25, p) + wave * amp + tilt;
}

void main() {
  vec2 uv = gl_FragCoord.xy / uRes;
  float aspect = uRes.x / uRes.y;
  float X = uv.x * aspect;
  float y = uv.y;
  float t = uTime;

  float F = frontAt(uProgress, y, t, aspect, 0.0);
  float d = X - F;
  float inside = 1.0 - smoothstep(-0.012, 0.012, d);

  // Liquide : chaud pres du front, profond vers la gauche, brasse par du bruit.
  float depth = clamp(-d / 3.0, 0.0, 1.0);
  vec3 liquid = mix(uC4, uC3, smoothstep(0.0, 0.25, depth));
  liquid = mix(liquid, uC2, smoothstep(0.3, 1.0, depth) * 0.85);
  liquid = mix(liquid, uC1, smoothstep(0.8, 1.0, depth) * 0.3);
  float churn = fbm(vec2(X * 2.0 - t * 0.8, y * 3.0 + t * 0.3));
  liquid *= 0.8 + 0.45 * churn;
  liquid += uC5 * 0.07 * smoothstep(0.55, 1.0, y);

  // Trainees qui filent vers le front.
  float trail = pow(noise(vec2(X * 1.4 - t * 1.3, y * 16.0)), 6.0);
  liquid += uC4 * trail * 0.7 * (1.0 - depth);

  // Front fantome en retard (echo).
  float E = frontAt(uEcho, y, t * 0.8, aspect, 2.1);
  float echo = exp(-abs(X - E) * 16.0) * 0.35;
  liquid += uC4 * echo;

  // Fond : bruit tres doux + halo (bloom) devant le front.
  vec3 bg = uBg * (0.85 + 0.3 * fbm(vec2(X * 1.5 + t * 0.1, y * 2.0)));
  float bloom = exp(-max(d, 0.0) * 5.0) * 0.6;
  bg += uC3 * bloom * step(0.001, uProgress);

  vec3 col = mix(bg, liquid, inside);

  // Liseré lumineux du front.
  float edge = exp(-abs(d) * 30.0);
  col += uC5 * edge * 0.75 * step(0.001, uProgress);

  // Etincelles accrochees au front.
  vec2 cell = floor(vec2(X * 26.0, y * 26.0)) + floor(t * 7.0);
  float spark = step(0.985, hash(cell)) * exp(-abs(d) * 9.0);
  col += uC5 * spark * 0.8;

  // Vignette + grain.
  vec2 q = uv - 0.5;
  col *= 1.0 - 0.45 * dot(q * vec2(0.6, 1.2), q * vec2(0.6, 1.2));
  col += (hash(gl_FragCoord.xy + fract(t) * 91.0) - 0.5) * 0.02;

  gl_FragColor = vec4(col, 1.0);
}
`;

function rgb(c: readonly number[]): [number, number, number] {
  return [c[0] / 255, c[1] / 255, c[2] / 255];
}

function compile(gl: WebGLRenderingContext, type: number, src: string) {
  const s = gl.createShader(type);
  if (!s) return null;
  gl.shaderSource(s, src);
  gl.compileShader(s);
  if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
    console.warn("[liquid-progress] shader:", gl.getShaderInfoLog(s));
    gl.deleteShader(s);
    return null;
  }
  return s;
}

export function LiquidProgress({
  progress,
  title,
  showPercent = true,
  width = 300,
  height = 84,
}: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const targetRef = useRef(progress);
  const [webgl, setWebgl] = useState(true);
  targetRef.current = Math.max(0, Math.min(1, progress));

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const gl = canvas.getContext("webgl", { antialias: false, premultipliedAlpha: false });
    if (!gl) {
      setWebgl(false);
      return;
    }
    const vs = compile(gl, gl.VERTEX_SHADER, VERT);
    const fs = compile(gl, gl.FRAGMENT_SHADER, FRAG);
    const prog = gl.createProgram();
    if (!vs || !fs || !prog) {
      setWebgl(false);
      return;
    }
    gl.attachShader(prog, vs);
    gl.attachShader(prog, fs);
    gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
      setWebgl(false);
      return;
    }
    gl.useProgram(prog);

    const buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
    const aPos = gl.getAttribLocation(prog, "aPos");
    gl.enableVertexAttribArray(aPos);
    gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 0, 0);

    const u = (n: string) => gl.getUniformLocation(prog, n);
    const uRes = u("uRes");
    const uTime = u("uTime");
    const uProgress = u("uProgress");
    const uEcho = u("uEcho");
    const uSlosh = u("uSlosh");
    gl.uniform3fv(u("uBg"), rgb(PALETTE.bg));
    gl.uniform3fv(u("uC1"), rgb(PALETTE.c1));
    gl.uniform3fv(u("uC2"), rgb(PALETTE.c2));
    gl.uniform3fv(u("uC3"), rgb(PALETTE.c3));
    gl.uniform3fv(u("uC4"), rgb(PALETTE.c4));
    gl.uniform3fv(u("uC5"), rgb(PALETTE.c5));

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    gl.viewport(0, 0, canvas.width, canvas.height);
    gl.uniform2f(uRes, canvas.width, canvas.height);

    const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    const speed = reduced ? 0.25 : 1;

    // Etat du ressort : p suit la cible, v sa vitesse ; echo traine derriere.
    let p = targetRef.current;
    let v = 0;
    let echo = p;
    let slosh = 0;
    let last = performance.now();
    let time = 0;
    let raf = 0;

    const frame = (now: number) => {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      time += dt * speed;

      const target = targetRef.current;
      v += (target - p) * 14 * dt;
      v *= Math.exp(-12 * dt);
      p += v * dt * 8;
      p = Math.max(0, Math.min(1, p));
      echo += (p - echo) * Math.min(1, dt * 1.4);
      // Ballottement : suit la vitesse, retombe doucement.
      slosh += (Math.max(-1, Math.min(1, v * 4)) - slosh) * Math.min(1, dt * 3);

      gl.uniform1f(uTime, time);
      gl.uniform1f(uProgress, p);
      gl.uniform1f(uEcho, echo);
      gl.uniform1f(uSlosh, slosh);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);

    const onLost = (e: Event) => {
      e.preventDefault();
      cancelAnimationFrame(raf);
      setWebgl(false);
    };
    canvas.addEventListener("webglcontextlost", onLost);

    return () => {
      cancelAnimationFrame(raf);
      canvas.removeEventListener("webglcontextlost", onLost);
      gl.deleteBuffer(buf);
      gl.deleteProgram(prog);
      gl.deleteShader(vs);
      gl.deleteShader(fs);
    };
  }, [width, height]);

  const pct = Math.round(targetRef.current * 100);

  return (
    <div
      className="reborn-liquid"
      style={{ width, height }}
      role="progressbar"
      aria-label={title}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={pct}
    >
      {webgl ? (
        <canvas ref={canvasRef} className="reborn-liquid-canvas" style={{ width, height }} />
      ) : (
        <div className="reborn-liquid-fallback" style={{ width: `${pct}%` }} />
      )}
      <div className="reborn-liquid-label">
        <span className="reborn-liquid-title">{title}</span>
        {showPercent && <span className="reborn-liquid-pct">{pct}%</span>}
      </div>
    </div>
  );
}
