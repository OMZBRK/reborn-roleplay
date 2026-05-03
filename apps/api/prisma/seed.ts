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

async function main() {
  await seedPatchNotes();
  await seedRules();
  await seedDevManifest();
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
