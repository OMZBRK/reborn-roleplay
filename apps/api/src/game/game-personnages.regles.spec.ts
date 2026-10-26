import {
  clanOuNull,
  donneesDepuisPhoto,
  formatNumero,
  normaliserMcUuid,
  validerPhoto,
} from './game-personnages.regles';

// Règles pures de la synchronisation des personnages (CANDIDATURE_ET_DOUANE.md
// §4.5) : une photo complète, contrôlée seule, champs inconnus refusés.

const PHOTO = {
  characterId: '5D3C2B1A-0000-4000-8000-000000000042',
  ownerMcUuid: '069a79f444e94726a5befca90e38aaf5',
  playerId: 42,
  firstName: ' Kaito ',
  lastName: 'Arashi',
  village: 'Konohagakure',
  rank: 'GENIN',
  sex: 'HOMME',
  age: 16,
  dead: false,
  playMinutes: 95,
  createdAt: '2026-10-25T18:00:00Z',
  lastSeenAt: '2026-10-25T19:30:12.345Z',
  snapshotAt: '2026-10-25T19:31:00.123456789Z',
};

describe('normaliserMcUuid', () => {
  it('forme compacte ou majuscules → minuscules avec tirets', () => {
    expect(normaliserMcUuid('069A79F444E94726A5BEFCA90E38AAF5')).toBe(
      '069a79f4-44e9-4726-a5be-fca90e38aaf5',
    );
    expect(normaliserMcUuid('069a79f4-44e9-4726-a5be-fca90e38aaf5')).toBe(
      '069a79f4-44e9-4726-a5be-fca90e38aaf5',
    );
  });

  it('valeur qui n’est pas un UUID : rendue en minuscules, telle quelle', () => {
    expect(normaliserMcUuid(' DEV:Kaito ')).toBe('dev:kaito');
  });
});

describe('formatNumero', () => {
  it('cinq chiffres comme le plugin, null sans N°', () => {
    expect(formatNumero(42)).toBe('00042');
    expect(formatNumero(123456)).toBe('123456');
    expect(formatNumero(null)).toBeNull();
    expect(formatNumero(-1)).toBeNull();
  });
});

describe('validerPhoto', () => {
  it('photo complète : acceptée (date à nanosecondes de Instant.toString comprise)', () => {
    const lu = validerPhoto(PHOTO);
    expect(lu.ok).toBe(true);
  });

  it('photo minimale : seuls les champs requis', () => {
    const lu = validerPhoto({
      characterId: PHOTO.characterId,
      ownerMcUuid: PHOTO.ownerMcUuid,
      firstName: 'Kaito',
      dead: false,
      playMinutes: 0,
      snapshotAt: PHOTO.snapshotAt,
    });
    expect(lu.ok).toBe(true);
  });

  it('champs facultatifs à null : acceptés', () => {
    const lu = validerPhoto({
      ...PHOTO,
      playerId: null,
      lastName: null,
      clan: null,
      village: null,
      type: null,
      rank: null,
      sex: null,
      age: null,
      deleted: null,
      createdAt: null,
      lastSeenAt: null,
      applicationId: null,
    });
    expect(lu.ok).toBe(true);
  });

  it('champ inconnu : refusée, characterId rendu en minuscules pour le plugin', () => {
    const lu = validerPhoto({ ...PHOTO, serverId: 'prod' });
    expect(lu).toEqual({
      ok: false,
      characterId: '5d3c2b1a-0000-4000-8000-000000000042',
      message: 'serverId: champ inconnu.',
    });
  });

  it('erreurs multiples : un message par champ, en français', () => {
    const lu = validerPhoto({
      ...PHOTO,
      firstName: '   ',
      sex: 'Homme',
      rank: 'Genin',
      playMinutes: 1.5,
    });
    expect(lu.ok).toBe(false);
    if (lu.ok) return;
    expect(lu.message).toContain('firstName : prénom vide');
    expect(lu.message).toContain('sex : HOMME ou FEMME.');
    expect(lu.message).toContain('rank : code du grade attendu');
    expect(lu.message).toContain('playMinutes : entier attendu.');
  });

  it('date sans fuseau : refusée (heure locale ambiguë)', () => {
    const lu = validerPhoto({ ...PHOTO, snapshotAt: '2026-10-25T19:31:00' });
    expect(lu.ok).toBe(false);
    if (lu.ok) return;
    expect(lu.message).toContain('snapshotAt : date ISO 8601 avec fuseau');
  });

  it('date ISO en semaine (que Date ne lit pas) : refusée', () => {
    const lu = validerPhoto({ ...PHOTO, createdAt: '2026-W43-7T10:00:00Z' });
    expect(lu.ok).toBe(false);
    if (lu.ok) return;
    expect(lu.message).toContain('createdAt');
  });

  it('caractère de contrôle dans un nom : refusé', () => {
    const lu = validerPhoto({ ...PHOTO, lastName: 'Ara\u0000shi' });
    expect(lu.ok).toBe(false);
  });

  it('pas un objet : refusée sans characterId', () => {
    expect(validerPhoto('texte')).toEqual({
      ok: false,
      characterId: null,
      message: 'objet attendu.',
    });
    expect(validerPhoto([PHOTO]).ok).toBe(false);
  });
});

