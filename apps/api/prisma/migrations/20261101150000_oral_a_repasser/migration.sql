-- Oral à repasser (décision du 01/11, amende celle du 19/10) : le staff peut exiger un nouvel oral d'un joueur
-- (POST /v1/admin/players/:id/oral-a-repasser), motif obligatoire, tracé à l'audit. Ces colonnes disent quand,
-- qui et pourquoi ; l'état « oral à passer » reste celui que lisent les règles (rôle PLAYER, oralPassedAt effacé).
-- Vérifié hors ligne : prisma migrate diff depuis le schéma précédent (même résultat, en une seule instruction).
-- Trois colonnes facultatives : aucune donnée existante n'est touchée.
ALTER TABLE "User" ADD COLUMN "oralRetakeRequestedAt" TIMESTAMP(3);
ALTER TABLE "User" ADD COLUMN "oralRetakeRequestedById" TEXT;
ALTER TABLE "User" ADD COLUMN "oralRetakeReason" TEXT;
