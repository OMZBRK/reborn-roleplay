import type { On } from 'claude-code'
import { expect, mock, test } from 'claude-code/testing'

import { chercherRacine } from '../hooks/calcul'
import {
  autreDe,
  avancement,
  choisirVedette,
  dossierDuProjet,
  etatRun,
  formatDans,
  lireDecisions,
  lireEtatSuivi,
  lirePlan,
  marquerFaitDans,
  msParis,
  vueChantier,
} from '../hooks/donnees'
import { carteHero, cartePlan, listeChantiers } from '../hooks/visuel'
import type { ChantierVue, Instantane } from '../types'

// Le kit de test n'a pas d'état préchargé : une mémoire tient lieu de l'hôte sous le mod.
function memoire(on: On, instantane?: Instantane) {
  const valeurs = new Map<string, { value: unknown; version: number }>()
  if (instantane) valeurs.set('instantane', { value: { shape: 'suivi-3', value: instantane }, version: 1 })
  on('state.get', (_, e) => ({ value: valeurs.get(e.key) ?? { value: undefined, version: 0 } }))
  on('state.set', (_, e) => {
    const avant = valeurs.get(e.key)?.version ?? 0
    if (e.ifVersion !== undefined && e.ifVersion !== avant) return { value: { isSet: false, version: avant } }
    valeurs.set(e.key, { value: e.value, version: avant + 1 })
    return { value: { isSet: true, version: avant + 1 } }
  })
}

const PANE = {
  plugin: 'reborn-suivi',
  component: 'Pane',
  requestId: 'suivi',
  props: {
    title: 'Suivi Reborn',
    isFocused: false,
    bodyColumns: 48,
    placement: 'dock',
    scroll: { offset: 0, bodyRows: 60 },
    view: {},
  },
} as const

const MAINTENANT = Date.UTC(2026, 10, 1, 20, 0)

const LOT2: ChantierVue = {
  id: 'lot2',
  titre: 'Lot 2 : reprises Ketto',
  groupe: 'Game Plan n° 17',
  etat: 'en-cours',
  etapes: [
    { titre: 'Moteur de texte', etat: 'fait', enDirect: true, toi: false },
    { titre: 'HUD bas-droite', etat: 'en-cours', enDirect: true, toi: false },
    { titre: 'Renommage nametag', etat: 'a-faire', enDirect: false, toi: false },
    { titre: 'Ta validation', etat: 'a-faire', enDirect: false, toi: true },
  ],
  faites: 1,
  prochaine: 'Captures puis relecture',
  branche: 'feature/lot2-ketto',
  enDirect: true,
  vedette: false,
  pourcent: 47,
  finLibelle: 'Prêt pour toi',
  fin: 'vers 3 h 40',
  dans: 'dans ~7 h',
  ensuite: 'puis ta validation',
  etapeEnCours: 'HUD bas-droite',
  depuis: '50 min',
}

const SNAP: Instantane = {
  minute: Math.floor(MAINTENANT / 60_000),
  depot: 'C:\\depot',
  fichierEtat: 'C:\\depot\\.claude\\suivi\\etat.json',
  erreurs: [],
  aToi: [{ id: 'deploy', type: 'deploiement', titre: 'Déployer le train web', detail: 'API et panel', commande: 'git pull' }],
  faitsRecents: [{ id: 'commit', type: 'commande', titre: 'Commit des décisions', fait: true }],
  decisions: [{ numero: '2', question: 'Rendu de la concentration', contexte: 'ensō A ou trait B' }],
  chantiers: [
    LOT2,
    { ...LOT2, id: 'lot5', titre: 'Lot 5 : saut', etat: 'plus-tard', etapes: [], faites: 0, enDirect: false, pourcent: 0, finLibelle: 'Plus tard', fin: undefined, dans: undefined, ensuite: undefined, etapeEnCours: undefined, depuis: undefined },
  ],
  workflows: [
    {
      runId: 'wf_1',
      nom: 'lot2-ketto-code',
      session: 'cette session',
      estCetteSession: true,
      etat: 'en-cours',
      phases: [
        { titre: 'Moteur', etat: 'fait' },
        { titre: 'Éléments', etat: 'en-cours' },
      ],
      agents: [{ label: 'bas-droite', phase: 'Éléments', etat: 'en-cours', debutMs: MAINTENANT - 34 * 60_000 }],
      debutMs: MAINTENANT - 3 * 3600_000,
      majMs: MAINTENANT,
    },
  ],
  autres: [],
  prod: [{ nom: 'Launcher', enLigne: '0.3.50 (01/11)', surMain: '0.3.50', aJour: true }],
  hero: {
    titre: LOT2.titre,
    groupe: LOT2.groupe,
    pourcent: 47,
    enCours: 'HUD bas-droite',
    depuis: '50 min',
    finLibelle: 'Prêt pour toi',
    fin: 'vers 3 h 40',
    dans: 'dans ~7 h',
    ensuite: 'puis ta validation',
    etapes: LOT2.etapes.map(e => ({ titre: e.titre, etat: e.etat, toi: e.toi || undefined })),
    faites: 1,
    total: 4,
  },
  plan: { titre: 'Game Plan beta', pourcent: 49, livres: 18, enCours: 6, aFaire: 19, horsBeta: 10, fin: 'vers le 01/12', rythme: '≈ 0,7 point par jour' },
  autresChantiers: [{ titre: 'Lot 5 : saut', pourcent: 0, statut: 'plus tard', ton: 'neutre' }],
}

