// Panneau « Suivi Reborn » à côté du chat : ce qui t'attend, ce que Claude fait en ce moment
// (workflows de toutes les sessions du projet), l'avancement des chantiers et ce qui est en ligne.
// Sources : .claude/suivi/etat.json (tenu par les sessions Claude), docs/CHANTIERS.md §4,
// docs/ETAT_DES_LIEUX.md §2 et les journaux des workflows.

import { atom, read, update } from 'claude-code'
import type { Color, EngineInterface, Register } from 'claude-code'

import type {
  AgentWf,
  AutreTache,
  ChantierVue,
  EtatChantier,
  EtatEtape,
  EtatWorkflow,
  ItemAToi,
  PhaseWf,
  TypeAToi,
  WorkflowVue,
} from '../types'
import { calculer } from './calcul'
import { horodatage, marquerFaitDans } from './donnees'
import { carteHero, cartePlan, listeChantiers } from './visuel'

const PANE = 'suivi'
const TITRE = 'Suivi Reborn'
const PERIODE_MS = 15_000

// Changer l'étiquette quand la forme de l'instantané change : l'ancienne valeur survit au rechargement.
const instantane = atom({ plugin: 'reborn-suivi', key: 'instantane' } as const, null, { shape: 'suivi-2' })
const ouverts = atom({ plugin: 'reborn-suivi', key: 'ouverts' } as const, [])
const taches = atom({ plugin: 'reborn-suivi', key: 'taches' } as const, [])

const ETAT_CHANTIER: Record<EtatChantier, { libelle: string; couleur: Color }> = {
  'en-cours': { libelle: 'en cours', couleur: 'warning' },
  recette: { libelle: 'en recette', couleur: 'suggestion' },
  'a-deployer': { libelle: 'à déployer', couleur: 'claude' },
  attente: { libelle: 'en attente', couleur: 'suggestion' },
  bloque: { libelle: 'bloqué', couleur: 'error' },
  'a-faire': { libelle: 'à faire', couleur: 'subtle' },
  'plus-tard': { libelle: 'plus tard', couleur: 'subtle' },
  fait: { libelle: 'fait', couleur: 'success' },
}

const ETAPE: Record<EtatEtape, { symbole: string; couleur: Color; svg: [string, number] }> = {
  fait: { symbole: '✓', couleur: 'success', svg: ['#C9A227', 1] },
  'en-cours': { symbole: '●', couleur: 'warning', svg: ['#C9A227', 0.45] },
  'a-faire': { symbole: '○', couleur: 'subtle', svg: ['#8A8580', 0.3] },
  echec: { symbole: '✕', couleur: 'error', svg: ['#D0503F', 1] },
  attente: { symbole: '◇', couleur: 'suggestion', svg: ['#6F86AD', 0.75] },
}

const PHASE_EN_ETAPE: Record<PhaseWf['etat'], EtatEtape> = {
  fait: 'fait',
  'en-cours': 'en-cours',
  'a-venir': 'a-faire',
  echec: 'echec',
}

const ETAT_RUN: Record<EtatWorkflow, { libelle: string; couleur: Color }> = {
  'en-cours': { libelle: 'en cours', couleur: 'warning' },
  silencieux: { libelle: 'silencieux', couleur: 'error' },
  termine: { libelle: 'terminé', couleur: 'success' },
  echoue: { libelle: 'échoué', couleur: 'error' },
  arrete: { libelle: 'arrêté', couleur: 'subtle' },
}

const TYPE_ATOI: Record<TypeAToi, { libelle: string; couleur: Color }> = {
  commande: { libelle: 'À coller', couleur: 'claude' },
  test: { libelle: 'À tester', couleur: 'warning' },
  decision: { libelle: 'À trancher', couleur: 'suggestion' },
  deploiement: { libelle: 'À déployer', couleur: 'claude' },
  validation: { libelle: 'À valider', couleur: 'warning' },
  info: { libelle: 'Info', couleur: 'subtle' },
}

const duree = (ms: number): string => {
  const min = Math.max(0, Math.round(ms / 60_000))
  if (min < 1) return 'moins d’une minute'
  if (min < 60) return `${min} min`
  const h = Math.floor(min / 60)
  const m = min % 60
  if (h < 24) return m ? `${h} h ${String(m).padStart(2, '0')}` : `${h} h`
  return `${Math.floor(h / 24)} j`
}

// L'environnement du mod peut tourner en UTC : l'heure affichée est celle de Paris.
const heure = (ms: number): string => {
  try {
    return new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Paris' }).format(ms)
  } catch {
    const d = new Date(ms)
    return `${String(d.getUTCHours()).padStart(2, '0')}:${String(d.getUTCMinutes()).padStart(2, '0')} UTC`
  }
}

