// Lecture des sources du panneau, sans effet de bord : fichier de suivi, CHANTIERS §4,
// ETAT_DES_LIEUX §2, journaux et méta des workflows. Tout ce qui touche au disque est dans calcul.ts.

import type {
  AgentWf,
  ArtefactVue,
  AutreVue,
  ChantierVue,
  DecisionVue,
  EtapeVue,
  EtatAgentWf,
  EtatChantier,
  EtatEtape,
  EtatWorkflow,
  HeroVue,
  ItemAToi,
  PhaseWf,
  PlanVue,
  TonAutre,
  TypeAToi,
} from '../types'

const ETATS_CHANTIER: readonly EtatChantier[] = [
  'en-cours',
  'recette',
  'a-deployer',
  'attente',
  'bloque',
  'a-faire',
  'plus-tard',
  'fait',
]
const ETATS_ETAPE: readonly EtatEtape[] = ['fait', 'en-cours', 'a-faire', 'echec', 'attente']
const TYPES_ATOI: readonly TypeAToi[] = ['commande', 'test', 'decision', 'deploiement', 'validation', 'info']

const texte = (v: unknown): string | undefined =>
  typeof v === 'string' && v.trim() !== '' ? v.trim() : undefined

const dans = <T extends string>(v: unknown, liste: readonly T[], defaut: T): T =>
  typeof v === 'string' && (liste as readonly string[]).includes(v) ? (v as T) : defaut

