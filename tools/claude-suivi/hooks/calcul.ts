// Calcul de l'instantané que le panneau dessine : lit le dépôt, le fichier de suivi et les
// journaux des workflows de toutes les sessions récentes de ce projet (worktrees compris).

import type { EngineInterface, FsEntry } from 'claude-code'

import type { AgentWf, AutreTache, Instantane, WorkflowVue } from '../types'
import {
  type AgentJournal,
  type EtatSuivi,
  type MetaWorkflow,
  TITRE_PLAN,
  autreDe,
  choisirVedette,
  dossierDuProjet,
  etatRun,
  heroDe,
  lireDecisions,
  lireEtatSuivi,
  lireJournal,
  lireMeta,
  lirePlan,
  lireProd,
  msParis,
  phasesDe,
  racineDepot,
  slugProjet,
  trierChantiers,
  vueChantier,
} from './donnees'

const SESSION_RECENTE_MS = 24 * 3600_000
const RUN_RECENT_MS = 12 * 3600_000
const FAIT_RECENT_MS = 48 * 3600_000
const ACTIF_SANS_JOURNAL_MS = 5 * 60_000
// Un run plus vieux que RUN_RECENT_MS n'est plus relu ; on revérifie de temps en temps qu'il n'a pas repris.
const RECONTROLE_ANCIEN_MS = 10 * 60_000
// Au-delà, $.fs.read refuse : le journal du run est remplacé par les fiches de ses agents.
const LECTURE_MAX = 4 * 1024 * 1024
const TACHE_MAX = 90

type Cache = { journalMs: number; finalMs?: number; vue: WorkflowVue }

// Un run terminé ne change plus : on le garde tant que son journal et son enregistrement final
// ne bougent pas. Variables de module, vidées à chaque rechargement du mod, ce qui est voulu.
const runsTermines = new Map<string, Cache>()
const runsAnciens = new Map<string, number>()
// Textes déjà lus (docs, fichier de suivi, journaux, scripts), gardés tant que date et taille ne bougent pas.
const textes = new Map<string, { ms: number; taille: number; texte: string }>()
const TEXTES_MAX = 64
// Dernière lecture valide du fichier de suivi : une lecture ratée (écriture en cours) ne vide pas le panneau.
let dernierSuivi: { chemin: string; suivi: EtatSuivi } | undefined

async function lireTexte($: EngineInterface, chemin: string): Promise<string> {
  return String(await $.fs.read(chemin))
}

/** Lit un fichier sauf si sa date et sa taille (données, sinon relues) n'ont pas bougé depuis la dernière fois. */
async function lireSiChange(
  $: EngineInterface,
  chemin: string,
  connu?: { mtimeMs: number; size: number },
): Promise<string> {
  const st = connu ?? (await $.fs.stat(chemin))
  const garde = textes.get(chemin)
  if (garde && garde.ms === st.mtimeMs && garde.taille === st.size) return garde.texte
  const texte = await lireTexte($, chemin)
  if (textes.size >= TEXTES_MAX) textes.clear()
  textes.set(chemin, { ms: st.mtimeMs, taille: st.size, texte })
  return texte
}

async function lister($: EngineInterface, chemin: string): Promise<FsEntry[]> {
  return $.fs.list(chemin).catch(() => [])
}

// Garde-fou seulement : la boucle s'arrête d'elle-même à la racine du disque.
const MONTEES_MAX = 64

/**
 * Racine du dépôt Reborn : depuis le dossier donné (un worktree est ramené au dépôt principal), on remonte
 * jusqu'au premier dossier qui contient docs/CHANTIERS.md. Une session ouverte dans un sous-dossier le retrouve,
 * aussi profond soit-il. Un chemin réseau, que $.fs.exists refuse, compte comme absent.
 */
export async function trouverRacine($: EngineInterface, depart: string): Promise<string | null> {
  return chercherRacine(depart, chemin => $.fs.exists(chemin).catch(() => false))
}

