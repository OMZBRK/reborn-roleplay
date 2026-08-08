package fr.reborn.hud.editor;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.ui.style.RebornColors;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Détecte et rend les guides d'alignement type Figma quand on drag une
 * box dans l'éditeur HUD.
 *
 * <p>Snap si l'un des candidates suivants se retrouve à moins de
 * {@link #SNAP_THRESHOLD_PX} pixels de la box draggée :
 * <ul>
 *   <li>centre X / Y de l'écran</li>
 *   <li>bords gauche / droit / haut / bas de l'écran</li>
 *   <li>centre X / Y de chaque autre box visible</li>
 *   <li>bord gauche / droit / haut / bas de chaque autre box</li>
 * </ul>
 *
 * <p>Quand un snap a lieu, on rend une ligne rouge vif {@code #ef4444} pleine
 * hauteur (vertical) ou pleine largeur (horizontal) avec un petit pill
 * label en haut décrivant la nature du snap.
 *
 * <p>Le snap est désactivé si Shift est pressé pendant le drag — le user
 * veut un positionnement libre pixel-precis.
 */
public final class AlignmentGuides {

    public static final int SNAP_THRESHOLD_PX = 4;

    public record SnapResult(int newX, int newY, List<Guide> guides) {}

    public record Guide(boolean vertical, int pos, String label) {}

    private AlignmentGuides() {}

    /**
     * Calcule snap + guides pour une box draggée. Retourne la position
     * snappée (ou inchangée si shiftHeld), et la liste des guides à
     * dessiner.
     *
     * @param targetX  position cible top-left X de la box draggée
     * @param targetY  position cible top-left Y de la box draggée
     * @param boxW     largeur de la box draggée
     * @param boxH     hauteur de la box draggée
     * @param dragged  élément en cours de drag (exclu des candidats)
     * @param boundsFn fonction qui renvoie les bounds courants d'un élément
     * @param screenW  largeur écran
     * @param screenH  hauteur écran
     * @param shiftHeld true → snap désactivé
     */
    public static SnapResult compute(int targetX, int targetY, int boxW, int boxH,
                                     HudElement dragged,
                                     Function<HudElement, HudElementBounds> boundsFn,
                                     int screenW, int screenH, boolean shiftHeld) {
        if (shiftHeld) {
            return new SnapResult(targetX, targetY, List.of());
        }

        // Candidates Y (verticales) → snap sur X
        List<Candidate> xCandidates = new ArrayList<>();
        xCandidates.add(new Candidate(screenW / 2, "CENTRE ÉCRAN"));
        xCandidates.add(new Candidate(0,           "BORD GAUCHE"));
        xCandidates.add(new Candidate(screenW,     "BORD DROIT"));

        List<Candidate> yCandidates = new ArrayList<>();
        yCandidates.add(new Candidate(screenH / 2, "CENTRE ÉCRAN"));
        yCandidates.add(new Candidate(0,           "BORD HAUT"));
        yCandidates.add(new Candidate(screenH,     "BORD BAS"));

        for (HudElement other : HudElement.EDITABLE) {
            if (other == dragged) continue;
            HudElementBounds b = boundsFn.apply(other);
            String name = other.displayName().toUpperCase();
            xCandidates.add(new Candidate(b.centerX(), "CENTRE " + name));
            xCandidates.add(new Candidate(b.x(),       "GAUCHE " + name));
            xCandidates.add(new Candidate(b.right(),   "DROITE " + name));
            yCandidates.add(new Candidate(b.centerY(), "CENTRE " + name));
            yCandidates.add(new Candidate(b.y(),       "HAUT "   + name));
            yCandidates.add(new Candidate(b.bottom(),  "BAS "    + name));
        }

        int snapX = targetX;
        int snapY = targetY;
        List<Guide> guides = new ArrayList<>();

        // Snap X — on teste 3 points de la box draggée : gauche, centre, droite
        int[] refXs = {targetX, targetX + boxW / 2, targetX + boxW};
        SnapHit hitX = findClosest(refXs, xCandidates);
        if (hitX != null) {
            snapX = targetX + (hitX.candidate.pos - refXs[hitX.refIdx]);
            guides.add(new Guide(true, hitX.candidate.pos, hitX.candidate.label));
        }

        int[] refYs = {targetY, targetY + boxH / 2, targetY + boxH};
        SnapHit hitY = findClosest(refYs, yCandidates);
        if (hitY != null) {
            snapY = targetY + (hitY.candidate.pos - refYs[hitY.refIdx]);
            guides.add(new Guide(false, hitY.candidate.pos, hitY.candidate.label));
        }

        return new SnapResult(snapX, snapY, guides);
    }

    /** Renvoie le candidat le plus proche d'un des points de référence, ou null. */
    private static SnapHit findClosest(int[] refs, List<Candidate> candidates) {
        int best = SNAP_THRESHOLD_PX + 1;
        SnapHit hit = null;
        for (int i = 0; i < refs.length; i++) {
            int r = refs[i];
            for (Candidate c : candidates) {
                int d = Math.abs(c.pos - r);
                if (d < best) {
                    best = d;
                    hit = new SnapHit(c, i);
                }
            }
        }
        return hit;
    }

    /** Render les guides détectés dans une SnapResult. */
    public static void renderGuides(GuiGraphicsExtractor ctx, List<Guide> guides, int screenW, int screenH,
                                    net.minecraft.client.gui.Font tr) {
        for (Guide g : guides) {
            if (g.vertical) {
                // Ligne verticale pleine hauteur
                ctx.fill(g.pos - 1, 0, g.pos + 1, screenH, RebornColors.DANGER);
                // Pill label en haut
                int labelW = tr.width(g.label) + 10;
                int labelX = g.pos - labelW / 2;
                int labelY = 24;
                ctx.fill(labelX, labelY, labelX + labelW, labelY + 14, RebornColors.DANGER);
                ctx.text(tr, net.minecraft.network.chat.Component.literal(g.label),
                    labelX + 5, labelY + 3, 0xFFFFFFFF, false);
            } else {
                // Ligne horizontale pleine largeur
                ctx.fill(0, g.pos - 1, screenW, g.pos + 1, RebornColors.DANGER);
                int labelW = tr.width(g.label) + 10;
                int labelX = 24;
                int labelY = g.pos - 7;
                ctx.fill(labelX, labelY, labelX + labelW, labelY + 14, RebornColors.DANGER);
                ctx.text(tr, net.minecraft.network.chat.Component.literal(g.label),
                    labelX + 5, labelY + 3, 0xFFFFFFFF, false);
            }
        }
    }

    private record Candidate(int pos, String label) {}
    private record SnapHit(Candidate candidate, int refIdx) {}
}
