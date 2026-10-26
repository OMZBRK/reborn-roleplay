import { createHmac } from "node:crypto";
import { config } from "./config.js";

/**
 * Client HTTP signe pour appeler les endpoints staff de l'API Reborn.
 * Le bot signe chaque requete avec REBORN_WEBHOOK_SECRET ; cote API,
 * le HmacSignatureGuard verifie le header X-Reborn-Signature.
 */

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

/** Statut d'une candidature ou d'une de ses parties (enum AppStatus de l'API). */
export type AppStatus = "PENDING" | "APPROVED" | "REJECTED" | "NEEDS_REVISION";

/** Les deux parties de l'ÉCRIT, tranchées séparément (CANDIDATURE_ET_DOUANE.md §1.3). */
export type PartieEcrit = "HRP" | "RP";

/** Décision du staff sur une partie (PENDING n'en est pas une). */
export type DecisionEcrit = Exclude<AppStatus, "PENDING">;

/**
 * Réponse de la décision par partie (même service que le panel). Les champs
 * marqués facultatifs servent à mettre à jour les boutons ; s'ils manquent, le
 * bot laisse les boutons tels quels.
 */
export interface WhitelistPartDecisionResponse {
  id: string;
  /** Statut global : APPROVED seulement après l'oral (ou oral déjà passé sur ce compte). */
  status: AppStatus;
  hrpStatus?: AppStatus;
  rpStatus?: AppStatus;
  oralStatus?: AppStatus;
  /** Renseigné quand HRP et RP sont validés (« écrit accepté », douane à passer). */
  writtenApprovedAt?: string | null;
  reviewedAt: string | null;
  reviewNotes: string | null;
  userId: string;
}

export interface TicketStatusResponse {
  id: string;
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
  userId: string;
  subject: string;
}

/**
 * Décision sur UNE partie de l'écrit (HRP ou RP), au nom du staff Discord
 * `actorDiscordId` : l'API retrouve son compte Reborn lié, contrôle son rôle
 * et inscrit la décision à l'audit. Même service que le panel
 * (PATCH /admin/whitelist/:id/decision). Le commentaire est VISIBLE du
 * candidat ; l'API l'exige pour « À revoir » et « Refusé ».
 *
 * Aucune décision globale (HRP et RP d'un coup) ne part plus du bot.
 */
export async function decideWhitelistPart(
  applicationId: string,
  decision: {
    part: PartieEcrit;
    status: DecisionEcrit;
    actorDiscordId: string;
    reviewNotes?: string;
  },
): Promise<WhitelistPartDecisionResponse> {
  return sendSigned("PATCH", `/staff/whitelist/${applicationId}/decision`, {
    part: decision.part,
    status: decision.status,
    actorDiscordId: decision.actorDiscordId,
    ...(decision.reviewNotes ? { reviewNotes: decision.reviewNotes } : {}),
  });
}

export async function setTicketStatus(
  ticketId: string,
  status: "IN_PROGRESS" | "RESOLVED" | "CLOSED",
): Promise<TicketStatusResponse> {
  return sendSigned("PATCH", `/staff/tickets/${ticketId}`, { status });
}

/**
 * Vrai si l'API ne connaît pas la route appelée (API pas encore à jour). À ne
 * pas confondre avec un 404 métier (« Candidature introuvable. ») : NestJS
 * répond alors « Cannot PATCH /v1/… ».
 */
export function routeAbsente(err: unknown): boolean {
  return (
    err instanceof ApiError &&
    err.status === 404 &&
    /^Cannot (GET|POST|PUT|PATCH|DELETE) \//.test(err.message)
  );
}

/** Message d'échec d'un appel à l'API, lisible par le staff. */
export function messageEchec(err: unknown): string {
  if (routeAbsente(err)) {
    return "l'API n'accepte pas encore la décision par partie depuis Discord. Décide depuis le panel en attendant sa mise à jour.";
  }
  return err instanceof ApiError ? `API ${err.status} : ${err.message}` : String(err);
}

export interface AssignmentResponse {
  kind: "whitelist" | "ticket";
  id: string;
  assignee: {
    id: string;
    minecraftUsername: string;
    discordUsername: string | null;
  } | null;
  assignedAt: string | null;
}

/** Claim / release flows pour le bouton "Prendre en charge". */
export async function claimAssignment(
  kind: "whitelist" | "ticket",
  id: string,
  discordUserId: string,
  force = false,
): Promise<AssignmentResponse> {
  return sendSigned("POST", `/staff/${kind}/${id}/assign`, {
    discordUserId,
    force,
  });
}

export async function releaseAssignment(
  kind: "whitelist" | "ticket",
  id: string,
  discordUserId: string,
): Promise<AssignmentResponse> {
  return sendSigned("DELETE", `/staff/${kind}/${id}/assign`, {
    discordUserId,
  });
}

export type RebornRole =
  | "PLAYER"
  | "WHITELISTED"
  | "HELPER"
  | "MODELISATEUR"
  | "DEVELOPPEUR"
  | "WHITELIST_REVIEWER"
  | "MODERATOR"
  | "ADMIN"
  | "OWNER";

export interface RoleChangeResponse {
  id: string;
  minecraftUsername: string;
  role: RebornRole;
  previousRole: RebornRole;
  changed: boolean;
}

/** `/role set` : change le rôle d'un joueur. Garde-fous côté API (l'acteur =
 *  actorDiscordId doit être ADMIN+, pas de self/pair/supérieur). */
export async function setPlayerRole(
  actorDiscordId: string,
  minecraftUsername: string,
  role: RebornRole,
): Promise<RoleChangeResponse> {
  return sendSigned("POST", "/staff/role", {
    actorDiscordId,
    minecraftUsername,
    role,
  });
}

async function sendSigned<T>(
  method: "POST" | "DELETE" | "PATCH",
  path: string,
  payload: object,
): Promise<T> {
  const body = JSON.stringify(payload);
  const signature = createHmac("sha256", config.webhookSecret)
    .update(body)
    .digest("hex");
  const url = `${config.apiBaseUrl.replace(/\/$/, "")}${path}`;
  const res = await fetch(url, {
    method,
    headers: {
      "content-type": "application/json",
      "x-reborn-signature": signature,
    },
    body,
    signal: AbortSignal.timeout(8000),
  });
  if (!res.ok) {
    let errorMessage = `${res.status}`;
    try {
      const errorBody = (await res.json()) as { message?: string | string[] };
      const m = errorBody.message;
      errorMessage = Array.isArray(m) ? m.join(", ") : m ?? errorMessage;
    } catch {
      // Reponse pas en JSON — on garde juste le status.
    }
    throw new ApiError(res.status, errorMessage);
  }
  // DELETE peut retourner du contenu (assignment dto) ou rien (204). On
  // garde JSON.parse en best-effort.
  const text = await res.text();
  if (text.length === 0) return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    return undefined as T;
  }
}
