import { Role, TicketCategory } from '@prisma/client';
import { SCOPES, ZONES_FICHIERS } from '../files/files.scopes';
import {
  aCapacite,
  CAPACITES_FIXES,
  capacitesDe,
  gradesAyant,
  LIBELLES_GRADES,
  messageRefus,
  peutTraiterTicket,
} from './capacites';
import type { Capacite } from './capacites';

// Matrice des capacités du panel v2 (PANEL_V2 §3.3, validée le 25/10). Ce test
// fige la table grade par grade : toute ouverture ou fermeture d'un accès doit
// passer par ici, en connaissance de cause.

const STAFF_COMMUN: Capacite[] = [
  'panel.acceder',
  'pilotage.voir',
  'wiki.editer',
];
const RECRUTEUR: Capacite[] = [
  ...STAFF_COMMUN,
  'joueurs.voir',
  'candidatures.relire',
  'douane.organiser',
  'douane.examiner',
  'tickets.traiter',
  'tickets.bug',
  'sanctions.voir',
];
const MODERATEUR: Capacite[] = [
  ...RECRUTEUR,
  'pilotage.analyses',
  'sanctions.poser',
  'wiki.supprimer',
  'reponses-types.gerer',
];
const ZONES_DEV: Capacite[] = [
  'fichiers.nexo',
  'fichiers.creator',
  'fichiers.magicspells',
  'fichiers.mythicmobs',
  'fichiers.modelengine',
  'fichiers.emotes',
  'fichiers.abilities',
];
const ADMIN: Capacite[] = [
  ...MODERATEUR,
  'pilotage.demographie',
  'joueurs.ip',
  'grades.changer',
  'candidatures.reinitialiser',
  'prises.forcer',
  'securite.voir',
  'audit.voir',
  'discord.publier',
  'techniques.editer',
  'techniques.deployer',
  'techniques.supprimer',
  ...ZONES_DEV,
  'fichiers.plugins',
  'fichiers.ecrire',
];

const ATTENDU: Record<Role, Capacite[]> = {
  [Role.PLAYER]: [],
  [Role.WHITELISTED]: [],
  [Role.HELPER]: [
    ...STAFF_COMMUN,
    'joueurs.voir',
    'tickets.traiter',
    'tickets.bug',
    'sanctions.voir',
  ],
  [Role.MODELISATEUR]: [
    ...STAFF_COMMUN,
    'fichiers.nexo',
    'fichiers.creator',
    'fichiers.emotes',
    'fichiers.ecrire',
  ],
  [Role.DEVELOPPEUR]: [
    ...STAFF_COMMUN,
    'tickets.bug',
    'techniques.editer',
    'techniques.deployer',
    ...ZONES_DEV,
    'fichiers.ecrire',
  ],
  [Role.WHITELIST_REVIEWER]: RECRUTEUR,
  [Role.MODERATOR]: MODERATEUR,
  [Role.ADMIN]: ADMIN,
  [Role.OWNER]: [...ADMIN, 'pilotage.revenus', 'fichiers.racine'],
};

const tri = (l: readonly string[]) => [...l].sort();

describe('capacités — matrice par grade', () => {
  it.each(Object.values(Role))('%s a exactement ses capacités', (role) => {
    expect(tri(capacitesDe(role))).toEqual(tri(ATTENDU[role]));
  });

  it('chaque capacité fixe sert au moins à un grade', () => {
    for (const c of CAPACITES_FIXES) {
      expect(gradesAyant([c]).length).toBeGreaterThan(0);
    }
  });

  it('aCapacite suit capacitesDe pour tous les grades', () => {
    const toutes = new Set<Capacite>([
      ...CAPACITES_FIXES,
      ...ZONES_FICHIERS.map((z): Capacite => `fichiers.${z.zone}`),
      'fichiers.ecrire',
    ]);
    for (const role of Object.values(Role)) {
      const siennes = capacitesDe(role);
      for (const c of toutes) {
        expect(aCapacite(role, c)).toBe(siennes.includes(c));
      }
    }
  });

  it('une capacité inconnue est refusée', () => {
    expect(aCapacite(Role.OWNER, 'nimporte.quoi' as Capacite)).toBe(false);
    expect(aCapacite(Role.OWNER, 'fichiers.inconnu')).toBe(false);
  });

  it('chaque grade a un libellé français', () => {
    for (const role of Object.values(Role)) {
      expect(LIBELLES_GRADES[role]).toMatch(/^[A-ZÉ][a-zéè]+$/);
    }
  });
});