/** La remontée elle-même, sur une fonction d'existence donnée (le disque, ou celui d'un test). */
export async function chercherRacine(depart: string, existe: (chemin: string) => Promise<boolean>): Promise<string | null> {
  const sep = depart.includes('\\') ? '\\' : '/'
  let dossier = racineDepot(depart).replace(/[\\/]+$/, '')
  for (let i = 0; i < MONTEES_MAX && dossier; i++) {
    if (await existe([dossier, 'docs', 'CHANTIERS.md'].join(sep))) return dossier
    const parent = dossier.replace(/[\\/][^\\/]*$/, '')
    if (parent === dossier) break
    dossier = parent
  }
  return null
}

/** Un instantané sans rien à montrer que ses erreurs (dépôt introuvable, calcul en échec). */
export function instantaneVide(maintenant: number, erreurs: string[]): Instantane {
  return {
    minute: Math.floor(maintenant / 60_000),
    depot: null,
    fichierEtat: null,
    erreurs,
    aToi: [],
    faitsRecents: [],
    decisions: [],
    chantiers: [],
    workflows: [],
    autres: [],
    prod: [],
    hero: null,
    plan: null,
    autresChantiers: [],
    listeIndisponible: true,
  }
}

export async function calculer($: EngineInterface, taches: AutreTache[]): Promise<Instantane> {
  const maintenant = await $.clock.now()
  const cwd = await $.session.cwd()
  // Le mod vit dans le dépôt : une session ouverte ailleurs (/suivi à la main) retrouve le dépôt par lui.
  const trouvee = (await trouverRacine($, cwd)) ?? (await trouverRacine($, $.plugin.root))
  const racine = trouvee ?? cwd
  const sep = racine.includes('\\') ? '\\' : '/'
  const j = (...parts: string[]) => parts.join(sep)
  const erreurs: string[] = []
  const base = instantaneVide(maintenant, erreurs)

  if (!trouvee) {
    erreurs.push(`Dépôt Reborn introuvable depuis ${cwd} : ouvre la session dans le dépôt (dossier qui contient docs/CHANTIERS.md).`)
    return base
  }

  const workflows = await lireWorkflows($, racine, sep, maintenant, erreurs)

  const fichierEtat = j(racine, '.claude', 'suivi', 'etat.json')
  let suivi: EtatSuivi = { aToi: [], chantiers: [], doublons: [] }
  let listeIndisponible = false
  if (await $.fs.exists(fichierEtat).catch(() => false)) {
    try {
      suivi = lireEtatSuivi(await lireSiChange($, fichierEtat))
      dernierSuivi = { chemin: fichierEtat, suivi }
    } catch (err) {
      // Lu pendant qu'une session le réécrit, ou cassé : la dernière lecture valide reste affichée.
      const garde = dernierSuivi?.chemin === fichierEtat ? dernierSuivi.suivi : undefined
      if (garde) suivi = garde
      else listeIndisponible = true
      erreurs.push(
        `.claude/suivi/etat.json illisible${garde ? ' (dernière lecture valide affichée)' : ''} : ${String(err).slice(0, 160)}`,
      )
    }
  } else {
    listeIndisponible = true
    erreurs.push('Pas encore de fichier de suivi (.claude/suivi/etat.json).')
  }
  for (const id of suivi.doublons) erreurs.push(`etat.json : id en double « ${id} », seul le premier est affiché.`)

  // Les agents d'un chantier, en direct : le run le plus récent de chaque workflow nommé gagne. Un run qui ne
  // tourne plus (silencieux, arrêté sans enregistrement final) ne fait plus avancer d'étape : ses agents
  // « en cours » sont morts, l'étape garde l'état écrit dans le fichier de suivi.
  const agentsDe = (noms: string[]): Map<string, AgentWf> => {
    const agents = new Map<string, AgentWf>()
    const runs = workflows.filter(w => noms.includes(w.nom)).sort((a, b) => a.majMs - b.majMs)
    for (const run of runs) {
      for (const a of run.agents) if (a.etat !== 'en-cours' || run.etat === 'en-cours') agents.set(a.label, a)
    }
    return agents
  }

  let decisions: Instantane['decisions'] = []
  try {
    const lues = lireDecisions(await lireSiChange($, j(racine, 'docs', 'CHANTIERS.md')))
    if (lues === null) erreurs.push('CHANTIERS.md : section « ## 4. » introuvable, décisions non lues.')
    decisions = lues ?? []
  } catch (err) {
    erreurs.push(`CHANTIERS.md illisible : ${String(err).slice(0, 120)}`)
  }
  let prod: Instantane['prod'] = []
  try {
    const lus = lireProd(await lireSiChange($, j(racine, 'docs', 'ETAT_DES_LIEUX.md')))
    if (lus === null) erreurs.push('ETAT_DES_LIEUX.md : section « ## 2. » introuvable, versions en ligne non lues.')
    prod = lus ?? []
  } catch (err) {
    erreurs.push(`ETAT_DES_LIEUX.md illisible : ${String(err).slice(0, 120)}`)
  }
  let plan: Instantane['plan'] = null
  try {
    const md = await lireSiChange($, j(racine, 'docs', 'GAMEPLAN.md'))
    if (!TITRE_PLAN.test(md)) erreurs.push('GAMEPLAN.md : section « Ordre de passage » introuvable, carte du Plan absente.')
    plan = lirePlan(md, maintenant)
  } catch (err) {
    erreurs.push(`GAMEPLAN.md illisible : ${String(err).slice(0, 120)}`)
  }

  const agents = await $.agent.list().catch(() => [])
  const uneLigne = (s: string) => {
    const l = s.replace(/\s+/g, ' ').trim()
    return l.length > TACHE_MAX ? l.slice(0, TACHE_MAX - 1) + '…' : l
  }
  const autres: AutreTache[] = [
    ...agents
      .filter(a => a.status === 'running')
      .map(a => ({ id: a.id, type: a.type, description: uneLigne(a.description), statut: a.status })),
    ...taches
      .filter(t => t.type !== 'workflow' && t.type !== 'subagent')
      .map(t => ({ ...t, description: uneLigne(t.description), auDernierTour: true })),
  ]

  const recent = (quand?: string) => {
    const t = msParis(quand) ?? (quand ? Date.parse(quand) : NaN)
    return Number.isFinite(t) && maintenant - t < FAIT_RECENT_MS
  }

  const chantiers = trierChantiers(suivi.chantiers.map(ch => vueChantier(ch, agentsDe, maintenant)))
  const vedette = choisirVedette(chantiers)
  const liesVedette = vedette?.workflows ?? []
  const wfHorsVedette = workflows.filter(
    w => (w.etat === 'en-cours' || w.etat === 'silencieux') && !liesVedette.includes(w.nom),
  ).length

  return {
    ...base,
    depot: racine,
    fichierEtat,
    listeIndisponible,
    aToi: suivi.aToi.filter(it => !it.fait),
    faitsRecents: suivi.aToi.filter(it => it.fait && recent(it.faitLe)),
    decisions,
    chantiers,
    workflows,
    autres,
    prod,
    hero: vedette ? heroDe(vedette) : null,
    plan,
    // Les chantiers finis sont rangés dans le détail : la carte montre ce qui reste à faire.
    autresChantiers: chantiers.filter(c => c !== vedette && c.etat !== 'fait').map(autreDe),
    wfHorsVedette,
    majEtat: suivi.majLe,
    majPar: suivi.majPar,
  }
}

