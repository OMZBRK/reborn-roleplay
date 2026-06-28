import {
  flattenMotd,
  mapStatus,
  offlineStatus,
  RawServerStatus,
  readVarInt,
  tryDecodeStatusResponse,
  writeVarInt,
} from './slp';

// Construit un paquet Status Response complet (comme le ferait un serveur MC)
// pour un objet status donné : VarInt(len) | VarInt(0x00) | VarInt(jsonLen) | json.
function buildStatusResponsePacket(status: unknown): Buffer {
  const jsonBuf = Buffer.from(JSON.stringify(status), 'utf8');
  const body = Buffer.concat([
    writeVarInt(0x00), // Packet ID Status Response
    writeVarInt(jsonBuf.length),
    jsonBuf,
  ]);
  return Buffer.concat([writeVarInt(body.length), body]);
}

describe('SLP parser', () => {
  describe('VarInt', () => {
    it('round-trips small and multi-byte values', () => {
      for (const v of [0, 1, 127, 128, 300, 767, 25565, 2097151]) {
        const buf = writeVarInt(v);
        expect(readVarInt(buf, 0).value).toBe(v);
      }
    });
  });

  describe('tryDecodeStatusResponse', () => {
    const sample = {
      version: { name: '1.21.1', protocol: 767 },
      players: { online: 7, max: 100, sample: [] },
      description: { text: 'Reborn §aRoleplay' },
      favicon: 'data:image/png;base64,AAAA',
    };

    it('decodes a complete status response buffer to the raw status', () => {
      const packet = buildStatusResponsePacket(sample);
      const raw = tryDecodeStatusResponse(packet);
      expect(raw).not.toBeNull();
      expect(raw?.players?.online).toBe(7);
      expect(raw?.players?.max).toBe(100);
      expect(raw?.version?.name).toBe('1.21.1');
    });

    it('returns null when the packet is not fully received yet', () => {
      const packet = buildStatusResponsePacket(sample);
      // On coupe le buffer au milieu : le décodeur doit attendre plus de data.
      const partial = packet.subarray(0, packet.length - 5);
      expect(tryDecodeStatusResponse(partial)).toBeNull();
    });

    it('maps a decoded status response to the public DTO', () => {
      const packet = buildStatusResponsePacket(sample);
      const raw = tryDecodeStatusResponse(packet);
      const fetchedAt = '2026-06-28T10:00:00.000Z';
      const dto = mapStatus(raw, 42, fetchedAt);

      expect(dto).toEqual({
        online: true,
        players: { online: 7, max: 100 },
        version: '1.21.1',
        motd: 'Reborn Roleplay', // codes couleur §a retirés
        latencyMs: 42,
        fetchedAt,
      });
    });
  });

  describe('flattenMotd', () => {
    it('handles a plain string description', () => {
      expect(flattenMotd('§6Hello §rworld')).toBe('Hello world');
    });

    it('handles a chat component with nested extra', () => {
      const desc = {
        text: 'A ',
        extra: [{ text: 'B ' }, 'C ', { text: 'D', extra: [' E'] }],
      };
      // Les chat components se concatènent verbatim (pas d'espace injecté).
      expect(flattenMotd(desc)).toBe('A B C D E');
    });

    it('returns null for empty / nullish descriptions', () => {
      expect(flattenMotd(undefined)).toBeNull();
      expect(flattenMotd(null)).toBeNull();
      expect(flattenMotd('   ')).toBeNull();
    });
  });

  describe('mapStatus defensiveness', () => {
    it('defaults missing fields without throwing', () => {
      const raw: RawServerStatus = {};
      const dto = mapStatus(raw, null, '2026-06-28T10:00:00.000Z');
      expect(dto.online).toBe(true);
      expect(dto.players).toEqual({ online: 0, max: 0 });
      expect(dto.version).toBeNull();
      expect(dto.motd).toBeNull();
      expect(dto.latencyMs).toBeNull();
    });
  });

  describe('offlineStatus', () => {
    it('returns a well-formed offline DTO', () => {
      const fetchedAt = '2026-06-28T10:00:00.000Z';
      expect(offlineStatus(fetchedAt)).toEqual({
        online: false,
        players: { online: 0, max: 0 },
        version: null,
        motd: null,
        latencyMs: null,
        fetchedAt,
      });
    });
  });
});
