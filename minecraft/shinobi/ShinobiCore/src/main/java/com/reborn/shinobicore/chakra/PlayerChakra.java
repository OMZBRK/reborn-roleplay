package com.reborn.shinobicore.chakra;

/**
 * @deprecated Chakra is no longer stored per-player; it lives on
 *             {@code ShinobiCharacter}. Use {@link ChakraPool} instead.
 *             This type is kept only so older imports still compile.
 */
@Deprecated(forRemoval = true)
public final class PlayerChakra {
    private PlayerChakra() {}
}
