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
