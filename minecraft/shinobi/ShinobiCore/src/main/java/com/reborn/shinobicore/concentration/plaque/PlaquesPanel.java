package com.reborn.shinobicore.concentration.plaque;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.staff.outils.Narrations;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Outil « Plaques » du Poste de garde (F7 › Outils), actions {@code plaque*} du canal {@code reborn:staff}
 * (PLUGINS_REFERENCE §4 undecies.3), branché par {@code StaffPanel.handle} comme {@code PersonnagesPanel}. Le grade est
 * relu à chaque message ; chaque action répond par un {@code toast} puis la liste {@code plaques} à jour.
 *
 * <table>
 *   <tr><th>{@code a}</th><th>Grade</th><th>Champs</th></tr>
 *   <tr><td>{@code plaques}</td><td>Helper</td><td>{@code r} (64, 256 au plus), {@code q}</td></tr>
 *   <tr><td>{@code plaque_creer}</td><td>Modérateur ; animateur (temporaire)</td><td>{@code ancre}, {@code segs} ou
 *       {@code texte}, {@code titre}, {@code p} (2-16), {@code expire} (minutes, 0 = jamais)</td></tr>
 *   <tr><td>{@code plaque_modifier}</td><td>Modérateur ; l'animateur, les siennes</td><td>{@code id} + champs à
 *       changer, {@code actif}</td></tr>
 *   <tr><td>{@code plaque_deplacer}</td><td>idem</td><td>{@code id}, {@code ancre}</td></tr>
 *   <tr><td>{@code plaque_suppr}</td><td>idem</td><td>{@code id}, {@code motif} (≥ 3)</td></tr>
 *   <tr><td>{@code plaque_restaurer}</td><td>Admin</td><td>{@code id}</td></tr>
 *   <tr><td>{@code plaque_tp}</td><td>Helper</td><td>{@code id}</td></tr>
 * </table>
 * L'aperçu est rendu par le mod à partir du texte de la liste (le staff Helper et plus le voit) : aucun aller-retour.
 */
public final class PlaquesPanel implements org.bukkit.event.Listener {

    private static final String CANAL = "reborn:staff";
    private final ShinobiCore plugin;
    private final PlaqueService svc;
    private final Runnable relire;
    private final Gson gson = new Gson();
    /** Dernière demande de liste par staff (rayon, recherche), reprise après chaque action. */
    private final Map<UUID, Object[]> derniere = new HashMap<>();

    public PlaquesPanel(ShinobiCore plugin, PlaqueService svc, Runnable relire) {
        this.plugin = plugin;
        this.svc = svc;
        this.relire = relire;
    }

    /** Déconnexion : la dernière demande de liste est oubliée. */
    @org.bukkit.event.EventHandler
    public void quitter(org.bukkit.event.player.PlayerQuitEvent e) { derniere.remove(e.getPlayer().getUniqueId()); }

