import { AppStatus, CharacterSex, CharacterType, Role } from '@prisma/client';
import type { PrismaService } from '../prisma/prisma.service';
import { GameService } from './game.service';

// Vue candidature lue par ShinobiCore (wizard de création) : depuis le 24/10,
// jamais de clan venu de la candidature (Q4), nom de famille libre pré-rempli
// et modifiable (v2 seulement), et rien tant que la candidature n'est pas acceptée.

const UUID = '069a79f4-44e9-4726-a5be-fca90e38aaf5';

function monter(
  role: Role,
  app: Record<string, unknown> | null,
  oralPassedAt: Date | null = null,
) {
  const prisma = {
    user: {
      findUnique: jest.fn(() =>
        Promise.resolve({
          id: 'u1',
          role,
          oralPassedAt,
          whitelistRejectedAt: null,
        }),
      ),
    },
    whitelistApplication: { findUnique: jest.fn(() => Promise.resolve(app)) },
  };
  return new GameService(prisma as unknown as PrismaService);
}

const acceptee = {
  id: '5b1c2d3e-4f50-4a6b-8c7d-9e0f1a2b3c4d',
  status: AppStatus.APPROVED,
  hrpStatus: AppStatus.APPROVED,
  rpStatus: AppStatus.APPROVED,
  oralStatus: AppStatus.APPROVED,
  oralDecidedAt: null,
  reviewedAt: null,
  firstName: 'Kaito',
  lastName: 'Arashi',
  village: 'Konohagakure',
  characterAge: 16,
  characterType: CharacterType.CIVIL,
  characterSex: CharacterSex.FEMME,
  formVersion: 2,
};

describe('GameService.candidatureByMcUuid', () => {
  it('v2 acceptée : clan « Autre », nom de famille libre, village, âge (verrouillé en jeu)', async () => {
    const vue = await monter(Role.WHITELISTED, acceptee).candidatureByMcUuid(
      UUID,
    );
    expect(vue).toMatchObject({
      found: true,
      name: 'Kaito',
      clan: 'Autre',
      familyName: 'Arashi',
      village: 'Konohagakure',
      age: 16,
      staff: false,
    });
  });

  it('acceptée : identifiant, type et sexe pour marquer le premier personnage (§4.4)', async () => {
    const vue = await monter(Role.WHITELISTED, acceptee).candidatureByMcUuid(
      UUID,
    );
    expect(vue).toMatchObject({
      found: true,
      applicationId: acceptee.id,
      type: 'CIVIL',
      sex: 'FEMME',
    });
  });

  it('ancien formulaire sans type ni sexe : null (= Ninja en jeu, sexe libre)', async () => {
    const vue = await monter(Role.WHITELISTED, {
      ...acceptee,
      formVersion: 1,
      characterType: null,
      characterSex: null,
    }).candidatureByMcUuid(UUID);
    expect(vue).toMatchObject({
      found: true,
      applicationId: acceptee.id,
      type: null,
      sex: null,
    });
  });

  it('candidature sans âge (ancien formulaire) : age null', async () => {
    const vue = await monter(Role.WHITELISTED, {
      ...acceptee,
      characterAge: null,
    }).candidatureByMcUuid(UUID);
    expect(vue).toMatchObject({ found: true, age: null });
  });

  it('v1 (lastName = clan choisi) : clan « Autre » quand même, pas de nom de famille', async () => {
    const vue = await monter(Role.WHITELISTED, {
      ...acceptee,
      formVersion: 1,
      lastName: 'Uchiha',
    }).candidatureByMcUuid(UUID);
    expect(vue).toMatchObject({ found: true, clan: 'Autre', familyName: null });
  });

  it('candidature pas encore acceptée : rien de pré-rempli', async () => {
    const vue = await monter(Role.PLAYER, {
      ...acceptee,
      status: AppStatus.PENDING,
      oralStatus: AppStatus.PENDING,
    }).candidatureByMcUuid(UUID);
    expect(vue).toEqual({ found: false, staff: false });
  });
});
