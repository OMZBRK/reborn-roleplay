// Les trois cartes du panneau, en SVG affichés comme des images : le chantier en vedette, le Game Plan
// beta et les autres chantiers. Kit Reborn « nuit / laque / or » : l'anneau dit « combien », l'or dit
// « quand », la laque est réservée à la part de l'utilisateur (« toi »).
// Choisi le 01/11 parmi trois pistes dessinées et comparées : base « Laque de nuit », heure aux
// chiffres pleins et liste à colonne de droite reprises d'« Horizon », ligne unique des chantiers pas
// commencés reprise de « Papier et encre ». Fonctions pures, XML strict, polices système, sortie ASCII.

import type { AutreVue, EtapeCarte, HeroVue, PlanVue, TonAutre } from '../types'

const FD = 'Segoe UI Variable Display, Segoe UI, system-ui, sans-serif'
const FT = 'Segoe UI Variable Text, Segoe UI, system-ui, sans-serif'

// Kit « nuit / laque / or »
const IVOIRE = '#f4ecdb'
const GRIS = '#a59f92'
const GRIS2 = '#837e74'
const OR = '#dcb666'
const OR_CLAIR = '#f7e4aa'
const OR_FONCE = '#a77b31'
const OR_SOURD = '#b39a6a'
const LAQUE = '#c8412e'
const LAQUE_TXT = '#e8907c'

const ESP = ' ' // espace insécable : les espaces d'un tspan survivent au rendu
const ESP_FINE = ' '

// Chasses mesurées de Segoe UI Variable (Text / Display, graisse 400), en centièmes de cadratin.
const GL = ' !"#$%&\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~·°…≈–—«»’'
const LT = [27,28,39,59,54,82,80,23,30,30,42,68,22,40,22,39,54,38,54,54,54,54,54,54,54,54,22,22,68,68,68,45,96,65,57,62,70,51,49,69,71,27,36,58,47,90,75,75,56,75,60,53,52,69,62,93,59,55,57,30,30,68,42,27,51,59,46,59,52,31,59,57,24,24,50,24,86,57,59,59,59,35,42,34,57,48,72,46,48,45,30,24,30,68,22,38,73,69,50,100,51,51,23]
const LD = [23,28,38,58,59,81,73,22,29,29,41,67,21,39,21,41,54,37,54,54,54,54,54,54,54,54,21,21,67,67,68,47,95,64,56,70,69,50,48,71,70,26,40,58,46,89,74,75,55,74,59,56,51,68,62,91,58,55,54,29,29,67,43,26,50,59,52,59,52,31,59,56,23,23,49,23,85,56,58,59,59,36,45,36,56,48,72,45,50,42,29,23,29,67,21,37,72,68,52,102,49,49,22]

// ---------- utilitaires ----------
// Les entrées viennent de fichiers tenus à la main : chaque champ est relu défensivement, même typé.
const f = (v: number) => Math.round(v * 10) / 10
const larg = (v: unknown) => {
  const n = Number(v)
  return Number.isFinite(n) && n > 0 ? Math.max(200, n) : 360
}
const pct = (v: unknown) => {
  const n = Number(v)
  return Number.isFinite(n) ? Math.min(100, Math.max(0, Math.round(n))) : 0
}
const nb = (v: unknown): number | null => {
  const n = Number(v)
  return v == null || v === '' || !Number.isFinite(n) ? null : Math.max(0, Math.round(n))
}
const net = (v: unknown): string =>
  (v == null ? '' : String(v))
    .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f￾￿]|\p{Cs}/gu, '')
    .replace(/\s+/g, ' ')
    .trim()
const esc = (s: string) =>
  String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;')
// Tout caractère non ASCII devient une référence numérique : le SVG reste valide quel que soit l'encodage de l'URL data.
const ascii = (s: string) => s.replace(/[^\x00-\x7e]/gu, c => '&#' + c.codePointAt(0) + ';')

type Mesure = { d?: boolean | number; b?: boolean | number; ls?: number }