async function lireWorkflows(
  $: EngineInterface,
  racine: string,
  sep: string,
  maintenant: number,
  erreurs: string[],
): Promise<WorkflowVue[]> {
  const j = (...parts: string[]) => parts.join(sep)
  const maison = (await $.env.get('USERPROFILE')) ?? (await $.env.get('HOME'))
  if (!maison) return []
  const projets = j(maison, '.claude', 'projects')
  const slug = slugProjet(racine)
  const dossiers = (await lister($, projets)).filter(d => d.kind === 'dir' && dossierDuProjet(d.name, slug))
  const cetteSession = await $.session.id().catch(() => '')
  const out: WorkflowVue[] = []

  for (const dossier of dossiers) {
    const dirProjet = j(projets, dossier.name)
    const sessions = (await lister($, dirProjet))
      .filter(e => e.kind === 'file' && e.name.endsWith('.jsonl') && maintenant - e.mtimeMs < SESSION_RECENTE_MS)
      .map(e => ({ sid: e.name.slice(0, -'.jsonl'.length), sessionMs: e.mtimeMs }))

    for (const { sid, sessionMs } of sessions) {
      const dirSession = j(dirProjet, sid)
      const dirRuns = j(dirSession, 'subagents', 'workflows')
      const runs = (await lister($, dirRuns)).filter(r => r.kind === 'dir' && r.name.startsWith('wf_'))
      if (runs.length === 0) continue

      let titre = sid === cetteSession ? 'cette session' : `session ${sid.slice(0, 8)}`
      if (sid !== cetteSession) {
        try {
          const t = JSON.parse(await lireTexte($, j(dirSession, 'custom-title.json'))) as { customTitle?: unknown }
          if (typeof t.customTitle === 'string' && t.customTitle.trim()) titre = t.customTitle.trim()
        } catch {
          // pas de titre donné à cette session
        }
      }
      const finaux = await lister($, j(dirSession, 'workflows'))
      const scripts = await lister($, j(dirSession, 'workflows', 'scripts'))

      for (const run of runs) {
        const dirRun = j(dirRuns, run.name)
        const vuAncien = runsAnciens.get(dirRun)
        if (vuAncien !== undefined && maintenant - vuAncien < RECONTROLE_ANCIEN_MS) continue
        const journal = await $.fs.stat(j(dirRun, 'journal.jsonl')).catch(() => undefined)
        if (!journal || maintenant - journal.mtimeMs > RUN_RECENT_MS) {
          runsAnciens.set(dirRun, maintenant)
          continue
        }
        runsAnciens.delete(dirRun)
        const finalMs = finaux.find(f => f.name === `${run.name}.json`)?.mtimeMs
        const enCache = runsTermines.get(dirRun)
        if (enCache && enCache.journalMs === journal.mtimeMs && enCache.finalMs === finalMs) {
          out.push(enCache.vue)
          continue
        }
        try {
          const vue = await lireRun($, {
            j,
            dirRun,
            dirSession,
            runId: run.name,
            titre,
            estCetteSession: sid === cetteSession,
            journalMs: journal.mtimeMs,
            finalMs,
            script: scripts.find(s => s.name.endsWith(`-${run.name}.js`)),
            sessionMs,
            maintenant,
            erreurs,
          })
          if (vue.etat === 'termine' || vue.etat === 'echoue' || vue.etat === 'arrete') {
            runsTermines.set(dirRun, { journalMs: journal.mtimeMs, finalMs, vue })
          }
          out.push(vue)
        } catch (err) {
          erreurs.push(`${run.name} illisible : ${String(err).slice(0, 120)}`)
        }
      }
    }
  }

  const rang = (w: WorkflowVue) => (w.etat === 'en-cours' ? 0 : w.etat === 'silencieux' ? 1 : 2)
  return out.sort((a, b) => rang(a) - rang(b) || b.majMs - a.majMs)
}

