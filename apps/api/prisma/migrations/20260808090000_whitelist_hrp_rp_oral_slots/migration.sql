-- CreateEnum
CREATE TYPE "OralSlotStatus" AS ENUM ('OPEN', 'BOOKED', 'DONE', 'CANCELLED');

-- AlterTable : statuts HRP/RP independants (L5). Defaut PENDING.
ALTER TABLE "WhitelistApplication" ADD COLUMN     "hrpStatus" "AppStatus" NOT NULL DEFAULT 'PENDING',
ADD COLUMN     "rpStatus" "AppStatus" NOT NULL DEFAULT 'PENDING';

-- Backfill : les candidatures existantes heritent de leur statut global pour
-- rester coherentes (une APPROVED devient HRP+RP approuves, etc.).
UPDATE "WhitelistApplication" SET "hrpStatus" = "status", "rpStatus" = "status";

-- CreateTable
CREATE TABLE "WhitelistOralSlot" (
    "id" TEXT NOT NULL,
    "startAt" TIMESTAMP(3) NOT NULL,
    "durationMin" INTEGER NOT NULL DEFAULT 30,
    "status" "OralSlotStatus" NOT NULL DEFAULT 'OPEN',
    "openedByUserId" TEXT NOT NULL,
    "bookedByUserId" TEXT,
    "bookedAt" TIMESTAMP(3),
    "notes" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "WhitelistOralSlot_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "WhitelistOralSlot_status_startAt_idx" ON "WhitelistOralSlot"("status", "startAt");

-- AddForeignKey
ALTER TABLE "WhitelistOralSlot" ADD CONSTRAINT "WhitelistOralSlot_openedByUserId_fkey" FOREIGN KEY ("openedByUserId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WhitelistOralSlot" ADD CONSTRAINT "WhitelistOralSlot_bookedByUserId_fkey" FOREIGN KEY ("bookedByUserId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;
