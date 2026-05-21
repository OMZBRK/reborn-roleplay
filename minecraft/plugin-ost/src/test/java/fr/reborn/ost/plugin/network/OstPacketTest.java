package fr.reborn.ost.plugin.network;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests des encodeurs {@link OstPacket}.
 *
 * <p>On vérifie l'output binaire byte-by-byte pour garantir la
 * compatibilité avec le décodage côté mod ({@code OstPayload.CODEC}).
 * Toute divergence se traduirait par un broadcast silencieusement
 * ignoré côté client (warning log only).
 */
class OstPacketTest {

    @Test
    void playAtPositionEncodesExpectedBytes() {
        byte[] out = OstPacket.playAtPosition(1.0, 2.0, 3.0, 16.0f, "ab", 0.5f);

        ByteBuffer buf = ByteBuffer.wrap(out);
        assertEquals(OstPacket.TYPE_PLAY_AT_POSITION, buf.get());
        assertEquals(1.0, buf.getDouble());
        assertEquals(2.0, buf.getDouble());
        assertEquals(3.0, buf.getDouble());
        assertEquals(16.0f, buf.getFloat());
        // string : VarInt(2) + 'a','b'
        assertEquals(2, buf.get()); // 2 octets UTF-8, fits in 7 bits → 1 byte VarInt
        assertEquals('a', buf.get());
        assertEquals('b', buf.get());
        assertEquals(0.5f, buf.getFloat());
        assertFalse(buf.hasRemaining());
    }

    @Test
    void stopBroadcastIsExactlyOneByte() {
        byte[] out = OstPacket.stopBroadcast();
        assertEquals(1, out.length);
        assertEquals(OstPacket.TYPE_STOP_BROADCAST, out[0]);
    }

    @Test
    void playGlobalEncodesExpectedBytes() {
        byte[] out = OstPacket.playGlobal("mission/event-x", 1.0f);
        ByteBuffer buf = ByteBuffer.wrap(out);
        assertEquals(OstPacket.TYPE_PLAY_GLOBAL, buf.get());
        // string "mission/event-x" = 15 bytes UTF-8.
        int strLen = buf.get() & 0xFF;
        assertEquals(15, strLen);
        byte[] strBytes = new byte[15];
        buf.get(strBytes);
        assertEquals("mission/event-x", new String(strBytes, StandardCharsets.UTF_8));
        assertEquals(1.0f, buf.getFloat());
        assertFalse(buf.hasRemaining());
    }

    @Test
    void varIntHandlesLargeStringLength() {
        // String de 200 chars ASCII = 200 bytes UTF-8 — la longueur 200
        // dépasse 127 donc le VarInt fait 2 bytes (0xC8 0x01).
        String big = "x".repeat(200);
        byte[] out = OstPacket.playGlobal(big, 0.0f);
        ByteBuffer buf = ByteBuffer.wrap(out);
        assertEquals(OstPacket.TYPE_PLAY_GLOBAL, buf.get());
        int byte1 = buf.get() & 0xFF;
        int byte2 = buf.get() & 0xFF;
        assertEquals(0xC8, byte1, "continuation bit + low 7 bits of 200");
        assertEquals(0x01, byte2, "high 7 bits of 200");
        byte[] strBytes = new byte[200];
        buf.get(strBytes);
        assertEquals(big, new String(strBytes, StandardCharsets.UTF_8));
        assertEquals(0.0f, buf.getFloat());
    }

    @Test
    void utf8MultibyteCharactersCountedCorrectly() {
        // "é" = 2 bytes UTF-8 (0xC3 0xA9)
        byte[] out = OstPacket.playGlobal("é", 0.0f);
        ByteBuffer buf = ByteBuffer.wrap(out);
        assertEquals(OstPacket.TYPE_PLAY_GLOBAL, buf.get());
        assertEquals(2, buf.get() & 0xFF, "VarInt(2) car 'é' = 2 bytes UTF-8");
        assertEquals((byte) 0xC3, buf.get());
        assertEquals((byte) 0xA9, buf.get());
    }
}
