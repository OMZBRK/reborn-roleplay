import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { Role, TicketCategory } from '@prisma/client';
import type { AuditService } from '../audit/audit.service';
import type { PrismaService } from '../prisma/prisma.service';
import {
  AdminService,
  estRenvoi,
  filtreCategorieTickets,
  WHERE_WHITELISTES,
  whereFiltresJoueurs,
} from './admin.service';

// Corrections du panel v2 côté service : compteur des whitelistés, IP et pays
// masqués dans la fiche et lus à la demande (journalisés), IP des connexions
// suspectes lue une à une, Pilotage selon le grade, tickets « Bug » seulement
// pour le développeur, filtres de la liste Joueurs en base.

function monter(
  opts: {
    user?: Record<string, unknown> | null;
    ticket?: { category: TicketCategory } | null;
    anomalies?: Array<Record<string, unknown>>;
  } = {},
) {
  const ordre: string[] = [];
  const prisma = {
    whitelistApplication: {
      count: jest.fn(() => Promise.resolve(0)),
      findMany: jest.fn(() => Promise.resolve([])),
    },
    ticket: {
      count: jest.fn(() => Promise.resolve(0)),
      findMany: jest.fn(() => Promise.resolve([])),
      findUnique: jest.fn(() => Promise.resolve(opts.ticket ?? null)),
    },
    user: {
      count: jest.fn(() => Promise.resolve(0)),
      findMany: jest.fn(() => Promise.resolve([])),
      findUnique: jest.fn(() => {
        ordre.push('lecture');
        return Promise.resolve(opts.user ?? null);
      }),
    },
    loginAnomaly: {
      findMany: jest.fn(() => Promise.resolve(opts.anomalies ?? [])),
      findUnique: jest.fn(() => {
        ordre.push('lecture');
        return Promise.resolve(opts.anomalies?.[0] ?? null);
      }),
    },
  };
  const audit = {
    log: jest.fn(() => {
      ordre.push('journal');
      return Promise.resolve(undefined);
    }),
  };
  return {
    service: new AdminService(
      prisma as unknown as PrismaService,
      audit as unknown as AuditService,
    ),
    prisma,
    audit,
    ordre,
  };
}

const JOUEUR = {
  id: 'j1',
  minecraftUsername: 'Kazuki',
  minecraftUuid: 'uuid-j1',
  discordUserId: null,
  discordUsername: null,
  steamUsername: null,
  role: Role.WHITELISTED,
  banned: false,
  banReason: null,
  bannedUntil: null,
  createdAt: new Date('2026-10-01T10:00:00Z'),
  lastLoginAt: null,
  lastKnownIp: 'ip-fictive-de-test',
  lastKnownCountry: 'FR',
  whitelistApp: null,
  tickets: [],
};

describe('AdminService — compteur des joueurs whitelistés', () => {
  it('compte tous les grades à partir de WHITELISTED, staff compris', () => {
    const compte = (role: Role) =>
      role !== (WHERE_WHITELISTES.role as { not: Role }).not;
    expect(Object.values(Role).filter(compte)).toEqual(
      Object.values(Role).filter((r) => r !== Role.PLAYER),
    );
  });

  it('le tableau de bord utilise ce filtre', async () => {
    const { service, prisma } = monter();
    await service.dashboard(Role.MODERATOR);
    expect(prisma.user.count).toHaveBeenCalledWith({
      where: WHERE_WHITELISTES,
    });
  });
});

describe('AdminService — Pilotage selon le grade', () => {
  it('avec pilotage.analyses : comptes et détail des candidatures', async () => {
    const { service } = monter();
    const d = await service.dashboard(Role.MODERATOR);
    expect(d.users).not.toBeNull();
    expect(d.whitelist.approved).toBe(0);
    expect(d.whitelist.rejected).toBe(0);
    expect(d.whitelist.needsRevision).toBe(0);
  });

  it.each([
    Role.HELPER,
    Role.MODELISATEUR,
    Role.DEVELOPPEUR,
    Role.WHITELIST_REVIEWER,
  ])(
    '%s : ni comptes ni détail du recrutement, même en appelant la route',
    async (role) => {
      const { service, prisma } = monter();
      const d = await service.dashboard(role);
      expect(d.users).toBeNull();
      expect(d.whitelist).toEqual({
        pending: 0,
        needsRevision: null,
        approved: null,
        rejected: null,
      });
      // Rien n'est même compté : pas de comptes, pas de statuts finaux.
      expect(prisma.user.count).not.toHaveBeenCalled();
      expect(prisma.whitelistApplication.count).toHaveBeenCalledTimes(1);
      // Les blocs Maintenant et Staff gardent leurs tickets.
      expect(d.tickets).toEqual({
        open: 0,
        inProgress: 0,
        resolved: 0,
        closed: 0,
      });
    },
  );
});

