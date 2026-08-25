-- CreateEnum
CREATE TYPE "WikiEntryStatus" AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED');

-- CreateEnum
CREATE TYPE "WikiIdeaStatus" AS ENUM ('PROPOSED', 'ACCEPTED', 'IN_PROGRESS', 'DONE', 'REJECTED');

-- CreateEnum
CREATE TYPE "WikiTagKind" AS ENUM ('SOURCE', 'CANON', 'TYPE', 'AUDIENCE');

-- CreateTable
CREATE TABLE "WikiTag" (
    "id" TEXT NOT NULL,
    "kind" "WikiTagKind" NOT NULL,
    "label" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "color" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "WikiTag_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WikiEntry" (
    "id" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "summary" TEXT,
    "body" TEXT NOT NULL,
    "status" "WikiEntryStatus" NOT NULL DEFAULT 'DRAFT',
    "sources" TEXT,
    "createdById" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "WikiEntry_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WikiRevision" (
    "id" TEXT NOT NULL,
    "entryId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "summary" TEXT,
    "body" TEXT NOT NULL,
    "editedById" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "WikiRevision_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WikiIdea" (
    "id" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "body" TEXT NOT NULL,
    "status" "WikiIdeaStatus" NOT NULL DEFAULT 'PROPOSED',
    "category" TEXT,
    "linkedEntryId" TEXT,
    "createdById" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "WikiIdea_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "_WikiEntryToWikiTag" (
    "A" TEXT NOT NULL,
    "B" TEXT NOT NULL,

    CONSTRAINT "_WikiEntryToWikiTag_AB_pkey" PRIMARY KEY ("A","B")
);

-- CreateIndex
CREATE UNIQUE INDEX "WikiTag_slug_key" ON "WikiTag"("slug");

-- CreateIndex
CREATE INDEX "WikiTag_kind_idx" ON "WikiTag"("kind");

-- CreateIndex
CREATE UNIQUE INDEX "WikiEntry_slug_key" ON "WikiEntry"("slug");

-- CreateIndex
CREATE INDEX "WikiEntry_status_idx" ON "WikiEntry"("status");

-- CreateIndex
CREATE INDEX "WikiEntry_updatedAt_idx" ON "WikiEntry"("updatedAt");

-- CreateIndex
CREATE INDEX "WikiRevision_entryId_createdAt_idx" ON "WikiRevision"("entryId", "createdAt");

-- CreateIndex
CREATE INDEX "WikiIdea_status_idx" ON "WikiIdea"("status");

-- CreateIndex
CREATE INDEX "_WikiEntryToWikiTag_B_index" ON "_WikiEntryToWikiTag"("B");

-- AddForeignKey
ALTER TABLE "WikiRevision" ADD CONSTRAINT "WikiRevision_entryId_fkey" FOREIGN KEY ("entryId") REFERENCES "WikiEntry"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WikiIdea" ADD CONSTRAINT "WikiIdea_linkedEntryId_fkey" FOREIGN KEY ("linkedEntryId") REFERENCES "WikiEntry"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_WikiEntryToWikiTag" ADD CONSTRAINT "_WikiEntryToWikiTag_A_fkey" FOREIGN KEY ("A") REFERENCES "WikiEntry"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_WikiEntryToWikiTag" ADD CONSTRAINT "_WikiEntryToWikiTag_B_fkey" FOREIGN KEY ("B") REFERENCES "WikiTag"("id") ON DELETE CASCADE ON UPDATE CASCADE;
