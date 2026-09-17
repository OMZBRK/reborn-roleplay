-- CreateEnum
CREATE TYPE "TechniqueGraphStatus" AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED');

-- CreateTable
CREATE TABLE "TechniqueGraph" (
    "id" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "category" TEXT NOT NULL DEFAULT '',
    "status" "TechniqueGraphStatus" NOT NULL DEFAULT 'DRAFT',
    "graph" JSONB NOT NULL,
    "createdById" TEXT,
    "updatedById" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "TechniqueGraph_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "TechniqueGraph_slug_key" ON "TechniqueGraph"("slug");

-- CreateIndex
CREATE INDEX "TechniqueGraph_status_updatedAt_idx" ON "TechniqueGraph"("status", "updatedAt");