test('les cartes et la liste à toi se dessinent sur chaque surface', async ($, on) => {
  mock.clock(on, { now: MAINTENANT })
  memoire(on, SNAP)

  for (const surface of ['desktop', 'vscode', 'mobile'] as const) {
    const ui = await $.ui.mount({ ...PANE, surface })
    // Un Svg montre son alt au lecteur d'écran ; le test le lit dans ses props.
    const hero = await ui.find({ type: 'Svg' })
    expect(String(hero?.props.alt)).toMatch(/47 %, prêt pour toi vers 3 h 40/)
    expect(JSON.stringify((await ui.find({ key: 'carte-plan' }))?.children)).toMatch(/Game Plan beta : 49 %/)
    expect(JSON.stringify((await ui.find({ key: 'carte-autres' }))?.children)).toMatch(/Lot 5 : saut 0 %/)
    expect(await ui.find({ type: 'Text', text: /^À TOI$/ })).toBeDefined()
    expect(await ui.find({ key: 'copier-deploy' })).toBeDefined()
    expect(await ui.find({ key: 'details' })).toBeDefined()
    await ui.unmount()
  }

  // Le terminal n'a pas de Svg : les cartes deviennent du texte.
  const terminal = await $.ui.mount({ ...PANE, surface: 'terminal' })
  expect(await terminal.find({ type: 'Text', text: /Lot 2 : reprises Ketto : 47 %/ })).toBeDefined()
  await terminal.unmount()
})

test('le détail se déplie à la demande', async ($, on) => {
  mock.clock(on, { now: MAINTENANT })
  memoire(on, SNAP)
  const ui = await $.ui.mount({ ...PANE, surface: 'desktop' })
  expect(await ui.find({ key: 'wf-wf_1' })).toBeUndefined()
  await ui.press({ key: 'details' })
  await ui.unmount()

  // La mémoire de test ne redessine pas d'elle-même : un nouveau montage relit l'état écrit.
  const apres = await $.ui.mount({ ...PANE, surface: 'desktop' })
  expect(await apres.find({ key: 'wf-wf_1' })).toBeDefined()
  expect(await apres.find({ key: 'ch-lot2' })).toBeDefined()
  await apres.unmount()
})

test('sans instantané, le panneau dit qu’il calcule', async ($, on) => {
  mock.clock(on, { now: MAINTENANT })
  memoire(on)
  const ui = await $.ui.mount({ ...PANE, surface: 'desktop' })
  expect((await ui.find({ type: 'Text' }))?.text).toBe('Calcul du suivi…')
  await ui.unmount()
})

test('avancement : pondéré par la durée, fin avant la part de l’utilisateur', () => {
  const av = avancement([
    { titre: 'a', etat: 'fait', toi: false, minutes: 60 },
    { titre: 'b', etat: 'en-cours', toi: false, minutes: 60, ecouleMin: 30 },
    { titre: 'c', etat: 'a-faire', toi: false, minutes: 60 },
    { titre: 'Ta validation', etat: 'a-faire', toi: true, minutes: 20 },
    { titre: 'Publication', etat: 'a-faire', toi: false, minutes: 40 },
  ])
  expect(av.pourcent).toBe(38)
  expect(av.restantMin).toBe(90)
  expect(av.prochaineToi).toBe('Ta validation')
  expect(av.apresToi).toEqual(['Publication'])
  expect(formatDans(av.restantMin)).toBe('dans ~2 h')
})

