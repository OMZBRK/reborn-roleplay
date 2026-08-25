import { PrismaClient } from '@prisma/client';
import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

const prisma = new PrismaClient();

async function seedPatchNotes() {
  const existing = await prisma.patchNote.findMany({ select: { version: true } });
  const have = new Set(existing.map((e) => e.version));
  const notes = [
    {
      version: '1.0.0',
      title: 'Bienvenue a Reborn Roleplay',
      pinned: true,
      content: `# Premiere release

Le launcher Reborn est officiellement en ligne. Bon RP a tous !

## Nouveautes
- Authentification Microsoft (compte Java premium)
- Telechargement automatique des mods autorises
- Verification d'integrite SHA-256
- Auto-connect au serveur depuis le bouton Jouer

## A venir
- Boutique et ZK Coin (v1.1)
- Liste d'amis + DM (v1.0.5)
- Lore interactif (v1.2)`,
    },
    {
      version: '0.9.0',
      title: 'Beta fermee : 50 testeurs valides',
      pinned: false,
      content: `# Beta privee

Merci aux **50 premiers testeurs** d'avoir essuye les platres :)

## Bugs majeurs corriges
- Crash a l'installation sur Windows 11 ARM
- Mauvaise resolution sur ecrans 4K
- Liaison Discord qui boucle a l'infini

## Reste a faire avant la v1.0
- [ ] Whitelist accessible aux nouveaux
- [ ] Bouton "Jouer" en mode hors-ligne
- [ ] Notifications cloche cliquables`,
    },
    {
      version: '0.8.5',
      title: 'Sodium 0.6.13 + Iris Shaders supporte',
      pinned: false,
      content: `# Visuels

On embarque maintenant **Sodium 0.6.13** ainsi que **Iris Shaders 1.8** par defaut. Les FPS sont multipliees par 2 a 3 sur la plupart des configurations.

## Compatibilites verifiees
- Complementary Reimagined v5.x
- BSL Shaders v8.x
- Sildur's Vibrant Shaders v1.x

## Note importante
Optifine n'est plus supporte. Si tu utilisais des shaders Optifine, ils sont generalement compatibles avec Iris a 90 %.`,
    },
  ];
  let added = 0;
  for (const n of notes) {
    if (have.has(n.version)) continue;
    await prisma.patchNote.create({ data: n });
    added += 1;
  }
  if (added > 0) console.log(`Seeded ${added} patch note(s).`);
}

async function seedDevManifest() {
  // Le manifest signe vit dans packages/manifest-signer/examples — on le
  // charge si present, sinon on skip (les tests CI n'ont pas de cle).
  const repoRoot = resolve(__dirname, '..', '..', '..');
  const signedPath = resolve(
    repoRoot,
    'packages/manifest-signer/examples/sample-manifest-signed.json',
  );

  if (!existsSync(signedPath)) {
    console.log(
      'Skip dev manifest seed (sample-manifest-signed.json absent — utiliser le CLI).',
    );
    return;
  }

  const signed = JSON.parse(readFileSync(signedPath, 'utf8'));
  const version: string = signed.version;

  const existing = await prisma.manifest.findUnique({ where: { version } });
  if (existing) {
    console.log(`Manifest ${version} deja en base.`);
    return;
  }

  // Marque tous les anciens comme non-courants.
  await prisma.manifest.updateMany({
    where: { isCurrent: true },
    data: { isCurrent: false },
  });

  await prisma.manifest.create({
    data: {
      version,
      minecraftVersion: signed.minecraftVersion,
      files: signed.files,
      signature: signed.signature,
      isCurrent: true,
      minLauncherVersion: signed.minLauncherVersion,
      issuedAt: new Date(signed.issuedAt),
      expiresAt: new Date(signed.expiresAt),
    },
  });
  console.log(`Seeded dev manifest ${version}`);
}

