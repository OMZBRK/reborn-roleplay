// Import wiki entries from a JSON array into the Reborn wiki (idempotent by slug).
// Usage: node import-wiki.js <path-to-wiki-import.json>
// Requires: run from a dir with @prisma/client + DATABASE_URL in env.
const fs = require('fs');
const { PrismaClient } = require('@prisma/client');
const prisma = new PrismaClient();

function slugify(s) {
  return (s || '')
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
    .slice(0, 80) || 'entry';
}

(async () => {
  const path = process.argv[2] || 'wiki-import.json';
  const data = JSON.parse(fs.readFileSync(path, 'utf-8'));
  if (!Array.isArray(data)) throw new Error('expected a JSON array');

  // Validate tag slugs exist up-front.
  const known = new Set((await prisma.wikiTag.findMany({ select: { slug: true } })).map((t) => t.slug));
  let created = 0, updated = 0, skippedTags = new Set();

  for (const e of data) {
    const slug = slugify(e.title);
    const tagSlugs = (e.tagSlugs || []).filter((s) => {
      if (known.has(s)) return true;
      skippedTags.add(s);
      return false;
    });
    const tags = tagSlugs.map((s) => ({ slug: s }));
    const exists = await prisma.wikiEntry.findUnique({ where: { slug } });
    await prisma.wikiEntry.upsert({
      where: { slug },
      update: {
        title: e.title, summary: e.summary ?? null, body: e.body,
        status: e.status || 'PUBLISHED', tags: { set: tags },
      },
      create: {
        title: e.title, slug, summary: e.summary ?? null, body: e.body,
        status: e.status || 'PUBLISHED', tags: { connect: tags },
      },
    });
    exists ? updated++ : created++;
    console.log(`  ${exists ? 'upd' : 'new'}  ${slug}  [${tagSlugs.join(', ')}]`);
  }

  const total = await prisma.wikiEntry.count();
  console.log(`\nDone: ${created} created, ${updated} updated. WikiEntry total = ${total}.`);
  if (skippedTags.size) console.log(`WARN unknown tag slugs skipped: ${[...skippedTags].join(', ')}`);
  await prisma.$disconnect();
})().catch((e) => { console.error(e); process.exit(1); });