test('Game Plan : livrés, en cours, hors beta et rythme', () => {
  const md = [
    '> **Méthode décidée le 2026-10-13** : on avance point par point.',
    '# ▶️ 54. Ordre de passage, point par point',
    '| # | Point | § | Statut | Reste |',
    '|---|---|---|---|---|',
    '| 1 | **Player ID** | §1 | ✅ **livré 14/10** | — |',
    '| 2 | HUD | §4 | ✍🏻 **validé et codé 19/10** | — |',
    '| 3 | Chat | §17 | 🟢 **validé 31/10** | — |',
    '| 4-6 | Métiers | §23 | proposition (médecin) / après la beta (le reste) | — |',
    '| 7 | Création | §43 | fait + ajouts | — |',
    '| 8 | Contrebande | §31 | après la beta | — |',
  ].join('\n')
  const plan = lirePlan(md, Date.UTC(2026, 10, 1))
  expect(plan?.livres).toBe(2)
  expect(plan?.enCours).toBe(2)
  expect(plan?.aFaire).toBe(1)
  expect(plan?.horsBeta).toBe(3)
  expect(plan?.pourcent).toBe(60)
  expect(plan?.pointEnCours).toBe('n° 3 · Chat')
  expect(plan?.rythme).toBe('≈ 0,1 point par jour')
})


// ── Constats du 02/11 : ce que le panneau faisait de travers en usage réel ──

const sansAgents = () => new Map()

test('une étape tenue à la main ancrée sur debutLe garde la même heure de fin quand l’horloge avance', () => {
  const ch = {
    id: 'lot',
    titre: 'Lot',
    etat: 'en-cours',
    etapes: [
      { titre: 'Moteur', etat: 'fait', estimation: 30 },
      { titre: 'HUD', etat: 'en-cours', estimation: 120, debutLe: '2026-11-01T20:30' },
      { titre: 'Ta validation', toi: true },
    ],
  }
  // MAINTENANT = 21 h à Paris : 30 min écoulées sur 120, fin vers 22 h 30.
  const a = vueChantier(ch, sansAgents, MAINTENANT)
  const b = vueChantier(ch, sansAgents, MAINTENANT + 20 * 60_000)
  expect(a.finLibelle).toBe('Prêt pour toi')
  expect(a.fin).toBe('vers 22 h 30')
  expect(b.fin).toBe(a.fin)
  expect(b.pourcent).toBeGreaterThan(a.pourcent)
})

test('une étape en cours sans agent ni heure de début n’invente pas d’heure de fin', () => {
  const ch = {
    id: 'lot',
    titre: 'Lot',
    etat: 'en-cours',
    majLe: '2026-11-01',
    etapes: [
      { titre: 'HUD', etat: 'en-cours', estimation: 60 },
      { titre: 'Ta validation', toi: true },
    ],
  }
  const v = vueChantier(ch, sansAgents, MAINTENANT)
  expect(v.finLibelle).toBe('En cours')
  expect(v.fin).toBeUndefined()
  expect(v.dans).toBeUndefined()
  expect(v.ensuite).toBe('puis ta validation')
})

test('un chantier à déployer aux étapes toutes faites attend toujours l’utilisateur', () => {
  const ch = {
    id: 'w',
    titre: 'Train web',
    etat: 'a-deployer',
    etapes: [
      { titre: 'a', etat: 'fait' },
      { titre: 'b', etat: 'fait' },
    ],
  }
  const v = vueChantier(ch, sansAgents, MAINTENANT)
  expect(v.finLibelle).toBe('À déployer')
  expect(autreDe(v)).toEqual({ titre: 'Train web', pourcent: 100, statut: 'à déployer', ton: 'toi' })
  expect(vueChantier({ ...ch, etat: 'en-cours' }, sansAgents, MAINTENANT).finLibelle).toBe('Terminé')
})

test('une marque vedette oubliée sur un lot fini laisse la place au chantier qui avance', () => {
  const fini: ChantierVue = { ...LOT2, id: 'fini', etat: 'fait', vedette: true, enDirect: false, finLibelle: 'Terminé' }
  expect(choisirVedette([fini, LOT2])?.id).toBe('lot2')
})

