package fr.reborn.hud.menu.tablist;

/**
 * Une ligne du tablist Reborn. En v1 les données sont <b>mockées</b> côté
 * client pour itérer sur le visuel ; en v2 elles viendront du canal serveur
 * {@code reborn:tablist} (nom réel selon relation, grade, clan… — voir
 * l'analyse ShinobiCore). {@code name} est déjà résolu côté « connaissance » :
 * vrai nom si connu, sinon {@code "Inconnu (Pas RP)"}.
 */
public record TabEntry(
    String name,
    Relation relation,
    boolean staff,
    String grade,      // libellé FR du rang (ex. "Apprenti Genin"), "???" si inconnu
    String clan,       // clan affiché, null si inconnu
    int clanColor,     // 0 = pas de couleur clan (fallback couleur relation)
    int ping,          // latence ms
    int level,         // infos panneau détail
    int age,
    String affinity
) {
    public enum Relation { SOI, AMI, CONNAISSANCE, INCONNU }

    public boolean known() { return relation != Relation.INCONNU; }
}
