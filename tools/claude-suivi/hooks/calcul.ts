// Calcul de l'instantané que le panneau dessine : lit le dépôt, le fichier de suivi et les
// journaux des workflows de toutes les sessions récentes de ce projet (worktrees compris).

import type { EngineInterface, FsEntry } from 'claude-code'

import type { AgentWf, AutreTache, Instantane, WorkflowVue } from '../types'
import {
  type AgentJournal,
  type EtatSuivi,
  type MetaWorkflow,
  autreDe,
  choisirVedette,
  etatRun,
  heroDe,
  lireDecisions,
  lireEtatSuivi,
  lireJournal,
  lireMeta,
  lirePlan,
  lireProd,
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

type Cache = { journalMs: number; finalMs?: number; vue: WorkflowVue }

// Un run terminé ne change plus : on le garde tant que son journal et son enregistrement final
// ne bougent pas. Variable de module, vidée à chaque rechargement du mod, ce qui est voulu.
const runsTermines = new Map<string, Cache>()

async function lireTexte($: EngineInterface, chemin: string): Promise<string> {
  return String(await $.fs.read(chemin))
}

async function lister($: EngineInterface, chemin: string): Promise<FsEntry[]> {
  return $.fs.list(chemin).catch(() => [])
}

export async function calculer($: EngineInterface, taches: AutreTache[]): Promise<Instantane> {
  const maintenant = await $.clock.now()
  const cwd = await $.session.cwd()
  const sep = cwd.includes('\\') ? '\\' : '/'
  const j = (...parts: string[]) => parts.join(sep)
  const racine = racineDepot(cwd)
  const erreurs: string[] = []
  const base: Instantane = {
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
  }

  if (!(await $.fs.exists(j(racine, 'docs', 'CHANTIERS.md')))) {
    erreurs.push(`Pas de docs/CHANTIERS.md sous ${racine} : ce panneau suit le dépôt Reborn.`)
    return base
  }

  const workflows = await lireWorkflows($, racine, sep, maintenant, erreurs)

  const fichierEtat = j(racine, '.claude', 'suivi', 'etat.json')
  let suivi: EtatSuivi = { aToi: [], chantiers: [] }
  if (await $.fs.exists(fichierEtat)) {
    try {
      suivi = lireEtatSuivi(await lireTexte($, fichierEtat))
    } catch (err) {
      erreurs.push(`.claude/suivi/etat.json illisible : ${String(err).slice(0, 160)}`)
    }
  } else {
    erreurs.push('Pas encore de fichier de suivi (.claude/suivi/etat.json).')
  }

  // Les agents d'un chantier, en direct : le run le plus récent de chaque workflow nommé gagne.
  const agentsDe = (noms: string[]): Map<string, AgentWf> => {
    const agents = new Map<string, AgentWf>()
    const runs = workflows.filter(w => noms.includes(w.nom)).sort((a, b) => a.majMs - b.majMs)
    for (const run of runs) for (const a of run.agents) agents.set(a.label, a)
    return agents
  }

  let decisions: Instantane['decisions'] = []
  try {
    decisions = lireDecisions(await lireTexte($, j(racine, 'docs', 'CHANTIERS.md')))
  } catch (err) {
    erreurs.push(`CHANTIERS.md illisible : ${String(err).slice(0, 120)}`)
  }
  let prod: Instantane['prod'] = []
  try {
    prod = lireProd(await lireTexte($, j(racine, 'docs', 'ETAT_DES_LIEUX.md')))
  } catch (err) {
    erreurs.push(`ETAT_DES_LIEUX.md illisible : ${String(err).slice(0, 120)}`)
  }
  let plan: Instantane['plan'] = null
  try {
    plan = lirePlan(await lireTexte($, j(racine, 'docs', 'GAMEPLAN.md')), maintenant)
  } catch (err) {
    erreurs.push(`GAMEPLAN.md illisible : ${String(err).slice(0, 120)}`)
  }

  const agents = await $.agent.list().catch(() => [])
  const autres: AutreTache[] = [
    ...agents
      .filter(a => a.status === 'running')
      .map(a => ({ id: a.id, type: a.type, description: a.description, statut: a.status })),
    ...taches.filter(t => t.type !== 'workflow' && t.type !== 'subagent'),
  ]

  const recent = (quand?: string) => {
    const t = quand ? Date.parse(quand) : NaN
    return Number.isFinite(t) && maintenant - t < FAIT_RECENT_MS
  }

  const chantiers = trierChantiers(suivi.chantiers.map(ch => vueChantier(ch, agentsDe, maintenant)))
  const vedette = choisirVedette(chantiers)

  return {
    ...base,
    depot: racine,
    fichierEtat,
    aToi: suivi.aToi.filter(it => !it.fait),
    faitsRecents: suivi.aToi.filter(it => it.fait && recent(it.faitLe)),
    decisions,
    chantiers,
    workflows,
    autres,
    prod,
    hero: vedette ? heroDe(vedette) : null,
    plan,
    autresChantiers: chantiers.filter(c => c !== vedette).map(autreDe),
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
  const dossiers = (await lister($, projets)).filter(
    d => d.kind === 'dir' && (d.name === slug || d.name.startsWith(slug + '-')),
  )
  const cetteSession = await $.session.id().catch(() => '')
  const out: WorkflowVue[] = []

  for (const dossier of dossiers) {
    const dirProjet = j(projets, dossier.name)
    const sessions = (await lister($, dirProjet))
      .filter(e => e.kind === 'file' && e.name.endsWith('.jsonl') && maintenant - e.mtimeMs < SESSION_RECENTE_MS)
      .map(e => e.name.slice(0, -'.jsonl'.length))

    for (const sid of sessions) {
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
        const journal = await $.fs.stat(j(dirRun, 'journal.jsonl')).catch(() => undefined)
        if (!journal || maintenant - journal.mtimeMs > RUN_RECENT_MS) continue
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
            script: scripts.find(s => s.name.endsWith(`-${run.name}.js`))?.name,
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
    script?: string
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
      meta = lireMeta(await lireTexte($, j(r.dirSession, 'workflows', 'scripts', r.script)))
    } catch {
      // script introuvable : on garde ce que l'enregistrement final disait
    }
  }

  let journal: AgentJournal[]
  try {
    journal = lireJournal(await lireTexte($, j(r.dirRun, 'journal.jsonl')))
  } catch {
    // Journal trop gros pour une lecture (4 Mio) : on retombe sur les fiches des agents.
    journal = await agentsDepuisFiches($, j, r.dirRun, fichiers, r.maintenant)
  }

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

  const etat = etatRun({ statutFinal, finalAJour, agents, journalMs: r.journalMs, maintenant: r.maintenant })
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