test('un run silencieux dont la session n’écrit plus depuis une heure est arrêté', () => {
  const agents = [{ label: 'a', phase: 'p', etat: 'en-cours' as const, activiteMs: MAINTENANT - 3 * 3600_000 }]
  const base = { finalAJour: false, agents, journalMs: MAINTENANT - 3 * 3600_000, maintenant: MAINTENANT }
  expect(etatRun({ ...base, sessionMs: MAINTENANT - 2 * 60_000 })).toBe('silencieux')
  expect(etatRun({ ...base, sessionMs: MAINTENANT - 2 * 3600_000 })).toBe('arrete')
})

test('statut court dans la carte des autres chantiers : l’heure tient entière à 280 px', () => {
  const lot3: ChantierVue = {
    ...LOT2,
    id: 'lot3',
    titre: 'Lot 3',
    fin: 'le 03/11 vers 14 h 20',
    finLibelle: 'Fin estimée',
    etapes: LOT2.etapes.map(e => ({ ...e, toi: false })),
  }
  const autre = autreDe(lot3)
  expect(autre.statut).toBe('prêt 03/11 14 h 20')
  // Une coupe « … » devient &#8230; dans le SVG.
  expect(listeChantiers([autre], 280)).not.toMatch(/&#8230;/)
})

test('etat.json écrit avec un BOM et des ids en double', () => {
  const bom = String.fromCharCode(0xfeff)
  const json =
    bom +
    JSON.stringify({
      aToi: [
        { id: 'a', titre: 'Premier' },
        { id: 'a', titre: 'Doublon' },
      ],
      chantiers: [{ id: 'c', titre: 'C' }],
    })
  const suivi = lireEtatSuivi(json)
  expect(suivi.aToi.map(i => i.titre)).toEqual(['Premier'])
  expect(suivi.doublons).toEqual(['a'])
  expect(marquerFaitDans(json, 'a', '2026-11-02T08:00')).toMatch(/"fait": true/)
})

test('une section renommée est signalée, pas tue', () => {
  expect(lireDecisions('# Chantiers\n## 5. Autre\n| 1 | q | c |')).toBeNull()
  expect(lireDecisions('## 4. Décisions\n| n° | Question | Contexte |\n| 1 | Rendu ? | A ou B |')).toEqual([
    { numero: '1', question: 'Rendu ?', contexte: 'A ou B' },
  ])
})

test('dossiers de projets : casse, worktrees, sous-dossiers, mais pas la copie voisine', () => {
  const slug = 'C--Users-omarb-Desktop-Reborn---Gestion-RBLAUNCHER'
  expect(dossierDuProjet('c--Users-omarb-Desktop-Reborn---Gestion-RBLAUNCHER', slug)).toBe(true)
  expect(dossierDuProjet(slug + '--claude-worktrees-lot2', slug)).toBe(true)
  expect(dossierDuProjet(slug + '-tools-claude-suivi', slug)).toBe(true)
  expect(dossierDuProjet(slug + '---Copie', slug)).toBe(false)
  expect(dossierDuProjet(slug + '2', slug)).toBe(false)
})

test('heure de Paris : été et hiver, date seule refusée comme ancre', () => {
  expect(msParis('2026-07-01T12:00')).toBe(Date.UTC(2026, 6, 1, 10, 0))
  expect(msParis('2026-11-02 07:30')).toBe(Date.UTC(2026, 10, 2, 6, 30))
  expect(msParis('2026-11-02T07:30Z')).toBe(Date.UTC(2026, 10, 2, 7, 30))
  expect(msParis('2026-11-02')).toBeUndefined()
})

test('cartes : pas de « 0 % » sans étapes, et le Plan dit quand le rythme n’est pas mesuré', () => {
  const hero = { titre: 'Ménage', pourcent: 0, finLibelle: 'En cours', etapes: [], faites: 0, total: 0 }
  expect(carteHero(hero, 400)).not.toMatch(/>0<tspan/)
  const plan = cartePlan({ titre: 'Game Plan beta', pourcent: 30, livres: 3, enCours: 0, aFaire: 10, horsBeta: 0 }, 400)
  expect(plan).toMatch(/pas encore mesur/)
})

test('la racine se retrouve depuis un dossier à neuf niveaux sous le dépôt', async () => {
  const cible = ['C:', 'R', 'docs', 'CHANTIERS.md'].join('\\')
  const vus: string[] = []
  const existe = async (chemin: string) => (vus.push(chemin), chemin === cible)
  const profond = ['C:', 'R', 'minecraft', 'shinobi', 'ShinobiSense', 'src', 'main', 'java', 'com', 'reborn', 'shinobisense']
  expect(await chercherRacine(profond.join('\\'), existe)).toBe(['C:', 'R'].join('\\'))
  expect(vus.length).toBe(10)
})

test('hors du dépôt, la remontée s’arrête à la racine du disque ou du partage réseau', async () => {
  const existe = async () => false
  expect(await chercherRacine(['C:', 'Users', 'omarb', 'ailleurs'].join('\\'), existe)).toBeNull()
  expect(await chercherRacine(['', '', 'wsl.localhost', 'Ubuntu', 'home'].join('\\'), existe)).toBeNull()
  expect(await chercherRacine('/home/omar/projet', existe)).toBeNull()
})

test('à toi : liste indisponible quand le suivi est illisible, au lieu de « rien ne t’attend »', async ($, on) => {
  mock.clock(on, { now: MAINTENANT })
  memoire(on, { ...SNAP, aToi: [], listeIndisponible: true, erreurs: ['etat.json illisible'] })
  for (const surface of ['terminal', 'desktop'] as const) {
    const ui = await $.ui.mount({ ...PANE, surface })
    expect(await ui.find({ type: 'Text', text: /Liste indisponible/ })).toBeDefined()
    expect(await ui.find({ type: 'Text', text: /Rien ne t’attend/ })).toBeUndefined()
    await ui.unmount()
  }
})

test('à toi : le titre suit l’étiquette dans le même texte', async ($, on) => {
  mock.clock(on, { now: MAINTENANT })
  memoire(on, SNAP)
  const ui = await $.ui.mount({ ...PANE, surface: 'desktop' })
  expect(await ui.find({ type: 'Text', text: /À déployer\s+Déployer le train web/ })).toBeDefined()
  await ui.unmount()
})

test('workflows hors vedette : « + 1 autre » avec une vedette', async ($, on) => {
  mock.clock(on, { now: MAINTENANT })
  memoire(on, { ...SNAP, wfHorsVedette: 1 })
  const ui = await $.ui.mount({ ...PANE, surface: 'desktop' })
  expect(await ui.find({ type: 'Text', text: /^\+ 1 autre workflow en cours/ })).toBeDefined()
  await ui.unmount()
})

test('workflows sans vedette : « 2 workflows en cours », sans « autre »', async ($, on) => {
  mock.clock(on, { now: MAINTENANT })
  memoire(on, { ...SNAP, hero: null, wfHorsVedette: 2 })
  const ui = await $.ui.mount({ ...PANE, surface: 'desktop' })
  expect(await ui.find({ type: 'Text', text: /^2 workflows en cours/ })).toBeDefined()
  await ui.unmount()
})

test('terminal : la carte du haut garde l’étape en cours et la suite', async ($, on) => {
  mock.clock(on, { now: MAINTENANT })
  memoire(on, SNAP)
  const ui = await $.ui.mount({ ...PANE, surface: 'terminal' })
  expect(await ui.find({ type: 'Text', text: /En cours : HUD bas-droite · depuis 50 min/ })).toBeDefined()
  await ui.unmount()
})

test('copie impossible : la commande se déplie sous l’élément', async ($, on) => {
  mock.clock(on, { now: MAINTENANT })
  memoire(on, SNAP)
  on('ui.copy', () => ({ value: { isCopied: false, reason: 'no-surface' } }))
  const ui = await $.ui.mount({ ...PANE, surface: 'desktop' })
  await ui.press({ key: 'copier-deploy' })
  await ui.unmount()
  const apres = await $.ui.mount({ ...PANE, surface: 'desktop' })
  expect(await apres.find({ key: 'cmd-deploy' })).toBeDefined()
  await apres.unmount()
})

test('Fait coche l’élément même dans un fichier écrit avec un BOM', async ($, on) => {
  mock.clock(on, { now: MAINTENANT })
  memoire(on, SNAP)
  const ecrits: string[] = []
  const bom = String.fromCharCode(0xfeff)
  on('fs.read', () => ({ value: bom + JSON.stringify({ aToi: [{ id: 'deploy', titre: 'Déployer le train web' }] }) }))
  on('fs.write', (_, e) => {
    ecrits.push(e.text)
    return { value: undefined }
  })
  const ui = await $.ui.mount({ ...PANE, surface: 'desktop' })
  await ui.press({ key: 'fait-deploy' })
  await ui.unmount()
  expect(ecrits.length).toBe(1)
  expect(JSON.parse(ecrits[0] ?? '{}').aToi[0]).toMatchObject({ id: 'deploy', fait: true, faitLe: '2026-11-01T21:00' })
})
