-- AlterEnum
-- Ajoute deux grades techniques (contributeurs) au rôle staff :
--   MODELISATEUR (accès fichiers Nexo) et DEVELOPPEUR (MagicSpells/MythicMobs/ModelEngine).
-- Migration ENUM-ONLY et standalone : `ALTER TYPE ... ADD VALUE` ne peut pas
-- tourner dans la même transaction qui utiliserait la valeur, donc on n'y met
-- rien d'autre. Postgres 12+ requis (prod = PG16).

ALTER TYPE "Role" ADD VALUE 'MODELISATEUR';
ALTER TYPE "Role" ADD VALUE 'DEVELOPPEUR';