describe('capacités — exigence du 25/10 : rien n’est retiré aux grades techniques', () => {
  it.each([Role.MODELISATEUR, Role.DEVELOPPEUR])(
    '%s garde une capacité par racine de fichiers et l’écriture',
    (role) => {
      const caps = capacitesDe(role);
      for (const racine of SCOPES[role]!.roots) {
        const zone = ZONES_FICHIERS.find((z) => z.chemin === racine.path);
        expect(zone).toBeDefined();
        expect(caps).toContain(`fichiers.${zone!.zone}`);
      }
      expect(caps).toContain('fichiers.ecrire');
    },
  );

  it('le modélisateur garde Nexo, les emotes et les ressources du créateur', () => {
    expect(capacitesDe(Role.MODELISATEUR)).toEqual(
      expect.arrayContaining([
        'fichiers.nexo',
        'fichiers.emotes',
        'fichiers.creator',
      ]),
    );
  });

  it('le développeur garde les techniques : éditer, aperçu, déployer', () => {
    expect(aCapacite(Role.DEVELOPPEUR, 'techniques.editer')).toBe(true);
    expect(aCapacite(Role.DEVELOPPEUR, 'techniques.deployer')).toBe(true);
  });

  it('le développeur garde les tickets « Bug »', () => {
    expect(peutTraiterTicket(Role.DEVELOPPEUR, TicketCategory.BUG)).toBe(true);
  });
});

describe('capacités — incohérences corrigées', () => {
  it.each([
    Role.WHITELIST_REVIEWER,
    Role.MODERATOR,
    Role.HELPER,
    Role.MODELISATEUR,
  ])('%s ne déploie ni n’édite les techniques', (role) => {
    expect(aCapacite(role, 'techniques.editer')).toBe(false);
    expect(aCapacite(role, 'techniques.deployer')).toBe(false);
  });

  it('seuls l’administrateur et le propriétaire voient l’IP', () => {
    expect(gradesAyant(['joueurs.ip'])).toEqual([Role.ADMIN, Role.OWNER]);
  });

  it('seuls l’administrateur et le propriétaire remettent une candidature à zéro', () => {
    expect(gradesAyant(['candidatures.reinitialiser'])).toEqual([
      Role.ADMIN,
      Role.OWNER,
    ]);
  });

  it('les grades techniques n’ont ni fiches joueurs ni tickets hors « Bug »', () => {
    for (const role of [Role.MODELISATEUR, Role.DEVELOPPEUR]) {
      expect(aCapacite(role, 'joueurs.voir')).toBe(false);
      expect(aCapacite(role, 'tickets.traiter')).toBe(false);
      for (const cat of Object.values(TicketCategory)) {
        if (cat === TicketCategory.BUG) continue;
        expect(peutTraiterTicket(role, cat)).toBe(false);
      }
    }
    expect(peutTraiterTicket(Role.MODELISATEUR, TicketCategory.BUG)).toBe(
      false,
    );
  });

  it('la modération traite toutes les catégories de tickets', () => {
    for (const role of [
      Role.HELPER,
      Role.WHITELIST_REVIEWER,
      Role.MODERATOR,
      Role.ADMIN,
    ]) {
      for (const cat of Object.values(TicketCategory)) {
        expect(peutTraiterTicket(role, cat)).toBe(true);
      }
    }
  });

  it('Recruteur et Modérateur n’ont aucune zone de fichiers', () => {
    for (const role of [Role.WHITELIST_REVIEWER, Role.MODERATOR, Role.HELPER]) {
      expect(capacitesDe(role).some((c) => c.startsWith('fichiers.'))).toBe(
        false,
      );
    }
  });
});

describe('capacités — message de refus', () => {
  it('dit « administrateurs » pour une capacité de direction', () => {
    expect(messageRefus(['joueurs.ip'])).toBe('Réservé aux administrateurs.');
  });

  it('nomme les grades en français, sans code brut', () => {
    expect(messageRefus(['techniques.deployer'])).toBe(
      'Réservé à : Développeur, Administrateur, Propriétaire.',
    );
  });

  it('prend l’union des capacités demandées', () => {
    expect(messageRefus(['tickets.traiter', 'tickets.bug'])).toBe(
      'Réservé à : Helper, Développeur, Recruteur, Modérateur, Administrateur, Propriétaire.',
    );
  });

  it('propriétaire seul', () => {
    expect(messageRefus(['pilotage.revenus'])).toBe('Réservé au propriétaire.');
  });
});