describe('AdminService — IP du joueur', () => {
  it('la fiche ne renvoie plus l’IP, seulement qu’elle est connue', async () => {
    const { service, audit } = monter({ user: JOUEUR });
    const fiche = await service.getPlayer('j1');
    expect(fiche.lastKnownIp).toBeNull();
    expect(fiche.ipConnue).toBe(true);
    expect(JSON.stringify(fiche)).not.toContain('ip-fictive-de-test');
    expect(audit.log).not.toHaveBeenCalled();
  });

  it('la fiche ne renvoie pas non plus le pays de connexion (joueurs.ip)', async () => {
    const { service, audit } = monter({ user: JOUEUR });
    const fiche = await service.getPlayer('j1');
    expect(fiche.lastKnownCountry).toBeNull();
    expect(JSON.stringify(fiche)).not.toContain('"FR"');
    expect(audit.log).not.toHaveBeenCalled();
  });

  it('la lecture à la demande est journalisée avant de répondre', async () => {
    const { service, audit, ordre } = monter({ user: JOUEUR });
    const r = await service.revelerIp('j1', 'admin1');
    expect(r).toEqual({ ip: 'ip-fictive-de-test', pays: 'FR' });
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorId: 'admin1',
        action: 'player.ip.view',
        targetUserId: 'j1',
        source: 'panel',
      }),
    );
    expect(ordre).toEqual(['lecture', 'journal']);
  });

  it('joueur introuvable : 404, rien au journal', async () => {
    const { service, audit } = monter({ user: null });
    await expect(service.revelerIp('jx', 'admin1')).rejects.toBeInstanceOf(
      NotFoundException,
    );
    expect(audit.log).not.toHaveBeenCalled();
  });
});

const ANOMALIE = {
  id: 'a1',
  userId: 'j1',
  ipAddress: 'ip-fictive-anomalie',
  country: 'Belgique',
  reason: 'Nouveau pays',
  acknowledged: false,
  notifiedVia: ['discord'],
  createdAt: new Date('2026-10-20T10:00:00Z'),
};

describe('AdminService — IP des connexions suspectes', () => {
  it('la liste ne renvoie pas l’IP, seulement qu’elle est connue', async () => {
    const { service, audit } = monter({ anomalies: [ANOMALIE] });
    const l = await service.listerAnomalies();
    expect(l[0]).toMatchObject({
      id: 'a1',
      ipConnue: true,
      country: 'Belgique',
    });
    expect(JSON.stringify(l)).not.toContain('ip-fictive-anomalie');
    expect(audit.log).not.toHaveBeenCalled();
  });

  it('filtre par joueur et par « non vues »', async () => {
    const { service, prisma } = monter();
    await service.listerAnomalies({ nonVues: true, userId: 'j1' });
    expect(prisma.loginAnomaly.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { acknowledged: false, userId: 'j1' } }),
    );
  });

  it('la lecture d’UNE adresse est journalisée avant de répondre, cible = l’anomalie', async () => {
    const { service, audit, ordre } = monter({ anomalies: [ANOMALIE] });
    const r = await service.revelerIpAnomalie('a1', 'admin1');
    expect(r).toEqual({ ip: 'ip-fictive-anomalie', pays: 'Belgique' });
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorId: 'admin1',
        action: 'anomaly.ip.view',
        targetUserId: 'j1',
        targetEntity: 'anomaly:a1',
      }),
    );
    expect(ordre).toEqual(['lecture', 'journal']);
  });

  it('anomalie introuvable : 404, rien au journal', async () => {
    const { service, audit } = monter();
    await expect(
      service.revelerIpAnomalie('ax', 'admin1'),
    ).rejects.toBeInstanceOf(NotFoundException);
    expect(audit.log).not.toHaveBeenCalled();
  });
});