/** Barre en segments, une case par étape, dorée quand c'est fait. */
const svgSegments = (etats: EtatEtape[], largeur = 180, hauteur = 6): string => {
  const n = Math.max(1, etats.length)
  const ecart = n > 1 ? Math.min(3, largeur / n / 4) : 0
  const w = (largeur - ecart * (n - 1)) / n
  const cases = (etats.length > 0 ? etats : (['a-faire'] as EtatEtape[]))
    .map((et, i) => {
      const [fill, opacite] = ETAPE[et].svg
      const x = (i * (w + ecart)).toFixed(1)
      return `<rect x="${x}" y="0" width="${w.toFixed(1)}" height="${hauteur}" rx="1.5" fill="${fill}" fill-opacity="${opacite}"/>`
    })
    .join('')
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${largeur}" height="${hauteur}" viewBox="0 0 ${largeur} ${hauteur}">${cases}</svg>`
}

const texteSegments = (etats: EtatEtape[]): string =>
  etats.map(et => (et === 'fait' ? '■' : et === 'en-cours' ? '▣' : et === 'echec' ? '✕' : '□')).join('')

// Un seul calcul à la fois ; l'empreinte du dernier évite de redessiner pour rien.
let calculEnCours = false
let dernier = ''

async function rafraichir($: EngineInterface): Promise<void> {
  if (calculEnCours) return
  calculEnCours = true
  try {
    const vue = await calculer($, await read($, taches))
    const empreinte = JSON.stringify(vue)
    if (empreinte !== dernier) {
      dernier = empreinte
      await update($, instantane, () => vue)
    }
  } catch (err) {
    $.ui.log(`reborn-suivi : calcul impossible (${String(err).slice(0, 200)})`)
  } finally {
    calculEnCours = false
  }
}

