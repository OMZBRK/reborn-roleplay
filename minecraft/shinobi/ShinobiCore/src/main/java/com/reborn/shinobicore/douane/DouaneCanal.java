package com.reborn.shinobicore.douane;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Canal {@code reborn:douane} (JSON UTF-8 brut, champs inconnus ignorés des deux côtés) — contrat complet dans
 * PLUGINS_REFERENCE §4 decies. Côté candidat : ligne d'attente, écran d'attente neutre, appel, question, Bienvenue,
 * résultat. Côté examinateur : onglet Douane du Poste de garde (file, bureaux, dossier résumé, tirage, décision).
 *
 * <p>Un client qui n'écoute pas le canal (mod antérieur au lot E) reçoit un repli vanilla : barre d'action, titres,
 * tchat, et l'écran noir existant de {@code reborn:ecran}. Tout est revérifié côté serveur (grade, permission, mode,
 * bureau) : le client ne fait qu'afficher et demander.
 */
public final class DouaneCanal implements PluginMessageListener {

    public static final String CANAL = "reborn:douane";
    static final String CANAL_ECRAN = "reborn:ecran";
    /** Version du protocole (champ {@code v} de chaque message S → C). */
    static final int VERSION = 1;
    /** Au-delà, un message C → S est ignoré. */
    static final int TAILLE_MAX = 32 * 1024;

    private final ShinobiCore plugin;
    private final DouaneManager douane;

    DouaneCanal(ShinobiCore plugin, DouaneManager douane) {
        this.plugin = plugin;
        this.douane = douane;
    }

