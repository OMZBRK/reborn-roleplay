-- Personnages du jeu (docs/gameplay/CANDIDATURE_ET_DOUANE.md §4.5, lot J) : dernière photo de chaque personnage
-- de ShinobiCore, poussée par POST /v1/game/characters/sync (route signée), lue par GET /v1/me/characters.
-- Généré hors ligne : prisma migrate diff depuis le schéma du 25/10 (après 20261025120000_candidature_v2_douane).
-- Aucune donnée existante n'est touchée.

-- CreateTable
CREATE TABLE "GameCharacter" (
    "id" TEXT NOT NULL,
    "serverId" TEXT NOT NULL,
    "characterId" TEXT NOT NULL,
    "ownerMcUuid" TEXT NOT NULL,
    "userId" TEXT,
    "playerId" INTEGER,
    "firstName" TEXT NOT NULL,
    "lastName" TEXT,
    "clan" TEXT,
    "village" TEXT,
    "type" "CharacterType" NOT NULL DEFAULT 'NINJA',
    "rank" TEXT,
    "sex" "CharacterSex",
    "age" INTEGER,
    "dead" BOOLEAN NOT NULL DEFAULT false,
    "deletedAt" TIMESTAMP(3),
    "playMinutes" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3),
    "lastSeenAt" TIMESTAMP(3),
    "applicationId" TEXT,
    "snapshotAt" TIMESTAMP(3) NOT NULL,
    "firstSyncedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "GameCharacter_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "GameCharacter_userId_serverId_idx" ON "GameCharacter"("userId", "serverId");

-- CreateIndex
CREATE INDEX "GameCharacter_ownerMcUuid_idx" ON "GameCharacter"("ownerMcUuid");

-- CreateIndex
CREATE INDEX "GameCharacter_applicationId_idx" ON "GameCharacter"("applicationId");

-- CreateIndex
CREATE UNIQUE INDEX "GameCharacter_serverId_characterId_key" ON "GameCharacter"("serverId", "characterId");

-- CreateIndex
CREATE UNIQUE INDEX "GameCharacter_serverId_playerId_key" ON "GameCharacter"("serverId", "playerId");

-- AddForeignKey
ALTER TABLE "GameCharacter" ADD CONSTRAINT "GameCharacter_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "GameCharacter" ADD CONSTRAINT "GameCharacter_applicationId_fkey" FOREIGN KEY ("applicationId") REFERENCES "WhitelistApplication"("id") ON DELETE SET NULL ON UPDATE CASCADE;

