-- CreateTable
CREATE TABLE "Lore" (
    "id" TEXT NOT NULL,
    "version" TEXT NOT NULL,
    "content" TEXT NOT NULL,
    "publishedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "isCurrent" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "Lore_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "Lore_version_key" ON "Lore"("version");

-- CreateIndex
CREATE INDEX "Lore_isCurrent_idx" ON "Lore"("isCurrent");