function mesure(s: string, t: number, o: Mesure = {}): number {
  const tab = o.d ? LD : LT
  let u = 0
  let k = 0
  for (const c of String(s)) {
    k++
    if (c === ESP_FINE) {
      u += 14
      continue
    }
    if (/[œæ]/.test(c)) {
      u += 86
      continue
    }
    if (/[ŒÆ]/.test(c)) {
      u += 100
      continue
    }
    let i = c === ESP ? 0 : GL.indexOf(c)
    if (i < 0) i = GL.indexOf(c.normalize('NFD')[0]!)
    u += i >= 0 ? (tab[i] ?? 64) : 64
  }
  return (u / 100) * t * (o.b ? 1.05 : 1) + (o.ls || 0) * k
}

// Tronque avec « … » pour tenir dans max (px). Chaîne vide si rien ne tient.
function coupe(brut: unknown, t: number, max: number, o?: Mesure): string {
  const s = net(brut)
  if (!s || mesure(s, t, o) <= max) return s
  const c = [...s]
  while (c.length > 1) {
    c.pop()
    const b = c.join('').replace(/[\s ·,;:.\-–—(]+$/u, '')
    if (b && mesure(b + '…', t, o) <= max) return b + '…'
  }
  return ''
}

type OptT = { t: number; c?: string; w?: number; a?: string; op?: number; ls?: number }

// <text> simple. o : t taille, c couleur, w graisse, a ancrage, op opacité, ls interlettrage.
function T(x: number, y: number, s: string, o: OptT): string {
  if (!s) return ''
  return (
    `<text x="${f(x)}" y="${f(y)}" font-size="${f(o.t)}"` +
    (o.w ? ` font-weight="${o.w}"` : '') +
    (o.a ? ` text-anchor="${o.a}"` : '') +
    (o.ls ? ` letter-spacing="${o.ls}"` : '') +
    ` fill="${o.c || IVOIRE}"` +
    (o.op != null ? ` fill-opacity="${o.op}"` : '') +
    `>${esc(s)}</text>`
  )
}

type Morceau = { s: string; c?: string; w?: number; dx?: number }

// Segments enchaînés dans un même <text> ; a : ancrage du bloc entier. Les écarts sont relatifs (dx) :
// une police de repli plus large décale la suite au lieu de la faire chevaucher.
function R(x: number, y: number, t: number, runs: (Morceau | '' | false | null | undefined)[], a?: string): string {
  const r = runs.filter((u): u is Morceau => !!u && !!u.s)
  if (!r.length) return ''
  return (
    `<text x="${f(x)}" y="${f(y)}" font-size="${f(t)}"${a ? ` text-anchor="${a}"` : ''}>` +
    r
      .map(
        u =>
          `<tspan${u.dx ? ` dx="${f(u.dx)}"` : ''} fill="${u.c || IVOIRE}"${u.w ? ` font-weight="${u.w}"` : ''}>${esc(u.s)}</tspan>`,
      )
      .join('') +
    '</text>'
  )
}

// Grand pourcentage : chiffres ivoire, « % » plus petit en or, séparé par une espace fine (un seul <text>).
const pourcent = (x: number, y: number, p: number, t: number, a?: string) =>
  `<text x="${f(x)}" y="${f(y)}" font-size="${f(t)}" font-family="${FD}" font-weight="600" fill="${IVOIRE}"${a ? ` text-anchor="${a}"` : ''}>${p}<tspan dx="${f(t * 0.09)}" font-size="${f(t * 0.42)}" fill="${OR}">%</tspan></text>`
const rect = (x: number, y: number, w: number, h: number, c: string, op?: number) =>
  `<rect x="${f(x)}" y="${f(y)}" width="${f(Math.max(0, w))}" height="${f(h)}" rx="${f(h / 2)}" fill="${c}"${op != null ? ` fill-opacity="${op}"` : ''}/>`
const point = (cx: number, cy: number, r: number, attrs: string) => `<circle cx="${f(cx)}" cy="${f(cy)}" r="${r}" ${attrs}/>`

type Grille = {
  L: (a: number, b: number) => number
  pad: number
  D: number
  inner: number
  ax: number
  wq: number
  eb: number
  ts: number
}

// Grille commune : mêmes marges, même colonne « combien » (l'anneau) et même axe « quand » d'une carte à l'autre.
function grille(W: number): Grille {
  const s = Math.min(1, Math.max(0, (W - 300) / 220))
  const L = (a: number, b: number) => a + (b - a) * s
  const pad = L(18, 24)
  const D = L(100, 134)
  const xg = pad + D
  const x1 = W - pad
  return { L, pad, D, inner: W - 2 * pad, ax: (xg + x1) / 2, wq: x1 - xg - 2 * L(10, 16), eb: L(10, 10.5), ts: L(12, 13) }
}

// Or du balayage : sombre au départ, lumineux en tête de progression.
const BALAYAGE = [
  [0x8e, 0x69, 0x2b],
  [0xd2, 0xa9, 0x58],
  [0xfa, 0xea, 0xb6],
]
function orA(t: number): string {
  const k = t < 0.5 ? 0 : 1
  const u = t < 0.5 ? t * 2 : (t - 0.5) * 2
  const a = BALAYAGE[k]!
  const b = BALAYAGE[k + 1]!
  return '#' + a.map((v, i) => Math.round(v + ((b[i] ?? v) - v) * u).toString(16).padStart(2, '0')).join('')
}
const polaire = (cx: number, cy: number, r: number, deg: number): [number, number] => [
  cx + r * Math.sin((deg * Math.PI) / 180),
  cy - r * Math.cos((deg * Math.PI) / 180),
]
// Arc en tronçons de couleur croissante (dégradé conique simulé), bouts arrondis par deux disques.
function arcBalaye(cx: number, cy: number, r: number, sw: number, p: number): string {
  const tot = (p / 100) * 360
  const n = Math.max(2, Math.ceil(tot / 5))
  const pas = tot / n
  let d = ''
  for (let i = 0; i < n; i++) {
    const a0 = i * pas
    const a1 = Math.min(tot, (i + 1) * pas + 0.8)
    const [x0, y0] = polaire(cx, cy, r, a0)
    const [x1, y1] = polaire(cx, cy, r, a1)
    d += `<path d="M${f(x0)} ${f(y0)}A${f(r)} ${f(r)} 0 0 1 ${f(x1)} ${f(y1)}" stroke="${orA((i + 0.5) / n)}"/>`
  }
  const [sx, sy] = polaire(cx, cy, r, 0)
  const [ex, ey] = polaire(cx, cy, r, tot)
  return `<g fill="none" stroke-width="${f(sw)}">${d}</g><circle cx="${f(sx)}" cy="${f(sy)}" r="${f(sw / 2)}" fill="${orA(0)}"/><circle cx="${f(ex)}" cy="${f(ey)}" r="${f(sw / 2)}" fill="${orA(1)}"/>`
}

function fond(W: number, H: number, id: string): string {
  return (
    `<defs><linearGradient id="${id}f" x1="0" y1="0" x2=".35" y2="1"><stop offset="0" stop-color="#1a233c"/><stop offset=".6" stop-color="#10162a"/><stop offset="1" stop-color="#0a0d18"/></linearGradient>` +
    `<linearGradient id="${id}h" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".06"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient></defs>` +
    `<rect width="${f(W)}" height="${H}" rx="14" fill="url(#${id}f)"/>` +
    `<rect x="1" y="1" width="${f(W - 2)}" height="${f(Math.min(H * 0.5, 110))}" rx="13" fill="url(#${id}h)"/>` +
    `<rect x=".5" y=".5" width="${f(W - 1)}" height="${H - 1}" rx="13.5" fill="none" stroke="${OR}" stroke-opacity=".3"/>`
  )
}

function svg(W: number, hauteur: number, id: string, corps: string, defs = ''): string {
  const H = Math.ceil(hauteur)
  return ascii(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${f(W)}" height="${H}" viewBox="0 0 ${f(W)} ${H}" font-family="${FT}">` +
      fond(W, H, id) +
      (defs ? `<defs>${defs}</defs>` : '') +
      corps +
      '</svg>',
  )
}

function surtitre(G: Grille, y: number, s: string): string {
  return T(G.pad, y, coupe(net(s).toUpperCase(), G.eb, G.inner, { ls: 1.5 }), { t: G.eb, c: OR, op: 0.8, ls: 1.5 })
}

type Fin = { avant: string; coeur: string; apres: string }

// « demain vers 14 h 20 » → { avant: 'demain', coeur: '14 h 20' } ; « vers le 24/11 » → { coeur: '24/11' }
function decoupe(fin: unknown): Fin | null {
  const s = net(fin)
  if (!s) return null
  const m = s.match(/\d{1,2}\s*h(?:\s*\d{2})?/) || s.match(/\d{1,2}\/\d{1,2}(?:\/\d{2,4})?/)
  if (!m) return { avant: '', coeur: s.replace(/^(vers|env\.?|environ)\s+(le\s+)?/i, ''), apres: '' }
  const index = m.index ?? 0
  const avant = s
    .slice(0, index)
    .split(' ')
    .filter(w => w && !/^(vers|le|la|à|a|env\.?|environ)$/i.test(w))
    .join(' ')
  const coeur = m[0].includes('/') ? m[0] : m[0].replace(/\s*h\s*/, ' h ').trim()
  return { avant, coeur, apres: s.slice(index + m[0].length).trim() }
}

// Grande valeur dorée. Une heure ou une date garde ses chiffres pleins ; le « h » passe plus petit et la barre
// d'une date en or sourd : « 3 h 40 », « 24/11 ». Tout autre libellé (« Terminé ») reste d'un seul tenant.
const PETIT = 0.6
type MorceauValeur = { s: string; k: 0 | 1 | 2 }
const morceaux = (v: string): MorceauValeur[] =>
  /^\d{1,2} h( \d{2})?$|^\d{1,2}\/\d{1,2}(\/\d{2,4})?$/.test(v)
    ? v
        .split(/(\d+)/)
        .filter(Boolean)
        .map(s => ({ s: s.replace(/ /g, ESP), k: /\d/.test(s) ? 0 : /h/.test(s) ? 2 : 1 }))
    : [{ s: v, k: 0 }]
const largeurValeur = (v: string, t: number) =>
  morceaux(v).reduce((w, m) => w + mesure(m.s, m.k === 2 ? t * PETIT : t, { d: 1, b: 1 }), 0)
function valeur(x: number, y: number, v: string, t: number, a: string, fill: string): string {
  if (!v) return ''
  const inner = morceaux(v)
    .map(m =>
      m.k === 0 ? esc(m.s) : `<tspan fill="${OR_SOURD}"${m.k === 2 ? ` font-size="${f(t * PETIT)}"` : ''}>${esc(m.s)}</tspan>`,
    )
    .join('')
  return `<text x="${f(x)}" y="${f(y)}" font-size="${f(t)}" font-family="${FD}" font-weight="600" fill="${fill}"${a ? ` text-anchor="${a}"` : ''}>${inner}</text>`
}
// Ajuste une grande valeur à la largeur : on réduit le corps jusqu'à min, puis on tronque.
function ajuste(v: string, taille: number, max: number, min: number): { v: string; t: number } {
  let t = taille
  const w = largeurValeur(v, t)
  if (w > max) t = Math.max(min, (t * max) / w)
  return { v: largeurValeur(v, t) > max ? coupe(v, t, max, { d: 1, b: 1 }) : v, t }
}
// Le mot « toi » passe en laque, comme la part de l'utilisateur dans la bande des étapes et dans la liste.
const toiEnLaque = (s: string, c: string): Morceau[] =>
  s.split(/\b(toi)\b/i).map((p, i) => ({ s: p.replace(/^ | $/g, ESP), c: i % 2 ? LAQUE_TXT : c }))

type LignePile = { t: number; a: number; d: number; g?: number }

// Pile de lignes centrée verticalement sur cy (a / d : hampes en cadratin, g : écart avant).
function pile(cy: number, lignes: LignePile[]): number[] {
  let b = 0
  let prev: LignePile | null = null
  const out = lignes.map(l => {
    b = prev ? b + prev.d * prev.t + (l.g || 0) + l.a * l.t : l.a * l.t
    prev = l
    return b
  })
  const dernier = lignes[lignes.length - 1]!
  const dec = cy - (b + dernier.d * dernier.t) / 2
  return out.map(v => v + dec)
}

// ---------- carte 1 : le chantier en vedette ----------
export function carteHero(hero: HeroVue, largeur: number): string {
  const h: Partial<HeroVue> = hero && typeof hero === 'object' ? hero : {}
  const W = larg(largeur)
  const G = grille(W)
  const L = G.L
  const p = pct(h.pourcent)
  const o: string[] = []
  let y = G.pad

  const sur = net(h.groupe)
  if (sur) {
    y += 7.5
    o.push(surtitre(G, y, sur))
  }
  const tt = L(15.5, 17.5)
  const titre = coupe(h.titre, tt, G.inner, { b: 1 })
  if (titre) {
    y += (sur ? 8 : 0) + tt * 0.72
    o.push(T(G.pad, y, titre, { t: tt, w: 600 }))
  }
  if (sur || titre) y += tt * 0.22 + L(16, 20)

  // Anneau
  const D = G.D
  const sw = L(7, 8.5)
  const r = D / 2 - sw / 2
  const cx = G.pad + D / 2
  const cy = y + D / 2
  const defs =
    `<linearGradient id="ha" gradientUnits="userSpaceOnUse" x1="0" y1="${f(cy - r)}" x2="0" y2="${f(cy + r)}"><stop offset="0" stop-color="${OR_CLAIR}"/><stop offset=".55" stop-color="${OR}"/><stop offset="1" stop-color="${OR_FONCE}"/></linearGradient>` +
    `<radialGradient id="hl" cx="${f(cx)}" cy="${f(cy)}" r="${f(D * 0.62)}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="${OR}" stop-opacity=".11"/><stop offset="1" stop-color="${OR}" stop-opacity="0"/></radialGradient><linearGradient id="ht" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f5dea0"/><stop offset="1" stop-color="#c99c48"/></linearGradient>`
  o.push(`<circle cx="${f(cx)}" cy="${f(cy)}" r="${f(D * 0.62)}" fill="url(#hl)"/>`)
  o.push(`<circle cx="${f(cx)}" cy="${f(cy)}" r="${f(r)}" fill="none" stroke="${IVOIRE}" stroke-opacity=".075" stroke-width="${f(sw)}"/>`)
  o.push(`<circle cx="${f(cx)}" cy="${f(cy)}" r="${f(r - sw / 2 - 4)}" fill="none" stroke="${OR}" stroke-opacity=".13"/>`)
  if (p >= 100) o.push(`<circle cx="${f(cx)}" cy="${f(cy)}" r="${f(r)}" fill="none" stroke="url(#ha)" stroke-width="${f(sw)}"/>`)
  else if (p > 0) o.push(arcBalaye(cx, cy, r, sw, p))
  else o.push(`<circle cx="${f(cx)}" cy="${f(cy - r)}" r="${f(sw / 2)}" fill="${OR}" fill-opacity=".5"/>`)
  const nt = D * (p >= 100 ? 0.29 : 0.35)
  o.push(pourcent(cx, cy + nt * 0.35, p, nt, 'middle'))

  // Colonne « quand » : centrée à droite de l'anneau, sur le même axe que la date de la carte du Plan.
  const fin = decoupe(h.fin)
  let lab = ''
  let emph = ''
  let big = ''
  let sub = net(h.dans)
  if (fin) {
    lab = net(h.finLibelle)
    emph = fin.avant
    big = fin.coeur
    if (fin.apres) sub = [fin.apres, sub].filter(Boolean).join(' · ')
  } else big = net(h.finLibelle) || (p >= 100 ? 'Terminé' : '')
  const B = ajuste(big, L(31, 42), G.wq, 18)
  const lt = L(11.5, 13)
  const st = L(12, 13.5)
  if (lab || emph) {
    const full = [lab, emph].filter(Boolean).join(' ')
    if (mesure(full, lt, { b: !!emph }) > G.wq) {
      lab = coupe(full, lt, G.wq)
      emph = ''
    }
  }
  sub = coupe(sub, st, G.wq)
  if (B.v) {
    const lignes: LignePile[] = []
    const kinds: ('lab' | 'big' | 'sub')[] = []
    if (lab || emph) {
      lignes.push({ t: lt, a: 0.72, d: 0.24 })
      kinds.push('lab')
    }
    lignes.push({ t: B.t, a: 0.71, d: 0.03, g: lignes.length ? L(7, 8) : 0 })
    kinds.push('big')
    if (sub) {
      lignes.push({ t: st, a: 0.72, d: 0.24, g: L(8, 9) })
      kinds.push('sub')
    }
    const bs = pile(cy, lignes)
    kinds.forEach((k, i) => {
      const yy = bs[i] ?? cy
      if (k === 'lab')
        o.push(R(G.ax, yy, lt, [...toiEnLaque(lab, GRIS), emph && { s: (lab ? ESP : '') + emph, c: IVOIRE, w: 600 }], 'middle'))
      if (k === 'big') o.push(valeur(G.ax, yy, B.v, B.t, 'middle', 'url(#ht)'))
      if (k === 'sub') o.push(T(G.ax, yy, sub, { t: st, c: GRIS, a: 'middle' }))
    })
  }
  y += D

  // Bande des étapes
  let et: EtapeCarte[] = Array.isArray(h.etapes) ? h.etapes.filter(e => e && typeof e === 'object') : []
  const tot = Math.min(nb(h.total) || 0, 60)
  const nfa = Math.min(nb(h.faites) || 0, tot)
  if (!et.length && tot)
    et = Array.from({ length: tot }, (_, i) => ({
      titre: '',
      etat: i < nfa ? 'fait' : i === nfa && h.enCours ? 'en-cours' : 'a-faire',
    }))
  if (et.length) {
    y += L(20, 24)
    const n = et.length
    const gp = n > 40 ? 1 : n > 20 ? 2 : 3
    const sg = (G.inner - gp * (n - 1)) / n
    const nf = et.filter(e => e.etat === 'fait').length
    let toiX: number | null = null
    let k = 0
    et.forEach((e, i) => {
      const x = G.pad + i * (sg + gp)
      const fait = e.etat === 'fait'
      const cours = e.etat === 'en-cours'
      if (cours) {
        o.push(`<rect x="${f(x - 1.5)}" y="${f(y - 3)}" width="${f(sg + 3)}" height="10" rx="5" fill="${e.toi ? LAQUE : OR}" fill-opacity=".2"/>`)
        o.push(rect(x, y - 1.5, sg, 7, e.toi ? LAQUE : orA(1)))
      } else if (fait) o.push(rect(x, y, sg, 4, orA(nf > 1 ? 0.3 + (0.45 * k++) / (nf - 1) : 0.6)))
      else o.push(rect(x, y, sg, 4, e.toi ? LAQUE : IVOIRE, e.toi ? 0.95 : 0.1))
      if (e.toi && !fait && toiX == null) toiX = x + sg / 2
    })
    y += 4
    if (toiX != null) {
      y += 12
      const a = toiX + 8 > W - G.pad ? 'end' : 'middle'
      o.push(T(a === 'end' ? W - G.pad : toiX, y, 'toi', { t: 10, c: LAQUE_TXT, a, w: 600 }))
    }
  }

  // En cours, puis la suite
  const ts = G.ts
  const ec = net(h.enCours)
  if (ec) {
    y += L(23, 26)
    const lb = 'En cours'
    const wl = mesure(lb, ts)
    const gx = 7
    const room = G.inner - wl - gx
    let suf = net(h.depuis) ? `${ESP}·${ESP}depuis ` + net(h.depuis) : ''
    if (mesure(suf, ts) > room * 0.5) suf = ''
    const val = coupe(ec, ts, room - mesure(suf, ts), { b: 1 })
    o.push(R(G.pad, y, ts, [{ s: lb, c: GRIS }, { s: val, dx: gx, w: 600 }, { s: suf, c: GRIS }]))
  }
  const en = net(h.ensuite)
  if (en) {
    y += ec ? L(18, 19.5) : L(23, 26)
    o.push(T(G.pad, y, coupe(ec ? en : en.charAt(0).toUpperCase() + en.slice(1), ts, G.inner), { t: ts, c: GRIS }))
  }
  y += (ec || en ? 4 : 0) + G.pad
  return svg(W, y, 'h', o.join(''), defs)
}

// ---------- carte 2 : le Game Plan ----------
export function cartePlan(plan: PlanVue, largeur: number): string {
  const P: Partial<PlanVue> = plan && typeof plan === 'object' ? plan : {}
  const W = larg(largeur)
  const G = grille(W)
  const L = G.L
  const p = pct(P.pourcent)
  const o: string[] = []
  let y = G.pad + 7.5
  o.push(surtitre(G, y, net(P.titre) || 'Game Plan'))

  const nt = L(31, 38)
  const base = y + L(22, 25) + nt * 0.71
  o.push(pourcent(G.pad, base, p, nt))
  let bas = base

  // Colonne « quand », sur le même axe que l'heure de la carte du chantier
  const fin = decoupe(P.fin)
  let lab = ''
  let big = ''
  let sub = ''
  if (fin) {
    lab = 'Fin à ce rythme'
    big = fin.coeur.replace(/^fin\s+/i, '')
    sub = net(P.rythme)
  } else if (p >= 100) big = 'Terminé'
  const B = ajuste(big, L(21, 25), G.wq, 15)
  if (B.v) {
    const lt = L(11.5, 12.5)
    o.push(valeur(G.ax, base, B.v, B.t, 'middle', 'url(#pt)'))
    if (lab) o.push(T(G.ax, base - B.t * 0.71 - L(7, 8), coupe(lab, lt, G.wq), { t: lt, c: GRIS, a: 'middle' }))
    if (sub) {
      bas = base + L(8, 9) + lt * 0.72
      o.push(T(G.ax, bas, coupe(sub, lt, G.wq), { t: lt, c: GRIS, a: 'middle' }))
    }
  }

  // Barre
  y = bas + L(18, 20)
  const fw = (G.inner * p) / 100
  const defs = `<linearGradient id="pb" gradientUnits="userSpaceOnUse" x1="${f(G.pad)}" y1="0" x2="${f(G.pad + Math.max(fw, 1))}" y2="0"><stop offset="0" stop-color="${orA(0)}"/><stop offset=".5" stop-color="${orA(0.5)}"/><stop offset="1" stop-color="${orA(1)}"/></linearGradient><linearGradient id="pt" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f5dea0"/><stop offset="1" stop-color="#c99c48"/></linearGradient>`
  o.push(rect(G.pad, y, G.inner, 4, IVOIRE, 0.075))
  if (p > 0) o.push(rect(G.pad, y, Math.max(fw, 4), 4, 'url(#pb)'))
  y += 4

  // Compteurs
  const ts = G.ts
  const liv = nb(P.livres)
  const parts: [number, string][] = (
    [
      [liv, liv !== null && liv > 1 ? 'livrés' : 'livré'],
      [nb(P.enCours), 'en cours'],
      [nb(P.aFaire), 'à faire'],
    ] as [number | null, string][]
  ).filter((x): x is [number, string] => x[0] !== null && x[0] > 0)
  if (!parts.length && liv != null) parts.push([liv, 'livré'])
  if (parts.length) {
    y += L(21, 23)
    const runs: Morceau[] = []
    parts.forEach(([v, mot], i) => {
      runs.push({ s: (i ? `${ESP}${ESP}·${ESP}${ESP}` : '') + v, w: 600 })
      runs.push({ s: ESP + mot, c: GRIS })
    })
    if (mesure(parts.map(([v, m]) => v + ' ' + m).join('  ·  '), ts, { b: 1 }) <= G.inner) o.push(R(G.pad, y, ts, runs))
    else o.push(T(G.pad, y, coupe(parts.map(([v, m]) => v + ' ' + m).join(' · '), ts, G.inner), { t: ts, c: GRIS }))
  }
  const pe = net(P.pointEnCours)
  if (pe) {
    y += parts.length ? L(18, 19.5) : L(21, 23)
    const lb = 'En cours'
    const wl = mesure(lb, ts)
    const gx = 7
    o.push(R(G.pad, y, ts, [{ s: lb, c: GRIS }, { s: coupe(pe, ts, G.inner - wl - gx, { b: 1 }), dx: gx, w: 600 }]))
  }
  y += (parts.length || pe ? 4 : 0) + G.pad
  return svg(W, y, 'p', o.join(''), defs)
}

// ---------- carte 3 : les autres chantiers ----------
// À qui revient la balle : laque = toi, or = Claude, gris = personne pour l'instant.
const TONS: Record<TonAutre, { point: string; c: string; w?: number }> = {
  toi: { point: `fill="${LAQUE}"`, c: LAQUE_TXT, w: 600 },
  claude: { point: `fill="${OR}"`, c: GRIS },
  neutre: { point: `fill="none" stroke="${IVOIRE}" stroke-opacity=".35"`, c: GRIS2 },
}

export function listeChantiers(autres: AutreVue[], largeur: number): string {
  const A = Array.isArray(autres) ? autres.filter(a => a && typeof a === 'object') : []
  const W = larg(largeur)
  const G = grille(W)
  const L = G.L
  const o: string[] = []
  const x1 = W - G.pad
  let y = G.pad + 7.5
  o.push(surtitre(G, y, 'Autres chantiers'))
  if (!A.length) {
    y += L(24, 26)
    o.push(T(G.pad, y, 'Aucun autre chantier', { t: G.ts, c: GRIS2 }))
    return svg(W, y + 4 + G.pad, 'l', o.join(''))
  }
  const tt = L(13, 14)
  const st = L(11.5, 12)
  const qt = L(12, 12.5)
  const vus = A.length > 8 ? A.slice(0, 7) : A
  const reste = A.length - vus.length
  const lignes = vus.map(a => {
    const p = pct(a.pourcent)
    const ton: TonAutre = a.ton in TONS ? a.ton : 'neutre'
    const s = net(a.statut)
    // Pas commencé et personne n'attend : une seule ligne discrète, le statut tient lieu de « 0 % ».
    return { a, p, ton, s, seule: p === 0 && ton === 'neutre' && mesure(s, st) <= G.inner * 0.4 }
  })
  // Colonne des statuts, commune à toutes les lignes : les barres ont toutes la même longueur.
  const colSt = Math.min(
    G.inner * 0.46,
    Math.max(0, ...lignes.filter(l => !l.seule && l.s).map(l => mesure(l.s, st, { b: TONS[l.ton].w }) + 10)),
  )
  const wbar = G.inner - colSt - (colSt ? L(16, 20) : 0)
  y += L(6, 8)
  lignes.forEach(l => {
    const ton = TONS[l.ton]
    y += L(22, 24)
    const statut = l.s ? coupe(l.s, st, (l.seule ? G.inner * 0.4 : colSt) - 10, { b: ton.w }) : ''
    const ws = statut ? mesure(statut, st, { b: ton.w }) : 0
    const yStatut = l.seule ? y : y + L(15, 16)
    if (statut) {
      o.push(point(x1 - ws - 7, yStatut - st * 0.34, 2.6, ton.point))
      o.push(T(x1, yStatut, statut, { t: st, c: ton.c, w: ton.w, a: 'end' }))
    }
    if (l.seule) {
      o.push(T(G.pad, y, coupe(l.a.titre, tt, G.inner - ws - 24), { t: tt, c: GRIS }))
      y += L(5, 6)
      return
    }
    const q = l.p + `${ESP}%`
    const wq = mesure(q, qt, { b: 1 })
    o.push(T(G.pad, y, coupe(l.a.titre, tt, G.inner - wq - 14), { t: tt }))
    o.push(T(x1, y, q, { t: qt, w: 600, a: 'end', c: l.p > 0 ? IVOIRE : GRIS2 }))
    y = yStatut
    o.push(rect(G.pad, y - 5, wbar, 3, IVOIRE, 0.08))
    if (l.p > 0) o.push(rect(G.pad, y - 5, Math.max((wbar * l.p) / 100, 3), 3, 'url(#lg)'))
    y += L(9, 10)
  })
  if (reste > 0) {
    y += L(24, 26)
    o.push(T(G.pad, y, `+ ${reste} autres chantiers`, { t: G.ts, c: GRIS2 }))
    y += 4
  }
  return svg(
    W,
    y + G.pad,
    'l',
    o.join(''),
    `<linearGradient id="lg"><stop offset="0" stop-color="${orA(0.15)}"/><stop offset="1" stop-color="${orA(0.85)}"/></linearGradient>`,
  )
}
