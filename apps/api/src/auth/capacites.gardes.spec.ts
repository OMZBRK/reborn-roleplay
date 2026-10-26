import { ExecutionContext, ForbiddenException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Role } from '@prisma/client';
import { AbilitiesController } from '../abilities/abilities.controller';
import { AdminController } from '../admin/admin.controller';
import { FilesController } from '../files/files.controller';
import { WikiController } from '../wiki/wiki.controller';
import { CAPACITES_KEY } from './capacite.decorator';
import { RolesGuard } from './roles.guard';

// Les gardes des routes concernées lisent la table des capacités : on fait
// passer chaque grade devant les vraies méthodes des contrôleurs (métadonnées
// posées par les décorateurs), sans démarrer Nest.

type Classe = { name: string; prototype: object };

function contexte(
  role: Role | null,
  classe: Classe,
  methode: string,
): ExecutionContext {
  const handler = (classe.prototype as Record<string, unknown>)[methode];
  if (typeof handler !== 'function') {
    throw new Error(`${classe.name}.${methode} n'existe pas`);
  }
  return {
    getHandler: () => handler,
    getClass: () => classe,
    switchToHttp: () => ({
      getRequest: () => ({
        user: role ? { sub: 'u1', uuid: 'x', role } : undefined,
      }),
    }),
  } as unknown as ExecutionContext;
}

const garde = new RolesGuard(new Reflector());

function passe(role: Role, classe: Classe, methode: string): boolean {
  try {
    return garde.canActivate(contexte(role, classe, methode));
  } catch (e) {
    if (e instanceof ForbiddenException) return false;
    throw e;
  }
}

/** Ordre des grades du rang historique (celui des listes attendues ci-dessous). */
const ORDRE: Role[] = [
  Role.PLAYER,
  Role.WHITELISTED,
  Role.HELPER,
  Role.MODELISATEUR,
  Role.DEVELOPPEUR,
  Role.WHITELIST_REVIEWER,
  Role.MODERATOR,
  Role.ADMIN,
  Role.OWNER,
];

function gradesQuiPassent(classe: Classe, methode: string): Role[] {
  return ORDRE.filter((r) => passe(r, classe, methode));
}

const TECHNIQUE = [Role.DEVELOPPEUR, Role.ADMIN, Role.OWNER];
const DIRECTION = [Role.ADMIN, Role.OWNER];
const MODERATION = [
  Role.HELPER,
  Role.WHITELIST_REVIEWER,
  Role.MODERATOR,
  Role.ADMIN,
  Role.OWNER,
];
const RECRUTEMENT = [
  Role.WHITELIST_REVIEWER,
  Role.MODERATOR,
  Role.ADMIN,
  Role.OWNER,
];
const STAFF = [
  Role.HELPER,
  Role.MODELISATEUR,
  Role.DEVELOPPEUR,
  Role.WHITELIST_REVIEWER,
  Role.MODERATOR,
  Role.ADMIN,
  Role.OWNER,
];

describe('gardes — Technique Creator', () => {
  const ROUTES_EDITION = [
    'list',
    'get',
    'create',
    'update',
    'validate',
    'importParse',
    'import',
    'preview',
  ];

  it.each(ROUTES_EDITION)('%s : développeur, admin, owner', (m) => {
    expect(gradesQuiPassent(AbilitiesController as Classe, m)).toEqual(
      TECHNIQUE,
    );
  });

  it('déployer et son récapitulatif : développeur, admin, owner — plus Recruteur ni Modérateur', () => {
    for (const m of ['deploy', 'recapitulatifDeploiement']) {
      expect(gradesQuiPassent(AbilitiesController as Classe, m)).toEqual(
        TECHNIQUE,
      );
    }
  });

  it('supprimer une technique : direction seulement', () => {
    expect(gradesQuiPassent(AbilitiesController as Classe, 'remove')).toEqual(
      DIRECTION,
    );
  });

  it('refus en français', () => {
    expect(() =>
      garde.canActivate(
        contexte(Role.MODERATOR, AbilitiesController as Classe, 'deploy'),
      ),
    ).toThrow('Réservé à : Développeur, Administrateur, Propriétaire.');
  });
});

