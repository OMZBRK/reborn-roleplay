package com.reborn.shinobiradio;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Registre persistant des escouades ({@code plugins/ShinobiRadio/escouades.yml}) et du compteur de numéros
 * d'oreillettes (jamais réutilisé). Écriture atomique ({@code .tmp} puis renommage), différée : {@link #marquer()}
 * puis {@link #sauverSiBesoin()} toutes les quelques secondes, et à l'arrêt. Thread principal seulement.
 */
final class Escouades {

    private final File fichier;
    private final Logger log;
    private final Map<UUID, Escouade> parId = new HashMap<>();
    /** Personnage → escouade (un personnage est membre d'une escouade au plus). */
    private final Map<UUID, UUID> parPerso = new HashMap<>();
    private int prochainNumero = 1;
    private boolean sale;

    Escouades(File dossier, Logger log) {
        this.fichier = new File(dossier, "escouades.yml");
        this.log = log;
    }

    Collection<Escouade> toutes() { return parId.values(); }

    Escouade get(UUID id) { return id == null ? null : parId.get(id); }

    Escouade dePerso(UUID perso) {
        UUID e = perso == null ? null : parPerso.get(perso);
        return e == null ? null : parId.get(e);
    }

    int nouveauNumero() {
        sale = true;
        return prochainNumero++;
    }

    Escouade creer(String nom, UUID createur, long maintenant) {
        Escouade e = new Escouade(UUID.randomUUID(), nom, createur, maintenant);
        parId.put(e.id, e);
        sale = true;
        return e;
    }

    void ajouter(Escouade e, Escouade.Membre m) {
        e.membres.put(m.perso, m);
        parPerso.put(m.perso, e.id);
        e.exclues.remove(m.oreillette);
        sale = true;
    }

    /** Retire le membre (son oreillette liée est exclue) ; renvoie le membre retiré ou null. */
    Escouade.Membre retirer(Escouade e, UUID perso) {
        Escouade.Membre m = e.membres.remove(perso);
        if (m == null) return null;
        parPerso.remove(perso);
        if (m.oreillette >= 0) e.exclues.add(m.oreillette);
        if (e.estCreateur(perso)) {
            Escouade.Membre suivant = e.plusAncien(perso);
            e.createur = suivant == null ? null : suivant.perso;
        }
        sale = true;
        return m;
    }

    void dissoudre(Escouade e) {
        for (UUID p : new ArrayList<>(e.membres.keySet())) parPerso.remove(p);
        parId.remove(e.id);
        sale = true;
    }

    void marquer() { sale = true; }

    /* ------------------------------------------------------------------ disque */

    void charger() {
        parId.clear();
        parPerso.clear();
        if (!fichier.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        prochainNumero = Math.max(1, y.getInt("prochain-numero", 1));
        ConfigurationSection s = y.getConfigurationSection("escouades");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                ConfigurationSection c = s.getConfigurationSection(k);
                if (c == null) continue;
                Escouade e = new Escouade(UUID.fromString(k), c.getString("nom", "Escouade"),
                        uuid(c.getString("createur")), c.getLong("cree-le"));
                e.derniereConnexion = c.getLong("derniere-connexion", e.creeLe);
                e.exclues.addAll(c.getIntegerList("oreillettes-exclues"));
                ConfigurationSection ms = c.getConfigurationSection("membres");
                if (ms != null) {
                    for (String pk : ms.getKeys(false)) {
                        ConfigurationSection m = ms.getConfigurationSection(pk);
                        if (m == null) continue;
                        Escouade.Membre mb = new Escouade.Membre(UUID.fromString(pk), uuid(m.getString("compte")),
                                m.getString("nom", "?"), uuid(m.getString("ajoute-par")), m.getLong("depuis"),
                                m.getInt("oreillette", -1));
                        mb.prise = m.getBoolean("prise", false);
                        e.membres.put(mb.perso, mb);
                    }
                }
                if (e.membres.isEmpty()) continue;
                if (e.createur == null || !e.membres.containsKey(e.createur)) e.createur = e.plusAncien(null).perso;
                parId.put(e.id, e);
                for (UUID p : e.membres.keySet()) parPerso.put(p, e.id);
            } catch (RuntimeException ex) {
                log.warning("[radio] Escouade illisible « " + k + " » : " + ex.getMessage());
            }
        }
        log.info("[radio] " + parId.size() + " escouade(s) chargée(s), prochain numéro d'oreillette " + prochainNumero + ".");
    }

    void sauverSiBesoin() {
        if (sale) sauver();
    }

    void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("prochain-numero", prochainNumero);
        for (Escouade e : parId.values()) {
            String b = "escouades." + e.id + ".";
            y.set(b + "nom", e.nom);
            y.set(b + "createur", e.createur == null ? null : e.createur.toString());
            y.set(b + "cree-le", e.creeLe);
            y.set(b + "derniere-connexion", e.derniereConnexion);
            y.set(b + "oreillettes-exclues", new ArrayList<>(e.exclues));
            for (Escouade.Membre m : e.membres.values()) {
                String mb = b + "membres." + m.perso + ".";
                y.set(mb + "compte", m.compte == null ? null : m.compte.toString());
                y.set(mb + "nom", m.nom);
                y.set(mb + "ajoute-par", m.ajoutePar == null ? null : m.ajoutePar.toString());
                y.set(mb + "depuis", m.depuis);
                y.set(mb + "oreillette", m.oreillette);
                y.set(mb + "prise", m.prise);
            }
        }
        try {
            File dossier = fichier.getParentFile();
            if (!dossier.exists() && !dossier.mkdirs()) throw new IOException("dossier " + dossier);
            File tmp = new File(dossier, fichier.getName() + ".tmp");
            y.save(tmp);
            Files.move(tmp.toPath(), fichier.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            sale = false;
        } catch (IOException | RuntimeException ex) {
            log.warning("[radio] Sauvegarde des escouades échouée : " + ex.getMessage());
        }
    }

    /** Escouades dont personne ne s'est connecté depuis {@code jours} (0 = jamais). */
    List<Escouade> abandonnees(int jours, long maintenant) {
        List<Escouade> l = new ArrayList<>();
        if (jours <= 0) return l;
        long limite = maintenant - jours * 86_400_000L;
        for (Escouade e : parId.values()) if (e.derniereConnexion < limite) l.add(e);
        return l;
    }

    private static UUID uuid(String s) {
        try { return s == null || s.isBlank() ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
