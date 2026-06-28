import * as net from 'node:net';

// Implémentation du Server List Ping (SLP) "moderne" de Minecraft (1.7+) en
// TCP brut via le module `net` de Node — pas de dépendance npm. Le protocole :
//   1. Handshake (next state = 1)  : protocolVersion, host, port, nextState
//   2. Status Request               : paquet vide (id 0x00)
//   3. Status Response              : JSON {version, players, description, ...}
//   4. (option) Ping/Pong           : pour mesurer la latence (RTT)
// Chaque paquet est préfixé par sa longueur en VarInt.
//
// Référence : https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping

// Protocole MC 1.21.1. La valeur n'est qu'indicative pour le handshake — le
// serveur répond au status quelle que soit la version annoncée, donc on parse
// la réponse de façon défensive sans s'appuyer sur ce nombre.
const PROTOCOL_VERSION = 767;

export interface RawServerStatus {
  version?: { name?: string; protocol?: number };
  players?: { online?: number; max?: number; sample?: unknown };
  description?: unknown;
  favicon?: string;
}

// DTO public renvoyé par GET /v1/server/status (camelCase).
export interface ServerStatusDto {
  online: boolean;
  players: { online: number; max: number };
  version: string | null;
  motd: string | null;
  latencyMs: number | null;
  fetchedAt: string;
}

// ---------------------------------------------------------------------------
// VarInt (entier variable little-endian 7 bits) — encodage/décodage pur.
// ---------------------------------------------------------------------------

export function writeVarInt(value: number): Buffer {
  const bytes: number[] = [];
  let v = value >>> 0; // traite comme unsigned 32 bits
  do {
    let temp = v & 0x7f;
    v >>>= 7;
    if (v !== 0) temp |= 0x80;
    bytes.push(temp);
  } while (v !== 0);
  return Buffer.from(bytes);
}

export function readVarInt(
  buffer: Buffer,
  offset = 0,
): { value: number; size: number } {
  let value = 0;
  let size = 0;
  let byte: number;
  do {
    if (offset + size >= buffer.length) {
      throw new Error('VarInt incomplet : buffer trop court');
    }
    byte = buffer[offset + size];
    value |= (byte & 0x7f) << (7 * size);
    size += 1;
    if (size > 5) {
      throw new Error('VarInt trop long (max 5 octets)');
    }
  } while ((byte & 0x80) !== 0);
  return { value, size };
}

function writeString(str: string): Buffer {
  const strBuf = Buffer.from(str, 'utf8');
  return Buffer.concat([writeVarInt(strBuf.length), strBuf]);
}

// Préfixe le corps d'un paquet par sa longueur totale en VarInt.
function framePacket(body: Buffer): Buffer {
  return Buffer.concat([writeVarInt(body.length), body]);
}

// ---------------------------------------------------------------------------
// Construction des paquets sortants.
// ---------------------------------------------------------------------------

export function buildHandshakePacket(host: string, port: number): Buffer {
  const body = Buffer.concat([
    writeVarInt(0x00), // Packet ID handshake
    writeVarInt(PROTOCOL_VERSION),
    writeString(host),
    (() => {
      const p = Buffer.alloc(2);
      p.writeUInt16BE(port & 0xffff, 0);
      return p;
    })(),
    writeVarInt(1), // Next state = 1 (status)
  ]);
  return framePacket(body);
}

export function buildStatusRequestPacket(): Buffer {
  return framePacket(writeVarInt(0x00)); // Packet ID 0x00, corps vide
}

export function buildPingPacket(payload: bigint): Buffer {
  const body = Buffer.alloc(9);
  body.writeUInt8(0x01, 0); // Packet ID ping
  body.writeBigInt64BE(payload, 1);
  return framePacket(body);
}

// ---------------------------------------------------------------------------
// Décodage de la réponse — fonctions pures, testables sans socket.
// ---------------------------------------------------------------------------

/**
 * Tente de décoder un paquet Status Response complet depuis le buffer
 * accumulé. Renvoie `null` si le paquet n'est pas encore entièrement reçu
 * (il faut attendre plus de données du socket). Lève si le JSON est invalide.
 */
export function tryDecodeStatusResponse(buffer: Buffer): RawServerStatus | null {
  let outerLen: { value: number; size: number };
  try {
    outerLen = readVarInt(buffer, 0);
  } catch {
    return null; // même le VarInt de longueur est incomplet
  }
  const packetLength = outerLen.value;
  let offset = outerLen.size;
  // Le paquet complet est présent dès qu'on a `packetLength` octets après le
  // préfixe de longueur. Tant que non, on attend.
  if (buffer.length - offset < packetLength) {
    return null;
  }
  // Packet ID (doit être 0x00 pour Status Response).
  const idRes = readVarInt(buffer, offset);
  offset += idRes.size;
  // Longueur de la chaîne JSON.
  const jsonLen = readVarInt(buffer, offset);
  offset += jsonLen.size;
  const jsonBytes = buffer.subarray(offset, offset + jsonLen.value);
  const json = JSON.parse(jsonBytes.toString('utf8')) as RawServerStatus;
  return json;
}