describe('gardes — fichiers du serveur', () => {
  const ROUTES = [
    'list',
    'read',
    'write',
    'upload',
    'remove',
    'mkdir',
    'move',
    'reload',
    'createAnimatedItem',
  ];

  it.each(ROUTES)(
    '%s : modélisateur, développeur, admin, owner (inchangé)',
    (m) => {
      expect(gradesQuiPassent(FilesController as Classe, m)).toEqual([
        Role.MODELISATEUR,
        Role.DEVELOPPEUR,
        Role.ADMIN,
        Role.OWNER,
      ]);
    },
  );

  it('les racines et les cibles de rechargement restent lisibles par tout le staff', () => {
    for (const m of ['scopes', 'reloadTargets']) {
      expect(gradesQuiPassent(FilesController as Classe, m)).toEqual(STAFF);
    }
  });
});

describe('gardes — panel /admin', () => {
  const A = AdminController as Classe;

  it('fiche et recherche joueur : modération, sans les grades techniques', () => {
    expect(gradesQuiPassent(A, 'getPlayer')).toEqual(MODERATION);
    expect(gradesQuiPassent(A, 'searchPlayers')).toEqual(MODERATION);
  });

  it('IP d’un joueur : direction seulement', () => {
    expect(gradesQuiPassent(A, 'ipJoueur')).toEqual(DIRECTION);
  });

  it('connexions suspectes et IP de l’une d’elles : direction seulement', () => {
    expect(gradesQuiPassent(A, 'listAnomalies')).toEqual(DIRECTION);
    expect(gradesQuiPassent(A, 'ipAnomalie')).toEqual(DIRECTION);
  });

  it('tickets : modération + développeur (catégorie vérifiée ensuite)', () => {
    for (const m of [
      'listTickets',
      'getTicket',
      'setTicketStatus',
      'postTicketMessage',
      'claimTicket',
      'releaseTicket',
    ]) {
      expect(gradesQuiPassent(A, m)).toEqual([
        Role.HELPER,
        Role.DEVELOPPEUR,
        Role.WHITELIST_REVIEWER,
        Role.MODERATOR,
        Role.ADMIN,
        Role.OWNER,
      ]);
    }
  });

  it('candidatures : recrutement ; remise à zéro : direction', () => {
    for (const m of [
      'listWhitelist',
      'getWhitelist',
      'postWhitelistMessage',
      'claimWhitelist',
      'releaseWhitelist',
      'ecouterVoix',
      'decideWhitelistPart',
      'notePriveeEcrit',
    ]) {
      expect(gradesQuiPassent(A, m)).toEqual(RECRUTEMENT);
    }
    expect(gradesQuiPassent(A, 'resetWhitelist')).toEqual(DIRECTION);
  });

  it('douane et anciens créneaux : recrutement', () => {
    for (const m of [
      'listerExaminateurs',
      'listOralSlots',
      'openOralSlots',
      'cancelOralSlot',
      'markOralSlotDone',
    ]) {
      expect(gradesQuiPassent(A, m)).toEqual(RECRUTEMENT);
    }
  });

  it('journal, sécurité, grades : direction', () => {
    for (const m of [
      'listAudit',
      'listAnomalies',
      'ackAnomaly',
      'changePlayerRole',
    ]) {
      expect(gradesQuiPassent(A, m)).toEqual(DIRECTION);
    }
  });

  it('accueil et prises : tout le staff, jamais un joueur', () => {
    expect(gradesQuiPassent(A, 'dashboard')).toEqual(STAFF);
    expect(gradesQuiPassent(A, 'myInbox')).toEqual(STAFF);
  });

  it('chaque route du contrôleur porte une capacité (propre ou de classe)', () => {
    const methodes = Object.getOwnPropertyNames(A.prototype).filter(
      (m) => m !== 'constructor',
    );
    const deClasse = Reflect.getMetadata(CAPACITES_KEY, A) as
      | string[]
      | undefined;
    expect(deClasse).toEqual(['panel.acceder']);
    for (const m of methodes) {
      expect(passe(Role.PLAYER, A, m)).toBe(false);
      expect(passe(Role.WHITELISTED, A, m)).toBe(false);
    }
  });
});

describe('gardes — @MinRole toujours lu (routes pas encore passées aux capacités)', () => {
  it('wiki : suppression réservée au Modérateur et au-dessus, refus en français', () => {
    expect(() =>
      garde.canActivate(
        contexte(Role.HELPER, WikiController as Classe, 'deleteEntry'),
      ),
    ).toThrow('Réservé au grade Modérateur et au-dessus.');
    expect(passe(Role.MODERATOR, WikiController as Classe, 'deleteEntry')).toBe(
      true,
    );
  });

  it('sans utilisateur : refus', () => {
    expect(() =>
      garde.canActivate(contexte(null, AbilitiesController as Classe, 'list')),
    ).toThrow('Authentification requise.');
  });
});
