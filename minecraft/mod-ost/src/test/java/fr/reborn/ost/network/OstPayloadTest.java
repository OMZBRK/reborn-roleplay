package fr.reborn.ost.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du codec {@link OstPayload#CODEC} — round-trip encode/decode et
 * gestion d'un type byte inconnu.
 *
 * <p>NB : {@link PacketByteBuf} repose sur Netty {@link Unpooled}. Le
 * jar netty est dans le classpath dev grâce à Fabric Loom.
 */
class OstPayloadTest {

    private static PacketByteBuf newBuf() {
        return new PacketByteBuf(Unpooled.buffer());
    }

    @Test
    void roundTripPlayAtPosition() {
        OstPayload original = new OstPayload.PlayAtPosition(
            12.5, 64.0, -33.25, 48.0f, "combat/duel-1", 0.85f);

        PacketByteBuf buf = newBuf();
        OstPayload.CODEC.encode(buf, original);
        OstPayload decoded = OstPayload.CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "buf doit être consommé");

        assertInstanceOf(OstPayload.PlayAtPosition.class, decoded);
        OstPayload.PlayAtPosition p = (OstPayload.PlayAtPosition) decoded;
        assertEquals(12.5, p.x());
        assertEquals(64.0, p.y());
        assertEquals(-33.25, p.z());
        assertEquals(48.0f, p.radius());
        assertEquals("combat/duel-1", p.trackId());
        assertEquals(0.85f, p.volume());
    }

    @Test
    void roundTripStopBroadcast() {
        OstPayload original = new OstPayload.StopBroadcast();
        PacketByteBuf buf = newBuf();
        OstPayload.CODEC.encode(buf, original);
        assertEquals(1, buf.readableBytes(), "STOP_BROADCAST = 1 byte (type seul)");
        OstPayload decoded = OstPayload.CODEC.decode(buf);
        assertInstanceOf(OstPayload.StopBroadcast.class, decoded);
    }

    @Test
    void roundTripPlayGlobal() {
        OstPayload original = new OstPayload.PlayGlobal("mission/annonce-event", 1.0f);
        PacketByteBuf buf = newBuf();
        OstPayload.CODEC.encode(buf, original);
        OstPayload decoded = OstPayload.CODEC.decode(buf);
        assertInstanceOf(OstPayload.PlayGlobal.class, decoded);
        OstPayload.PlayGlobal p = (OstPayload.PlayGlobal) decoded;
        assertEquals("mission/annonce-event", p.trackId());
        assertEquals(1.0f, p.volume());
    }

    @Test
    void unknownTypeCodeThrows() {
        PacketByteBuf buf = newBuf();
        buf.writeByte(0x99); // code invalide
        assertThrows(IllegalArgumentException.class, () -> OstPayload.CODEC.decode(buf));
    }

    @Test
    void truncatedBufferDoesNotCrashUnexpectedly() {
        // 0x01 (PLAY_AT) puis seulement 4 bytes au lieu des 8+8+8+4+...
        // PacketByteBuf jette IndexOutOfBoundsException — qui DOIT être
        // attrapée côté caller (OstNetworking.handle wrap-catch).
        PacketByteBuf buf = newBuf();
        buf.writeByte(OstPacketType.PLAY_AT_POSITION.code());
        buf.writeInt(42);
        assertThrows(IndexOutOfBoundsException.class, () -> OstPayload.CODEC.decode(buf));
    }
}
