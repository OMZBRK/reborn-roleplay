/*
  Warnings:

  - Added the required column `expiresAt` to the `Manifest` table without a default value. This is not possible if the table is not empty.
  - Added the required column `issuedAt` to the `Manifest` table without a default value. This is not possible if the table is not empty.

*/
-- AlterTable
ALTER TABLE "Manifest" ADD COLUMN     "expiresAt" TIMESTAMP(3) NOT NULL,
ADD COLUMN     "issuedAt" TIMESTAMP(3) NOT NULL;
