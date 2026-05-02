import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  // Premier patch note pour amorcer l ecran d accueil
  const existing = await prisma.patchNote.findFirst({ where: { version: '1.0.0' } });
  if (!existing) {
    await prisma.patchNote.create({
      data: {
        version: '1.0.0',
        title: 'Bienvenue a Reborn Roleplay',
        content:
          '# Premiere release\n\nLe launcher Reborn est officiellement en ligne. Bon RP a tous !',
        pinned: true,
      },
    });
  }
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
