-- CreateTable
CREATE TABLE "Rules" (
    "id" TEXT NOT NULL,
    "version" TEXT NOT NULL,
    "content" TEXT NOT NULL,
    "publishedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "isCurrent" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "Rules_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "Rules_version_key" ON "Rules"("version");

-- CreateIndex
CREATE INDEX "Rules_isCurrent_idx" ON "Rules"("isCurrent");
