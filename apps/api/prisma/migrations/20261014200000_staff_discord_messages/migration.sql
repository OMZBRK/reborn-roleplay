-- CreateEnum
CREATE TYPE "StaffDiscordChannel" AS ENUM ('ANNONCES', 'DEV_UPDATES');

-- CreateEnum
CREATE TYPE "StaffDiscordMessageStatus" AS ENUM ('DRAFT', 'PUBLISHED', 'REMOVED');

-- CreateTable
CREATE TABLE "StaffDiscordMessage" (
    "id" TEXT NOT NULL,
    "channel" "StaffDiscordChannel" NOT NULL,
    "status" "StaffDiscordMessageStatus" NOT NULL DEFAULT 'DRAFT',
    "title" TEXT NOT NULL,
    "color" TEXT NOT NULL DEFAULT '#A0182B',
    "blocks" JSONB NOT NULL,
    "ping" TEXT NOT NULL DEFAULT 'aucun',
    "discordMessageId" TEXT,
    "discordChannelId" TEXT,
    "discordUrl" TEXT,
    "previewMessageId" TEXT,
    "createdById" TEXT NOT NULL,
    "createdByName" TEXT NOT NULL,
    "updatedById" TEXT,
    "updatedByName" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "publishedAt" TIMESTAMP(3),
    "removedAt" TIMESTAMP(3),

    CONSTRAINT "StaffDiscordMessage_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "StaffDiscordMessage_status_updatedAt_idx" ON "StaffDiscordMessage"("status", "updatedAt");

-- CreateIndex
CREATE INDEX "StaffDiscordMessage_channel_createdAt_idx" ON "StaffDiscordMessage"("channel", "createdAt");

