-- CreateEnum
CREATE TYPE "MessageAuthor" AS ENUM ('USER', 'STAFF', 'SYSTEM');

-- DropForeignKey
ALTER TABLE "TicketMessage" DROP CONSTRAINT "TicketMessage_ticketId_fkey";

-- AlterTable
ALTER TABLE "Ticket" ADD COLUMN     "discordThreadId" TEXT;

-- AlterTable
ALTER TABLE "TicketMessage" ADD COLUMN     "attachments" JSONB NOT NULL DEFAULT '[]',
ADD COLUMN     "authorName" TEXT,
ADD COLUMN     "authorType" "MessageAuthor" NOT NULL DEFAULT 'USER',
ADD COLUMN     "discordMessageId" TEXT,
ALTER COLUMN "authorId" DROP NOT NULL;

-- AlterTable
ALTER TABLE "WhitelistApplication" ADD COLUMN     "discordThreadId" TEXT;

-- CreateTable
CREATE TABLE "WhitelistMessage" (
    "id" TEXT NOT NULL,
    "applicationId" TEXT NOT NULL,
    "authorType" "MessageAuthor" NOT NULL,
    "authorId" TEXT,
    "authorName" TEXT,
    "content" TEXT NOT NULL,
    "attachments" JSONB NOT NULL DEFAULT '[]',
    "discordMessageId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "WhitelistMessage_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "WhitelistMessage_applicationId_createdAt_idx" ON "WhitelistMessage"("applicationId", "createdAt");

-- CreateIndex
CREATE INDEX "TicketMessage_ticketId_createdAt_idx" ON "TicketMessage"("ticketId", "createdAt");

-- AddForeignKey
ALTER TABLE "WhitelistMessage" ADD CONSTRAINT "WhitelistMessage_applicationId_fkey" FOREIGN KEY ("applicationId") REFERENCES "WhitelistApplication"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TicketMessage" ADD CONSTRAINT "TicketMessage_ticketId_fkey" FOREIGN KEY ("ticketId") REFERENCES "Ticket"("id") ON DELETE CASCADE ON UPDATE CASCADE;
