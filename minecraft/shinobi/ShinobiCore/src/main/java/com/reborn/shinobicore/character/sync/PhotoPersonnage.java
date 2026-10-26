package com.reborn.shinobicore.character.sync;

import com.google.gson.JsonObject;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.select.ReglesCreation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Photo complète d'un personnage pour {@code POST /v1/game/characters/sync} (lot J, CANDIDATURE_ET_DOUANE.md §4.5 ;
 * contrat : {@code apps/api/src/game/dto/game-personnages.dto.ts}) — règles pures, sans Bukkit.
 *
 * <p>Chaque photo décrit <b>tout</b> le personnage : un champ facultatif absent vaut sa valeur par défaut côté API, ce
 * qui rend l'envoi idempotent. Les valeurs sont mises au format attendu ici pour qu'une photo ne soit jamais refusée
 * pour une forme : textes sans caractère de contrôle et coupés à 40, dates {@link Instant#toString()} (fuseau « Z »),
 * grade en code d'enum ({@code GENIN}…), sexe {@code HOMME} / {@code FEMME}.
 *
 * <p>Clan : le mod enregistre dans le champ clan soit un clan de session (« Uchiha »), soit le nom de famille libre du
 * clan « Autre » (« Arashi »), soit « None ». Le premier part en {@code clan}, le deuxième en {@code lastName}.
 */
public final class PhotoPersonnage {

    private PhotoPersonnage() {}

    /** Longueur maximale des textes acceptée par l'API. */
    static final int TEXTE_MAX = 40;
    static final int AGE_MAX = 200;
    static final int MINUTES_MAX = 52_560_000;

    /**
     * Données d'une photo. {@code playerId} -1 = pas encore attribué ; dates en ms epoch, 0 = inconnue ;
     * {@code clanBrut} = champ clan du personnage, tel quel.
     */
    public record Donnees(UUID characterId, UUID ownerId, int playerId, String nom, String clanBrut, String village,
                          String type, String rank, String sexe, int age, boolean dead, boolean deleted,
                          int playMinutes, long createdAt, long lastSeenAt, String applicationId) {}

    /** Données d'un personnage chargé. Fil principal (lecture des champs du personnage). */
    public static Donnees de(ShinobiCharacter c, boolean deleted) {
        return new Donnees(c.id(), c.ownerId(), c.playerId(), c.name(), c.clan(), c.village(), c.type(),
                c.rank().name(), c.sexe(), c.age(), c.dead(), deleted, c.playMinutes(), c.createdAt(), c.lastSeenAt(),
                c.applicationId());
    }

    /**
     * Données lues directement dans un fichier {@code characters/<owner>.yml} (rattrapage du démarrage, hors fil
     * principal) : {@code s} est la section du personnage, telle que SnakeYAML la rend (cartes et listes simples).
     * Mêmes clés et mêmes défauts que {@code YamlCharacterRepository}.
     */
    public static Donnees deYaml(UUID characterId, UUID ownerId, Map<?, ?> s) {
        return new Donnees(characterId, ownerId, entier(s.get("playerId"), -1), texte(s.get("name"), "Shinobi"),
                texte(s.get("clan"), "None"), texte(s.get("village"), ""),
                ShinobiCharacter.TYPE_CIVIL.equalsIgnoreCase(texte(s.get("type"), "")) ? ShinobiCharacter.TYPE_CIVIL
                        : ShinobiCharacter.TYPE_NINJA,
                Rank.from(texte(s.get("rank"), null)).name(), texte(s.get("sexe"), "Homme"), entier(s.get("age"), 12),
                Boolean.TRUE.equals(s.get("dead")), false, entier(s.get("play-minutes"), 0),
                long_(s.get("created-at")), long_(s.get("last-seen-at")), texte(s.get("application"), null));
    }

    /** JSON de la photo, daté de {@code snapshotAt} (ms epoch). */
    public static JsonObject json(Donnees d, long snapshotAt) {
        JsonObject o = contenu(d);
        o.addProperty("playMinutes", Math.max(0, Math.min(MINUTES_MAX, d.playMinutes())));
        if (d.lastSeenAt() > 0) o.addProperty("lastSeenAt", Instant.ofEpochMilli(d.lastSeenAt()).toString());
        o.addProperty("snapshotAt", Instant.ofEpochMilli(snapshotAt).toString());
        return o;
    }

    /**
     * Clé de contenu : tout sauf le temps de jeu, la dernière partie et l'instant de la photo. Une clé qui change =
     * une photo à envoyer ; le temps de jeu seul part au plus toutes les quelques minutes.
     */
    public static String cle(Donnees d) {
        return contenu(d).toString();
    }

    private static JsonObject contenu(Donnees d) {
        JsonObject o = new JsonObject();
        o.addProperty("characterId", d.characterId().toString());
        o.addProperty("ownerMcUuid", d.ownerId().toString());
        if (d.playerId() >= 0) o.addProperty("playerId", d.playerId());
        String prenom = propre(d.nom());
        o.addProperty("firstName", prenom.isEmpty() ? "?" : prenom);
        String brut = propre(d.clanBrut());
        if (!brut.isEmpty() && !"None".equalsIgnoreCase(brut) && !ReglesCreation.AUTRE.equalsIgnoreCase(brut)) {
            String session = ReglesCreation.clanDeSession(brut);
            if (session != null) o.addProperty("clan", session);
            else o.addProperty("lastName", brut);
        }
        String village = propre(d.village());
        if (!village.isEmpty()) o.addProperty("village", village);
        o.addProperty("type", ShinobiCharacter.TYPE_CIVIL.equals(d.type()) ? ShinobiCharacter.TYPE_CIVIL
                : ShinobiCharacter.TYPE_NINJA);
        if (d.rank() != null && d.rank().matches("^[A-Z][A-Z0-9_]{0,31}$")) o.addProperty("rank", d.rank());
        String sexe = ReglesCreation.sexeCanonique(d.sexe());
        if (sexe != null) o.addProperty("sex", sexe.toUpperCase(java.util.Locale.ROOT));
        o.addProperty("age", Math.max(0, Math.min(AGE_MAX, d.age())));
        o.addProperty("dead", d.dead());
        if (d.deleted()) o.addProperty("deleted", true);
        if (d.createdAt() > 0) o.addProperty("createdAt", Instant.ofEpochMilli(d.createdAt()).toString());
        String app = uuid(d.applicationId());
        if (app != null) o.addProperty("applicationId", app);
        return o;
    }

    /** Sans caractère de contrôle ni de mise en forme, rogné, coupé à {@value #TEXTE_MAX} caractères. */
    static String propre(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            int t = Character.getType(cp);
            if (t != Character.CONTROL && t != Character.FORMAT) b.appendCodePoint(cp);
        });
        String r = b.toString().strip();
        if (r.length() > TEXTE_MAX) {
            int fin = TEXTE_MAX;
            if (Character.isHighSurrogate(r.charAt(fin - 1))) fin--;   // jamais un demi-caractère
            r = r.substring(0, fin).strip();
        }
        return r;
    }

    private static String uuid(String s) {
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s.strip()).toString(); } catch (IllegalArgumentException e) { return null; }
    }

    private static String texte(Object v, String defaut) {
        return v == null ? defaut : String.valueOf(v);
    }

    private static int entier(Object v, int defaut) {
        if (v instanceof Number n) return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, n.longValue()));
        if (v instanceof String s) {
            try { return Integer.parseInt(s.strip()); } catch (NumberFormatException ignored) { }
        }
        return defaut;
    }

    private static long long_(Object v) {
        if (v instanceof Number n) return Math.max(0L, n.longValue());
        if (v instanceof String s) {
            try { return Math.max(0L, Long.parseLong(s.strip())); } catch (NumberFormatException ignored) { }
        }
        return 0L;
    }
}
