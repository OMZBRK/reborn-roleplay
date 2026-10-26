import { AppStatus, Role } from '@prisma/client';
import {
  calculerSuivi,
  type PhaseCandidature,
} from '../whitelist/whitelist.rules';
import { WHERE_FILTRE_WHITELIST } from './admin.service';
import type { FiltreWhitelist } from './dto/admin.dto';

// Les onglets « à faire » de la liste whitelist (maquette P1) sont des
// requêtes Prisma écrites à la main : la phase n'est pas stockée. Ce test les
// confronte à `calculerSuivi`, la règle du launcher et du panel, sur toutes
// les combinaisons de statuts : un onglet ne doit retenir que les dossiers
// de sa phase, et tous ceux-là.

/** Évalue le sous-ensemble de filtres Prisma utilisé par WHERE_FILTRE_WHITELIST. */
function correspond(
  ligne: Record<string, unknown>,
  where: Record<string, unknown>,
): boolean {
  return Object.entries(where).every(([cle, cond]) => {
    if (cle === 'AND') {
      return (cond as Record<string, unknown>[]).every((w) =>
        correspond(ligne, w),
      );
    }
    if (cle === 'OR') {
      return (cond as Record<string, unknown>[]).some((w) =>
        correspond(ligne, w),
      );
    }
    if (cle === 'NOT') {
      const liste = Array.isArray(cond) ? cond : [cond];
      return (liste as Record<string, unknown>[]).every(
        (w) => !correspond(ligne, w),
      );
    }
    const valeur = ligne[cle];
    if (cond !== null && typeof cond === 'object') {
      const c = cond as Record<string, unknown>;
      if ('in' in c || 'notIn' in c || 'not' in c || 'equals' in c) {
        if ('in' in c && !(c.in as unknown[]).includes(valeur)) return false;
        if ('notIn' in c && (c.notIn as unknown[]).includes(valeur)) {
          return false;
        }
        if ('not' in c && valeur === c.not) return false;
        if ('equals' in c && valeur !== c.equals) return false;
        return true;
      }
      // Relation (user) : filtre imbriqué.
      return correspond(valeur as Record<string, unknown>, c);
    }
    return valeur === cond;
  });
}

const PHASES_DU_FILTRE: Record<FiltreWhitelist, PhaseCandidature[]> = {
  ECRIT: ['ECRIT_EN_ATTENTE'],
  DOUANE: ['DOUANE_A_RESERVER', 'DOUANE_RESERVEE', 'DOUANE_A_REVOIR'],
  REVISION: ['ECRIT_A_REVOIR'],
};

const STATUTS = Object.values(AppStatus);
const MAINTENANT = new Date('2026-10-25T18:00:00.000Z');

describe('Onglets « à faire » de la liste whitelist', () => {
  const cas: Array<{
    ligne: Record<string, unknown>;
    phase: PhaseCandidature;
  }> = [];
  for (const status of STATUTS)
    for (const hrpStatus of STATUTS)
      for (const rpStatus of STATUTS)
        for (const oralStatus of STATUTS)
          for (const role of [Role.PLAYER, Role.WHITELISTED])
            for (const oralPassedAt of [null, new Date('2026-09-01')])
              for (const place of [false, true]) {
                const { phase } = calculerSuivi({
                  role,
                  oralPassedAt,
                  whitelistRejectedAt: null,
                  app: {
                    status,
                    hrpStatus,
                    rpStatus,
                    oralStatus,
                    oralDecidedAt: null,
                    reviewedAt: null,
                  },
                  reservation: place
                    ? {
                        id: 'r',
                        seanceId: 's',
                        convocationAt: new Date('2026-10-26T19:00:00.000Z'),
                      }
                    : null,
                  now: MAINTENANT,
                });
                cas.push({
                  ligne: {
                    status,
                    hrpStatus,
                    rpStatus,
                    oralStatus,
                    user: { role, oralPassedAt },
                  },
                  phase,
                });
              }

  it.each(Object.keys(PHASES_DU_FILTRE) as FiltreWhitelist[])(
    'le filtre %s retient exactement les dossiers de sa phase',
    (filtre) => {
      const where = WHERE_FILTRE_WHITELIST[filtre] as Record<string, unknown>;
      const ecarts = cas.filter(
        (c) =>
          correspond(c.ligne, where) !==
          PHASES_DU_FILTRE[filtre].includes(c.phase),
      );
      expect(ecarts.slice(0, 5)).toEqual([]);
    },
  );

  it('les onglets ne se recouvrent pas', () => {
    for (const c of cas) {
      const retenu = (Object.keys(PHASES_DU_FILTRE) as FiltreWhitelist[]).filter(
        (f) =>
          correspond(
            c.ligne,
            WHERE_FILTRE_WHITELIST[f] as Record<string, unknown>,
          ),
      );
      expect(retenu.length).toBeLessThanOrEqual(1);
    }
  });
});