    void enregistrer() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CANAL)) m.registerOutgoingPluginChannel(plugin, CANAL);
        if (!m.isIncomingChannelRegistered(plugin, CANAL)) m.registerIncomingPluginChannel(plugin, CANAL, this);
        if (!m.isOutgoingChannelRegistered(plugin, CANAL_ECRAN)) m.registerOutgoingPluginChannel(plugin, CANAL_ECRAN);
    }

    boolean ecoute(Player p) {
        return p != null && p.getListeningPluginChannels().contains(CANAL);
    }

    private boolean ecouteEcran(Player p) {
        return p != null && p.getListeningPluginChannels().contains(CANAL_ECRAN);
    }

    private void envoyer(Player p, JsonObject o) {
        o.addProperty("v", VERSION);
        try {
            p.sendPluginMessage(plugin, CANAL, o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) { }
    }

    private static JsonObject msg(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("t", type);
        return o;
    }

    /* ================================================================ candidat */

    void ligne(Player p, String etat, String texte, int rang, int total) {
        JsonObject o = msg("ligne");
        o.addProperty("etat", etat);
        o.addProperty("texte", texte);
        if (rang > 0) { o.addProperty("rang", rang); o.addProperty("total", total); }
        envoyer(p, o);
    }

    void attente(Player p, boolean on, String texte) {
        JsonObject o = msg("attente");
        o.addProperty("on", on);
        if (on) o.addProperty("texte", texte);
        envoyer(p, o);
    }

    /** Écran noir existant du mod ({@code reborn:ecran}, PLUGINS_REFERENCE §4 bis.4) : repli pour un mod sans ce canal. */
    void noir(Player p, boolean on, String texte) {
        JsonObject o = msg("noir");
        o.addProperty("on", on);
        if (on) { o.addProperty("texte", texte); o.addProperty("s", 0); }
        o.addProperty("fondu", 10);
        try {
            p.sendPluginMessage(plugin, CANAL_ECRAN, o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) { }
    }

    void appel(Player p) {
        if (ecoute(p)) {
            JsonObject o = msg("appel");
            o.addProperty("texte", ReglesDouane.TEXTE_APPEL);
            o.addProperty("fondu", 10);
            envoyer(p, o);
            return;
        }
        p.showTitle(Title.title(Component.empty(), Component.text(ReglesDouane.TEXTE_APPEL, NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        if (ecouteEcran(p)) {
            noir(p, true, "");
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) noir(p, false, ""); }, 12L);
        }
    }

    /** Question montrée au candidat ; texte vide = l'effacer. */
    void question(Player p, String texte) {
        if (ecoute(p)) {
            JsonObject o = msg("question");
            o.addProperty("texte", texte == null ? "" : texte);
            envoyer(p, o);
            return;
        }
        if (texte == null || texte.isEmpty()) return;
        p.sendMessage(Component.text("Question · " + texte, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
    }

    void bienvenue(Player p, int secondes) {
        if (ecoute(p)) {
            JsonObject o = msg("bienvenue");
            o.addProperty("titre", ReglesDouane.TEXTE_BIENVENUE);
            o.addProperty("s", Math.max(1, secondes));
            envoyer(p, o);
            return;
        }
        if (ecouteEcran(p)) noir(p, true, "");
        p.showTitle(Title.title(Component.text(ReglesDouane.TEXTE_BIENVENUE, NamedTextColor.GOLD), Component.empty(),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(Math.max(1, secondes)), Duration.ofMillis(400))));
    }

    void resultat(Player p, String resultat, String texte, int secondes) {
        if (ecoute(p)) {
            JsonObject o = msg("resultat");
            o.addProperty("resultat", resultat);
            o.addProperty("texte", texte);
            o.addProperty("s", Math.max(1, secondes));
            envoyer(p, o);
            return;
        }
        p.showTitle(Title.title(Component.empty(), Component.text(texte, NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(Math.max(1, secondes)), Duration.ofMillis(400))));
        p.sendMessage(Component.text(texte, NamedTextColor.GRAY));
    }

    /**
     * Occupants du monde de la douane : examinateurs en mode (plaque « Examinateur ») ; les autres joueurs du monde
     * gardent leur pseudo (le mod l'affiche à la place d'« Inconnu », {@code reborn:tablist} ne change pas).
     */
    void plaques(Player p, java.util.List<String> examinateurs) {
        JsonObject o = msg("plaques");
        JsonArray a = new JsonArray();
        for (String u : examinateurs) a.add(u);
        o.add("examinateurs", a);
        envoyer(p, o);
    }

    /** Sortie de la douane : le mod efface ligne, question et écran d'attente. */
    void fin(Player p) {
        if (ecoute(p)) envoyer(p, msg("fin"));
        if (ecouteEcran(p)) noir(p, false, "");
        p.clearTitle();
    }

    /**
     * Changement de monde depuis celui de la douane (staff, examinateur) : {@code fin} sur ce canal seulement, pour que
     * le mod cesse d'appliquer les dernières {@code plaques}. Rien sur {@code reborn:ecran} ni les titres : un écran
     * noir du staff ou une cinématique n'est pas touché.
     */
    void sortie(Player p) {
        if (ecoute(p)) envoyer(p, msg("fin"));
    }

    /* ================================================================ examinateur */

    void toast(Player p, String m, boolean erreur) {
        JsonObject o = msg("toast");
        o.addProperty("m", m);
        o.addProperty("erreur", erreur);
        envoyer(p, o);
    }

    void mode(Player p, boolean actif) {
        if (!ecoute(p)) return;
        JsonObject o = msg("mode");
        o.addProperty("examinateur", actif);
        envoyer(p, o);
    }

    /** Question tirée, pour l'examinateur seul ({@code reste} -1 = inchangé). */
    void tirage(Player ex, String texte, boolean montree, int reste) {
        if (ecoute(ex)) {
            JsonObject o = msg("tirage");
            o.addProperty("texte", texte);
            o.addProperty("montree", montree);
            if (reste >= 0) o.addProperty("reste", reste);
            envoyer(ex, o);
            return;
        }
        ex.sendMessage(Component.text(montree ? "Question montrée : " : "Question tirée : ", NamedTextColor.GOLD)
                .append(Component.text(texte, NamedTextColor.WHITE))
                .append(montree ? Component.empty() : Component.text("  [Montrer]", NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/douane question montrer"))));
    }

    /** État de l'envoi d'une décision : {@code en_cours}, {@code attente} (nouvel essai prévu), {@code ok}, {@code erreur}. */
    void envoi(Player ex, String etat, String resultat, String texte) {
        if (ecoute(ex)) {
            JsonObject o = msg("envoi");
            o.addProperty("etat", etat);
            o.addProperty("resultat", resultat);
            o.addProperty("texte", texte);
            envoyer(ex, o);
            return;
        }
        ex.sendMessage(Component.text(texte, "erreur".equals(etat) ? NamedTextColor.RED
                : "ok".equals(etat) ? NamedTextColor.GREEN : NamedTextColor.GOLD));
    }

    /** Fiche résumée (réponse de l'API telle quelle), ou l'erreur. */
    void dossier(Player ex, UUID candidat, JsonObject dossier, String erreur) {
        if (ecoute(ex)) {
            JsonObject o = msg("dossier");
            o.addProperty("u", candidat.toString());
            if (dossier != null) o.add("dossier", dossier);
            else o.addProperty("erreur", erreur);
            envoyer(ex, o);
            return;
        }
        if (dossier == null) { ex.sendMessage(Component.text(erreur, NamedTextColor.RED)); return; }
        resumeTchat(ex, dossier);
    }

    /** Repli sans le mod : quelques lignes de tchat et le lien vers le panel. */
    private void resumeTchat(Player ex, JsonObject d) {
        JsonObject ca = obj(d, "candidat"), pe = obj(d, "personnage"), pa = obj(d, "passage"), vo = obj(d, "voix");
        String pseudo = DouaneClient.str(ca, "pseudo");
        ex.sendMessage(Component.text("— Dossier de " + (pseudo == null ? "?" : pseudo) + " —", NamedTextColor.GOLD));
        ex.sendMessage(gris("Candidat : " + val(ca, "ageDeclare", " ans") + " · RP : " + val(ca, "anneesRp", "")
                + " · Naruto : " + val(ca, "connaissanceNaruto", "")));
        ex.sendMessage(gris("Personnage : " + val(pe, "prenom", "") + " " + val(pe, "nomFamille", "") + " · "
                + val(pe, "sexe", "") + " · " + val(pe, "age", " ans") + " · " + val(pe, "village", "") + " · " + val(pe, "type", "")));
        if (pa != null) {
            boolean deux = DouaneClient.bool(pa, "deuxiemePassage");
            String motif = DouaneClient.str(pa, "motifPrecedent");
            ex.sendMessage(gris((deux ? "2ᵉ passage" : "1ᵉʳ passage") + " · convocation "
                    + douane.heure(DouaneClient.instant(pa, "convocationAt"))
                    + (motif == null || motif.isBlank() ? "" : " · motif précédent : " + coupe(motif, 200))));
        }
        JsonObject note = obj(d, "noteEcrit");
        if (note != null) ex.sendMessage(gris("Note de l'écrit : " + coupe(DouaneClient.str(note, "texte"), 300)));
        if (d.has("extraits") && d.get("extraits").isJsonArray()) {
            for (JsonElement el : (JsonArray) d.get("extraits")) {
                if (!el.isJsonObject()) continue;
                JsonObject x = el.getAsJsonObject();
                ex.sendMessage(gris("« " + val(x, "titre", "") + " » " + coupe(DouaneClient.str(x, "texte"), 200)));
            }
        }
        String consigne = DouaneClient.str(vo, "consigne");
        ex.sendMessage(gris(consigne == null ? "Voix : à réécouter dans le panel" : consigne));
        String lien = DouaneClient.str(d, "lienPanel");
        if (lien != null && lien.startsWith("https://")) {
            ex.sendMessage(Component.text("[Ouvrir dans le panel]", NamedTextColor.YELLOW).clickEvent(ClickEvent.openUrl(lien)));
        }
    }

    private static Component gris(String s) {
        return Component.text(s, NamedTextColor.GRAY);
    }

    private static JsonObject obj(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null;
    }

    private static String val(JsonObject o, String k, String suffixe) {
        String s = DouaneClient.str(o, k);
        return s == null || s.isBlank() ? "—" : s + suffixe;
    }

    private static String coupe(String s, int max) {
        if (s == null) return "—";
        String t = s.replace('\n', ' ').strip();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /* ================================================================ onglet Douane : instantané */

    /** Instantané pour l'onglet Douane du Poste de garde. */
    JsonObject etat(Player viewer) {
        JsonObject o = msg("etat");
        o.addProperty("actif", douane.actif() && douane.monde() != null);
        o.addProperty("ouvert", douane.ouvert());
        o.addProperty("mode", douane.mode().actif(viewer.getUniqueId()));
        o.addProperty("peut", DouaneManager.peutExaminer(viewer));
        long t = System.currentTimeMillis();
        JsonArray file = new JsonArray();
        int rang = 1;
        for (FileDouane.Entree e : douane.file().ordre()) {
            JsonObject j = new JsonObject();
            j.addProperty("u", e.id().toString());
            j.addProperty("pseudo", e.pseudo());
            j.addProperty("rang", rang++);
            j.addProperty("attenteMin", Math.max(0L, (t - e.arriveeAt()) / 60_000L));
            j.addProperty("convocation", douane.heure(e.convocationAt()));
            j.addProperty("convocationAt", e.convocationAt());
            j.addProperty("village", e.village());
            j.addProperty("deuxiemePassage", e.deuxiemePassage());
            j.addProperty("enTete", e.teteAt() > 0);
            file.add(j);
        }
        o.add("file", file);
        JsonArray bureaux = new JsonArray();
        String mien = null;
        DouaneManager.Bureau monBureau = null;
        for (DouaneManager.Bureau b : douane.bureaux().values()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", b.conf.id());
            j.addProperty("libre", b.libre());
            j.addProperty("examinateur", b.examinateur == null ? null : b.examinateur.toString());
            j.addProperty("examinateurPseudo", nom(b.examinateur));
            UUID cand = b.candidat != null ? b.candidat : b.absent;
            j.addProperty("candidat", cand == null ? null : cand.toString());
            j.addProperty("candidatPseudo", nom(cand));
            j.addProperty("absent", b.candidat == null && b.absent != null);
            j.addProperty("envoi", cand != null && douane.envoiEnCours(cand) ? "attente" : "aucun");
            bureaux.add(j);
            if (viewer.getUniqueId().equals(b.examinateur)) { mien = b.conf.id(); monBureau = b; }
        }
        o.add("bureaux", bureaux);
        o.addProperty("monBureau", mien);
        if (monBureau != null && monBureau.question != null) {
            JsonObject q = new JsonObject();
            q.addProperty("texte", monBureau.question);
            q.addProperty("montree", monBureau.montree);
            o.add("question", q);
        }
        o.addProperty("enAttente", douane.file().taille());
        o.addProperty("envoisEnAttente", douane.envoisEnAttente());
        return o;
    }

    private static String nom(UUID id) {
        if (id == null) return null;
        Player p = Bukkit.getPlayer(id);
        return p != null ? p.getName() : Bukkit.getOfflinePlayer(id).getName();
    }

    /** À tous les examinateurs en ligne qui écoutent le canal (compteur de l'onglet, même fermé). */
    void etatPourTous() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (ecoute(p) && DouaneManager.peutExaminer(p)) envoyer(p, etat(p));
        }
    }

    /* ================================================================ C → S */

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] message) {
        if (!CANAL.equals(channel) || message.length > TAILLE_MAX) return;
        String brut = new String(message, StandardCharsets.UTF_8);
        // Les messages de plugin peuvent arriver hors du fil principal selon les versions de Paper.
        Bukkit.getScheduler().runTask(plugin, () -> traiter(p, brut));
    }

    private void traiter(Player p, String brut) {
        if (!p.isOnline() || !DouaneManager.peutExaminer(p)) return;
        JsonObject in;
        try { in = JsonParser.parseString(brut).getAsJsonObject(); }
        catch (RuntimeException e) { return; }
        String a = DouaneClient.str(in, "a");
        if (a == null) return;
        switch (a) {
            case "etat" -> envoyer(p, etat(p));
            case "mode" -> douane.mode().basculer(p);
            case "aller" -> douane.aller(p);
            case "quitter" -> douane.quitterDouane(p);
            case "appeler" -> douane.appeler(p, DouaneClient.str(in, "u"));
            case "renvoyer" -> douane.renvoyer(p);
            case "retirer" -> douane.retirer(p, DouaneClient.str(in, "u"), DouaneClient.str(in, "motif"));
            case "dossier" -> douane.dossier(p, DouaneClient.str(in, "u"));
            case "question" -> douane.question(p);
            case "montrer" -> douane.montrer(p, true);
            case "masquer" -> douane.montrer(p, false);
            case "notes" -> douane.notes(p, DouaneClient.str(in, "texte"));
            case "decision" -> {
                String r = DouaneClient.str(in, "resultat");
                if (!ReglesDouane.APPROUVE.equals(r) && !ReglesDouane.A_REVOIR.equals(r) && !ReglesDouane.REFUSE.equals(r)) {
                    toast(p, "Décision inconnue.", true);
                    return;
                }
                douane.decider(p, r, DouaneClient.str(in, "motif"), in.has("notes") ? DouaneClient.str(in, "notes") : null);
            }
            default -> { }
        }
        douane.marquerSale();
    }
}
