import { ConflictException } from '@nestjs/common';
import { Prisma } from '@prisma/client';

/**
 * Verrous de ligne (SELECT … FOR UPDATE) pris dans une transaction
 * interactive Prisma. Postgres les garde jusqu'à la fin de la transaction :
 * une seconde transaction qui demande la même ligne attend, puis relit les
 * données validées par la première (READ COMMITTED). C'est ce qui rend
 * atomiques « une seule réservation active par compte » et « capacité
 * respectée », sans index partiel (Prisma ne sait pas le décrire).
 *
 * Ordre fixe pour éviter les interblocages : la candidature d'abord, la
 * séance ensuite.
 */

/** Sérialise tout ce qui touche à l'oral d'un compte : réservation, changement de séance, décision. */
export async function verrouillerCandidature(
  tx: Prisma.TransactionClient,
  applicationId: string,
): Promise<void> {
  await tx.$queryRaw`SELECT "id" FROM "WhitelistApplication" WHERE "id" = ${applicationId} FOR UPDATE`;
}

/** Sérialise le décompte des places d'une séance et ses changements d'état. */
export async function verrouillerSeance(
  tx: Prisma.TransactionClient,
  seanceId: string,
): Promise<void> {
  await tx.$queryRaw`SELECT "id" FROM "DouaneSeance" WHERE "id" = ${seanceId} FOR UPDATE`;
}

/** Options des transactions de la douane : un peu d'attente permise sous la charge d'une ouverture de séance. */
export const OPTIONS_TRANSACTION = { maxWait: 5_000, timeout: 10_000 } as const;

/**
 * Conflit d'écriture ou interblocage (P2034), ou attente de verrou trop
 * longue (P2028) : on renvoie un 409 lisible plutôt qu'une erreur interne.
 * Toute autre erreur (dont les refus métier déjà en 4xx) repart telle quelle.
 */
export function traduireConflitTransaction(err: unknown): never {
  if (
    err instanceof Prisma.PrismaClientKnownRequestError &&
    (err.code === 'P2034' || err.code === 'P2028')
  ) {
    throw new ConflictException(
      'Beaucoup de demandes en même temps : réessaie dans un instant.',
    );
  }
  throw err;
}
