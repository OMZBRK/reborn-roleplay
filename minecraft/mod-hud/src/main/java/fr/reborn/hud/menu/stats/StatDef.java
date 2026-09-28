package fr.reborn.hud.menu.stats;

import java.util.List;
import java.util.Locale;

/**
 * Les six stats côté client — ordre, clé réseau, couleur et texte d'aide.
 * Miroir de {@code StatsService.Stat} (ShinobiCore) : la clé {@link #key()} est
 * celle du JSON {@code reborn:stats}.
 */
public enum StatDef {
    TAIJUTSU("TAI", "TAIJUTSU", "Taïjutsu", 0xFFE8743B,
        List.of("Dégâts des techniques Taï et du M1 à mains nues.",
                "Fait monter un peu l'endurance max.")),
    KENJUTSU("KEN", "KENJUTSU", "Kenjutsu", 0xFFB9C7D6,
        List.of("Dégâts des techniques Ken et du M1 armé.",
                "Parade et combos à l'arme.")),
    NINJUTSU("NIN", "NINJUTSU", "Ninjutsu", 0xFF9B6BFF,
        List.of("Dégâts des techniques de chakra,",
                "toutes natures confondues.")),
    CONTROLE("CTR", "CONTROLE", "Contrôle", 0xFF3CCB9A,
        List.of("Réduit le coût de toutes les techniques,",
                "renforce les soins, accélère la régénération",
                "et donne une chance de critique.")),
    VIGUEUR("VIG", "VIGUEUR", "Vigueur", 0xFFE0485A,
        List.of("PV max et endurance max.",
                "Résistance aux altérations (à venir).")),
    CHAKRA("CHK", "CHAKRA", "Chakra", 0xFF3FB8F5,
        List.of("Taille de la réserve de chakra,",
                "donc le nombre de techniques lançables."));

    public final String shortLabel;
    /** Libellé ASCII (police ArcadePix, sans accents). */
    public final String arcadeLabel;
    public final String displayName;
    public final int color;
    public final List<String> help;

    StatDef(String shortLabel, String arcadeLabel, String displayName, int color, List<String> help) {
        this.shortLabel = shortLabel;
        this.arcadeLabel = arcadeLabel;
        this.displayName = displayName;
        this.color = color;
        this.help = help;
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
