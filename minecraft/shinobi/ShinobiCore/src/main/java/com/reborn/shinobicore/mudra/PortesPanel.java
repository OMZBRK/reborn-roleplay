package com.reborn.shinobicore.mudra;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.concentration.plaque.PlaqueRegles;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outil « Portes » du Poste de garde (F7 › Outils › Portes, validé le 16/11), actions {@code porte*} du canal
 * {@code reborn:staff}, branché par {@code StaffPanel.handle} comme l'outil Plaques. Le grade est relu à chaque message
 * et revérifié par {@link PorteService} (qui journalise ses refus) ; un joueur sans grade ne reçoit que « Réservé au
 * staff. ». Chaque action de modification répond par un {@code toast} puis par la liste {@code portes} à jour
 * ({@code dernier} = la porte touchée, absent après un échec).
 *
 * <table>
 *   <tr><th>{@code a}</th><th>Grade</th><th>Champs</th></tr>
 *   <tr><td>{@code portes}</td><td>Helper</td><td>{@code r} : rayon des « proches » (64, de 4 à 256), gardé jusqu'à la
 *       déconnexion</td></tr>
 *   <tr><td>{@code porte_verifier}</td><td>Modérateur</td><td>{@code n} (numéro de demande), {@code boite} ; répond
 *       {@code porte_verif}, sans liste</td></tr>
 *   <tr><td>{@code porte_creer}</td><td>Modérateur</td><td>{@code boite}, {@code nom}, {@code signes} (ordinaux 0 à 11,
 *       2 à 12), {@code leve} (0 = toute la hauteur)</td></tr>
 *   <tr><td>{@code porte_signes} · {@code porte_nom} · {@code porte_leve}</td><td>Modérateur</td><td>{@code id} +
 *       {@code signes} · {@code nom} · {@code leve}</td></tr>
 *   <tr><td>{@code porte_ouvrir} · {@code porte_fermer} · {@code porte_retirer}</td><td>Modérateur</td><td>{@code id}</td></tr>
 *   <tr><td>{@code porte_tp}</td><td>Helper</td><td>{@code id}</td></tr>
 *   <tr><td>{@code porte_systeme}</td><td>Admin</td><td>{@code on} (booléen ; absent ou autre : ignoré)</td></tr>
 *   <tr><td>{@code porte_debloquer}</td><td>Modérateur</td><td>{@code id}, {@code joueur} (UUID)</td></tr>
 * </table>
 * {@code boite} = {@code {"x","y","z","dx","dy","dz"}} : coin bas et taille, dans le monde du staff.
 *
 * <p><b>La suite ne part que vers un Modérateur ou plus</b> ({@link #porteJson}) : un Helper reçoit {@code n}, le nombre
 * de signes. Elle n'est écrite dans aucun toast, aucune ligne de journal, aucun log.
 *
 * <p>Débit : {@link MudraRegles#OUTIL_PAR_S} messages {@code porte*} par seconde et par joueur, au-delà ignorés (chaque
 * retouche écrit {@code mudras.yml} tout de suite, et la vérification d'une dalle lit ses blocs).
 */
public final class PortesPanel implements Listener {

    private static final String CANAL = "reborn:staff";
    /** Un identifiant de porte tient en quelques caractères : ce qui dépasse n'est jamais redit dans un toast. */
    private static final int ID_MAX = 8;

    private final ShinobiCore plugin;
    private final PorteService svc;
    private final MudraService mudras;
    private final Gson gson = new Gson();
    /** Dernier rayon demandé par chaque staff, repris pour la liste renvoyée après chaque action. */
    private final Map<UUID, Integer> rayons = new HashMap<>();
    private final PlaqueRegles.Limiteur<UUID> debit = new PlaqueRegles.Limiteur<>(MudraRegles.OUTIL_PAR_S, 1000);

    public PortesPanel(ShinobiCore plugin, PorteService svc, MudraService mudras) {
        this.plugin = plugin;
        this.svc = svc;
        this.mudras = mudras;
    }

    /** Déconnexion : le rayon demandé et le compte du débit sont oubliés. */
    @EventHandler
    public void quitter(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        rayons.remove(id);
        debit.oublier(id);
    }

    public void handle(Player p, int grade, String a, JsonObject in) {
        // StaffPanel diffère d'un tick : un joueur parti entre-temps n'agit plus et ne recrée ni son débit ni son rayon
        if (!p.isOnline()) return;
        UUID moi = p.getUniqueId();
        if (!debit.essayer(moi, System.currentTimeMillis())) return;
        if (!MudraRegles.peutLister(grade)) { toast(p, "Réservé au staff."); return; }
        PorteService.Resultat r;
        String fait;
        switch (a) {
            case "portes" -> {
                rayons.put(moi, MudraRegles.bornerRayon(intDe(in, "r", MudraRegles.OUTIL_RAYON)));
                liste(p, grade, null);
                return;
            }
            case "porte_verifier" -> {
                String e = svc.verifier(p, boite(in));
                JsonObject o = new JsonObject();
                o.addProperty("t", "porte_verif");
                o.addProperty("n", intDe(in, "n", 0));
                o.addProperty("ok", e == null);
                o.addProperty("m", e == null ? "" : e);
                envoyer(p, o);
                return;
            }
            case "porte_systeme" -> {
                JsonElement on = in.get("on");
                // un message mal formé n'éteint pas les mudras de tout le serveur : il faut un vrai booléen
                if (on == null || !on.isJsonPrimitive() || !on.getAsJsonPrimitive().isBoolean()) return;
                String e = svc.systeme(p, on.getAsBoolean());
                toast(p, e != null ? e : "Mudras " + (on.getAsBoolean() ? "allumés" : "éteints") + " pour tout le serveur.");
                liste(p, grade, null);
                return;
            }
            case "porte_creer" -> {
                MudraRegles.Suite su = suite(in);
                // un Helper passe au service même avec une suite mal formée : c'est lui qui refuse et journalise
                if (!su.ok() && MudraRegles.peutGerer(grade)) { echec(p, grade, su.erreur()); return; }
                r = svc.creer(p, boite(in), str(in, "nom"), su.signes(), intDe(in, "leve", 0));
                fait = r.ok() ? "scellée · dalle " + r.porte().boite.dx() + " × " + r.porte().boite.dy() + " × "
                        + r.porte().boite.dz() + " · " + r.porte().signes.size() + " signes · levée " + r.porte().leve : "";
            }
            case "porte_signes" -> {
                MudraRegles.Suite su = suite(in);
                if (!su.ok() && MudraRegles.peutGerer(grade)) { echec(p, grade, su.erreur()); return; }
                r = svc.signes(p, id(in), su.signes());
                fait = "a sa nouvelle suite (" + su.signes().size() + " signes)";
            }
            case "porte_nom" -> {
                r = svc.nom(p, id(in), str(in, "nom"));
                fait = "renommée";
            }
            case "porte_leve" -> {
                r = svc.leve(p, id(in), intDe(in, "leve", 0));
                fait = r.ok() ? "se lèvera de " + r.porte().leve + " bloc" + (r.porte().leve > 1 ? "s" : "") : "";
            }
            case "porte_ouvrir" -> {
                r = svc.essaiOuvrir(p, id(in));
                fait = "se lève";
            }
            case "porte_fermer" -> {
                r = svc.essaiFermer(p, id(in));
                if (PorteService.PASSAGE_OCCUPE.equals(r.erreur())) {
                    // la descente est planifiée, elle attend que le passage se libère : une information, pas un échec
                    PorteScellee d = svc.get(id(in));
                    toast(p, r.erreur());
                    liste(p, grade, d == null ? null : d.id);
                    return;
                }
                fait = "se referme";
            }
            case "porte_retirer" -> {
                r = svc.retirer(p, id(in));
                fait = "retirée (les blocs restent en place)";
            }
            case "porte_tp" -> {
                r = svc.teleporter(p, id(in));
                if (r.ok()) {
                    toast(p, "Devant la porte " + r.porte().id + ".");
                    liste(p, grade, r.porte().id);
                    return;
                }
                fait = "";
            }
            case "porte_debloquer" -> {
                UUID joueur = uuid(str(in, "joueur"));
                r = mudras.debloquer(p, id(in), joueur);
                if (r.ok()) {
                    toast(p, "Blocage levé : la porte " + r.porte().id + " répond de nouveau à " + nomDe(joueur) + ".");
                    liste(p, grade, r.porte().id);
                    return;
                }
                fait = "";
            }
            default -> { return; }
        }
        if (!r.ok()) { echec(p, grade, r.erreur()); return; }
        toast(p, "Porte " + r.porte().id + " " + fait + ".");
        liste(p, grade, r.porte().id);
    }

    /** Un échec : l'erreur, sans ses renvois à une commande ({@link MudraRegles#pourOutil}), puis la liste telle qu'elle est. */
    private void echec(Player p, int grade, String erreur) {
        toast(p, MudraRegles.pourOutil(erreur));
        liste(p, grade, null);
    }

    /* ================================================================ liste */

    /**
     * Liste {@code portes} : toutes les portes ({@link MudraRegles#OUTIL_LISTE_MAX} au plus), celles du monde du staff
     * d'abord, de la plus proche à la plus lointaine. La suite n'y est que pour un Modérateur ou plus.
     */
    private void liste(Player p, int grade, String dernier) {
        Map<String, List<MudraRegles.Blocage<UUID>>> bloques = mudras.blocages();
        Map<UUID, String> noms = new HashMap<>();
        boolean suite = MudraRegles.peutGerer(grade);
        JsonArray l = new JsonArray();
        for (PorteScellee d : svc.liste(p)) {
            if (l.size() >= MudraRegles.OUTIL_LISTE_MAX) break;
            JsonArray b = new JsonArray();
            for (MudraRegles.Blocage<UUID> x : bloques.getOrDefault(d.id, List.of()))
                b.add(blocageJson(x.joueur().toString(), noms.computeIfAbsent(x.joueur(), this::nomDe), x.resteMs()));
            l.add(porteJson(d, suite, svc.etatDe(d), svc.resteAvantDescente(d), PorteService.distance(d, p), b));
        }
        envoyer(p, listeJson(svc.actif(), grade, svc.bloque(), rayons.getOrDefault(p.getUniqueId(), MudraRegles.OUTIL_RAYON),
                dernier, l));
    }

    /** Nom montré au staff pour un joueur bloqué : son personnage actif s'il est en ligne, sinon son dernier pseudo connu. */
    private String nomDe(UUID id) {
        Player en = Bukkit.getPlayer(id);
        if (en != null) {
            ShinobiCharacter c = plugin.characters() == null ? null : plugin.characters().getActive(id);
            return c != null ? c.name() : en.getName();
        }
        return plugin.staffPanel() != null ? plugin.staffPanel().store().nameOf(id) : id.toString().substring(0, 8);
    }

    /* ================================================================ sérialisation (pure, testée) */

    /** L'enveloppe de la liste : interrupteur, droits du destinataire (le serveur revérifie toujours), fichier bloqué. */
    static JsonObject listeJson(boolean actif, int grade, boolean bloque, int rayon, String dernier, JsonArray portes) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "portes");
        o.addProperty("actif", actif);
        o.addProperty("peutGerer", MudraRegles.peutGerer(grade));
        o.addProperty("peutSysteme", MudraRegles.peutSysteme(grade));
        o.addProperty("bloque", bloque);
        o.addProperty("r", rayon);
        if (dernier != null) o.addProperty("dernier", dernier);
        o.add("l", portes);
        return o;
    }

    /**
     * Une porte de la liste. {@code avecSuite} : le destinataire est Modérateur ou plus ; sinon <b>la clé {@code signes}
     * n'existe pas</b>, seul {@code n} (le nombre de signes) part. {@code reste} : secondes avant la redescente, -1 si
     * sans objet ; {@code dist} : blocs jusqu'à la dalle, infinie dans un autre monde (envoyée -1). {@code modif} est
     * toujours là : {@code par} vide et {@code le} à 0 pour une porte jamais retouchée.
     */
    static JsonObject porteJson(PorteScellee d, boolean avecSuite, MudraRegles.Etat etat, int reste, double dist, JsonArray blocages) {
        JsonObject x = new JsonObject();
        x.addProperty("id", d.id);
        x.addProperty("nom", d.nom);
        x.addProperty("monde", d.monde);
        x.addProperty("x", d.boite.x());
        x.addProperty("y", d.boite.y());
        x.addProperty("z", d.boite.z());
        x.addProperty("dx", d.boite.dx());
        x.addProperty("dy", d.boite.dy());
        x.addProperty("dz", d.boite.dz());
        x.addProperty("leve", d.leve);
        x.addProperty("n", d.signes.size());
        if (avecSuite) {
            JsonArray s = new JsonArray();
            for (int o : MudraRegles.ordinaux(d.signes)) s.add(o);
            x.add("signes", s);
        }
        x.addProperty("etat", etat.code());
        x.addProperty("reste", etat == MudraRegles.Etat.LEVEE ? reste : -1);
        x.addProperty("dist", Double.isInfinite(dist) || Double.isNaN(dist) ? -1 : Math.round(dist * 10) / 10.0);
        x.add("cree", parLe(d.creePseudo, d.creeLe));
        x.add("modif", parLe(d.modifPseudo, d.modifLe));
        x.add("blocages", blocages);
        return x;
    }

    private static JsonObject parLe(String par, long le) {
        JsonObject o = new JsonObject();
        o.addProperty("par", par == null ? "" : par);
        o.addProperty("le", Math.max(0, le));
        return o;
    }

    /** Un joueur que la porte ne reconnaît plus : son compte, le nom à montrer, les secondes qui restent (au moins 1). */
    static JsonObject blocageJson(String uuid, String nom, long resteMs) {
        JsonObject o = new JsonObject();
        o.addProperty("u", uuid);
        o.addProperty("nom", nom);
        o.addProperty("reste", Math.max(1, (resteMs + 999) / 1000));
        return o;
    }

    /* ================================================================ lecture des demandes (pure, testée) */

    /**
     * La dalle choisie à la souris : {@code boite} = {@code {"x","y","z","dx","dy","dz"}}, six entiers. null si elle
     * manque ou ne se lit pas ; ses bornes sont jugées par {@link PorteService} ({@link MudraRegles#validerBoite}).
     */
    static MudraRegles.Boite boite(JsonObject in) {
        JsonElement e = in.get("boite");
        if (e == null || !e.isJsonObject()) return null;
        JsonObject b = e.getAsJsonObject();
        Integer x = entier(b.get("x")), y = entier(b.get("y")), z = entier(b.get("z"));
        Integer dx = entier(b.get("dx")), dy = entier(b.get("dy")), dz = entier(b.get("dz"));
        if (x == null || y == null || z == null || dx == null || dy == null || dz == null) return null;
        return new MudraRegles.Boite(x, y, z, dx, dy, dz);
    }

    /**
     * La suite composée dans la grille : {@code signes} = tableau d'ordinaux de 0 à 11. Une case qui n'est pas un entier
     * vaut un signe inconnu ; rien n'est lu au-delà de {@link MudraRegles#SUITE_MAX} + 1 cases.
     */
    static MudraRegles.Suite suite(JsonObject in) {
        JsonElement e = in.get("signes");
        List<Integer> l = new ArrayList<>();
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                if (l.size() > MudraRegles.SUITE_MAX) break;
                l.add(entier(x));
            }
        }
        return MudraRegles.suiteDepuisOrdinaux(l);
    }

    /** Un entier exact ({@code 3}, {@code 3.0}) ; null pour tout le reste (absent, texte, booléen, {@code 3.5}, démesuré). */
    static Integer entier(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        double d = e.getAsDouble();
        return d == Math.rint(d) && Math.abs(d) <= 1_000_000_000 ? (int) d : null;
    }

    /** L'identifiant de porte de la demande, borné : il peut être redit dans un toast (« Porte introuvable »). */
    static String id(JsonObject in) {
        String s = str(in, "id").strip();
        return s.length() > ID_MAX ? s.substring(0, ID_MAX) : s;
    }

    private static UUID uuid(String s) {
        try { return s.isBlank() ? null : UUID.fromString(s.strip()); } catch (IllegalArgumentException e) { return null; }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        try { return e == null || e.isJsonNull() ? "" : e.getAsString(); } catch (RuntimeException ex) { return ""; }
    }

    private static int intDe(JsonObject o, String k, int def) {
        Integer n = entier(o.get(k));
        return n == null ? def : n;
    }

    /* ================================================================ envoi */

    private void toast(Player p, String m) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "toast");
        o.addProperty("m", m);
        if (p.getListeningPluginChannels().contains(CANAL)) envoyer(p, o);
        else p.sendMessage("§6[Portes] §f" + m);
    }

    private void envoyer(Player p, JsonObject o) {
        if (!plugin.isEnabled()) return;
        p.sendPluginMessage(plugin, CANAL, gson.toJson(o).getBytes(StandardCharsets.UTF_8));
    }
}
