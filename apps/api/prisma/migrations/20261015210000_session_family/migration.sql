-- Famille de rotation des refresh tokens : un replay ne révoque plus que sa famille.
ALTER TABLE "Session" ADD COLUMN "familyId" TEXT;

CREATE INDEX "Session_familyId_idx" ON "Session"("familyId");
