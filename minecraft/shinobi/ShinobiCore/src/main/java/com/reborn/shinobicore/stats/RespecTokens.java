package com.reborn.shinobicore.stats;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * <b>Jetons de réinitialisation</b> des stats — un compteur par <b>compte joueur</b>
 * (UUID Minecraft), pas par personnage : un jeton acheté sert au perso de son choix.
 *
 * <p>Persisté dans {@code plugins/ShinobiCore/respec-tokens.yml}. Aucune dépendance
 * à la boutique : n'importe quel canal crédite via {@link #add} — commande staff /
 * console {@code /stats jeton give <joueur> <n>}, et plus tard la boutique web par le
 * pont panel (qui exécute déjà des commandes console).
 *
 * <p>Mode ({@code stats.respec}) : {@code TOKEN} (défaut, un jeton par respec),
 * {@code FREE} (phase de test) ou {@code STAFF} (réservé au staff). Le staff
 * ({@code shinobicore.stats.admin}) réinitialise toujours gratuitement.
 */
public final class RespecTokens {

    public enum Mode { TOKEN, FREE, STAFF }

    private final ShinobiCore plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public RespecTokens(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "respec-tokens.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public Mode mode() {
        String raw = plugin.getConfig().getString("stats.respec", "TOKEN");
        try {
            return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Mode.TOKEN;
        }
    }

    public int get(UUID player) {
        return Math.max(0, yaml.getInt(player.toString(), 0));
    }

    /** Ajoute (n &gt; 0) ou retire (n &lt; 0) des jetons ; jamais sous zéro. Retourne le nouveau solde. */
    public int add(UUID player, int n) {
        return set(player, get(player) + n);
    }

    public int set(UUID player, int n) {
        int v = Math.max(0, n);
        yaml.set(player.toString(), v == 0 ? null : v);
        save();
        return v;
    }

    /** Consomme un jeton s'il y en a un. */
    public boolean tryConsume(UUID player) {
        int have = get(player);
        if (have <= 0) return false;
        set(player, have - 1);
        return true;
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "respec-tokens.yml : sauvegarde impossible", e);
        }
    }
}
