import type { On } from 'claude-code'
import { expect, mock, test } from 'claude-code/testing'

import { avancement, formatDans, lirePlan } from '../hooks/donnees'
import type { ChantierVue, Instantane } from '../types'

// Le kit de test n'a pas d'état préchargé : une mémoire tient lieu de l'hôte sous le mod.
function memoire(on: On, instantane?: Instantane) {
  const valeurs = new Map<string, { value: unknown; version: number }>()
  if (instantane) valeurs.set('instantane', { value: { shape: 'suivi-2', value: instantane }, version: 1 })
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

