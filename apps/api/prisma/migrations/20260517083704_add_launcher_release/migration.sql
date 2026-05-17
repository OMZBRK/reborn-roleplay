-- CreateTable
CREATE TABLE "LauncherRelease" (
    "id" TEXT NOT NULL,
    "version" TEXT NOT NULL,
    "channel" TEXT NOT NULL DEFAULT 'stable',
    "target" TEXT NOT NULL,
    "url" TEXT NOT NULL,
    "signature" TEXT NOT NULL,
    "notes" TEXT,
    "publishedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "LauncherRelease_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "LauncherRelease_target_channel_publishedAt_idx" ON "LauncherRelease"("target", "channel", "publishedAt");

-- CreateIndex
CREATE UNIQUE INDEX "LauncherRelease_version_target_key" ON "LauncherRelease"("version", "target");