async function ouvrir($: EngineInterface, demande: boolean): Promise<void> {
  const ouvert = await $.ui.open({ id: PANE, title: TITRE })
  if (demande && !ouvert.isPlaced) $.ui.toast(`Panneau en attente : ${ouvert.reason}`)
  void rafraichir($)
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    await $.command.register({
      name: 'suivi',
      description: 'Ouvre le panneau Suivi Reborn (ce qui t’attend, ce que Claude fait, les chantiers)',
    })
    void ouvrir($, false)
    $.clock.every(PERIODE_MS, () => {
      void $.ui.panes().then(panes => {
        if (panes.some(p => p.id === PANE)) void rafraichir($)
      })
    })
    return next(e)
  })

  on('command.run', { command: 'suivi' }, async $ => {
    await ouvrir($, true)
    return { text: 'Panneau Suivi Reborn ouvert.' }
  })

  // Les tâches de fond de cette session (shells, moniteurs) ne sont lisibles qu'en fin de tour.
  on('classic.Stop', async ($, e, next) => {
    const enVol: AutreTache[] = (e.background_tasks ?? []).map(t => ({
      id: t.id,
      type: t.type,
      description: t.name ?? t.description,
      statut: t.status,
    }))
    await update($, taches, () => enVol)
    void rafraichir($)
    return next(e)
  })

  on('turn.complete', async ($, e, next) => {
    void rafraichir($)
    return next(e)
  })

  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const table = $.ui.resolve(e)
    const { Box, Text, Button } = table
    // Le terminal résout aussi « Svg », en un élément vide : il dessine les cartes en texte.
    const Svg = e.surface !== 'terminal' && 'Svg' in table ? table.Svg : undefined
    const vue = await read($, instantane)
    const ouv = await read($, ouverts)
    const maintenant = await $.clock.now()
    const estOuvert = (id: string) => ouv.includes(id)
    const basculer = (id: string) => () => {
      void update($, ouverts, l => (l.includes(id) ? l.filter(x => x !== id) : [...l, id]))
    }

    const titre = (texte: string, compte?: number) => (
      <Box flexDirection="row" gap={1} marginTop={1}>
        <Text size="footnote" weight="semibold" color="subtle">
          {texte.toUpperCase()}
        </Text>
        {compte !== undefined && (
          <Text size="footnote" color="subtle">
            {String(compte)}
          </Text>
        )}
      </Box>
    )

    const barre = (etats: EtatEtape[], alt: string) =>
      Svg ? (
        <Svg source={svgSegments(etats)} alt={alt} />
      ) : (
        <Text color="warning">{texteSegments(etats)}</Text>
      )

    if (vue === null) {
      return (
        <Box flexDirection="column">
          <Text dimColor>Calcul du suivi…</Text>
        </Box>
      )
    }

    // ── À toi ──
    const ligneAToi = (item: ItemAToi) => {
      const type = TYPE_ATOI[item.type]
      return (
        <Box flexDirection="column" gap={0}>
          <Box flexDirection="row" gap={1} flexWrap="wrap">
            <Text color={type.couleur} weight="semibold" size="footnote">
              {type.libelle}
            </Text>
            <Text>{item.titre}</Text>
          </Box>
          {item.detail !== undefined && (
            <Text dimColor size="footnote">
              {item.detail}
            </Text>
          )}
          <Box flexDirection="row" gap={1}>
            {item.commande !== undefined && (
              <Button
                key={`copier-${item.id}`}
                label="Copier"
                onPress={press => {
                  const texte = item.commande ?? ''
                  void $.ui.copy({ text: texte, surface: press.surface }).then(r => {
                    $.ui.toast(r.isCopied ? 'Copié' : 'Copie impossible ici : sélectionne le texte dans etat.json')
                  })
                }}
              />
            )}
            <Button
              key={`fait-${item.id}`}
              label="Fait"
              onPress={() => {
                void marquerFait($, vue.fichierEtat, item).then(() => rafraichir($))
              }}
            />
          </Box>
        </Box>
      )
    }

    // ── Workflows ──
    const lignePhases = (w: WorkflowVue) => (
      <Box flexDirection="row" gap={1} flexWrap="wrap">
        {w.phases.map(p => {
          const et = ETAPE[PHASE_EN_ETAPE[p.etat]]
          return (
            <Text color={et.couleur} size="footnote">
              {et.symbole} {p.titre}
            </Text>
          )
        })}
      </Box>
    )

    const ligneAgent = (a: AgentWf) => {
      const et = ETAPE[a.etat]
      const temps =
        a.etat === 'en-cours' && a.debutMs !== undefined ? ` · depuis ${duree(maintenant - a.debutMs)}` : ''
      const silence =
        a.etat === 'en-cours' && a.activiteMs !== undefined && maintenant - a.activiteMs > 10 * 60_000
          ? ` · rien depuis ${duree(maintenant - a.activiteMs)}`
          : ''
      return (
        <Text size="footnote" color={a.etat === 'fait' ? 'subtle' : undefined}>
          <Text color={et.couleur}>{et.symbole}</Text> {a.label}
          <Text dimColor>
            {a.phase ? ` (${a.phase})` : ''}
            {temps}
            {silence}
          </Text>
        </Text>
      )
    }

    const blocRun = (w: WorkflowVue) => {
      const etat = ETAT_RUN[w.etat]
      const id = `wf-${w.runId}`
      const actifs = w.agents.filter(a => a.etat === 'en-cours')
      const faites = w.phases.filter(p => p.etat === 'fait').length
      const actif = w.etat === 'en-cours' || w.etat === 'silencieux'
      const quand = actif
        ? w.debutMs !== undefined
          ? `depuis ${duree(maintenant - w.debutMs)}`
          : ''
        : `il y a ${duree(maintenant - w.majMs)}${w.dureeMs !== undefined ? ` · durée ${duree(w.dureeMs)}` : ''}`
      return (
        <Box flexDirection="column" gap={0}>
          <Button key={id} plain onPress={basculer(id)}>
            <Text weight="semibold">{w.nom}</Text> <Text color={etat.couleur}>{etat.libelle}</Text>{' '}
            <Text dimColor>
              {quand}
              {w.estCetteSession ? '' : ` · ${w.session}`}
            </Text>
          </Button>
          {w.phases.length > 0 && (
            <Box flexDirection="row" gap={1} alignItems="center">
              {barre(
                w.phases.map(p => PHASE_EN_ETAPE[p.etat]),
                `${faites} phases sur ${w.phases.length}`,
              )}
              <Text dimColor size="footnote">
                {`${faites}/${w.phases.length}`}
              </Text>
            </Box>
          )}
          {actif && w.phases.length > 0 && lignePhases(w)}
          {actif && actifs.map(ligneAgent)}
          {estOuvert(id) && (
            <Box flexDirection="column" gap={0} paddingLeft={2}>
              {w.description !== undefined && (
                <Text dimColor size="footnote">
                  {w.description}
                </Text>
              )}
              {(actif ? w.agents.filter(a => a.etat !== 'en-cours') : w.agents).map(ligneAgent)}
            </Box>
          )}
        </Box>
      )
    }

    // ── Chantiers ──
    const blocChantier = (c: ChantierVue) => {
      const etat = ETAT_CHANTIER[c.etat]
      const id = `ch-${c.id}`
      return (
        <Box flexDirection="column" gap={0}>
          <Button key={id} plain onPress={basculer(id)}>
            <Text weight="semibold">{c.titre}</Text> <Text color={etat.couleur}>{etat.libelle}</Text>
            {c.enDirect ? <Text color="warning"> · en direct</Text> : ''}
          </Button>
          {c.etapes.length > 0 && (
            <Box flexDirection="row" gap={1} alignItems="center">
              {barre(
                c.etapes.map(et => et.etat),
                `${c.faites} étapes sur ${c.etapes.length}`,
              )}
              <Text dimColor size="footnote">
                {`${c.faites}/${c.etapes.length}`}
              </Text>
            </Box>
          )}
          {c.prochaine !== undefined && (
            <Text size="footnote">
              <Text color="subtle">→ </Text>
              {c.prochaine}
            </Text>
          )}
          {estOuvert(id) && (
            <Box flexDirection="column" gap={0} paddingLeft={2}>
              {c.etapes.map(et => (
                <Text size="footnote" color={et.etat === 'fait' ? 'subtle' : undefined}>
                  <Text color={ETAPE[et.etat].couleur}>{ETAPE[et.etat].symbole}</Text> {et.titre}
                  {et.enDirect ? <Text dimColor> · direct</Text> : ''}
                </Text>
              ))}
              {(c.groupe !== undefined || c.branche !== undefined || c.majLe !== undefined) && (
                <Text dimColor size="footnote">
                  {[c.groupe, c.branche, c.majLe ? `maj ${c.majLe}` : undefined].filter(Boolean).join(' · ')}
                </Text>
              )}
            </Box>
          )}
        </Box>
      )
    }

    const actifs = vue.workflows.filter(w => w.etat === 'en-cours' || w.etat === 'silencieux')
    const finis = vue.workflows.filter(w => w.etat !== 'en-cours' && w.etat !== 'silencieux')
    const chantiersVifs = vue.chantiers.filter(c => c.etat !== 'fait' && c.etat !== 'plus-tard')
    const chantiersRanges = vue.chantiers.filter(c => c.etat === 'fait' || c.etat === 'plus-tard')
    // Les cartes visent la largeur du panneau ; une image plus large que lui est réduite, jamais l'inverse.
    const largeur = Math.max(280, Math.min(560, Math.round(e.props.bodyColumns * 8.2)))
    const hero = vue.hero
    const plan = vue.plan

    // Chaque carte dans une Box à clé : le test la retrouve, et le terminal (sans Svg) reçoit le texte.
    const carte = (cle: string, source: string, alt: string, repli: string) => (
      <Box key={cle}>{Svg ? <Svg source={source} alt={alt} /> : <Text>{repli}</Text>}</Box>
    )

    const resumeHero = hero
      ? `${hero.titre} : ${hero.pourcent} %, ${hero.fin ? `${hero.finLibelle.toLowerCase()} ${hero.fin}` : hero.finLibelle.toLowerCase()}`
      : ''
    const resumePlan = plan
      ? `${plan.titre} : ${plan.pourcent} % (${plan.livres} livrés, ${plan.enCours} en cours, ${plan.aFaire} à faire)${plan.fin ? `, fin ${plan.fin} à ce rythme` : ''}`
      : ''

    const details = (
      <Box flexDirection="column" gap={1}>
        <Box flexDirection="column" gap={1}>
          {titre('Claude travaille', actifs.length)}
          {actifs.length === 0 && <Text dimColor>Aucun workflow en cours.</Text>}
          {actifs.map(blocRun)}
          {vue.autres.map(t => (
            <Text size="footnote">
              <Text color="warning">●</Text> {t.description}
              <Text dimColor>{` (${t.type}, ${t.statut})`}</Text>
            </Text>
          ))}
          {finis.length > 0 && (
            <Box flexDirection="column" gap={1}>
              <Button key="finis" plain onPress={basculer('finis')}>
                {estOuvert('finis') ? '▾ ' : '▸ '}
                {`${finis.length} terminés ces 12 h`}
              </Button>
              {estOuvert('finis') && finis.map(blocRun)}
            </Box>
          )}
        </Box>

        <Box flexDirection="column" gap={1}>
          {titre('Chantiers', chantiersVifs.length)}
          {vue.chantiers.length === 0 && <Text dimColor>Aucun chantier dans le fichier de suivi.</Text>}
          {chantiersVifs.map(blocChantier)}
          {chantiersRanges.length > 0 && (
            <Box flexDirection="column" gap={1}>
              <Button key="ranges" plain onPress={basculer('ranges')}>
                {estOuvert('ranges') ? '▾ ' : '▸ '}
                {`${chantiersRanges.length} plus tard ou faits`}
              </Button>
              {estOuvert('ranges') && chantiersRanges.map(blocChantier)}
            </Box>
          )}
        </Box>

        {vue.decisions.length > 0 && (
          <Box flexDirection="column" gap={0}>
            {titre('Décisions en attente', vue.decisions.length)}
            {vue.decisions.map(d => (
              <Box flexDirection="column" gap={0}>
                <Text size="footnote">
                  <Text color="suggestion">{`n° ${d.numero}`}</Text> {d.question}
                </Text>
                {d.contexte !== '' && (
                  <Text dimColor size="footnote">
                    {d.contexte}
                  </Text>
                )}
              </Box>
            ))}
          </Box>
        )}

        {vue.prod.length > 0 && (
          <Box flexDirection="column" gap={0}>
            {titre('En ligne')}
            {vue.prod.map(a => (
              <Text size="footnote">
                <Text weight="medium">{a.nom}</Text> {a.enLigne}
                {a.aJour === false ? <Text color="warning">{` · main : ${a.surMain}`}</Text> : ''}
              </Text>
            ))}
          </Box>
        )}

        <Text dimColor size="caption">
          Sources : .claude/suivi/etat.json · GAMEPLAN §54 · CHANTIERS §4 · ETAT_DES_LIEUX §2 · journaux des workflows
        </Text>
      </Box>
    )

    return (
      <Box flexDirection="column" gap={1}>
        {vue.erreurs.map(err => (
          <Text color="error" size="footnote">
            {err}
          </Text>
        ))}

        {hero && carte('carte-hero', carteHero(hero, largeur), resumeHero, resumeHero)}
        {actifs.length > 1 && (
          <Text dimColor size="footnote">
            {`+ ${actifs.length - 1} autre${actifs.length > 2 ? 's' : ''} workflow${actifs.length > 2 ? 's' : ''} en cours (voir le détail)`}
          </Text>
        )}
        {plan && carte('carte-plan', cartePlan(plan, largeur), resumePlan, resumePlan)}

        <Box flexDirection="column" gap={1}>
          {titre('À toi', vue.aToi.length)}
          {vue.aToi.length === 0 && <Text dimColor>Rien ne t’attend.</Text>}
          {vue.aToi.map(ligneAToi)}
          {vue.faitsRecents.length > 0 && (
            <Text dimColor size="footnote">
              {`Fait récemment : ${vue.faitsRecents.map(f => f.titre).join(' · ')}`}
            </Text>
          )}
        </Box>

        {vue.autresChantiers.length > 0 &&
          carte(
            'carte-autres',
            listeChantiers(vue.autresChantiers, largeur),
            vue.autresChantiers.map(a => `${a.titre} ${a.pourcent} % (${a.statut})`).join(', '),
            vue.autresChantiers.map(a => `${a.titre} · ${a.pourcent} % · ${a.statut}`).join('\n'),
          )}

        <Box flexDirection="row" justifyContent="space-between" alignItems="center" gap={1}>
          <Button key="details" plain onPress={basculer('details')}>
            {estOuvert('details') ? '▾ Masquer le détail' : '▸ Tout le détail'}
            <Text dimColor>{` · ${vue.decisions.length} décisions, workflows, versions`}</Text>
          </Button>
          <Button key="actualiser" label="Actualiser" onPress={() => void rafraichir($)} />
        </Box>
        <Text dimColor size="caption">
          {`calculé à ${heure(vue.minute * 60_000)}`}
          {vue.majEtat ? ` · suivi tenu le ${vue.majEtat}` : ''}
        </Text>

        {estOuvert('details') && details}
      </Box>
    )
  })
}

async function marquerFait($: EngineInterface, fichier: string | null, item: ItemAToi): Promise<void> {
  if (!fichier) return
  try {
    const avant = String(await $.fs.read(fichier))
    const apres = marquerFaitDans(avant, item.id, horodatage(await $.clock.now()))
    if (apres === null) {
      $.ui.toast(`« ${item.titre} » n’est plus dans le fichier de suivi`)
      return
    }
    await $.fs.write(fichier, apres)
    $.ui.toast(`Fait : ${item.titre}`)
  } catch (err) {
    $.ui.toast(`Impossible de cocher : ${String(err).slice(0, 120)}`)
  }
}