describe('donneesDepuisPhoto', () => {
  const snapshotAt = new Date('2026-10-25T19:31:00Z');
  const ctx = {
    ownerMcUuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5',
    userId: 'u1',
    applicationId: null,
    snapshotAt,
    deletedAtExistant: null,
  };

  it('photo complète : champs absents à leur valeur par défaut, textes rognés', () => {
    const lu = validerPhoto(PHOTO);
    if (!lu.ok) throw new Error(lu.message);
    expect(donneesDepuisPhoto(lu.photo, ctx)).toEqual({
      ownerMcUuid: ctx.ownerMcUuid,
      userId: 'u1',
      playerId: 42,
      firstName: 'Kaito',
      lastName: 'Arashi',
      clan: null,
      village: 'Konohagakure',
      type: 'NINJA',
      rank: 'GENIN',
      sex: 'HOMME',
      age: 16,
      dead: false,
      deletedAt: null,
      playMinutes: 95,
      createdAt: new Date('2026-10-25T18:00:00Z'),
      lastSeenAt: new Date('2026-10-25T19:30:12.345Z'),
      applicationId: null,
      snapshotAt,
    });
  });

  it('supprimé : date de la photo la première fois, puis inchangée', () => {
    const lu = validerPhoto({ ...PHOTO, deleted: true });
    if (!lu.ok) throw new Error(lu.message);
    expect(donneesDepuisPhoto(lu.photo, ctx).deletedAt).toEqual(snapshotAt);
    const avant = new Date('2026-10-20T10:00:00Z');
    expect(
      donneesDepuisPhoto(lu.photo, { ...ctx, deletedAtExistant: avant })
        .deletedAt,
    ).toEqual(avant);
    const restaure = validerPhoto({ ...PHOTO, deleted: false });
    if (!restaure.ok) throw new Error(restaure.message);
    expect(
      donneesDepuisPhoto(restaure.photo, { ...ctx, deletedAtExistant: avant })
        .deletedAt,
    ).toBeNull();
  });

  it('civil : type gardé', () => {
    const lu = validerPhoto({ ...PHOTO, type: 'CIVIL', rank: null });
    if (!lu.ok) throw new Error(lu.message);
    const d = donneesDepuisPhoto(lu.photo, ctx);
    expect(d.type).toBe('CIVIL');
    expect(d.rank).toBeNull();
  });
});

describe('clanOuNull', () => {
  it('« Autre » (clan personnalisé du mod) et vide valent sans clan', () => {
    expect(clanOuNull('Autre')).toBeNull();
    expect(clanOuNull(' autre ')).toBeNull();
    expect(clanOuNull('')).toBeNull();
    expect(clanOuNull(null)).toBeNull();
    expect(clanOuNull('Uchiha')).toBe('Uchiha');
  });
});
