package com.reborn.shinobicore.douane;

import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * File d'envoi locale des décisions (CANDIDATURE_ET_DOUANE.md §3.9) : une décision ne se perd pas si l'API est
 * injoignable. Elle est gardée sur disque ({@code douane-envois.yml}, survit à un redémarrage) et renvoyée toutes les
 * {@code douane.delais.renvoi-secondes} (ou selon {@code Retry-After}), seulement sur une coupure, un 429 ou un 5xx.
 * Un autre 4xx est définitif. Le corps envoyé est le même à chaque essai (la signature, elle, est refaite).
 *
 * <p>Le joueur n'est <b>jamais</b> promu sans une réponse 200 de l'API. Fil principal, sauf l'appel HTTP.
 */
final class EnvoisDecision {

    /** Une décision à envoyer. */
    record Envoi(String id, UUID candidat, String pseudoCandidat, UUID examinateur, String resultat,
                 String reservationId, String corps, long cree, int tentatives, long prochain) {
        Envoi apres(long quand) {
            return new Envoi(id, candidat, pseudoCandidat, examinateur, resultat, reservationId, corps, cree,
                    tentatives + 1, quand);
        }
    }

    /** Ce que fait la douane de l'issue d'un envoi (fil principal). */
    interface Suite {
        void succes(Envoi e, JsonObject reponse);
        void echec(Envoi e, int code, String message);
        void report(Envoi e, String message);
    }

    private final ShinobiCore plugin;
    private final DouaneClient client;
    private final Suite suite;
    private final File fichier;
    private final LongSupplier horloge;
    private final Map<String, Envoi> envois = new LinkedHashMap<>();
    private final Set<String> enVol = new HashSet<>();
    private long renvoiMs = 30_000L, abandonMs = 48L * 3600_000L;

    EnvoisDecision(ShinobiCore plugin, DouaneClient client, Suite suite) {
        this.plugin = plugin;
        this.client = client;
        this.suite = suite;
        this.fichier = new File(plugin.getDataFolder(), "douane-envois.yml");
        this.horloge = System::currentTimeMillis;
    }

    void regler(long renvoiMs, long abandonMs) {
        this.renvoiMs = renvoiMs;
        this.abandonMs = abandonMs;
    }

    int taille() { return envois.size(); }

    /** Envoi en cours ou en attente pour ce candidat, ou null. */
    Envoi pour(UUID candidat) {
        for (Envoi e : envois.values()) if (e.candidat().equals(candidat)) return e;
        return null;
    }

    List<Envoi> tous() { return new ArrayList<>(envois.values()); }

    /** Ajoute une décision et tente l'envoi tout de suite. */
    Envoi ajouter(UUID candidat, String pseudo, UUID examinateur, String resultat, String reservationId, String corps) {
        long t = horloge.getAsLong();
        Envoi e = new Envoi(UUID.randomUUID().toString(), candidat, pseudo, examinateur, resultat, reservationId,
                corps, t, 0, t);
        envois.put(e.id(), e);
        sauver();
        tenter(e);
        return e;
    }

    /** Toutes les secondes : relance ce qui est dû. */
    void tick() {
        long t = horloge.getAsLong();
        for (Envoi e : List.copyOf(envois.values())) {
            if (!enVol.contains(e.id()) && e.prochain() <= t) tenter(e);
        }
    }

    private void tenter(Envoi e) {
        if (!enVol.add(e.id())) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DouaneClient.Reponse r = client.decision(e.corps());
            Bukkit.getScheduler().runTask(plugin, () -> traiter(e, r));
        });
    }

    private void traiter(Envoi e, DouaneClient.Reponse r) {
        enVol.remove(e.id());
        if (!envois.containsKey(e.id())) return;
        long t = horloge.getAsLong();
        if (r.ok()) {
            envois.remove(e.id());
            sauver();
            suite.succes(e, r.corps());
            return;
        }
        if (ReglesDouane.aRenvoyer(r.code(), r.reseau())) {
            if (t - e.cree() > abandonMs) {
                envois.remove(e.id());
                sauver();
                suite.echec(e, r.code(), "abandonnée après " + (abandonMs / 3600_000L) + " h sans réponse de l'API");
                return;
            }
            long attente = Math.max(renvoiMs, r.retryAfterS() * 1000L);
            Envoi suivant = e.apres(t + attente);
            envois.put(e.id(), suivant);
            sauver();
            suite.report(suivant, r.message() == null ? "API injoignable" : r.message());
            return;
        }
        envois.remove(e.id());
        sauver();
        suite.echec(e, r.code(), r.message() == null ? "HTTP " + r.code() : r.message());
    }

    /* ================================================================ disque */

    void charger() {
        envois.clear();
        if (!fichier.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        ConfigurationSection s = y.getConfigurationSection("envois");
        if (s == null) return;
        long t = horloge.getAsLong();
        for (String id : s.getKeys(false)) {
            ConfigurationSection e = s.getConfigurationSection(id);
            if (e == null) continue;
            try {
                envois.put(id, new Envoi(id, UUID.fromString(e.getString("candidat", "")), e.getString("pseudo", ""),
                        UUID.fromString(e.getString("examinateur", "")), e.getString("resultat", ""),
                        e.getString("reservation", ""), e.getString("corps", ""), e.getLong("cree", t),
                        e.getInt("tentatives", 0), t));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[douane] envoi « " + id + " » illisible, ignoré : " + ex.getMessage());
            }
        }
        if (!envois.isEmpty()) {
            plugin.getLogger().info("[douane] " + envois.size() + " décision(s) en attente d'envoi reprise(s).");
        }
    }

    private void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        for (Envoi e : envois.values()) {
            String k = "envois." + e.id();
            y.set(k + ".candidat", e.candidat().toString());
            y.set(k + ".pseudo", e.pseudoCandidat());
            y.set(k + ".examinateur", e.examinateur().toString());
            y.set(k + ".resultat", e.resultat());
            y.set(k + ".reservation", e.reservationId());
            y.set(k + ".corps", e.corps());
            y.set(k + ".cree", e.cree());
            y.set(k + ".tentatives", e.tentatives());
        }
        try {
            if (!fichier.getParentFile().exists()) fichier.getParentFile().mkdirs();
            y.save(fichier);
        } catch (IOException ex) {
            plugin.getLogger().warning("[douane] douane-envois.yml non écrit : " + ex.getMessage());
        }
    }
}
