package fr.reborn.ost.plugin.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Encodage wire compatible avec {@code PacketByteBuf} de Minecraft —
 * c'est ce que le mod client utilise pour décoder.
 *
 * <p>Spécificités vs {@link DataOutputStream} standard :
 * <ul>
 *   <li>Strings : préfixées d'un <strong>VarInt</strong> (pas un short
 *       2 bytes comme {@code DataOutputStream.writeUTF}) suivi des bytes
 *       UTF-8 bruts. Cf
 *       {@code PacketByteBuf#writeString} (Mojang yarn).</li>
 *   <li>Primitives (byte/float/double) : big-endian comme DataOutputStream,
 *       donc compatibles tel quel.</li>
 * </ul>
 *
 * <p>Pas de dépendance Netty ici — pur Java pour faciliter les tests
 * unitaires sans démarrer un serveur Paper.
 */
public final class OstWireFormat {

    private OstWireFormat() {}

    /**
     * Crée un nouveau {@link ByteBufferWriter} prêt à recevoir des
     * primitives + strings.
     */
    public static ByteBufferWriter writer() {
        return new ByteBufferWriter();
    }

    /**
     * Builder fluide qui encapsule l'écriture binaire dans un buffer
     * dynamique. Appel {@link #toByteArray()} à la fin.
     */
    public static final class ByteBufferWriter {

        private final ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
        private final DataOutputStream dos = new DataOutputStream(baos);

        private ByteBufferWriter() {}

        public ByteBufferWriter writeByte(int v) {
            try { dos.writeByte(v); } catch (IOException e) { throw bug(e); }
            return this;
        }

        public ByteBufferWriter writeFloat(float v) {
            try { dos.writeFloat(v); } catch (IOException e) { throw bug(e); }
            return this;
        }

        public ByteBufferWriter writeDouble(double v) {
            try { dos.writeDouble(v); } catch (IOException e) { throw bug(e); }
            return this;
        }

        /**
         * Écrit une string compatible {@code PacketByteBuf#readString} :
         * VarInt(longueur en bytes UTF-8) + bytes UTF-8.
         */
        public ByteBufferWriter writeString(String s) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            writeVarInt(bytes.length);
            try { dos.write(bytes); } catch (IOException e) { throw bug(e); }
            return this;
        }

        /**
         * VarInt MC : 7-bit value + 1-bit continuation, LSB first.
         * Cf {@code PacketByteBuf#writeVarInt}.
         */
        public ByteBufferWriter writeVarInt(int value) {
            try {
                while ((value & ~0x7F) != 0) {
                    dos.writeByte((value & 0x7F) | 0x80);
                    value >>>= 7;
                }
                dos.writeByte(value & 0x7F);
            } catch (IOException e) { throw bug(e); }
            return this;
        }

        public byte[] toByteArray() {
            try { dos.flush(); } catch (IOException e) { throw bug(e); }
            return baos.toByteArray();
        }

        private static IllegalStateException bug(IOException e) {
            // ByteArrayOutputStream ne peut pas réellement throw IOException.
            return new IllegalStateException("ByteArrayOutputStream raised IOException (impossible)", e);
        }
    }
}
