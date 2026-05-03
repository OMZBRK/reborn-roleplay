import { PrismaClient } from '@prisma/client';
import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

const prisma = new PrismaClient();

async function seedPatchNote() {
  const existing = await prisma.patchNote.findFirst({ where: { version: '1.0.0' } });
  if (existing) return;
  await prisma.patchNote.create({
    data: {
      version: '1.0.0',
      title: 'Bienvenue a Reborn Roleplay',
      content:
        '# Premiere release\n\nLe launcher Reborn est officiellement en ligne. Bon RP a tous !',
      pinned: true,
    },
  });
  console.log('Seeded patch note 1.0.0');
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

async function main() {
  await seedPatchNote();
  await seedDevManifest();
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
