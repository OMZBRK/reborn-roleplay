package com.reborn.shinobicore.concentration.plaque;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ligne de vue des plaques (lot 4, étape B ; revue du 07/11, point bloquant) : un mur entre le joueur et l'objet bloque
 * <b>toujours</b> le texte, aussi pour un cadre ou un meuble Nexo ; seules les barrières collées au meuble (sa hitbox)
 * se traversent. Grille de blocs simulée ({@link Grille}), même règle que le serveur ({@link PlaqueRegles#enVue}).
 */
class PlaqueVueTest {

    /** Blocs pleins et barrières d'une petite scène ; le rayon avance par pas de 5 mm (impact à l'entrée du bloc). */
    private static final class Grille implements PlaqueRegles.Traceur {
        final Set<Long> pleins = new HashSet<>(), barrieres = new HashSet<>();

        static long cle(int x, int y, int z) { return ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | (long) z & 0xFFFFF; }

        Grille plein(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) pleins.add(cle(x, y, z));
            return this;
        }

        Grille barriere(int x, int y, int z) {
            barrieres.add(cle(x, y, z));
            return this;
        }

        @Override
        public PlaqueRegles.Impact tracer(double ox, double oy, double oz, double dx, double dy, double dz, double dist) {
            for (double s = 0; s <= dist; s += 0.005) {
                double x = ox + dx * s, y = oy + dy * s, z = oz + dz * s;
                int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
                long k = cle(bx, by, bz);
                boolean b = barrieres.contains(k);
                if (b || pleins.contains(k)) return new PlaqueRegles.Impact(x, y, z, bx, by, bz, b);
            }
            return null;
        }
    }

    private static final double OEIL = 1.62;

    @Test
    void stele_aDecouvert_puisDerriereUnMur() {
        double[] stele = Ancre.bloc("world", 10, 64, 0, 1, 2, 1).boite();
        Grille g = new Grille().plein(10, 64, 0, 10, 65, 0);
        assertTrue(PlaqueRegles.enVue(g, 4.5, 64 + OEIL, 0.5, stele, false), "à découvert : la stèle se lit");
        g.plein(7, 64, -2, 7, 67, 2);
        assertFalse(PlaqueRegles.enVue(g, 4.5, 64 + OEIL, 0.5, stele, false), "un mur entre les deux bloque le texte");
    }

    @Test
    void cadre_dansUneSalleClose_neSeLitPasDuDehors() {
        // mur nord de la salle : z = 9 (de 9 à 10) ; cadre accroché sur la face intérieure, au-dessus du sol
        Grille g = new Grille().plein(2, 64, 9, 9, 68, 9);
        // cadre de 0,75 × 0,75 × 1/16 sur la face intérieure (z = 10) : boîte de lecture autour de son centre
        double[] cadre = Ancre.boiteLecture(5.125, 65.125, 10.0, 5.875, 65.875, 10.0625, true);
        assertEquals(9.53125, cadre[2], 1e-9);   // dépasse de 0,47 dans le mur
        // dehors, à 6,5 blocs du cadre (dans la portée + 1,5), tourné vers le mur : rien
        assertTrue(PlaqueRegles.aPortee(5.5, 64 + OEIL, 3.0, cadre, 6));
        assertFalse(PlaqueRegles.enVue(g, 5.5, 64 + OEIL, 3.0, cadre, true), "cadre derrière un mur d'un bloc : jamais lu");
        // dans la salle, en face : lu
        assertTrue(PlaqueRegles.enVue(g, 5.5, 64 + OEIL, 14.0, cadre, true), "dans la salle : lu");
    }

    @Test
    void meuble_colleAUnMur_neSeLitPasDeLAutreCote() {
        // meuble Nexo de 1 × 1 posé contre le mur (z = 10) : la face extérieure du mur est à 1,0 de la boîte
        Grille g = new Grille().plein(2, 64, 10, 9, 67, 10);
        double[] meuble = Ancre.boiteEntite(5.5, 64, 11.5, 1, 1);
        assertFalse(PlaqueRegles.enVue(g, 5.5, 64 + OEIL, 6.0, meuble, true), "meuble collé au mur, joueur dehors : rien");
        // du bon côté, mur collé derrière le meuble : lu
        Grille g2 = new Grille().plein(2, 64, 12, 9, 67, 12);
        assertTrue(PlaqueRegles.enVue(g2, 5.5, 64 + OEIL, 7.0, meuble, true), "mur derrière le meuble : lu de face");
    }

    @Test
    void barriereDuMeuble_seTraverse_maisPasUnBlocPlein() {
        double[] meuble = Ancre.boiteEntite(5.5, 64, 11.5, 1, 1);
        // hitbox Nexo : une barrière juste devant la boîte
        Grille g = new Grille().barriere(5, 64, 10).barriere(5, 65, 10);
        assertTrue(PlaqueRegles.enVue(g, 5.5, 64 + OEIL, 7.0, meuble, true), "les barrières du meuble ne cachent pas");
        // la même place en pierre : c'est un mur
        Grille pierre = new Grille().plein(5, 64, 10, 5, 65, 10).plein(4, 64, 10, 6, 66, 10);
        assertFalse(PlaqueRegles.enVue(pierre, 5.5, 64 + OEIL, 7.0, meuble, true), "un bloc plein devant le meuble cache");
        // une barrière loin du meuble (plus d'un bloc) n'est pas sa hitbox : elle cache
        Grille loin = new Grille().barriere(4, 64, 8).barriere(5, 64, 8).barriere(6, 64, 8).barriere(4, 65, 8).barriere(5, 65, 8)
            .barriere(6, 65, 8).barriere(4, 66, 8).barriere(5, 66, 8).barriere(6, 66, 8);
        assertFalse(PlaqueRegles.enVue(loin, 5.5, 64 + OEIL, 6.0, meuble, true), "barrière à 3 blocs du meuble : cache");
        // pour une plaque de bloc, aucune exception : une barrière cache
        double[] stele = Ancre.bloc("world", 5, 64, 11, 1, 1, 1).boite();
        Grille g3 = new Grille().plein(5, 64, 11, 5, 64, 11).barriere(5, 64, 10).barriere(5, 65, 10);
        assertFalse(PlaqueRegles.enVue(g3, 5.5, 64 + OEIL, 7.0, stele, false), "plaque de bloc : la barrière cache");
    }

    @Test
    void argsPoser_porteeExpliciteSeulement() {
        PlaqueRegles.ArgsPoser a = PlaqueRegles.argsPoser(List.of("3", "guerriers", "reposent", "ici"));
        assertEquals(0, a.portee(), "un nombre seul reste du texte");
        assertEquals(0, a.debut());
        a = PlaqueRegles.argsPoser(List.of("p:3", "+24h", "Ici", "reposent"));
        assertEquals(3, a.portee());
        assertEquals(1440, a.expire());
        assertEquals(2, a.debut());
        a = PlaqueRegles.argsPoser(List.of("+30m", "P=12", "texte"));
        assertEquals(12, a.portee());
        assertEquals(30, a.expire());
        assertEquals(2, a.debut());
    }
}
