package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.EtatService.Peine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Peines de prison</b> (cadre {@code PRISON}, docs/technique/ETATS_JOUEUR.md §4.2, §7.3 ; décision du 14/10 n° 8 :
 * prison par zone + menottes anti-chakra, peine en <b>temps réel</b>, infirmerie de la prison). Portées par le
 * <b>personnage</b> ({@code prison.yml}), gardées à la déconnexion et au redémarrage.
 *
 * <p>La zone (facultative) est une zone RP du staff ({@code /zonerp creer prison <id>} — type {@code PRISON}) : en sortir
 * pendant la peine est une <b>évasion</b> (journal + alerte au Poste de garde, rien d'automatique) ; l'hôpital y réveille
 * le détenu ({@code /hopital} → entrée de la zone). L'arrestation par la police (Faction §12) appellera
 * {@code EtatService#emprisonner} ; aujourd'hui seul le staff pose une peine ({@code /etats prison}).
 */
public final class Prison {

    private final ShinobiCore plugin;
    private final File fichier;
    private final Map<UUID, Peine> peines = new ConcurrentHashMap<>();
    private boolean sale;

    Prison(ShinobiCore plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "prison.yml");
    }

    void charger() {
        peines.clear();
        if (!fichier.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        ConfigurationSection s = y.getConfigurationSection("peines");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                UUID cid = UUID.fromString(k);
                peines.put(cid, new Peine(cid, s.getLong(k + ".fin"), s.getString(k + ".zone"), s.getString(k + ".motif"),
                        s.getString(k + ".par", "?")));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    void sauver() {
        if (!sale && fichier.exists()) return;
        sale = false;
        YamlConfiguration y = new YamlConfiguration();
        for (Peine p : peines.values()) {
            String k = "peines." + p.personnage();
            y.set(k + ".fin", p.finEpochMs());
            if (p.zone() != null) y.set(k + ".zone", p.zone());
            if (p.motif() != null) y.set(k + ".motif", p.motif());
            y.set(k + ".par", p.par());
        }
        try {
            y.save(fichier);
        } catch (IOException e) {
            plugin.getLogger().warning("[etats] prison.yml : " + e.getMessage());
        }
    }

    /** Peine en cours (non échue) ? */
    public boolean enPrison(UUID personnage) {
        Peine p = personnage == null ? null : peines.get(personnage);
        return p != null && p.finEpochMs() > System.currentTimeMillis();
    }

    public Peine peine(UUID personnage) {
        Peine p = personnage == null ? null : peines.get(personnage);
        return p != null && p.finEpochMs() > System.currentTimeMillis() ? p : null;
    }

    void poser(UUID personnage, long fin, String zone, String motif, String par) {
        peines.put(personnage, new Peine(personnage, fin, zone == null || zone.isBlank() ? null : zone, motif, par));
        sale = true;
        sauver();
    }

    void lever(UUID personnage) {
        if (peines.remove(personnage) != null) { sale = true; sauver(); }
    }

    /** Peines échues : retirées (le message de fin est envoyé par {@link EtatSuivi}). */
    boolean echue(UUID personnage) {
        Peine p = peines.get(personnage);
        if (p == null || p.finEpochMs() > System.currentTimeMillis()) return false;
        peines.remove(personnage);
        sale = true;
        sauver();
        return true;
    }

    public Map<UUID, Peine> toutes() { return Map.copyOf(peines); }
}
