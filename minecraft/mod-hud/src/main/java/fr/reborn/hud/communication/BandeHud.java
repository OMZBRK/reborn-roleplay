package fr.reborn.hud.communication;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * La bande du haut de l'écran (maquettes v3c 02 et 06, CLOCHE_ET_MISSIVES.md §2.4, §3.4, §3.6) : <b>une bande fine et
 * nette</b>, jamais une plaque — fond sombre uni semi-opaque (≈ 84 %), bords francs, ombre d'un pixel, aucun dégradé ;
 * <b>une seule couleur</b> (celle du type) sur la marque et le titre en petites capitales ; séparateur fin ; texte crème
 * (le gras et l'italique de la zone d'écriture gardés) ; signature en gris ; filet du temps restant neutre en bas.
 * Fondu à l'entrée (0,25 s) et à la sortie (0,4 s). 200 caractères tiennent sur une ligne, deux au plus.
 * Trois sortes : l'annonce de la cloche (or / ambre / vermillon, 10 / 12 / 15 s), « un corbeau a déposé une missive pour
 * toi » et l'alerte du palais (8 s). Elles s'empilent (trois au plus).
 */
public final class BandeHud {

    record Bande(String sorte, String type, String titre, int col, Identifier icone, int iw, int ih,
                 List<CommArt.Seg> texte, String signe, long at, long ms, float prog) {
        float alpha(long now) {
            long age = now - at;
            if (age < 0) return 0f;
            if (age < 250) return age / 250f;
            if (age > ms) return Math.max(0f, 1f - (age - ms) / 400f);
            return 1f;
        }
    }

    private static final List<Bande> BANDES = new ArrayList<>();
    private static final CommArt.Plume PZ = new CommArt.Plume();

    private BandeHud() {}

    static void effacer() { synchronized (BANDES) { BANDES.clear(); } }

    static void ajouter(Bande b) {
        synchronized (BANDES) {
            if (b.sorte().equals("annonce")) BANDES.removeIf(x -> x.sorte().equals("annonce"));
            BANDES.add(b);
            while (BANDES.size() > 3) BANDES.remove(0);
        }
    }

    /** L'annonce de la cloche (chez les concernés présents). */
    static void annonce(String type, String titre, List<CommArt.Seg> texte, String signe, int secondes) {
        String t = type == null || type.isEmpty() ? "normale" : type;
        ajouter(new Bande("annonce", t, titre == null || titre.isEmpty() ? "ANNONCE DU VILLAGE" : titre, CommArt.couleurType(t),
                CommArt.cloche(t), CommArt.largeurCloche(t), 9, texte, signe == null || signe.isEmpty() ? null : "— Signé : " + signe,
                System.currentTimeMillis(), secondes * 1000L, -1));
    }