async function seedRules() {
  const existing = await prisma.rules.findFirst({ where: { version: '1.0' } });
  if (existing) return;
  await prisma.rules.updateMany({ where: { isCurrent: true }, data: { isCurrent: false } });
  await prisma.rules.create({
    data: {
      version: '1.0',
      isCurrent: true,
      content: `# Reglement Reborn Roleplay

Bienvenue sur **Reborn Roleplay**. Le respect de ces regles est obligatoire et conditionne ton acces au serveur.

## 1. Respect general
- Pas d'insultes, racisme, sexisme, homophobie, discrimination de toute nature.
- Pas de harcelement, en jeu comme sur Discord.
- Tout le monde joue pour s'amuser : adapte ton ton et tes propos.

## 2. Roleplay
- Garde la **separation IC / HRP** stricte.
- Ne meta-game pas (utiliser des infos HRP en RP est interdit).
- Ne power-game pas (imposer une action ou un resultat).
- Le **PvP non consenti** sans raison RP est interdit.

## 3. Triche
- **Aucun mod gameplay** non listee dans la modlist Reborn.
- Pas de duplication, exploit, x-ray, killaura, etc.
- Le launcher verifie l'integrite : tout fichier modifie est detecte.

## 4. Compte
- Un compte = un personnage RP. Pas de partage de compte.
- Liaison Discord obligatoire pour postuler a la whitelist.
- Le pseudo Minecraft doit rester stable apres acceptation.

## 5. Sanctions
| Niveau | Type | Duree typique |
|---|---|---|
| 1 | Warn (avertissement) | — |
| 2 | Mute | 1h a 24h |
| 3 | Kick | — |
| 4 | Ban temporaire | 1j a 7j |
| 5 | Ban definitif | permanent |

Le staff applique le niveau approprie selon la gravite et l'historique. **Le staff a le dernier mot**, mais tu peux toujours faire appel via un ticket.

## 6. Boutique
- Aucun avantage gameplay vendu (politique pay-to-win interdite).
- Items cosmetiques + grades de chat uniquement.
- Tout achat est definitif sauf cas exceptionnel valide par un Admin.

## 7. Lore
- Le lore Reborn evolue. Reste a jour via les patch notes et les events.
- Toute creation de personnage doit s'inscrire dans le canon actuel.

---

> **Tu acceptes ce reglement** en validant ta candidature whitelist. Une violation peut entrainer une exclusion immediate.

> *Derniere mise a jour : 2026-05-03 — Version 1.0*`,
    },
  });
  console.log('Seeded rules 1.0');
}

async function seedLore() {
  const existing = await prisma.lore.findFirst({ where: { version: '1.0' } });
  if (existing) return;
  await prisma.lore.updateMany({ where: { isCurrent: true }, data: { isCurrent: false } });
  await prisma.lore.create({
    data: {
      version: '1.0',
      isCurrent: true,
      content: `# Lore — Reborn Roleplay

> *"Le monde shinobi se reconstruit, mais les blessures du passe saignent encore."*

## L'ere actuelle

Plusieurs decennies se sont ecoulees depuis la Quatrieme Grande Guerre. Les villages cachees ont reconstruit leurs murs, leurs ecoles et leurs hierarchies — mais l'equilibre reste fragile. Une nouvelle generation grandit dans l'ombre des anciens heros, ignorante des veritables couts de la paix.

## Les cinq grandes nations

### Pays du Feu — **Konohagakure**
Le village de la feuille, autrefois fer de lance de l'alliance shinobi, traverse une periode de **renouveau politique**. Le Hokage actuel cherche a moderniser les structures du village tout en preservant la **Volonte du Feu**.

### Pays du Vent — **Sunagakure**
Sune souffre toujours d'une **economie fragile** liee au climat aride. Le Kazekage pousse une politique d'**ouverture commerciale** vers les autres nations, au prix de tensions internes avec les vieilles familles.

### Pays de l'Eau — **Kirigakure**
Apres des decennies sous la "**Brume Sanglante**", Kiri a rejoint la communaute internationale. Mais des **factions extremistes** persistent dans les iles du sud, refusant la reforme.

### Pays de la Foudre — **Kumogakure**
Kumo est devenue la **puissance militaire dominante** des cinq nations. Le Raikage actuel maintient une discipline de fer et investit massivement dans la **recherche sur le chakra**.

### Pays de la Terre — **Iwagakure**
Iwa reste **isolationniste**. Le village conserve ses traditions millenaires et ses techniques ancestrales — au prix d'un retard technologique grandissant.

## Les menaces emergentes

- **Les Sans-Banniere** : ronins shinobi sans village, certains organises en mercenariats, d'autres en sectes mystiques.
- **Le Reveil** : un mouvement souterrain qui pretend que la **paix actuelle est artificielle** et que l'equilibre du chakra a ete brise.
- **Phenomenes inexpliques** : des kekkei genkai oublies refont surface chez des civils. Origine inconnue.

## Ton personnage

Tu rejoins ce monde en tant que **shinobi de la nouvelle generation**, **civil ambitieux**, ou **etranger en quete de but**. Ton background s'inscrit forcement dans ce canon — au moment de la **whitelist**, le staff valide la coherence de ton histoire avec l'ere actuelle.

> Le lore evolue avec les **events RP** organises par le staff. Reste a jour via les patch notes.

---

> *Derniere mise a jour : 2026-05-03 — Version 1.0*`,
    },
  });
  console.log('Seeded lore 1.0');
}