async function lireRun(
  $: EngineInterface,
  r: {
    j: (...parts: string[]) => string
    dirRun: string
    dirSession: string
    runId: string
    titre: string
    estCetteSession: boolean
    journalMs: number
    finalMs?: number
    script?: FsEntry
    sessionMs: number
    maintenant: number
    erreurs: string[]
  },
): Promise<WorkflowVue> {
  const { j } = r
  const fichiers = await lister($, r.dirRun)
  const mtime = (nom: string) => fichiers.find(f => f.name === nom)?.mtimeMs

  // L'enregistrement final n'est écrit qu'à la fin d'un run : plus ancien que le journal, c'est
  // celui d'une exécution précédente (une reprise tourne).
  const finalAJour = r.finalMs !== undefined && r.finalMs >= r.journalMs - 2000
  let statutFinal: string | undefined
  let dureeMs: number | undefined
  let debutMs: number | undefined
  let meta: MetaWorkflow = { phases: [] }
  if (r.finalMs !== undefined && (finalAJour || !r.script)) {
    try {
      const fin = JSON.parse(await lireTexte($, j(r.dirSession, 'workflows', `${r.runId}.json`))) as Record<
        string,
        unknown
      >
      if (finalAJour) {
        statutFinal = typeof fin.status === 'string' ? fin.status : undefined
        dureeMs = typeof fin.durationMs === 'number' ? fin.durationMs : undefined
        debutMs = typeof fin.startTime === 'number' ? fin.startTime : undefined
      }
      meta = {
        nom: typeof fin.workflowName === 'string' ? fin.workflowName : undefined,
        description: typeof fin.summary === 'string' ? fin.summary : undefined,
        phases: Array.isArray(fin.phases)
          ? fin.phases.map(p => (p as { title?: unknown }).title).filter((t): t is string => typeof t === 'string')
          : [],
      }
    } catch {
      // enregistrement final illisible : le script et le journal suffisent
    }
  }
  if (r.script) {
    try {
      meta = lireMeta(await lireSiChange($, j(r.dirSession, 'workflows', 'scripts', r.script.name), r.script))
    } catch {
      // script introuvable : on garde ce que l'enregistrement final disait
    }
  }

  // Journal trop gros pour une lecture (4 Mio) : on retombe sur les fiches des agents. Toute autre erreur
  // de lecture remonte (run signalé illisible) au lieu de passer pour un journal trop gros.
  const entreeJournal = fichiers.find(f => f.name === 'journal.jsonl')
  const journal: AgentJournal[] =
    entreeJournal && entreeJournal.size > LECTURE_MAX
      ? await agentsDepuisFiches($, j, r.dirRun, fichiers, r.maintenant)
      : lireJournal(await lireSiChange($, j(r.dirRun, 'journal.jsonl'), entreeJournal))

  let agents: AgentWf[] = journal.map(a => ({
    label: a.label,
    phase: a.phase,
    etat: a.etat,
    debutMs: mtime(`agent-${a.agentId}.meta.json`),
    activiteMs: mtime(`agent-${a.agentId}.jsonl`),
  }))
  // Une reprise tourne : les échecs de l'exécution précédente attendent leur relance.
  if (!finalAJour && r.finalMs !== undefined) {
    const avant = r.finalMs + 60_000
    agents = agents.filter(a => !(a.etat === 'echec' && (a.activiteMs ?? 0) <= avant))
  }
  if (meta.phases.length > 0) agents = agents.filter(a => meta.phases.includes(a.phase))

  const etat = etatRun({
    statutFinal,
    finalAJour,
    agents,
    journalMs: r.journalMs,
    maintenant: r.maintenant,
    sessionMs: r.sessionMs,
  })
  const debuts = agents.map(a => a.debutMs).filter((t): t is number => t !== undefined)
  return {
    runId: r.runId,
    nom: meta.nom ?? r.runId,
    description: meta.description,
    session: r.titre,
    estCetteSession: r.estCetteSession,
    etat,
    phases: phasesDe(meta.phases, agents),
    agents,
    debutMs: debutMs ?? (debuts.length > 0 ? Math.min(...debuts) : undefined),
    dureeMs,
    majMs: Math.max(r.journalMs, r.finalMs ?? 0),
  }
}

async function agentsDepuisFiches(
  $: EngineInterface,
  j: (...parts: string[]) => string,
  dirRun: string,
  fichiers: FsEntry[],
  maintenant: number,
): Promise<AgentJournal[]> {
  const out: AgentJournal[] = []
  for (const f of fichiers.filter(f => /^agent-[^.]+\.meta\.json$/.test(f.name))) {
    const agentId = f.name.slice('agent-'.length, -'.meta.json'.length)
    try {
      const fiche = JSON.parse(await lireTexte($, j(dirRun, f.name))) as Record<string, unknown>
      const activite = fichiers.find(x => x.name === `agent-${agentId}.jsonl`)?.mtimeMs ?? 0
      out.push({
        label: typeof fiche.description === 'string' ? fiche.description : agentId,
        phase: typeof fiche.workflowPhase === 'string' ? fiche.workflowPhase : '',
        agentId,
        etat: maintenant - activite < ACTIF_SANS_JOURNAL_MS ? 'en-cours' : 'fait',
      })
    } catch {
      // fiche illisible : agent ignoré
    }
  }
  return out
}
