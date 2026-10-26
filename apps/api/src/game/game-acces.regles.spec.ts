import { AppStatus, Role } from '@prisma/client';
import {
  MSG_CANDIDATURE_REQUISE,
  MSG_COMPTE_PAS_A_JOUR,
  evaluerAcces,
  type EntreeAcces,
} from './game-acces.regles';

// Aiguillage du serveur de jeu (CANDIDATURE_ET_DOUANE.md §3.3, §3.10) : même
// règle que le jeton de jeu, refus par défaut.

// Samedi 24/10/2026, 21 h à Paris (UTC+2) : heure de convocation H.
const H = new Date('2026-10-24T19:00:00Z');
const MIN = 60 * 1000;

const ecritAccepte = {
  status: AppStatus.PENDING,
  hrpStatus: AppStatus.APPROVED,
  rpStatus: AppStatus.APPROVED,
  oralStatus: AppStatus.PENDING,
  oralDecidedAt: null,
  reviewedAt: new Date('2026-10-20T10:00:00Z'),
  writtenApprovedAt: new Date('2026-10-20T10:00:00Z'),
};

function entree(over: Partial<EntreeAcces> = {}): EntreeAcces {
  return {
    compte: {
      role: Role.PLAYER,
      banned: false,
      oralPassedAt: null,
      whitelistRejectedAt: null,
    },
    candidature: ecritAccepte,
    reservation: { id: 'r1', seanceId: 's1', convocationAt: H },
    maintenant: new Date(H.getTime() - 5 * MIN),
    douaneActive: true,
    retardMaxMin: 30,
    ...over,
  };
}

describe('evaluerAcces', () => {
  it('candidat dans sa fenêtre, douane active : portée douane', () => {
    expect(evaluerAcces(entree())).toEqual({
      portee: 'douane',
      raison: 'DOUANE_FENETRE_OUVERTE',
      message: null,
      phase: 'DOUANE_RESERVEE',
      staff: false,
    });
  });

  it.each([
    ['20 min avant', -20, 'DOUANE_A_VENIR'],
    ['10 min avant', -10, 'DOUANE_FENETRE_OUVERTE'],
    ['25 min après', 25, 'DOUANE_FENETRE_OUVERTE'],
    ['35 min après', 35, 'DOUANE_FENETRE_PASSEE'],
  ])('fenêtre H − 15 min à H + 30 min : %s', (_cas, decalage, raison) => {
    const a = evaluerAcces(
      entree({ maintenant: new Date(H.getTime() + decalage * MIN) }),
    );
    expect(a.raison).toBe(raison);
    expect(a.portee).toBe(
      raison === 'DOUANE_FENETRE_OUVERTE' ? 'douane' : 'aucune',
    );
  });

  it('séance à venir : le message donne l’heure de Paris', () => {
    const a = evaluerAcces(
      entree({ maintenant: new Date(H.getTime() - 3600_000) }),
    );
    expect(a.message).toContain('21 h');
  });

  it('douane coupée : aucune, même texte que le jeton', () => {
    expect(evaluerAcces(entree({ douaneActive: false }))).toMatchObject({
      portee: 'aucune',
      raison: 'DOUANE_COUPEE',
      message: MSG_CANDIDATURE_REQUISE,
    });
  });

  it('écrit accepté sans place : à réserver', () => {
    expect(evaluerAcces(entree({ reservation: null }))).toMatchObject({
      portee: 'aucune',
      raison: 'DOUANE_A_RESERVER',
      phase: 'DOUANE_A_RESERVER',
    });
  });

  it('écrit en attente : candidature requise', () => {
    const a = evaluerAcces(
      entree({
        candidature: {
          ...ecritAccepte,
          rpStatus: AppStatus.PENDING,
          writtenApprovedAt: null,
        },
      }),
    );
    expect(a).toMatchObject({
      portee: 'aucune',
      raison: 'CANDIDATURE_REQUISE',
      phase: 'ECRIT_EN_ATTENTE',
    });
  });

  it('écrit rouvert alors que la date d’écrit est restée : jamais la douane', () => {
    const a = evaluerAcces(
      entree({
        candidature: { ...ecritAccepte, rpStatus: AppStatus.NEEDS_REVISION },
      }),
    );
    expect(a).toMatchObject({
      portee: 'aucune',
      raison: 'CANDIDATURE_REQUISE',
    });
  });

  it('compte inconnu : aucune', () => {
    expect(evaluerAcces(entree({ compte: null }))).toMatchObject({
      portee: 'aucune',
      raison: 'COMPTE_INCONNU',
      phase: null,
    });
  });

  it('compte banni : aucune, même staff', () => {
    const a = evaluerAcces(
      entree({
        compte: {
          role: Role.ADMIN,
          banned: true,
          oralPassedAt: null,
          whitelistRejectedAt: null,
        },
      }),
    );
    expect(a).toMatchObject({ portee: 'aucune', raison: 'BANNI', staff: true });
  });

  it('staff : play, jamais la douane, même avec une place réservée', () => {
    const a = evaluerAcces(
      entree({
        compte: {
          role: Role.HELPER,
          banned: false,
          oralPassedAt: null,
          whitelistRejectedAt: null,
        },
      }),
    );
    expect(a).toMatchObject({ portee: 'play', raison: 'STAFF', staff: true });
  });

  it('whitelisté : play', () => {
    const a = evaluerAcces(
      entree({
        compte: {
          role: Role.WHITELISTED,
          banned: false,
          oralPassedAt: new Date('2026-10-01T00:00:00Z'),
          whitelistRejectedAt: null,
        },
        candidature: null,
        reservation: null,
      }),
    );
    expect(a).toMatchObject({
      portee: 'play',
      raison: 'ACCEPTE',
      phase: 'ACCEPTE',
      staff: false,
    });
  });

  it('PLAYER dont l’oral est passé et la candidature acceptée : compte pas à jour', () => {
    const a = evaluerAcces(
      entree({
        compte: {
          role: Role.PLAYER,
          banned: false,
          oralPassedAt: new Date('2026-10-01T00:00:00Z'),
          whitelistRejectedAt: null,
        },
        candidature: { ...ecritAccepte, status: AppStatus.APPROVED },
      }),
    );
    expect(a).toMatchObject({
      portee: 'aucune',
      raison: 'COMPTE_PAS_A_JOUR',
      message: MSG_COMPTE_PAS_A_JOUR,
    });
  });
});
