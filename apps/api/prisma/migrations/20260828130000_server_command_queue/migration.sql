-- CreateEnum
CREATE TYPE "ServerCommandStatus" AS ENUM ('PENDING', 'DISPATCHED', 'DONE', 'FAILED');

-- CreateTable
CREATE TABLE "ServerCommand" (
    "id" TEXT NOT NULL,
    "target" TEXT NOT NULL,
    "command" TEXT NOT NULL,
    "status" "ServerCommandStatus" NOT NULL DEFAULT 'PENDING',
    "output" TEXT,
    "requestedById" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "dispatchedAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),

    CONSTRAINT "ServerCommand_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "ServerCommand_status_createdAt_idx" ON "ServerCommand"("status", "createdAt");