/** Markdown d'une cellule ou d'une puce, ramené à du texte simple. */
export const nettoyer = (s: string): string =>
  s
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\*\*|~~|`/g, '')
    .replace(/\s+/g, ' ')
    .trim()

// ── Fichier de suivi (.claude/suivi/etat.json) ───────────────────────────────

export type EtapeBrute = {
  titre?: unknown
  etat?: unknown
  agent?: unknown
  toi?: unknown
  estimation?: unknown
  debutLe?: unknown
}

export type ChantierBrut = {
  id?: unknown
  titre?: unknown
  groupe?: unknown
  etat?: unknown
  etapes?: unknown
  prochaine?: unknown
  ensuite?: unknown
  branche?: unknown
  workflows?: unknown
  vedette?: unknown
  majLe?: unknown
}

export type EtatSuivi = {
  majLe?: string
  majPar?: string
  aToi: ItemAToi[]
  chantiers: (ChantierBrut & { id: string; titre: string })[]
  /** Ids présents deux fois : seul le premier est gardé, la session doit corriger le fichier. */
  doublons: string[]
}

// PowerShell 5.1 écrit un BOM en tête des fichiers UTF-8, que JSON.parse refuse.
export const sansBom = (s: string): string => s.replace(/^﻿/, '')

export function lireEtatSuivi(json: string): EtatSuivi {
  const brut = JSON.parse(sansBom(json)) as Record<string, unknown>
  const doublons: string[] = []
  const vus = new Set<string>()
  const unique = (id: string): boolean => {
    if (!vus.has(id)) {
      vus.add(id)
      return true
    }
    if (!doublons.includes(id)) doublons.push(id)
    return false
  }
  const aToi: ItemAToi[] = []
  for (const it of Array.isArray(brut.aToi) ? brut.aToi : []) {
    const o = (it ?? {}) as Record<string, unknown>
    const id = texte(o.id)
    const titre = texte(o.titre)
    if (!id || !titre || !unique(id)) continue
    aToi.push({
      id,
      titre,
      type: dans(o.type, TYPES_ATOI, 'info'),
      detail: texte(o.detail),
      commande: typeof o.commande === 'string' && o.commande !== '' ? o.commande : undefined,
      chantier: texte(o.chantier),
      depuis: texte(o.depuis),
      fait: o.fait === true,
      faitLe: texte(o.faitLe),
    })
  }
  vus.clear()
  const chantiers: EtatSuivi['chantiers'] = []
  for (const ch of Array.isArray(brut.chantiers) ? brut.chantiers : []) {
    const o = (ch ?? {}) as ChantierBrut
    const id = texte(o.id)
    const titre = texte(o.titre)
    if (id && titre && unique(id)) chantiers.push({ ...o, id, titre })
  }
  return { majLe: texte(brut.majLe), majPar: texte(brut.majPar), aToi, chantiers, doublons }
}

/** Le fichier avec un élément « à toi » marqué fait, prêt à réécrire tel quel. */
export function marquerFaitDans(json: string, id: string, quand: string): string | null {
  const brut = JSON.parse(sansBom(json)) as Record<string, unknown>
  const liste = Array.isArray(brut.aToi) ? (brut.aToi as Record<string, unknown>[]) : []
  const item = liste.find(it => it && it.id === id)
  if (!item) return null
  item.fait = true
  item.faitLe = quand
  return JSON.stringify(brut, null, 2) + '\n'
}

// Durées par défaut d'une étape sans estimation (minutes) : la part de l'utilisateur est courte,
// une étape de code vaut la moyenne mesurée des agents du workflow, sinon trois quarts d'heure.
const DUREE_TOI_MIN = 15
const DUREE_ETAPE_MIN = 45
const DUREE_MESUREE_MAX_MIN = 12 * 60

const dureeMesuree = (a: AgentWf): number | undefined => {
  if (a.etat !== 'fait' || a.debutMs === undefined || a.activiteMs === undefined) return undefined
  const min = (a.activiteMs - a.debutMs) / 60_000
  return min > 1 && min < DUREE_MESUREE_MAX_MIN ? min : undefined
}

/**
 * Un chantier tel que le panneau le dessine. Une étape liée à un agent de workflow (`agent`)
 * prend l'état que le journal lui donne et sa durée mesurée, ce qui garde les lots en cours à
 * jour sans retouche. Le pourcentage est pondéré par la durée des étapes ; l'heure de fin est
 * celle où ma part s'arrête : la fin du chantier, ou le moment où il attend l'utilisateur.
 */
export function vueChantier(
  ch: EtatSuivi['chantiers'][number],
  agentsDe: (noms: string[]) => Map<string, AgentWf>,
  maintenant: number,
): ChantierVue {
  const noms = Array.isArray(ch.workflows) ? ch.workflows.filter((n): n is string => typeof n === 'string') : []
  const direct = noms.length > 0 ? agentsDe(noms) : new Map<string, AgentWf>()
  const mesurees = [...direct.values()].map(dureeMesuree).filter((m): m is number => m !== undefined)
  const moyenne = mesurees.length > 0 ? mesurees.reduce((s, m) => s + m, 0) / mesurees.length : DUREE_ETAPE_MIN

  const etapes: EtapeVue[] = []
  const calcul: EtapeCalcul[] = []
  for (const et of Array.isArray(ch.etapes) ? (ch.etapes as EtapeBrute[]) : []) {
    const titre = texte(et?.titre)
    if (!titre) continue
    const agent = texte(et.agent)
    const live = agent ? direct.get(agent) : undefined
    const etat: EtatEtape = live?.etat ?? dans(et.etat, ETATS_ETAPE, 'a-faire')
    const toi = et.toi === true
    const estimation = typeof et.estimation === 'number' && et.estimation > 0 ? et.estimation : undefined
    const minutes =
      (live ? dureeMesuree(live) : undefined) ?? estimation ?? (toi ? DUREE_TOI_MIN : agent ? moyenne : DUREE_ETAPE_MIN)
    // Le temps écoulé d'une étape en cours vient de son agent, sinon de l'heure où la session l'a lancée
    // (`debutLe`, à défaut la mise à jour du chantier) : sans ancre, son heure de fin reculerait avec l'horloge.
    const debut = live ? live.debutMs : (msParis(et.debutLe) ?? msParis(ch.majLe))
    const ecouleMin = etat === 'en-cours' && debut !== undefined ? Math.max(0, (maintenant - debut) / 60_000) : undefined
    etapes.push({ titre, etat, enDirect: live !== undefined, toi })
    calcul.push({ titre, etat, toi, minutes, ecouleMin })
  }

  const etat = dans(ch.etat, ETATS_CHANTIER, 'a-faire')
  const av = avancement(calcul)
  const enCours = etapes.find(e => e.etat === 'en-cours')
  const debutEnCours = calcul.find(e => e.etat === 'en-cours')?.ecouleMin
  const actif = etat === 'en-cours' && enCours !== undefined
  // Une étape en cours sans agent ni heure de début : on ne sait pas quand elle finira, on ne l'invente pas.
  const sansAncre = calcul.some(e => e.etat === 'en-cours' && !e.toi && e.ecouleMin === undefined)
  let finLibelle = LIBELLE_ETAT[etat]
  let fin: string | undefined
  let dansCombien: string | undefined
  let ensuite = texte(ch.ensuite)
  if (etapes.length > 0 && av.restantes === 0) {
    // Toutes les étapes faites ne veulent pas dire fini : un déploiement ou une recette attend encore l'utilisateur.
    if (etat === 'en-cours' || etat === 'fait') {
      finLibelle = 'Terminé'
      ensuite = undefined
    }
  } else if (actif && av.restantMin > 0 && sansAncre) {
    ensuite ??= av.prochaineToi ? `puis ${listeFr([av.prochaineToi, ...av.apresToi].map(minuscule), 2)}` : undefined
  } else if (actif && av.restantMin > 0) {
    finLibelle = av.prochaineToi ? 'Prêt pour toi' : 'Fin estimée'
    fin = formatFin(maintenant + av.restantMin * 60_000, maintenant)
    dansCombien = formatDans(av.restantMin)
    ensuite ??= av.prochaineToi ? `puis ${listeFr([av.prochaineToi, ...av.apresToi].map(minuscule), 2)}` : undefined
  } else if (av.prochaineToi && av.restantMin === 0) {
    finLibelle = 'À toi'
    ensuite ??= av.prochaineToi
  }

  return {
    id: ch.id,
    titre: ch.titre,
    groupe: texte(ch.groupe),
    etat,
    etapes,
    faites: etapes.filter(e => e.etat === 'fait').length,
    prochaine: texte(ch.prochaine),
    branche: texte(ch.branche),
    majLe: texte(ch.majLe),
    enDirect: etapes.some(e => e.enDirect && e.etat === 'en-cours'),
    vedette: ch.vedette === true,
    workflows: noms,
    pourcent: etat === 'fait' ? 100 : av.pourcent,
    finLibelle,
    fin,
    dans: dansCombien,
    ensuite,
    etapeEnCours: enCours?.titre,
    depuis: debutEnCours !== undefined ? formatDuree(debutEnCours) : undefined,
  }
}

const LIBELLE_ETAT: Record<EtatChantier, string> = {
  'en-cours': 'En cours',
  recette: 'En recette',
  'a-deployer': 'À déployer',
  attente: 'En attente',
  bloque: 'Bloqué',
  'a-faire': 'À faire',
  'plus-tard': 'Plus tard',
  fait: 'Terminé',
}

const minuscule = (s: string) => s.charAt(0).toLowerCase() + s.slice(1)

/** « a », « a et b », « a, b et 2 autres étapes » au-delà de `max`. */
const listeFr = (items: string[], max: number): string => {
  const vus = items.slice(0, max)
  const reste = items.length - vus.length
  if (reste > 0) vus.push(reste === 1 ? 'une autre étape' : `${reste} autres étapes`)
  return vus.length <= 1 ? (vus[0] ?? '') : `${vus.slice(0, -1).join(', ')} et ${vus[vus.length - 1]}`
}

// ── Avancement et heure de fin ───────────────────────────────────────────────

export type EtapeCalcul = { titre: string; etat: EtatEtape; toi: boolean; minutes: number; ecouleMin?: number }

export type Avancement = {
  pourcent: number
  restantes: number
  /** Minutes de travail avant la part de l'utilisateur (ou avant la fin s'il n'en a pas). */
  restantMin: number
  prochaineToi?: string
  apresToi: string[]
}

export function avancement(etapes: EtapeCalcul[]): Avancement {
  const total = etapes.reduce((s, e) => s + e.minutes, 0)
  const restantes = etapes.filter(e => e.etat !== 'fait')
  if (etapes.length === 0 || total <= 0) return { pourcent: 0, restantes: restantes.length, restantMin: 0, apresToi: [] }

  let fait = 0
  for (const e of etapes) {
    if (e.etat === 'fait') fait += e.minutes
    // Une étape en cours compte pour son temps écoulé, jamais plus de 90 % d'elle-même.
    else if (e.etat === 'en-cours') fait += e.minutes * Math.min(0.9, (e.ecouleMin ?? e.minutes / 2) / e.minutes)
  }

  let restantMin = 0
  let prochaineToi: string | undefined
  const apresToi: string[] = []
  for (const e of restantes) {
    if (prochaineToi !== undefined) apresToi.push(e.titre)
    else if (e.toi) prochaineToi = e.titre
    else if (e.etat === 'en-cours') {
      // Une étape qui dépasse son estimation garde un reste plancher plutôt que de tomber à zéro.
      const ecoule = e.ecouleMin ?? e.minutes / 2
      restantMin += Math.max(e.minutes - ecoule, Math.max(10, e.minutes * 0.15))
    } else restantMin += e.minutes
  }

  const pourcent = restantes.length === 0 ? 100 : Math.min(99, Math.round((100 * fait) / total))
  return { pourcent, restantes: restantes.length, restantMin: Math.round(restantMin), prochaineToi, apresToi }
}

// Heures affichées à Paris ; l'environnement du mod peut ne pas connaître les fuseaux.
// Règle européenne : heure d'été du dernier dimanche de mars au dernier dimanche d'octobre, à 01:00 UTC.
const dernierDimanche = (annee: number, mois: number): number => {
  const fin = new Date(Date.UTC(annee, mois + 1, 0))
  return Date.UTC(annee, mois, fin.getUTCDate() - fin.getUTCDay(), 1)
}

export const decalageParis = (ms: number): number => {
  const annee = new Date(ms).getUTCFullYear()
  return ms >= dernierDimanche(annee, 2) && ms < dernierDimanche(annee, 9) ? 7200_000 : 3600_000
}

/**
 * Un horodatage écrit par les sessions (« 2026-11-02T07:30 » ou « 2026-11-02 07:30 », heure de Paris), en ms.
 * Une date seule ne dit pas l'heure : elle ne sert pas d'ancre (undefined). Un décalage explicite est respecté.
 */
export function msParis(v: unknown): number | undefined {
  if (typeof v !== 'string') return undefined
  const s = v.trim()
  const m = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?(Z|[+-]\d{2}:?\d{2})?$/.exec(s)
  if (!m) return undefined
  if (m[7]) {
    const t = Date.parse(s.replace(' ', 'T'))
    return Number.isFinite(t) ? t : undefined
  }
  const local = Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]), Number(m[4]), Number(m[5]), Number(m[6] ?? 0))
  return local - decalageParis(local - 3600_000)
}

type PartiesHeure = { jour: string; heure: number; minute: number; jj: string; mm: string }

const partiesParis = (ms: number): PartiesHeure => {
  try {
    const f = new Intl.DateTimeFormat('fr-FR', {
      timeZone: 'Europe/Paris',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
    const p = Object.fromEntries(f.formatToParts(ms).map(x => [x.type, x.value]))
    return { jour: `${p.year}-${p.month}-${p.day}`, heure: Number(p.hour), minute: Number(p.minute), jj: p.day ?? '', mm: p.month ?? '' }
  } catch {
    const d = new Date(ms + decalageParis(ms))
    const jj = String(d.getUTCDate()).padStart(2, '0')
    const mm = String(d.getUTCMonth() + 1).padStart(2, '0')
    return { jour: `${d.getUTCFullYear()}-${mm}-${jj}`, heure: d.getUTCHours(), minute: d.getUTCMinutes(), jj, mm }
  }
}

const heureFr = (h: number, m: number) => (m === 0 ? `${h} h` : `${h} h ${String(m).padStart(2, '0')}`)

/** « vers 3 h 40 », « demain vers 9 h », « le 03/11 vers 14 h 20 » ; arrondi aux 10 minutes. */
export function formatFin(finMs: number, maintenant: number): string {
  const arrondi = Math.round(finMs / 600_000) * 600_000
  const fin = partiesParis(arrondi)
  const ici = partiesParis(maintenant)
  const demain = partiesParis(maintenant + 24 * 3600_000)
  const h = heureFr(fin.heure, fin.minute)
  if (fin.jour === ici.jour) return `vers ${h}`
  if (fin.jour === demain.jour) return `demain vers ${h}`
  return `le ${fin.jj}/${fin.mm} vers ${h}`
}

/** « 21:32 », à l'heure de Paris. */
export function heureParis(ms: number): string {
  const p = partiesParis(ms)
  return `${String(p.heure).padStart(2, '0')}:${String(p.minute).padStart(2, '0')}`
}

/** Horodatage à l'heure de Paris, « 2026-11-01T21:32 », comme ceux que les sessions écrivent dans le suivi. */
export function horodatage(ms: number): string {
  const p = partiesParis(ms)
  return `${p.jour}T${String(p.heure).padStart(2, '0')}:${String(p.minute).padStart(2, '0')}`
}

/** « dans ~45 min », « dans ~7 h », « dans ~2 j ». */
export function formatDans(minutes: number): string {
  if (minutes < 55) return `dans ~${Math.max(5, Math.round(minutes / 5) * 5)} min`
  const heures = minutes / 60
  if (heures < 36) return `dans ~${Math.round(heures)} h`
  return `dans ~${Math.round(heures / 24)} j`
}

/** « 50 min », « 1 h 20 », « 3 j ». */
export function formatDuree(minutes: number): string {
  const min = Math.max(1, Math.round(minutes))
  if (min < 60) return `${min} min`
  const h = Math.floor(min / 60)
  if (h < 24) return heureFr(h, min % 60)
  return `${Math.round(h / 24)} j`
}

// ── docs/GAMEPLAN.md §54 : avancement du Game Plan beta ───────────────────────

const PLAN_DEBUT_DEFAUT = '2026-10-13'

/** Le titre de la section que lit lirePlan : son absence est signalée, pas tue. */
export const TITRE_PLAN = /^#+\s.*Ordre de passage/im

export function lirePlan(md: string, maintenant: number): PlanVue | null {
  const lignes = md.split(/\r?\n/)
  const debut = lignes.findIndex(l => /^#+\s.*Ordre de passage/i.test(l))
  if (debut < 0) return null
  let livres = 0
  let livresDepuisMethode = 0
  let enCours = 0
  let aFaire = 0
  let horsBeta = 0
  let pointEnCours: string | undefined
  for (const l of lignes.slice(debut + 1)) {
    if (/^#+\s/.test(l)) break
    const cellules = l.split('|').slice(1, -1).map(c => c.trim())
    const [num, point, , statutBrut] = cellules
    const plage = /^(\d+)(?:\s*-\s*(\d+))?$/.exec(num ?? '')
    if (cellules.length < 4 || !plage || !point || statutBrut === undefined) continue
    const n = plage[2] ? Number(plage[2]) - Number(plage[1]) + 1 : 1
    const statut = nettoyer(statutBrut)
    if (/✅/.test(statut)) {
      livres += n
      livresDepuisMethode += n
    } else if (/^fait\b/i.test(statut)) livres += n
    else if (/✍|🟢/.test(statut)) {
      enCours += n
      if (!pointEnCours && /🟢/.test(statut)) pointEnCours = `n° ${plage[1]} · ${nettoyer(point).split(/\s*:\s*/)[0]}`
    } else if (/après la beta/i.test(statut)) {
      // « proposition (médecin) / après la beta (le reste) » : un point reste dans la beta.
      const dansBeta = /proposition/i.test(statut) && n > 1 ? 1 : 0
      aFaire += dansBeta
      horsBeta += n - dansBeta
    } else aFaire += n
  }
  const beta = livres + enCours + aFaire
  if (beta === 0) return null

  const debutMethode = Date.parse(/Méthode décidée le (\d{4}-\d{2}-\d{2})/.exec(md)?.[1] ?? PLAN_DEBUT_DEFAUT)
  const jours = (maintenant - debutMethode) / 86_400_000
  const vitesse = jours >= 1 ? livresDepuisMethode / jours : 0
  const reste = aFaire + enCours * 0.5
  let fin: string | undefined
  let rythme: string | undefined
  if (vitesse > 0 && reste > 0) {
    const finMs = maintenant + (reste / vitesse) * 86_400_000
    const p = partiesParis(finMs)
    fin = `vers le ${p.jj}/${p.mm}`
    const v = Math.round(vitesse * 10) / 10
    rythme = `≈ ${String(v).replace('.', ',')} point${v >= 2 ? 's' : ''} par jour`
  }
  return {
    titre: 'Game Plan beta',
    pourcent: Math.round((100 * (livres + enCours * 0.5)) / beta),
    livres,
    enCours,
    aFaire,
    horsBeta,
    pointEnCours,
    fin,
    rythme,
  }
}

// ── Les cartes ───────────────────────────────────────────────────────────────

/**
 * Le chantier mis en avant : marqué « vedette », sinon celui qu'un workflow fait avancer, sinon le premier en cours.
 * Une marque « vedette » oubliée sur un lot fini ou remis à plus tard ne fige pas la carte du haut.
 */
export function choisirVedette(chantiers: ChantierVue[]): ChantierVue | undefined {
  const vivant = (c: ChantierVue) => c.etat !== 'fait' && c.etat !== 'plus-tard' && c.finLibelle !== 'Terminé'
  return (
    chantiers.find(c => c.vedette && vivant(c)) ??
    chantiers.find(c => c.enDirect) ??
    chantiers.find(c => c.etat === 'en-cours' && c.etapes.length > 0) ??
    chantiers.find(c => c.etat === 'en-cours')
  )
}

export const heroDe = (c: ChantierVue): HeroVue => ({
  titre: c.titre,
  groupe: c.groupe,
  pourcent: c.pourcent,
  enCours: c.etapeEnCours,
  depuis: c.depuis,
  finLibelle: c.finLibelle,
  fin: c.fin,
  dans: c.dans,
  ensuite: c.ensuite,
  etapes: c.etapes.map(e => ({ titre: e.titre, etat: e.etat, toi: e.toi || undefined })),
  faites: c.faites,
  total: c.etapes.length,
})

export function autreDe(c: ChantierVue): AutreVue {
  const attendToi = c.finLibelle === 'À toi' || c.etapes.some(e => e.toi && e.etat === 'en-cours')
  if (c.etat === 'fait') return { titre: c.titre, pourcent: 100, statut: 'terminé', ton: 'neutre' }
  if (attendToi) return { titre: c.titre, pourcent: c.pourcent, statut: c.etat === 'a-deployer' ? 'à déployer par toi' : 'attend toi', ton: 'toi' }
  if (c.finLibelle === 'Terminé') return { titre: c.titre, pourcent: 100, statut: 'terminé', ton: 'neutre' }
  // Statut court : la colonne de droite de la carte est étroite, l'heure doit y tenir entière.
  if (c.fin) return { titre: c.titre, pourcent: c.pourcent, statut: `prêt ${finCourte(c.fin)}`, ton: 'claude' }
  const statut: Record<EtatChantier, [string, TonAutre]> = {
    'en-cours': ['en cours', 'claude'],
    recette: ['en recette', 'toi'],
    'a-deployer': ['à déployer', 'toi'],
    attente: ['en attente', 'neutre'],
    bloque: ['bloqué', 'neutre'],
    'a-faire': ['à faire', 'neutre'],
    'plus-tard': ['plus tard', 'neutre'],
    fait: ['terminé', 'neutre'],
  }
  const [libelle, ton] = statut[c.etat]
  return { titre: c.titre, pourcent: c.pourcent, statut: libelle, ton }
}

/** « vers 3 h 40 » reste tel quel, « demain vers 9 h » → « demain 9 h », « le 03/11 vers 14 h 20 » → « 03/11 14 h 20 ». */
const finCourte = (fin: string): string => (fin.startsWith('vers ') ? fin : fin.replace(/^le /, '').replace(' vers ', ' '))

const RANG_CHANTIER: Record<EtatChantier, number> = {
  'en-cours': 0,
  recette: 1,
  'a-deployer': 2,
  attente: 3,
  bloque: 4,
  'a-faire': 5,
  'plus-tard': 6,
  fait: 7,
}

export const trierChantiers = (liste: ChantierVue[]): ChantierVue[] =>
  [...liste].sort((a, b) => RANG_CHANTIER[a.etat] - RANG_CHANTIER[b.etat])

// ── docs/CHANTIERS.md §4 : décisions qui attendent l'utilisateur ────────────

/** null quand la section 4 n'est plus là (renumérotée, renommée) : le panneau le signale au lieu de se taire. */
export function lireDecisions(md: string): DecisionVue[] | null {
  const lignes = md.split(/\r?\n/)
  const debut = lignes.findIndex(l => /^##\s+4\.\s/.test(l))
  if (debut < 0) return null
  const out: DecisionVue[] = []
  for (const l of lignes.slice(debut + 1)) {
    if (/^##\s/.test(l)) break
    const cellules = l.split('|').slice(1, -1).map(c => c.trim())
    const [numero, question, contexte] = cellules
    if (cellules.length < 3 || !numero || !/^\d+$/.test(numero) || !question) continue
    // Une question barrée est tranchée.
    if (question.startsWith('~~')) continue
    out.push({ numero, question: nettoyer(question), contexte: nettoyer(contexte ?? '') })
  }
  return out
}

// ── docs/ETAT_DES_LIEUX.md §2 : ce qui est en ligne ───────────────────────────

const VERSION = /\d+\.\d+\.\d+(?:-[\w.]+)?/

function resumer(cellule: string): string {
  const propre = nettoyer(cellule)
  const version = VERSION.exec(propre)?.[0]
  if (version) {
    const date = /\((\d{2}\/\d{2})\)/.exec(propre)?.[1]
    return date ? `${version} (${date})` : version
  }
  const court = propre.split(/ \(| — | : /)[0] ?? propre
  return court.length > 60 ? court.slice(0, 59) + '…' : court
}

/** null quand la section 2 n'est plus là. */
export function lireProd(md: string): ArtefactVue[] | null {
  const lignes = md.split(/\r?\n/)
  const debut = lignes.findIndex(l => /^##\s+2\.\s/.test(l))
  if (debut < 0) return null
  const out: ArtefactVue[] = []
  for (const l of lignes.slice(debut + 1)) {
    if (/^##\s/.test(l)) break
    if (!l.startsWith('|') || /^\|\s*-/.test(l)) continue
    const cellules = l.split('|').slice(1, -1).map(c => c.trim())
    const [nom, enLigne, surMain] = cellules
    if (cellules.length < 3 || !nom || !enLigne || /^Artefact$/i.test(nettoyer(nom))) continue
    const vEnLigne = VERSION.exec(nettoyer(enLigne))?.[0]
    const vMain = VERSION.exec(nettoyer(surMain ?? ''))?.[0]
    out.push({
      nom: nettoyer(nom),
      enLigne: resumer(enLigne),
      surMain: resumer(surMain ?? ''),
      aJour: vEnLigne && vMain ? vEnLigne === vMain : null,
    })
  }
  return out
}

// ── Workflows : méta du script, journal, état ─────────────────────────────────

export type MetaWorkflow = { nom?: string; description?: string; phases: string[] }

export function lireMeta(script: string): MetaWorkflow {
  const bloc = /export\s+const\s+meta\s*=\s*\{([\s\S]*?)\n\}/.exec(script)?.[1] ?? script.slice(0, 4000)
  const chaine = (cle: string) => new RegExp(`${cle}:\\s*(['"\`])((?:\\\\.|(?!\\1).)*)\\1`).exec(bloc)?.[2]
  const phases = [...bloc.matchAll(/title:\s*(['"`])((?:\\.|(?!\1).)*)\1/g)].map(m => m[2] ?? '')
  return { nom: chaine('name'), description: chaine('description'), phases: phases.filter(Boolean) }
}

export type AgentJournal = { label: string; phase: string; agentId: string; etat: EtatAgentWf }

/**
 * Les agents d'un run, un par label : le dernier lancé l'emporte, si bien qu'une étape échouée
 * puis relancée à la reprise compte pour sa relance.
 */
export function lireJournal(contenu: string): AgentJournal[] {
  const parCle = new Map<string, AgentJournal & { ordre: number }>()
  let ordre = 0
  for (const ligne of contenu.split('\n')) {
    if (!ligne.trim()) continue
    let o: Record<string, unknown>
    try {
      o = JSON.parse(ligne) as Record<string, unknown>
    } catch {
      continue
    }
    const cle = typeof o.key === 'string' ? o.key : undefined
    if (!cle) continue
    if (o.type === 'started') {
      parCle.set(cle, {
        label: texte(o.label) ?? '?',
        phase: texte(o.phase) ?? '',
        agentId: texte(o.agentId) ?? '',
        etat: 'en-cours',
        ordre: ordre++,
      })
    } else if (o.type === 'result' || o.type === 'failed') {
      const a = parCle.get(cle)
      if (a) a.etat = o.type === 'result' ? 'fait' : 'echec'
    }
  }
  const parLabel = new Map<string, AgentJournal & { ordre: number }>()
  for (const a of [...parCle.values()].sort((x, y) => x.ordre - y.ordre)) parLabel.set(a.label, a)
  return [...parLabel.values()]
    .sort((x, y) => x.ordre - y.ordre)
    .map(({ ordre: _ordre, ...a }) => a)
}

export function phasesDe(titres: string[], agents: AgentWf[]): PhaseWf[] {
  return titres.map(titre => {
    const siens = agents.filter(a => a.phase === titre)
    if (siens.some(a => a.etat === 'en-cours')) return { titre, etat: 'en-cours' }
    if (siens.some(a => a.etat === 'echec')) return { titre, etat: 'echec' }
    if (siens.length > 0) return { titre, etat: 'fait' }
    return { titre, etat: 'a-venir' }
  })
}

const SILENCE_MS = 20 * 60_000
const ENTRE_DEUX_MS = 15 * 60_000
const SESSION_MORTE_MS = 60 * 60_000

/**
 * État d'un run d'après son enregistrement final (s'il est plus récent que le journal) et ses agents.
 * Un run silencieux dont la session n'écrit plus rien depuis une heure est mort sans enregistrement final
 * (application fermée, veille, plantage) : il est arrêté, plus « en cours ».
 */
export function etatRun(args: {
  statutFinal?: string
  finalAJour: boolean
  agents: AgentWf[]
  journalMs: number
  maintenant: number
  sessionMs?: number
}): EtatWorkflow {
  const { statutFinal, finalAJour, agents, journalMs, maintenant, sessionMs } = args
  if (statutFinal && finalAJour) {
    if (statutFinal === 'completed') return 'termine'
    if (statutFinal === 'failed') return 'echoue'
    return 'arrete'
  }
  const actifs = agents.filter(a => a.etat === 'en-cours')
  if (actifs.length > 0) {
    const silencieux = actifs.every(a => maintenant - (a.activiteMs ?? journalMs) > SILENCE_MS)
    if (!silencieux) return 'en-cours'
    return sessionMs !== undefined && maintenant - sessionMs > SESSION_MORTE_MS ? 'arrete' : 'silencieux'
  }
  return maintenant - journalMs < ENTRE_DEUX_MS ? 'en-cours' : 'arrete'
}

/** Slug du dossier de projets de Claude Code pour un chemin (`C:\a b` → `C--a-b`). */
export const slugProjet = (chemin: string): string => chemin.replace(/[^A-Za-z0-9]/g, '-')

/**
 * Le dossier de projets est-il celui du dépôt, d'un de ses worktrees ou d'un de ses sous-dossiers ? Sans
 * tenir compte de la casse : Windows ne la distingue pas, et `c:` comme `C:` donnent chacun leur slug.
 * Un voisin « RBLAUNCHER - Copie » (`…RBLAUNCHER---Copie`) n'en est pas un.
 */
export function dossierDuProjet(nom: string, slug: string): boolean {
  const n = nom.toLowerCase()
  const s = slug.toLowerCase()
  if (n === s) return true
  if (!n.startsWith(s + '-')) return false
  const reste = n.slice(s.length + 1)
  return reste.startsWith('-claude-worktrees-') || /^[a-z0-9]/.test(reste)
}

/** Racine du dépôt principal, même depuis un worktree de `.claude/worktrees/`. */
export const racineDepot = (cwd: string): string => {
  const i = cwd.search(/[\\/]\.claude[\\/]worktrees[\\/]/)
  return i >= 0 ? cwd.slice(0, i) : cwd
}
