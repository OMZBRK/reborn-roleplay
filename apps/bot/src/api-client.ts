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

export interface WhitelistDecisionResponse {
  id: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "NEEDS_REVISION";
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

export async function decideWhitelist(
  applicationId: string,
  status: "APPROVED" | "REJECTED" | "NEEDS_REVISION",
  reviewNotes?: string,
): Promise<WhitelistDecisionResponse> {
  return patchSigned(`/staff/whitelist/${applicationId}`, {
    status,
    reviewNotes,
  });
}

export async function setTicketStatus(
  ticketId: string,
  status: "IN_PROGRESS" | "RESOLVED" | "CLOSED",
): Promise<TicketStatusResponse> {
  return patchSigned(`/staff/tickets/${ticketId}`, { status });
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

async function patchSigned<T>(path: string, payload: object): Promise<T> {
  const body = JSON.stringify(payload);
  const signature = createHmac("sha256", config.webhookSecret)
    .update(body)
    .digest("hex");
  const url = `${config.apiBaseUrl.replace(/\/$/, "")}${path}`;
  const res = await fetch(url, {
    method: "PATCH",
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
  return (await res.json()) as T;
}
