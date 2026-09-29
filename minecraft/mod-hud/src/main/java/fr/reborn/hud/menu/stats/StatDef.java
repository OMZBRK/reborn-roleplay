package fr.reborn.hud.menu.stats;

import java.util.List;
import java.util.Locale;

/**
 * Les six stats côté client — ordre, clé réseau, couleur et textes d'aide.
 * Miroir de {@code StatsService.Stat} (ShinobiCore) : la clé {@link #key()} est
 * celle du JSON {@code reborn:stats}.
 *
 * <p>Les textes disent <b>ce que la stat change</b>, jamais les coefficients :
 * la mécanique chiffrée reste derrière (leviers serveur), le joueur lit l'effet.
 */
public enum StatDef {
    TAIJUTSU("TAI", "TAIJUTSU", "Taïjutsu", 0xFFE8743B,
        "L'art du corps, sans arme ni chakra.",
        List.of("Tes coups à mains nues et tes techniques de taïjutsu frappent plus fort.",
                "Ton souffle s'allonge un peu : tu tiens l'effort plus longtemps.")),
    KENJUTSU("KEN", "KENJUTSU", "Kenjutsu", 0xFFB9C7D6,
        "La voie de la lame.",
        List.of("Tes coups d'arme et tes techniques d'épée gagnent en tranchant.",
                "Parades et enchaînements armés en tirent profit.")),
    NINJUTSU("NIN", "NINJUTSU", "Ninjutsu", 0xFF9B6BFF,
        "Le chakra façonné en arme.",
        List.of("Toutes tes techniques de chakra gagnent en puissance, quelle que soit leur nature.")),
    CONTROLE("CTR", "CONTROLE", "Contrôle", 0xFF3CCB9A,
        "La précision du flux de chakra.",
        List.of("Tes techniques te coûtent moins cher.",
                "Tes soins sont plus efficaces et la méditation te rend ton chakra plus vite.",
                "Il arrive qu'une technique frappe avec une force inattendue.")),
    VIGUEUR("VIG", "VIGUEUR", "Vigueur", 0xFFE0485A,
        "La solidité du corps.",
        List.of("Tu encaisses davantage avant de tomber.",
                "Ton endurance grandit nettement.")),
    CHAKRA("CHK", "CHAKRA", "Chakra", 0xFF3FB8F5,
        "La profondeur de ta réserve.",
        List.of("Ta réserve de chakra s'agrandit : tu enchaînes plus de techniques avant de t'épuiser."));

    public final String shortLabel;
    /** Libellé ASCII (police ArcadePix, sans accents). */
    public final String arcadeLabel;
    public final String displayName;
    public final int color;
    /** Une ligne d'ambiance, sous le titre de l'infobulle. */
    public final String motto;
    /** Ce que la stat change, en phrases (pas de chiffres). */
    public final List<String> effects;

    StatDef(String shortLabel, String arcadeLabel, String displayName, int color,
            String motto, List<String> effects) {
        this.shortLabel = shortLabel;
        this.arcadeLabel = arcadeLabel;
        this.displayName = displayName;
        this.color = color;
        this.motto = motto;
        this.effects = effects;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static StatDef fromKey(String k) {
        if (k == null) return null;
        for (StatDef d : values()) if (d.key().equalsIgnoreCase(k.trim())) return d;
        return null;
    }
}
