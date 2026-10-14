package com.reborn.shinobiradio;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

/** Réglages de {@code config.yml} (valeurs par défaut du code si la clé manque). */
final class RadioConfig {

    final int tailleMax;
    final double distance;
    final int invitationSecondes;
    final int dissolutionJours;
    final int nomMax;
    final List<String> idsOreillette;
    final String emplacement;
    final String idNexo;
    final int chuchoteBlocs;
    final int activationPoids;
    final int lignePoids;
    final double volumeDefaut;
    final double hacheTramesPerdues;
    final boolean porteHache;
    final int ecouteDefaut;
    final int ecouteMax;
    final int coupureDefaut;
    final boolean journalEmissions;
    final int emissionMinSecondes;

    RadioConfig(FileConfiguration c) {
        tailleMax = Math.max(2, c.getInt("escouade.taille-max", 8));
        distance = Math.max(1, c.getDouble("escouade.distance", 3.0));
        invitationSecondes = Math.max(3, c.getInt("escouade.invitation-secondes", 10));
        dissolutionJours = Math.max(0, c.getInt("escouade.dissolution-jours", 30));
        nomMax = Math.max(4, c.getInt("escouade.nom-max", 24));
        List<String> ids = c.getStringList("oreillette.ids");
        idsOreillette = (ids.isEmpty() ? List.of("oreillette") : ids).stream()
                .map(s -> s.toLowerCase(Locale.ROOT).trim()).toList();
        emplacement = c.getString("oreillette.emplacement", "BOUCLE").toUpperCase(Locale.ROOT);
        idNexo = c.getString("oreillette.id-nexo", "oreillette");
        chuchoteBlocs = Math.max(0, c.getInt("voix.chuchote-blocs", 4));
        activationPoids = c.getInt("voix.activation-poids", 20);
        lignePoids = c.getInt("voix.ligne-poids", 20);
        volumeDefaut = Math.max(0, Math.min(1, c.getDouble("voix.volume-defaut", 1.0)));
        hacheTramesPerdues = Math.max(0, Math.min(0.9, c.getDouble("voix.hache-trames-perdues", 0.35)));
        porteHache = c.getBoolean("voix.porte-hache", true);
        ecouteDefaut = Math.max(1, c.getInt("staff.ecoute-minutes-defaut", 10));
        ecouteMax = Math.max(ecouteDefaut, c.getInt("staff.ecoute-minutes-max", 60));
        coupureDefaut = Math.max(1, c.getInt("staff.coupure-minutes-defaut", 30));
        journalEmissions = c.getBoolean("journal.emissions", true);
        emissionMinSecondes = Math.max(0, c.getInt("journal.emission-min-secondes", 1));
    }
}