    public void handle(Player p, int grade, String a, JsonObject in) {
        boolean anim = svc.animation(p);
        if (!PlaqueRegles.peutVoir(grade, anim)) { toast(p, "Réservé au staff."); return; }
        PlaqueService.Resultat r = null;
        switch (a) {
            case "plaques" -> {
                int rayon = Math.max(4, Math.min(PlaqueRegles.LISTE_RAYON_MAX, intDe(in, "r", PlaqueRegles.LISTE_RAYON)));
                derniere.put(p.getUniqueId(), new Object[]{rayon, str(in, "q")});
                liste(p, null);
                return;
            }
            case "plaque_creer" -> {
                Object an = svc.ancreDepuisJson(p, in.has("ancre") && in.get("ancre").isJsonObject() ? in.getAsJsonObject("ancre") : null);
                if (an instanceof String e) { toast(p, e); return; }
                Narrations.Analyse t = texte(in);
                if (t == null) { toast(p, "Écris d'abord le texte."); return; }
                if (!t.ok()) { toast(p, t.erreur()); return; }
                r = svc.creer(p, (Ancre) an, t.segments(), str(in, "titre"), intDe(in, "p", 0), Math.max(0, intDe(in, "expire", 0)));
                if (r.ok()) toast(p, "Plaque " + r.plaque().id + " posée · " + r.plaque().ancre.libelle() + "."
                        + (r.plaque().temporaire ? " Temporaire (mode animation)." : ""));
            }
            case "plaque_modifier" -> {
                List<Narrations.Segment> segs = null;
                if (in.has("segs") || in.has("texte")) {
                    Narrations.Analyse t = texte(in);
                    if (t == null || !t.ok()) { toast(p, t == null ? "Texte vide." : t.erreur()); return; }
                    segs = t.segments();
                }
                r = svc.modifier(p, str(in, "id"), segs, in.has("titre") ? str(in, "titre") : null,
                        in.has("p") ? intDe(in, "p", PlaqueRegles.PORTEE_DEFAUT) : null,
                        in.has("expire") && intDe(in, "expire", -1) >= 0 ? intDe(in, "expire", 0) : null,
                        in.has("actif") ? boolDe(in, "actif") : null);
                if (r.ok()) toast(p, "Plaque " + r.plaque().id + " enregistrée.");
            }
            case "plaque_deplacer" -> {
                Object an = svc.ancreDepuisJson(p, in.has("ancre") && in.get("ancre").isJsonObject() ? in.getAsJsonObject("ancre") : null);
                if (an instanceof String e) { toast(p, e); return; }
                r = svc.deplacer(p, str(in, "id"), (Ancre) an);
                if (r.ok()) toast(p, "Plaque " + r.plaque().id + " déplacée · " + r.plaque().ancre.libelle() + ".");
            }
            case "plaque_suppr" -> {
                r = svc.supprimer(p, str(in, "id"), str(in, "motif"));
                if (r.ok()) toast(p, "Plaque " + r.plaque().id + " mise à la corbeille (" + PlaqueRegles.CORBEILLE_JOURS + " jours).");
            }
            case "plaque_restaurer" -> {
                r = svc.restaurer(p, str(in, "id"));
                if (r.ok()) toast(p, "Plaque " + r.plaque().id + " restaurée.");
            }
            case "plaque_tp" -> {
                r = svc.teleporter(p, str(in, "id"));
                if (r.ok()) toast(p, "Devant la plaque " + r.plaque().id + ".");
            }
            default -> { return; }
        }
        if (r != null && !r.ok()) toast(p, r.erreur());
        if (r != null && r.ok()) relire.run();
        liste(p, r != null && r.ok() ? r.plaque().id : null);
    }

    /** {@code segs} (segments du volet) ou {@code texte} (balises) ; null si aucun des deux. */
    private static Narrations.Analyse texte(JsonObject in) {
        if (in.has("segs")) return Narrations.depuisJson(in.get("segs"));
        if (in.has("texte")) return Narrations.analyser(str(in, "texte"));
        return null;
    }