// Vrai dès qu'un paquet complet (quel que soit son contenu) est dans le
// buffer — utilisé pour détecter l'arrivée du Pong.
export function isCompletePacket(buffer: Buffer): boolean {
  try {
    const { value, size } = readVarInt(buffer, 0);
    return buffer.length - size >= value;
  } catch {
    return false;
  }
}

function stripColorCodes(s: string): string {
  // Codes Minecraft : § suivi d'un chiffre/lettre de couleur ou de style.
  return s.replace(/§[0-9a-fk-or]/gi, '');
}

/**
 * Aplatit la `description` (MOTD) en texte brut. Elle peut être une simple
 * chaîne, un chat component `{ text, extra: [...] }`, ou une imbrication des
 * deux. On retire les codes couleur et on normalise les espaces.
 */
export function flattenMotd(description: unknown): string | null {
  if (description == null) return null;

  const parts: string[] = [];
  const visit = (node: unknown): void => {
    if (node == null) return;
    if (typeof node === 'string') {
      parts.push(node);
      return;
    }
    if (Array.isArray(node)) {
      node.forEach(visit);
      return;
    }
    if (typeof node === 'object') {
      const obj = node as Record<string, unknown>;
      if (typeof obj.text === 'string') parts.push(obj.text);
      if (Array.isArray(obj.extra)) obj.extra.forEach(visit);
    }
  };
  visit(description);

  const text = stripColorCodes(parts.join(''))
    .replace(/\s+/g, ' ')
    .trim();
  return text.length > 0 ? text : null;
}

export function mapStatus(
  raw: RawServerStatus | null,
  latencyMs: number | null,
  fetchedAt: string,
): ServerStatusDto {
  const online = raw?.players?.online;
  const max = raw?.players?.max;
  return {
    online: true,
    players: {
      online: typeof online === 'number' ? online : 0,
      max: typeof max === 'number' ? max : 0,
    },
    version: typeof raw?.version?.name === 'string' ? raw.version.name : null,
    motd: flattenMotd(raw?.description),
    latencyMs,
    fetchedAt,
  };
}

export function offlineStatus(fetchedAt: string): ServerStatusDto {
  return {
    online: false,
    players: { online: 0, max: 0 },
    version: null,
    motd: null,
    latencyMs: null,
    fetchedAt,
  };
}

// ---------------------------------------------------------------------------
// Ping réel via socket TCP. Ne lève jamais : renvoie un DTO offline en cas
// d'erreur/timeout.
// ---------------------------------------------------------------------------

export function pingServer(
  host: string,
  port: number,
  timeoutMs = 3000,
): Promise<ServerStatusDto> {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    let buffer = Buffer.alloc(0);
    let statusReceived = false;
    let pendingRaw: RawServerStatus | null = null;
    let pingSentAt = 0;
    let settled = false;

    const now = () => new Date().toISOString();

    const finish = (dto: ServerStatusDto): void => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve(dto);
    };

    // Si on a déjà le status mais que le Pong n'arrive pas (serveur muet sur
    // le ping), on renvoie quand même le status — latence inconnue.
    const finishWithWhatWeHave = (): void => {
      if (statusReceived) {
        finish(mapStatus(pendingRaw, null, now()));
      } else {
        finish(offlineStatus(now()));
      }
    };

    socket.setTimeout(timeoutMs);
    socket.on('timeout', finishWithWhatWeHave);
    socket.on('error', finishWithWhatWeHave);
    socket.on('close', finishWithWhatWeHave);

    socket.connect(port, host, () => {
      socket.write(buildHandshakePacket(host, port));
      socket.write(buildStatusRequestPacket());
    });

    socket.on('data', (chunk: Buffer) => {
      buffer = Buffer.concat([buffer, chunk]);

      if (!statusReceived) {
        let raw: RawServerStatus | null;
        try {
          raw = tryDecodeStatusResponse(buffer);
        } catch {
          // JSON malformé / réponse inattendue → considéré offline.
          finish(offlineStatus(now()));
          return;
        }
        if (raw === null) return; // paquet incomplet, on attend la suite
        statusReceived = true;
        pendingRaw = raw;
        buffer = Buffer.alloc(0); // reset pour lire le Pong
        pingSentAt = Date.now();
        socket.write(buildPingPacket(BigInt(pingSentAt)));
        return;
      }

      // Phase Pong : dès qu'un paquet complet arrive, c'est notre Pong.
      if (isCompletePacket(buffer)) {
        const latency = Date.now() - pingSentAt;
        finish(mapStatus(pendingRaw, latency, now()));
      }
    });
  });
}
