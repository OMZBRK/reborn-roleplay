package fr.reborn.hud.byakugan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Byakugan (lot 5, étape B, 5e tour du 10/11) : pendant la vision, <b>les feuilles sont pleines</b> (comme en graphismes
 * « rapides ») et <b>les plantes basses cachées</b> (herbes, fougères, fleurs, cultures, pousses, plantes croisées, canne
 * à sucre, lianes qui pendent ; le fluide d'un bloc noyé reste). Raison : une texture découpée laisse un trou dans la
 * profondeur à chaque texel transparent ; l'encre, tirée de la profondeur, en faisait de la dentelle. Un houppier plein
 * est une masse de blocs, cernée net et continue bloc par bloc ; une plante cachée ne fait aucun bruit (le Byakugan
 * voit au travers, et rien ne coûte au filtre).
 *
 * <p>Ni l'option « Feuilles découpées » du joueur ni {@code options.txt} ne sont touchées : le compilateur des chunks
 * demande {@code ModelBlockRenderer.forceOpaque} et lit la forme de rendu du bloc, deux crochets qui lisent
 * {@link #actif()} ({@code ModelBlockRendererByakuganMixin}, {@code SectionCompilerByakuganMixin}) ; seul le drapeau statique
 * de {@link LeavesBlock} (faces cachées entre feuilles voisines) est posé, puis rendu à l'option du joueur. Un plantage en
 * pleine vision ne laisse donc rien de changé.
 *
 * <p>Bascule : une fois à l'ouverture, une fois à la fin du fondu de sortie (et à la coupure nette du KO, à la
 * déconnexion) ; seules les sections à portée qui portent une feuille ou une plante (palette de la section, sans lire ses
 * blocs) sont marquées sales : même chemin qu'un bloc changé par le serveur, recompilées en tâche de fond, l'ancien
 * maillage reste affiché jusque-là (aucun trou). À la fermeture, elles le sont par anneaux, du plus proche au plus
 * lointain, sur ~0,35 s (la saccade ne tombe plus sur quelques images). Un monde chargé pendant la vision (changement de
 * dimension) est compilé d'emblée dans l'état en cours : rien à refaire.
 */
public final class FeuillagePlein {

    private static volatile boolean actif;
    /** Reconstruction en cours : prochain anneau à marquer (-1 : rien), son centre, son rayon et son monde (null sinon). */
    private static int anneau = -1, cx, cz, rayon;
    private static ClientLevel niveau;
    /** Blocs dont le maillage change pendant la vision (sans regarder {@link #actif}). */
    private static final Predicate<BlockState> CONCERNE = s -> s.getBlock() instanceof LeavesBlock || estPlante(s.getBlock());

    private FeuillagePlein() {}

    /** Feuilles pleines et plantes cachées en ce moment ? (lu par les fils de compilation des chunks) */
    public static boolean actif() { return actif; }

    /** Ce bloc est-il une feuille à rendre pleine ? */
    public static boolean feuillePleine(BlockState s) {
        return actif && s.getBlock() instanceof LeavesBlock;
    }

    /** Ce bloc est-il une plante basse à cacher ? */
    public static boolean plante(BlockState s) {
        return actif && estPlante(s.getBlock());
    }

    private static boolean estPlante(Block b) {
        return b instanceof VegetationBlock || b instanceof SugarCaneBlock || b instanceof GrowingPlantBlock;
    }

    /**
     * À chaque tick : suit le filtre voulu ({@code voulu}) et reconstruit les chunks quand l'état change. À l'ouverture,
     * tout d'un coup (l'encre en a besoin tout de suite) ; à la fermeture, par anneaux de chunks autour de la caméra, du plus
     * proche au plus lointain, deux par tick : la recompilation s'étale sur ~0,35 s au lieu de tomber sur quelques images
     * (le filtre est déjà parti : un houppier lointain qui reprend ses trous un instant plus tard ne se voit pas).
     */
    static void suivre(Minecraft mc, boolean voulu) {
        ClientLevel l = mc.level;
        if (l == null) voulu = false;
        if (niveau != null && l != niveau) fini();
        if (voulu != actif) {
            actif = voulu;
            LeavesBlock.setCutoutLeaves(!voulu && mc.options.cutoutLeaves().get());
            if (l == null) {
                fini();
                return;
            }
            niveau = l;
            SectionPos c = SectionPos.of(mc.gameRenderer.mainCamera().position());
            cx = c.x();
            cz = c.z();
            rayon = mc.options.getEffectiveRenderDistance() + 1;
            anneau = 0;
        }
        if (anneau < 0) return;
        for (int n = voulu ? rayon + 1 : 2; n > 0 && anneau <= rayon; n--, anneau++) salir(mc, l, anneau);
        if (anneau > rayon) fini();
    }

    /** Plus rien à reconstruire (et aucune référence gardée au monde). */
    private static void fini() {
        anneau = -1;
        niveau = null;
    }

    /** Marque sales les sections à feuilles ou plantes de l'anneau r (distance de Tchebychev en chunks) autour de (cx, cz). */
    private static void salir(Minecraft mc, ClientLevel l, int r) {
        for (int x = cx - r; x <= cx + r; x++) {
            boolean bordX = x == cx - r || x == cx + r;
            for (int z = cz - r; z <= cz + r; z += bordX || r == 0 ? 1 : 2 * r) {
                LevelChunk ch = l.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (ch == null) continue;
                LevelChunkSection[] sections = ch.getSections();
                for (int i = 0; i < sections.length; i++) {
                    if (!sections[i].hasOnlyAir() && sections[i].maybeHas(CONCERNE)) {
                        mc.levelExtractor.setSectionDirty(x, l.getSectionYFromSectionIndex(i), z);
                    }
                }
            }
        }
    }
}