async function seedWikiTags() {
  // Taxonomie par defaut de la base de connaissances staff (4 axes). Idempotent :
  // upsert par slug, donc re-seed sans doublon. Additif — ne touche pas aux tags
  // crees a la main via le panel.
  const tags: Array<{
    kind: 'SOURCE' | 'CANON' | 'TYPE' | 'AUDIENCE';
    label: string;
    slug: string;
    color?: string;
  }> = [
    // Source
    { kind: 'SOURCE', label: 'Animé', slug: 'source-anime', color: '#f59e0b' },
    { kind: 'SOURCE', label: 'Manga', slug: 'source-manga', color: '#f59e0b' },
    { kind: 'SOURCE', label: 'Databook', slug: 'source-databook', color: '#f59e0b' },
    { kind: 'SOURCE', label: 'Film', slug: 'source-film', color: '#f59e0b' },
    // Canonicité
    { kind: 'CANON', label: 'Canon', slug: 'canon-canon', color: '#22c55e' },
    { kind: 'CANON', label: 'Reborn', slug: 'canon-reborn', color: '#3b82f6' },
    { kind: 'CANON', label: 'Headcanon', slug: 'canon-headcanon', color: '#a855f7' },
    // Type
    { kind: 'TYPE', label: 'Lore', slug: 'type-lore', color: '#38bdf8' },
    { kind: 'TYPE', label: 'Technique', slug: 'type-technique', color: '#38bdf8' },
    { kind: 'TYPE', label: 'Clan', slug: 'type-clan', color: '#38bdf8' },
    { kind: 'TYPE', label: 'Village', slug: 'type-village', color: '#38bdf8' },
    { kind: 'TYPE', label: 'Dôjutsu', slug: 'type-dojutsu', color: '#38bdf8' },
    { kind: 'TYPE', label: 'Personnage', slug: 'type-personnage', color: '#38bdf8' },
    { kind: 'TYPE', label: 'Historique', slug: 'type-historique', color: '#38bdf8' },
    { kind: 'TYPE', label: 'Mécanique', slug: 'type-mecanique', color: '#38bdf8' },
    // Audience
    { kind: 'AUDIENCE', label: 'Public', slug: 'audience-public', color: '#94a3b8' },
    { kind: 'AUDIENCE', label: 'Genin', slug: 'audience-genin', color: '#94a3b8' },
    { kind: 'AUDIENCE', label: 'Genin confirmé', slug: 'audience-genin-confirme', color: '#94a3b8' },
    { kind: 'AUDIENCE', label: 'Chûnin', slug: 'audience-chunin', color: '#94a3b8' },
    { kind: 'AUDIENCE', label: 'Jônin', slug: 'audience-jonin', color: '#94a3b8' },
    { kind: 'AUDIENCE', label: 'Staff only', slug: 'audience-staff-only', color: '#ef4444' },
  ];
  let added = 0;
  for (const t of tags) {
    const res = await prisma.wikiTag.upsert({
      where: { slug: t.slug },
      update: { kind: t.kind, label: t.label, color: t.color },
      create: { kind: t.kind, label: t.label, slug: t.slug, color: t.color },
    });
    if (res) added += 1;
  }
  console.log(`Seeded/updated ${added} wiki tag(s).`);
}

async function main() {
  await seedPatchNotes();
  await seedRules();
  await seedLore();
  await seedDevManifest();
  await seedWikiTags();
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
