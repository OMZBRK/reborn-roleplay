// Contrat d'état du panneau Suivi Reborn : ce que le calcul produit et ce que le panneau dessine.

export type EtatEtape = 'fait' | 'en-cours' | 'a-faire' | 'echec' | 'attente'

export type EtatChantier =
  | 'en-cours'
  | 'recette'
  | 'a-deployer'
  | 'attente'
  | 'bloque'
  | 'a-faire'
  | 'plus-tard'
  | 'fait'

export type TypeAToi = 'commande' | 'test' | 'decision' | 'deploiement' | 'validation' | 'info'

export type ItemAToi = {
  id: string
  type: TypeAToi
  titre: string
  detail?: string
  commande?: string
  chantier?: string
  depuis?: string
  fait?: boolean
  faitLe?: string
}

export type EtapeVue = { titre: string; etat: EtatEtape; enDirect: boolean; toi: boolean }

export type ChantierVue = {
  id: string
  titre: string
  groupe?: string
  etat: EtatChantier
  etapes: EtapeVue[]
  faites: number
  prochaine?: string
  branche?: string
  majLe?: string
  enDirect: boolean
  vedette: boolean
  /** Les workflows nommés par le chantier dans le fichier de suivi. */
  workflows?: string[]
  // Avancement pondéré par la durée des étapes, et ce qu'on peut dire de la fin.
  pourcent: number
  finLibelle: string
  fin?: string
  dans?: string
  ensuite?: string
  etapeEnCours?: string
  depuis?: string
}

// Ce que les cartes dessinent (voir visuel.ts).
export type EtapeCarte = { titre: string; etat: EtatEtape; toi?: boolean }

export type HeroVue = {
  titre: string
  groupe?: string
  pourcent: number
  enCours?: string
  depuis?: string
  finLibelle: string
  fin?: string
  dans?: string
  ensuite?: string
  etapes: EtapeCarte[]
  faites: number
  total: number
}

export type PlanVue = {
  titre: string
  pourcent: number
  livres: number
  enCours: number
  aFaire: number
  horsBeta: number
  pointEnCours?: string
  fin?: string
  rythme?: string
}

export type TonAutre = 'toi' | 'claude' | 'neutre'

export type AutreVue = { titre: string; pourcent: number; statut: string; ton: TonAutre }

export type EtatAgentWf = 'en-cours' | 'fait' | 'echec'

export type AgentWf = {
  label: string
  phase: string
  etat: EtatAgentWf
  debutMs?: number
  activiteMs?: number
}

export type PhaseWf = { titre: string; etat: 'fait' | 'en-cours' | 'a-venir' | 'echec' }

export type EtatWorkflow = 'en-cours' | 'silencieux' | 'termine' | 'echoue' | 'arrete'

export type WorkflowVue = {
  runId: string
  nom: string
  description?: string
  session: string
  estCetteSession: boolean
  etat: EtatWorkflow
  phases: PhaseWf[]
  agents: AgentWf[]
  debutMs?: number
  dureeMs?: number
  majMs: number
}

export type AutreTache = {
  id: string
  type: string
  description: string
  statut: string
  /** Lue à la dernière fin de tour (shells, moniteurs) : son statut peut avoir changé depuis. */
  auDernierTour?: boolean
}

export type DecisionVue = { numero: string; question: string; contexte: string }

export type ArtefactVue = { nom: string; enLigne: string; surMain: string; aJour: boolean | null }

export type Instantane = {
  minute: number
  depot: string | null
  fichierEtat: string | null
  erreurs: string[]
  aToi: ItemAToi[]
  faitsRecents: ItemAToi[]
  decisions: DecisionVue[]
  chantiers: ChantierVue[]
  workflows: WorkflowVue[]
  autres: AutreTache[]
  prod: ArtefactVue[]
  hero: HeroVue | null
  plan: PlanVue | null
  autresChantiers: AutreVue[]
  majEtat?: string
  majPar?: string
  /** Le fichier de suivi n'a pas pu être lu et aucune lecture précédente ne le remplace. */
  listeIndisponible?: boolean
  /** Workflows en cours qui ne font pas avancer le chantier en vedette. */
  wfHorsVedette?: number
}

declare module 'claude-code' {
  interface PluginState {
    'reborn-suivi': {
      instantane: Shaped<Instantane | null>
      ouverts: string[]
      taches: AutreTache[]
    }
  }
}
