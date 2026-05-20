type Props = {
  seed: string;
  size?: number;
};

// Trois palettes determinees a partir d'un hash djb2 du seed (UUID stable
// du compte). Les codes du pattern (H/F/E/M/J/S) sont des alias semantiques
// vers une palette donnee.
const HAIR_PALETTE = ["#3a2616", "#5a3a1c", "#0f0f12", "#7a4a22", "#a35a2a", "#d99a52"];
const SKIN_PALETTE = ["#f1c79a", "#e3a679", "#c98a5a", "#8c5a36", "#5a3a22", "#e6c4a0"];
const SHIRT_PALETTE = ["#3b5bdb", "#2a7d4f", "#7a3a8c", "#a33a3a", "#1f2a4a", "#0e1014"];

const PATTERN: readonly string[] = [
  "HHHHHHHH",
  "HFFFFFFH",
  "HFEFFEFH",
  "HFFFFFFH",
  "HFFMMFFH",
  "HFFFFFFH",
  "HJJJJJJH",
  "SSSSSSSS",
];

function hash32(seed: string): number {
  let h = 5381;
  for (let i = 0; i < seed.length; i++) {
    h = ((h << 5) + h + seed.charCodeAt(i)) | 0;
  }
  return h >>> 0;
}

function pick<T>(palette: readonly T[], n: number): T {
  return palette[n % palette.length] as T;
}

function darken(hex: string, amount: number): string {
  const m = /^#([0-9a-f]{6})$/i.exec(hex);
  if (!m) return hex;
  const raw = parseInt(m[1] as string, 16);
  const r = Math.max(0, Math.round(((raw >> 16) & 0xff) * (1 - amount)));
  const g = Math.max(0, Math.round(((raw >> 8) & 0xff) * (1 - amount)));
  const b = Math.max(0, Math.round((raw & 0xff) * (1 - amount)));
  return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, "0")}`;
}

export function MinecraftHead({ seed, size = 48 }: Props) {
  const h = hash32(seed);
  const hair = pick(HAIR_PALETTE, h);
  const skin = pick(SKIN_PALETTE, h >>> 8);
  const shirt = pick(SHIRT_PALETTE, h >>> 16);
  const jaw = darken(skin, 0.25);
  const mouth = darken(skin, 0.4);
  const eye = "#0a0a0c";

  const colorFor = (code: string): string => {
    switch (code) {
      case "H":
        return hair;
      case "F":
        return skin;
      case "E":
        return eye;
      case "M":
        return mouth;
      case "J":
        return jaw;
      case "S":
        return shirt;
      default:
        return skin;
    }
  };

  return (
    <div
      role="img"
      aria-label={`Avatar pixel de ${seed}`}
      style={{
        width: size,
        height: size,
        display: "grid",
        gridTemplateColumns: "repeat(8, 1fr)",
        gridTemplateRows: "repeat(8, 1fr)",
        imageRendering: "pixelated",
        borderRadius: 6,
        overflow: "hidden",
        boxShadow: "0 2px 6px rgba(0,0,0,0.4)",
      }}
    >
      {PATTERN.flatMap((row, y) =>
        row.split("").map((code, x) => (
          <div key={`${x}-${y}`} style={{ background: colorFor(code) }} />
        )),
      )}
    </div>
  );
}
