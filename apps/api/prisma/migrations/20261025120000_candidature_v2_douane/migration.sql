-- Candidature v2 et douane en jeu (docs/gameplay/CANDIDATURE_ET_DOUANE.md §2.9, lots A et B).
-- Schéma : nouveaux champs de la candidature (âge en nombre, type, sexe et âge du personnage, voix privée,
-- consentements datés, formVersion, décisions écrit / oral séparées), brouillon serveur, séances de douane avec
-- capacité et réservations, nonces des routes signées du jeu. Les anciens champs restent : l'ancien launcher
-- continue d'envoyer (formVersion 1) ; dob, motivation, experience et appearance deviennent facultatifs.
-- Fin du fichier : migration des dossiers en cours (données).

-- CreateEnum
CREATE TYPE "RpYears" AS ENUM ('JAMAIS', 'MOINS_D_UN_AN', 'UN_A_DEUX_ANS', 'TROIS_A_CINQ_ANS', 'SIX_ANS_ET_PLUS');

-- CreateEnum
CREATE TYPE "NarutoKnowledge" AS ENUM ('DEBUTANT', 'QUELQUES_ARCS', 'BONNE_CONNAISSANCE', 'INCOLLABLE');

-- CreateEnum
CREATE TYPE "DiscoverySource" AS ENUM ('DISCORD', 'TIKTOK', 'YOUTUBE', 'AMI', 'AUTRE_SERVEUR', 'RECHERCHE', 'AUTRE');

-- CreateEnum
CREATE TYPE "CharacterType" AS ENUM ('NINJA', 'CIVIL');

-- CreateEnum
CREATE TYPE "CharacterSex" AS ENUM ('HOMME', 'FEMME');

-- CreateEnum
CREATE TYPE "OralVia" AS ENUM ('DOUANE', 'HORS_JEU');

-- CreateEnum
CREATE TYPE "DouaneKind" AS ENUM ('WHITELIST');

-- CreateEnum
CREATE TYPE "DouaneSeanceStatut" AS ENUM ('OUVERTE', 'FERMEE', 'TERMINEE', 'ANNULEE');

-- CreateEnum
CREATE TYPE "DouaneReservationStatut" AS ENUM ('BOOKED', 'DONE', 'MISSED', 'CANCELLED');

-- AlterTable
ALTER TABLE "User" ADD COLUMN     "oralPassedAt" TIMESTAMP(3),
ADD COLUMN     "whitelistRejectedAt" TIMESTAMP(3);

-- AlterTable
ALTER TABLE "WhitelistApplication" ADD COLUMN     "characterAge" INTEGER,
ADD COLUMN     "characterSex" "CharacterSex",
ADD COLUMN     "characterType" "CharacterType",
ADD COLUMN     "discoverySource" "DiscoverySource",
ADD COLUMN     "formVersion" INTEGER NOT NULL DEFAULT 1,
ADD COLUMN     "narutoKnowledge" "NarutoKnowledge",
ADD COLUMN     "oralDecidedAt" TIMESTAMP(3),
ADD COLUMN     "oralExaminerUserId" TEXT,
ADD COLUMN     "oralNotes" TEXT,
ADD COLUMN     "oralReason" TEXT,
ADD COLUMN     "oralStatus" "AppStatus" NOT NULL DEFAULT 'PENDING',
ADD COLUMN     "oralVia" "OralVia",
ADD COLUMN     "playerAge" INTEGER,
ADD COLUMN     "rpYears" "RpYears",
ADD COLUMN     "rulesAcceptedAt" TIMESTAMP(3),
ADD COLUMN     "voiceConsentAt" TIMESTAMP(3),
ADD COLUMN     "voiceDeletedAt" TIMESTAMP(3),
ADD COLUMN     "voiceDurationSec" INTEGER,
ADD COLUMN     "voiceKey" TEXT,
ADD COLUMN     "voiceMimeType" TEXT,
ADD COLUMN     "voicePurgeAt" TIMESTAMP(3),
ADD COLUMN     "voiceUploadedAt" TIMESTAMP(3),
ADD COLUMN     "writtenApprovedAt" TIMESTAMP(3),
ADD COLUMN     "writtenStaffNotes" TEXT,
ALTER COLUMN "dob" DROP NOT NULL,
ALTER COLUMN "motivation" DROP NOT NULL,
ALTER COLUMN "experience" DROP NOT NULL,
ALTER COLUMN "appearance" DROP NOT NULL;