    /** Liste {@code plaques} au staff : autour de lui, puis la corbeille. */
    public void liste(Player p, String dernier) {
        Object[] d = derniere.getOrDefault(p.getUniqueId(), new Object[]{PlaqueRegles.LISTE_RAYON, ""});
        int rayon = (int) d[0];
        int grade = StaffGrades.of(p);
        boolean anim = svc.animation(p);
        long now = System.currentTimeMillis();
        JsonObject o = new JsonObject();
        o.addProperty("t", "plaques");
        o.addProperty("grade", grade);
        o.addProperty("anim", anim);
        o.addProperty("peutCreer", PlaqueRegles.peutCreer(grade, anim));
        o.addProperty("temporaire", PlaqueRegles.temporaire(grade, anim));
        o.addProperty("peutRestaurer", PlaqueRegles.peutRestaurer(grade));
        boolean tout = PlaqueRegles.voirTout(grade);
        o.addProperty("voirTout", tout);
        o.addProperty("rayon", rayon);
        o.addProperty("texteMax", PlaqueRegles.TEXTE_MAX);
        if (dernier != null) o.addProperty("dernier", dernier);
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE);
        JsonArray l = new JsonArray();
        var eye = p.getEyeLocation();
        int n = 0;
        for (Plaque pl : svc.autour(p, rayon, (String) d[1])) {
            if (n++ >= 60) break;
            JsonObject x = new JsonObject();
            x.addProperty("id", pl.id);
            x.addProperty("titre", pl.titre);
            x.addProperty("texte", pl.aPlat(160));
            x.add("segs", PlaqueService.segsJson(pl.segs));
            ancre(x, pl);
            double dist = PlaqueService.distance(pl, eye);
            x.addProperty("dist", dist < 0 ? -1 : Math.round(dist * 10) / 10.0);
            x.addProperty("p", pl.portee);
            x.addProperty("actif", pl.actif);
            x.addProperty("expire", pl.expire > 0 ? Math.max(1, (pl.expire - now + 59_999) / 60_000) : 0);
            x.addProperty("temporaire", pl.temporaire);
            x.addProperty("rev", pl.rev);
            x.addProperty("par", pl.creePseudo);
            x.addProperty("le", pl.creeLe > 0 ? f.format(new Date(pl.creeLe)) : "");
            x.addProperty("modifPar", pl.modifPseudo);
            x.addProperty("modifLe", pl.modifLe > 0 ? f.format(new Date(pl.modifLe)) : "");
            x.addProperty("lectures", pl.lectures);
            x.addProperty("moi", p.getUniqueId().equals(pl.creePar));
            x.addProperty("peutModifier", svc.peutModifier(p, pl));
            if (svc.absente(pl)) x.addProperty("absente", true);   // meuble cassé : plus lue, à déplacer ou retirer
            l.add(x);
        }
        o.add("l", l);
        JsonArray c = new JsonArray();
        n = 0;
        // corbeille : Helper et plus (un animateur sans grade n'y a pas accès)
        for (Plaque pl : tout ? svc.corbeille() : List.<Plaque>of()) {
            if (n++ >= 40) break;
            JsonObject x = new JsonObject();
            x.addProperty("id", pl.id);
            x.addProperty("titre", pl.titre);
            x.addProperty("texte", pl.aPlat(160));
            ancre(x, pl);
            x.addProperty("par", pl.supprimee.pseudo());
            x.addProperty("le", f.format(new Date(pl.supprimee.le())));
            x.addProperty("motif", pl.supprimee.motif());
            long reste = pl.supprimee.le() + PlaqueRegles.CORBEILLE_JOURS * 86_400_000L - now;
            x.addProperty("reste", Math.max(0, (reste + 86_399_999) / 86_400_000));
            c.add(x);
        }
        o.add("corbeille", c);
        envoyer(p, o);
    }

    private static void ancre(JsonObject x, Plaque pl) {
        Ancre a = pl.ancre;
        x.addProperty("k", a.type().id());
        x.addProperty("monde", a.monde());
        if (a.type() == Ancre.Type.BLOC) {
            x.addProperty("x", a.x());
            x.addProperty("y", a.y());
            x.addProperty("z", a.z());
            JsonArray b = new JsonArray();
            b.add(a.dx());
            b.add(a.dy());
            b.add(a.dz());
            x.add("b", b);
            if (!a.face().isEmpty()) x.addProperty("f", a.face());
        } else if (a.type() == Ancre.Type.ENTITE) {
            x.addProperty("x", a.ex());
            x.addProperty("y", a.ey());
            x.addProperty("z", a.ez());
            x.addProperty("w", a.ew());
            x.addProperty("h", a.eh());
            x.addProperty("d", a.ed());
            x.addProperty("e", a.entite().toString());
        }
    }

    private void toast(Player p, String m) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "toast");
        o.addProperty("m", m);
        if (p.getListeningPluginChannels().contains(CANAL)) envoyer(p, o);
        else p.sendMessage("§6[Plaques] §f" + m);
    }

    private void envoyer(Player p, JsonObject o) {
        if (!plugin.isEnabled()) return;
        p.sendPluginMessage(plugin, CANAL, gson.toJson(o).getBytes(StandardCharsets.UTF_8));
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        try { return e == null || e.isJsonNull() ? "" : e.getAsString(); } catch (RuntimeException ex) { return ""; }
    }

    private static int intDe(JsonObject o, String k, int def) {
        try { JsonElement e = o.get(k); return e == null || e.isJsonNull() ? def : e.getAsInt(); } catch (RuntimeException ex) { return def; }
    }

    private static boolean boolDe(JsonObject o, String k) {
        try { JsonElement e = o.get(k); return e != null && !e.isJsonNull() && e.getAsBoolean(); } catch (RuntimeException ex) { return false; }
    }
}
