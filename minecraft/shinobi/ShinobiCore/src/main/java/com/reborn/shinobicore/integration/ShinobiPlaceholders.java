package com.reborn.shinobicore.integration;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Expansion PlaceholderAPI « shinobi » — expose les stats du personnage actif
 * pour que <b>BetterHUD</b> (ou tout plugin PAPI) puisse dessiner le HUD in-game
 * (PV, chakra, grade, clan…). Reste server-authoritative : le client ne fait que
 * recevoir le resource pack généré par BetterHUD.
 *
 * <p>Placeholders (préfixe {@code %shinobi_…%}) :
 * <ul>
 *   <li>{@code name} · {@code clan} · {@code grade} · {@code affinity}</li>
 *   <li>{@code level} · {@code age}</li>
 *   <li>{@code hp} · {@code maxhp} · {@code hp_percent}</li>
 *   <li>{@code chakra} · {@code maxchakra} · {@code chakra_percent}</li>
 * </ul>
 * <p><b>Jamais de chaîne vide</b> : BetterHUD interprète une valeur non résolue
 * comme « placeholder introuvable » et abandonne le rendu de tout le layout.
 * Sans personnage actif, on renvoie donc des défauts non-vides (nom → pseudo MC,
 * stats → {@code 0}, grade/clan/affinité → {@code —}).
 */
public class ShinobiPlaceholders extends PlaceholderExpansion {

    private final ShinobiCore plugin;

    public ShinobiPlaceholders(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "shinobi"; }
    @Override public @NotNull String getAuthor()     { return "Reborn"; }
    @Override public @NotNull String getVersion()    { return "1.0.0"; }
    /** Garde l'expansion enregistrée même si PlaceholderAPI /reload. */
    @Override public boolean persist() { return true; }

    /** Tiret cadratin utilisé comme valeur par défaut pour les champs texte. */
    private static final String DASH = "—"; // —

    @Override
    public String onRequest(OfflinePlayer offline, @NotNull String params) {
        String key = params.toLowerCase();

        // Personnage actif (peut être null : pas connecté / pas de perso RP).
        // On NE renvoie JAMAIS de chaîne vide : BetterHud interprète une valeur
        // vide/non-résolue comme "placeholder not found" et ABANDONNE tout le
        // layout. Chaque champ a donc un défaut non-vide.
        Player player = (offline != null && offline.isOnline()) ? offline.getPlayer() : null;
        ShinobiCharacter c = (player != null) ? plugin.characters().getActive(player.getUniqueId()) : null;

        if (c == null) {
            // Pas de perso : défauts sûrs (le HUD s'affiche quand même).
            return switch (key) {
                case "name"                    -> player != null ? player.getName() : DASH;
                case "clan", "grade", "rank",
                     "affinity"                -> DASH;
                case "level", "age",
                     "hp", "maxhp", "hp_percent",
                     "chakra", "maxchakra",
                     "chakra_percent"          -> "0";
                default                        -> null; // placeholder inconnu → PAPI laisse tel quel
            };
        }

        return switch (key) {
            case "name" -> {
                String n = c.name();
                yield (n == null || n.isBlank()) ? (player != null ? player.getName() : DASH) : n;
            }
            case "clan" -> {
                String clan = c.clan();
                yield (clan == null || clan.isBlank() || clan.equalsIgnoreCase("None")) ? DASH : clan;
            }
            case "grade", "rank" -> c.rank() != null ? c.rank().displayName() : DASH;
            case "affinity" -> c.affinity() != null ? c.affinity().displayName() : DASH;
            case "level" -> String.valueOf(c.level());
            case "age" -> String.valueOf(c.age());
            case "hp" -> String.valueOf(Math.round(c.currentHp()));
            case "maxhp" -> String.valueOf(Math.round(c.maxHp()));
            case "hp_percent" -> pct(c.currentHp(), c.maxHp());
            case "chakra" -> String.valueOf(Math.round(c.chakra().current()));
            case "maxchakra" -> String.valueOf(Math.round(c.chakra().max()));
            case "chakra_percent" -> pct(c.chakra().current(), c.chakra().max());
            default -> null; // placeholder inconnu → PAPI laisse tel quel
        };
    }

    private static String pct(double cur, double max) {
        if (max <= 0.0) return "0";
        return String.valueOf((int) Math.round(cur / max * 100.0));
    }
}