    /** Le staff a tu la cloche : la ligne en cours se retire (fondu). */
    static void finAnnonce() {
        synchronized (BANDES) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < BANDES.size(); i++) {
                Bande b = BANDES.get(i);
                if (b.sorte().equals("annonce") && now - b.at() < b.ms())
                    BANDES.set(i, new Bande(b.sorte(), b.type(), b.titre(), b.col(), b.icone(), b.iw(), b.ih(), b.texte(), b.signe(),
                            b.at(), Math.max(250, now - b.at()), b.prog()));
            }
        }
    }

    /** « Un corbeau a déposé une missive pour toi à la … » ou l'alerte du palais. */
    static void alerte(String k, String lieu, int secondes) {
        boolean palais = "palais".equals(k);
        List<CommArt.Seg> t = new ArrayList<>();
        if (palais) t.add(new CommArt.Seg("Une missive importante est arrivée à la volière du palais.", ""));
        else {
            t.add(new CommArt.Seg("Un corbeau a déposé une missive pour toi à la ", ""));
            t.add(new CommArt.Seg(lieu == null || lieu.isEmpty() ? "volière" : lieu, "g"));
            t.add(new CommArt.Seg(".", ""));
        }
        ajouter(new Bande(palais ? "palais" : "missive", "", palais ? "VOLIERE DU PALAIS" : "MISSIVE", palais ? CommArt.AMBRE : CommArt.OR_CLAIR,
                CommArt.MISSIVE_BANDE, 12, 12, t, null, System.currentTimeMillis(), secondes * 1000L, -1));
        RebornSounds.playReborn("stats.light", palais ? 0.8f : 1.1f, 0.4f);
    }

    /* ================================================================ rendu */

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        List<Bande> l;
        long now = System.currentTimeMillis();
        synchronized (BANDES) {
            BANDES.removeIf(b -> now - b.at() > b.ms() + 400);
            if (BANDES.isEmpty()) return;
            l = new ArrayList<>(BANDES);
        }
        int km = DaKit.echelleMenu();
        int sw = mc.getWindow().getWidth() / km;
        DaKit.unites(g, km);
        float y = 13;
        for (Bande b : l) y += peindre(g, mc.font, b, sw / 2f, y, now, sw) + 4;
        DaKit.finUnites(g);
    }

    /** Une bande centrée en (cx, y) ; renvoie sa hauteur (unités menu). */
    static float peindre(GuiGraphicsExtractor g, Font f, Bande b, float cx, float y, long now, float sw) {
        float a = b.alpha(now);
        if (a <= 0f) return 0;
        CommArt.Plume pz = PZ;
        pz.police(f);
        float sc = DaKit.facteurPetit();
        float tw = pz.largeurArcade(b.titre(), sc);
        float ws = b.signe() == null ? 0 : pz.largeur(b.signe(), 13, false) + 9;
        float tete = 9 + b.iw() + 5 + tw + 7 + 0.5f + 7.5f;
        float maxTexte = Math.min(Math.max(220, sw * 0.62f) - tete - 9, 760);
        List<List<CommArt.Seg>> lignes = CommArt.lignes(f, b.texte(), maxTexte - (CommArt.plat(b.texte()).length() > 110 ? 0 : ws), 15, 2);
        float wt = 0;
        for (List<CommArt.Seg> li : lignes) wt = Math.max(wt, pz.largeurRiche(li, 15));
        float wDerniere = lignes.isEmpty() ? 0 : pz.largeurRiche(lignes.get(lignes.size() - 1), 15);
        boolean signeSeule = ws > 0 && wDerniere + ws > maxTexte;
        float wl = Math.max(wt, signeSeule ? ws - 9 : wDerniere + ws);
        float w = tete + wl + 9;
        float h = 18 + (lignes.size() - 1 + (signeSeule ? 1 : 0)) * 9.5f;
        float x = Math.round(cx - w / 2f), y0 = Math.round(y);
        // fond uni aux bords francs, ombre d'un pixel, arête haute
        pz.px(x + 1, y0 + 1.5f, x + w + 1, y0 + h + 1.5f, DaKit.argb(0x000000, 0.24f * a));
        pz.px(x, y0, x + w, y0 + h, DaKit.argb(0x0D080A, 0.84f * a));
        pz.h(x, x + w, y0, DaKit.argb(0xFFF0DC, 0.09f * a));
        float cy = y0 + h / 2f;
        float xx = x + 9;
        float ix = xx, iy = cy - b.ih() / 2f;
        xx += b.iw() + 5;
        pz.arcade(b.titre(), xx, cy - 3.5f * sc * 2 / 2f - 0.5f, DaKit.alpha(b.col(), a), sc);
        xx += tw + 7;
        pz.v(xx, cy - 4, cy + 4, DaKit.argb(0xFFF0DC, 0.16f * a));
        xx += 7.5f;
        float ly = y0 + 9 - 0.25f;
        for (int i = 0; i < lignes.size(); i++) {
            float lw = pz.riche(lignes.get(i), xx, ly + i * 9.5f, 15, DaKit.alpha(CommArt.CREME, a));
            if (i == lignes.size() - 1 && b.signe() != null) {
                if (signeSeule) pz.texte(b.signe(), xx, ly + (i + 1) * 9.5f, 13, DaKit.alpha(CommArt.GRIS, a), false);
                else pz.texte(b.signe(), xx + lw + 9, ly + i * 9.5f + 0.4f, 13, DaKit.alpha(CommArt.GRIS, a), false);
            }
        }
        // temps restant : filet neutre en bas de la bande
        float reste = Math.max(0f, 1f - (now - b.at()) / (float) b.ms());
        pz.h(x, x + (w - 1) * reste, y0 + h - 1, DaKit.argb(0xF2E4C8, 0.27f * a));
        pz.vider(g);
        CommArt.sprite(g, b.icone(), b.iw(), b.ih(), ix, Math.round(iy), b.iw(), DaKit.blanc(a));
        return h;
    }

    /* ================================================================ banc */

    static void debug(String sorte, String type, String titre, List<CommArt.Seg> texte, String signe, long ageMs, long ms) {
        boolean cloche = sorte.equals("annonce");
        String t = type == null || type.isEmpty() ? "normale" : type;
        boolean palais = sorte.equals("palais");
        ajouter(new Bande(sorte, t, titre, cloche ? CommArt.couleurType(t) : palais ? CommArt.AMBRE : CommArt.OR_CLAIR,
                cloche ? CommArt.cloche(t) : CommArt.MISSIVE_BANDE, cloche ? CommArt.largeurCloche(t) : 12, cloche ? 9 : 12, texte,
                signe, System.currentTimeMillis() - ageMs, ms, -1));
    }
}