-- CreateTable
CREATE TABLE "CandidatureBrouillon" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "formVersion" INTEGER NOT NULL DEFAULT 2,
    "data" JSONB NOT NULL DEFAULT '{}',
    "voiceKey" TEXT,
    "voiceMimeType" TEXT,
    "voiceDurationSec" INTEGER,
    "voiceUploadedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CandidatureBrouillon_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DouaneSeance" (
    "id" TEXT NOT NULL,
    "kind" "DouaneKind" NOT NULL DEFAULT 'WHITELIST',
    "startAt" TIMESTAMP(3) NOT NULL,
    "endAt" TIMESTAMP(3) NOT NULL,
    "capacite" INTEGER NOT NULL,
    "statut" "DouaneSeanceStatut" NOT NULL DEFAULT 'OUVERTE',
    "openedByUserId" TEXT NOT NULL,
    "notes" TEXT,
    "clotureeAt" TIMESTAMP(3),
    "annuleeAt" TIMESTAMP(3),
    "motifAnnulation" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "DouaneSeance_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DouaneReservation" (
    "id" TEXT NOT NULL,
    "seanceId" TEXT NOT NULL,
    "applicationId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "convocationAt" TIMESTAMP(3) NOT NULL,
    "statut" "DouaneReservationStatut" NOT NULL DEFAULT 'BOOKED',
    "presentAt" TIMESTAMP(3),
    "calledAt" TIMESTAMP(3),
    "departAt" TIMESTAMP(3),
    "resultat" "AppStatus",
    "decidedAt" TIMESTAMP(3),
    "examinateurUserId" TEXT,
    "rappelEnvoyeAt" TIMESTAMP(3),
    "annuleeAt" TIMESTAMP(3),
    "motifAnnulation" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "DouaneReservation_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "GameRequestNonce" (
    "nonce" TEXT NOT NULL,
    "serverId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "GameRequestNonce_pkey" PRIMARY KEY ("nonce")
);

-- CreateTable
CREATE TABLE "_DouaneSeanceExaminateurs" (
    "A" TEXT NOT NULL,
    "B" TEXT NOT NULL,

    CONSTRAINT "_DouaneSeanceExaminateurs_AB_pkey" PRIMARY KEY ("A","B")
);

-- CreateIndex
CREATE UNIQUE INDEX "CandidatureBrouillon_userId_key" ON "CandidatureBrouillon"("userId");

-- CreateIndex
CREATE INDEX "DouaneSeance_statut_startAt_idx" ON "DouaneSeance"("statut", "startAt");

-- CreateIndex
CREATE INDEX "DouaneSeance_kind_startAt_idx" ON "DouaneSeance"("kind", "startAt");

-- CreateIndex
CREATE INDEX "DouaneReservation_seanceId_statut_idx" ON "DouaneReservation"("seanceId", "statut");

-- CreateIndex
CREATE INDEX "DouaneReservation_userId_statut_idx" ON "DouaneReservation"("userId", "statut");

-- CreateIndex
CREATE INDEX "DouaneReservation_applicationId_idx" ON "DouaneReservation"("applicationId");

-- CreateIndex
CREATE INDEX "DouaneReservation_statut_convocationAt_idx" ON "DouaneReservation"("statut", "convocationAt");

-- CreateIndex
CREATE INDEX "GameRequestNonce_createdAt_idx" ON "GameRequestNonce"("createdAt");

-- CreateIndex
CREATE INDEX "_DouaneSeanceExaminateurs_B_index" ON "_DouaneSeanceExaminateurs"("B");

-- AddForeignKey
ALTER TABLE "WhitelistApplication" ADD CONSTRAINT "WhitelistApplication_oralExaminerUserId_fkey" FOREIGN KEY ("oralExaminerUserId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CandidatureBrouillon" ADD CONSTRAINT "CandidatureBrouillon_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DouaneSeance" ADD CONSTRAINT "DouaneSeance_openedByUserId_fkey" FOREIGN KEY ("openedByUserId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DouaneReservation" ADD CONSTRAINT "DouaneReservation_seanceId_fkey" FOREIGN KEY ("seanceId") REFERENCES "DouaneSeance"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DouaneReservation" ADD CONSTRAINT "DouaneReservation_applicationId_fkey" FOREIGN KEY ("applicationId") REFERENCES "WhitelistApplication"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DouaneReservation" ADD CONSTRAINT "DouaneReservation_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DouaneReservation" ADD CONSTRAINT "DouaneReservation_examinateurUserId_fkey" FOREIGN KEY ("examinateurUserId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_DouaneSeanceExaminateurs" ADD CONSTRAINT "_DouaneSeanceExaminateurs_A_fkey" FOREIGN KEY ("A") REFERENCES "DouaneSeance"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_DouaneSeanceExaminateurs" ADD CONSTRAINT "_DouaneSeanceExaminateurs_B_fkey" FOREIGN KEY ("B") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;


-- ──────────────────────────────────────────────────────
-- Migration des dossiers en cours (§2.9) — sans elle, les joueurs déjà passés
-- par l'ancien oral le repasseraient.
-- ──────────────────────────────────────────────────────

-- 1. Ancien sens de hrpStatus : « HRP validé via le test oral » = oral passé.
--    Un statut global APPROVED vaut aussi oral passé (dans le nouveau sens,
--    APPROVED n'arrive qu'après l'oral), y compris quand l'ancienne décision
--    globale du bot l'a posé sans toucher hrpStatus. L'ancien oral se passait
--    dans le vocal Discord : HORS_JEU. oralDecidedAt reste vide (date inconnue).
UPDATE "WhitelistApplication"
SET "oralStatus" = 'APPROVED',
    "oralVia" = 'HORS_JEU'
WHERE "hrpStatus" = 'APPROVED' OR "status" = 'APPROVED';

-- 2. Candidatures déjà acceptées : l'écrit est validé depuis la dernière relecture.
UPDATE "WhitelistApplication"
SET "writtenApprovedAt" = COALESCE("reviewedAt", "submittedAt")
WHERE "status" = 'APPROVED';

-- 3. Comptes WHITELISTED ou plus (tout rôle autre que PLAYER) : oral passé, à la date de migration.
UPDATE "User"
SET "oralPassedAt" = CURRENT_TIMESTAMP
WHERE "role" <> 'PLAYER' AND "oralPassedAt" IS NULL;

-- 4. Candidats encore PLAYER dont l'ancien oral est validé (étape 1) : même marque sur le compte, pour qu'un
--    retrait suivi d'une nouvelle candidature ne les renvoie pas à l'oral (décision du 19/10).
UPDATE "User" AS u
SET "oralPassedAt" = CURRENT_TIMESTAMP
FROM "WhitelistApplication" AS a
WHERE a."userId" = u."id"
  AND a."oralStatus" = 'APPROVED'
  AND u."oralPassedAt" IS NULL;
