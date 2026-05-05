/*
  Warnings:

  - You are about to drop the column `background` on the `WhitelistApplication` table. All the data in the column will be lost.
  - You are about to drop the column `characterAge` on the `WhitelistApplication` table. All the data in the column will be lost.
  - You are about to drop the column `characterName` on the `WhitelistApplication` table. All the data in the column will be lost.
  - Added the required column `appearance` to the `WhitelistApplication` table without a default value. This is not possible if the table is not empty.
  - Added the required column `availability` to the `WhitelistApplication` table without a default value. This is not possible if the table is not empty.
  - Added the required column `dob` to the `WhitelistApplication` table without a default value. This is not possible if the table is not empty.
  - Added the required column `experience` to the `WhitelistApplication` table without a default value. This is not possible if the table is not empty.
  - Added the required column `firstName` to the `WhitelistApplication` table without a default value. This is not possible if the table is not empty.
  - Added the required column `history` to the `WhitelistApplication` table without a default value. This is not possible if the table is not empty.
  - Added the required column `lastName` to the `WhitelistApplication` table without a default value. This is not possible if the table is not empty.
  - Added the required column `objectives` to the `WhitelistApplication` table without a default value. This is not possible if the table is not empty.
  - Added the required column `village` to the `WhitelistApplication` table without a default value. This is not possible if the table is not empty.

*/
-- AlterTable
ALTER TABLE "WhitelistApplication" DROP COLUMN "background",
DROP COLUMN "characterAge",
DROP COLUMN "characterName",
ADD COLUMN     "appearance" TEXT NOT NULL,
ADD COLUMN     "availability" TEXT NOT NULL,
ADD COLUMN     "dob" TIMESTAMP(3) NOT NULL,
ADD COLUMN     "experience" TEXT NOT NULL,
ADD COLUMN     "firstName" TEXT NOT NULL,
ADD COLUMN     "history" TEXT NOT NULL,
ADD COLUMN     "lastName" TEXT NOT NULL,
ADD COLUMN     "objectives" TEXT NOT NULL,
ADD COLUMN     "support" TEXT,
ADD COLUMN     "village" TEXT NOT NULL;
