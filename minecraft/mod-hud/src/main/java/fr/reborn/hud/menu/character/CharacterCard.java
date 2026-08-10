package fr.reborn.hud.menu.character;

/**
 * Une carte de personnage telle qu'affichée dans l'écran de sélection
 * ({@link CharacterSelectScreen}). Données envoyées par le serveur (ShinobiCore)
 * sur le canal {@code reborn:character} — ici en lecture seule côté client.
 *
 * @param id        UUID du personnage (adressage serveur)
 * @param firstName prénom RP
 * @param clan      clan (vide si aucun)
 * @param clanColor couleur ARGB du clan (0 = couleur par défaut)
 * @param village   village RP (vide si aucun)
 * @param rank      rang ninja, label FR (ex "Genin")
 * @param level     niveau RP
 * @param dead      personnage mort (RPK) → carte verrouillée + badge
 * @param appearance queue {@code SkinSpec.serialize()} du skin RP composé ("" = skin défaut)
 */
public record CharacterCard(
    String id,
    String firstName,
    String clan,
    int clanColor,
    String village,
    String rank,
    int level,
    boolean dead,
    String appearance
) {
    public boolean hasClan() { return clan != null && !clan.isBlank(); }
    public boolean hasVillage() { return village != null && !village.isBlank(); }
    public boolean hasAppearance() { return appearance != null && !appearance.isBlank(); }
}
