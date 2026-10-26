import { Role } from '@prisma/client';
import type { AuditService } from '../audit/audit.service';
import type { FilesService } from '../files/files.service';
import type { PrismaService } from '../prisma/prisma.service';
import { AbilitiesService } from './abilities.service';

// Récapitulatif montré par le panel avant « Déployer » : serveur visé,
// techniques publiées, celles modifiées depuis le dernier déploiement
// journalisé (`abilities.deploy`), et celles que le déploiement RETIRE du
// serveur (déployées la dernière fois, plus publiées aujourd'hui).

interface Technique {
  id: string;
  slug: string;
  name: string;
  category: string;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  updatedAt: Date;
}

const TECHNIQUES: Technique[] = [
  {
    id: 'a',
    slug: 'katon_goukakyu',
    name: 'Boule de feu',
    category: 'katon',
    status: 'PUBLISHED',
    updatedAt: new Date('2026-10-20T10:00:00Z'),
  },
  {
    id: 'b',
    slug: 'suiton_mizurappa',
    name: 'Vague',
    category: 'suiton',
    status: 'PUBLISHED',
    updatedAt: new Date('2026-10-24T10:00:00Z'),
  },
  {
    id: 'c',
    slug: 'doton_mur',
    name: 'Mur de terre',
    category: 'doton',
    status: 'DRAFT',
    updatedAt: new Date('2026-10-23T10:00:00Z'),
  },
];

type Where = {
  status?: string | { not: string };
  slug?: { in: string[] };
  updatedAt?: { gt: Date };
};

/** Évalue le petit sous-ensemble de `where` utilisé par le service. */
function retenue(t: Technique, where: Where): boolean {
  if (typeof where.status === 'string' && t.status !== where.status)
    return false;
  if (typeof where.status === 'object' && t.status === where.status.not) {
    return false;
  }
  if (where.slug && !where.slug.in.includes(t.slug)) return false;
  if (where.updatedAt && !(t.updatedAt > where.updatedAt.gt)) return false;
  return true;
}

function monter(
  dernierDeploiement: Date | null,
  metadata: unknown = { slugs: ['katon_goukakyu', 'suiton_mizurappa'] },
) {
  const prisma = {
    techniqueGraph: {
      findMany: jest.fn(({ where }: { where: Where }) =>
        Promise.resolve(TECHNIQUES.filter((t) => retenue(t, where))),
      ),
    },
    auditLog: {
      findFirst: jest.fn(() =>
        Promise.resolve(
          dernierDeploiement
            ? { createdAt: dernierDeploiement, metadata }
            : null,
        ),
      ),
    },
  };
  const files = {
    scopesFor: jest.fn(() => ({ server: 'dev', canWrite: true, roots: [] })),
  };
  const audit = { log: jest.fn(() => Promise.resolve(undefined)) };
  return {
    service: new AbilitiesService(
      prisma as unknown as PrismaService,
      files as unknown as FilesService,
      audit as unknown as AuditService,
    ),
    prisma,
    audit,
  };
}

describe('AbilitiesService — récapitulatif de déploiement', () => {
  it('marque les techniques modifiées depuis le dernier déploiement', async () => {
    const { service, prisma } = monter(new Date('2026-10-22T00:00:00Z'));
    const r = await service.recapitulatifDeploiement(Role.DEVELOPPEUR);
    expect(r.serveur).toBe('dev');
    expect(r.dernierDeploiement).toBe('2026-10-22T00:00:00.000Z');
    expect(r.total).toBe(2);
    expect(r.modifiees).toBe(1);
    expect(r.techniques.map((t) => [t.slug, t.modifiee])).toEqual([
      ['katon_goukakyu', false],
      ['suiton_mizurappa', true],
    ]);
    expect(prisma.auditLog.findFirst).toHaveBeenCalledWith(
      expect.objectContaining({ where: { action: 'abilities.deploy' } }),
    );
  });

  it('sans déploiement journalisé, tout compte comme modifié, rien de retiré', async () => {
    const { service } = monter(null);
    const r = await service.recapitulatifDeploiement(Role.DEVELOPPEUR);
    expect(r.dernierDeploiement).toBeNull();
    expect(r.modifiees).toBe(2);
    expect(r.retirees).toEqual([]);
  });

  it('ne journalise rien : simple lecture', async () => {
    const { service, audit } = monter(null);
    await service.recapitulatifDeploiement(Role.ADMIN);
    expect(audit.log).not.toHaveBeenCalled();
  });

  it('toutes les techniques déployées sont encore publiées : rien de retiré', async () => {
    const { service } = monter(new Date('2026-10-22T00:00:00Z'));
    const r = await service.recapitulatifDeploiement(Role.DEVELOPPEUR);
    expect(r.retirees).toEqual([]);
  });

  it('une technique déployée puis repassée en brouillon est annoncée retirée', async () => {
    const { service } = monter(new Date('2026-10-22T00:00:00Z'), {
      slugs: ['katon_goukakyu', 'suiton_mizurappa', 'doton_mur'],
    });
    const r = await service.recapitulatifDeploiement(Role.DEVELOPPEUR);
    expect(r.retirees).toEqual([
      { slug: 'doton_mur', name: 'Mur de terre', status: 'DRAFT' },
    ]);
  });

  it('une technique déployée puis supprimée est annoncée retirée (statut null)', async () => {
    const { service } = monter(new Date('2026-10-22T00:00:00Z'), {
      slugs: ['katon_goukakyu', 'raiton_chidori'],
    });
    const r = await service.recapitulatifDeploiement(Role.DEVELOPPEUR);
    expect(r.retirees).toEqual([
      { slug: 'raiton_chidori', name: 'raiton_chidori', status: null },
    ]);
  });

  it('ancienne entrée sans slugs : repli sur les non publiées modifiées depuis', async () => {
    const { service } = monter(new Date('2026-10-22T00:00:00Z'), {
      deployed: 3,
    });
    const r = await service.recapitulatifDeploiement(Role.DEVELOPPEUR);
    expect(r.retirees).toEqual([
      { slug: 'doton_mur', name: 'Mur de terre', status: 'DRAFT' },
    ]);
  });
});