describe('AdminService — filtres de la liste Joueurs', () => {
  it('sans filtre : rien d’ajouté', () => {
    expect(whereFiltresJoueurs({})).toEqual([]);
  });

  it('« Bannis » et un grade s’appliquent en base, avant la limite', async () => {
    const { service, prisma } = monter();
    await service.searchPlayers('', 100, {
      role: Role.WHITELISTED,
      statut: 'banni',
    });
    expect(prisma.user.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { AND: [{ role: Role.WHITELISTED }, { banned: true }] },
        take: 100,
      }),
    );
  });

  it('avec une recherche, les filtres s’ajoutent au texte cherché', async () => {
    const { service, prisma } = monter();
    await service.searchPlayers('kaz', 30, { statut: 'non' });
    const appel = (
      prisma.user.findMany.mock.calls[0] as unknown as [
        { where: { AND: unknown[] } },
      ]
    )[0];
    expect(appel.where.AND[0]).toEqual({ banned: false, role: Role.PLAYER });
    expect(appel.where.AND[1]).toHaveProperty('OR');
  });

  it('« Whitelistés » : non bannis, tout grade sauf Joueur', () => {
    expect(whereFiltresJoueurs({ statut: 'whiteliste' })).toEqual([
      { banned: false },
      WHERE_WHITELISTES,
    ]);
  });
});

describe('AdminService — écrit renvoyé ou premier envoi', () => {
  const envoi = new Date('2026-10-20T10:00:00Z');

  it('premier envoi : jamais relu', () => {
    expect(
      estRenvoi({ reviewedAt: null, reviewedBy: null, submittedAt: envoi }),
    ).toBe(false);
  });

  it('premier envoi dont une partie est déjà tranchée : pas un renvoi', () => {
    expect(
      estRenvoi({
        reviewedAt: new Date('2026-10-21T10:00:00Z'),
        reviewedBy: 'renji',
        submittedAt: envoi,
        messages: [],
      }),
    ).toBe(false);
  });

  it('renvoi : relu une fois, puis renvoyé (reviewedAt remis à null)', () => {
    expect(
      estRenvoi({ reviewedAt: null, reviewedBy: 'renji', submittedAt: envoi }),
    ).toBe(true);
  });

  it('renvoi dont une partie est déjà retranchée : le message de renvoi suffit', () => {
    expect(
      estRenvoi({
        reviewedAt: new Date('2026-10-21T10:00:00Z'),
        reviewedBy: 'asuka',
        submittedAt: envoi,
        messages: [{ createdAt: new Date('2026-10-20T10:00:01Z') }],
      }),
    ).toBe(true);
  });

  it('un renvoi d’une ancienne candidature (avant le dernier envoi) ne compte pas', () => {
    expect(
      estRenvoi({
        reviewedAt: new Date('2026-10-21T10:00:00Z'),
        reviewedBy: 'asuka',
        submittedAt: envoi,
        messages: [{ createdAt: new Date('2026-10-01T10:00:00Z') }],
      }),
    ).toBe(false);
  });
});

describe('AdminService — tickets par catégorie', () => {
  it('la modération passe sans lire le ticket', async () => {
    const { service, prisma } = monter();
    await service.verifierAccesTicket('t1', Role.HELPER);
    expect(prisma.ticket.findUnique).not.toHaveBeenCalled();
  });

  it('le développeur passe sur un ticket « Bug »', async () => {
    const { service } = monter({ ticket: { category: TicketCategory.BUG } });
    await expect(
      service.verifierAccesTicket('t1', Role.DEVELOPPEUR),
    ).resolves.toBeUndefined();
  });

  it('le développeur est refusé sur un signalement', async () => {
    const { service } = monter({
      ticket: { category: TicketCategory.REPORT_PLAYER },
    });
    await expect(
      service.verifierAccesTicket('t1', Role.DEVELOPPEUR),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('ticket introuvable : 404', async () => {
    const { service } = monter({ ticket: null });
    await expect(
      service.verifierAccesTicket('t1', Role.DEVELOPPEUR),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it('la liste du développeur est filtrée sur « Bug », pas celle du helper', async () => {
    const { service, prisma } = monter();
    await service.listTickets({ take: 50, skip: 0 }, Role.DEVELOPPEUR);
    expect(prisma.ticket.findMany).toHaveBeenLastCalledWith(
      expect.objectContaining({ where: { category: 'BUG' } }),
    );
    await service.listTickets({ take: 50, skip: 0 }, Role.HELPER);
    expect(prisma.ticket.findMany).toHaveBeenLastCalledWith(
      expect.objectContaining({ where: {} }),
    );
  });

  it('filtre de catégorie selon le grade', () => {
    expect(filtreCategorieTickets(Role.MODERATOR)).toEqual({});
    expect(filtreCategorieTickets(Role.DEVELOPPEUR)).toEqual({
      category: 'BUG',
    });
  });

  it('« Mes prises » du développeur ne remonte que des « Bug »', async () => {
    const { service, prisma } = monter();
    await service.myInbox('dev1', Role.DEVELOPPEUR);
    expect(prisma.ticket.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          assignedToUserId: 'dev1',
          category: 'BUG',
        }) as unknown,
      }),
    );
  });
});
