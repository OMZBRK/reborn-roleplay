package com.reborn.shinobicore.concentration.plaque;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService;
import com.reborn.shinobicore.staff.outils.Narrations;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service des plaques (lot 4, étape B) : création, édition, corbeille, journal {@code STAFF}, contrôle de lecture,
 * ancres envoyées aux joueurs concentrés. Fil principal seulement (les écritures du fichier partent en tâche de fond).
 *
 * <p><b>Le texte d'une plaque n'atteint jamais un client qui n'est pas concentré face à elle</b> (« fais très attention
 * à ça », 01/11) : {@link #verifierLecture} relit à chaque demande la portée (+ 1,5 bloc), le cône de vue et la ligne de
 * vue depuis les yeux du joueur ; l'état « concentré » est tenu par {@code ConcentrationCanal}.
 */
public final class PlaqueService {

    /** PDC d'un objet porteur de plaque : {@code reborn:plaque = <id>}. */
    public static final NamespacedKey CLE_OBJET = new NamespacedKey("reborn", "plaque");
    private static final int TEXTE_JOURNAL = 300, MORCEAU = 550;
    private static final String BLOQUE = "plaques.yml n'a pas pu être lu : modifications bloquées (voir la console, puis /sc reload).";

    private final ShinobiCore plugin;
    private final PlaqueStore store;
    private boolean sale;
    private long seq;
    private int tache = -1;
    /** Compteur de changements des ancres (ajout, retrait, déplacement, révision) : les clients sont relus. */
    private long version;

    public PlaqueService(ShinobiCore plugin) {
        this.plugin = plugin;
        this.store = new PlaqueStore(plugin.getLogger(), new File(plugin.getDataFolder(), "plaques.yml"));
    }

    public void demarrer() {
        store.charger();
        plugin.getLogger().info("Plaques : " + store.plaques.size() + " chargée(s).");
        // sauvegarde différée (20 s), expirations et corbeille (30 s)
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 600L, 400L).getTaskId();
    }

    public void arreter() {
        if (tache >= 0) Bukkit.getScheduler().cancelTask(tache);
        if (sale) store.ecrire(store.serialiser(), ++seq);
        sale = false;
    }

    /**
     * {@code /sc reload} : l'état en mémoire est écrit puis {@code plaques.yml} relu, d'un bloc sous le verrou du store
     * ({@link PlaqueStore#recharger}) : une écriture de fond encore en route ne peut plus remettre un état périmé ni faire
     * perdre une plaque toute neuve ; une retouche à la main du fichier gagne sur la mémoire ; un fichier corrigé lève le
     * blocage des écritures ; un fichier toujours illisible reste bloqué (SEVERE) et la mémoire valide est gardée.
     */
    public void recharger() {
        store.recharger(store.serialiser(), ++seq);
        sale = false;
        version++;
        plugin.getLogger().info("Plaques : " + store.plaques.size() + " rechargée(s)" + (store.bloque() ? " — FICHIER BLOQUÉ" : "") + ".");
    }

    public Map<String, Plaque> plaques() { return store.plaques; }

    public Plaque get(String id) { return id == null ? null : store.plaques.get(id.trim().toUpperCase(java.util.Locale.ROOT)); }

    public long version() { return version; }

    /* ================================================================ réglages */

    public int porteeDefaut() {
        return PlaqueRegles.bornerPortee(plugin.getConfig().getInt("concentration.plaques.portee-defaut", PlaqueRegles.PORTEE_DEFAUT));
    }

    public double ancresPortee() {
        return Math.max(8, Math.min(64, plugin.getConfig().getDouble("concentration.plaques.ancres-portee", PlaqueRegles.ANCRES_PORTEE)));
    }

    public boolean journalLectures() { return plugin.getConfig().getBoolean("concentration.plaques.journal-lectures", false); }

    /* ================================================================ droits */

    public boolean animation(Player p) {
        var o = plugin.outilsStaff();
        return o != null && o.animation().actif(p.getUniqueId());
    }

    public boolean peutModifier(Player p, Plaque pl) {
        return PlaqueRegles.peutModifier(StaffGrades.of(p), animation(p), p.getUniqueId().equals(pl.creePar), pl.temporaire);
    }

    /* ================================================================ ancres */

    /**
     * Ancre choisie par le staff dans le volet ({@code {"k":"bloc","x","y","z","b":[dx,dy,dz]}}, {@code {"k":"entite","e":uuid}}
     * ou {@code {"k":"objet"}}), vérifiée : même monde, à 8 blocs des yeux (+ 1 de tolérance), boîte de 32 au plus par côté,
     * entité chargée. Renvoie l'ancre, ou une chaîne d'erreur.
     */
    public Object ancreDepuisJson(Player p, JsonObject a) {
        if (a == null) return "Choisis d'abord l'ancre (sélecteur).";
        Ancre.Type type = Ancre.Type.lire(str(a, "k"));
        if (type == null) return "Type d'ancre inconnu.";
        Location eye = p.getEyeLocation();
        switch (type) {
            case BLOC -> {
                int x = num(a, "x"), y = num(a, "y"), z = num(a, "z");
                int dx = 1, dy = 1, dz = 1;
                if (a.has("b") && a.get("b").isJsonArray()) {
                    JsonArray b = a.getAsJsonArray("b");
                    try { dx = b.get(0).getAsInt(); dy = b.get(1).getAsInt(); dz = b.get(2).getAsInt(); }
                    catch (RuntimeException e) { return "Boîte mal formée."; }
                }
                if (!PlaqueRegles.boiteValide(dx, dy, dz)) return "Boîte trop grande (" + PlaqueRegles.BOITE_MAX + " blocs par côté au plus).";
                Ancre an = Ancre.bloc(p.getWorld().getName(), x, y, z, dx, dy, dz, str(a, "f"));
                if (PlaqueRegles.distanceBoite(eye.getX(), eye.getY(), eye.getZ(), an.boite()) > PlaqueRegles.VISEE_STAFF + 1)
                    return "Trop loin : approche-toi à " + (int) PlaqueRegles.VISEE_STAFF + " blocs.";
                return an;
            }
            case ENTITE -> {
                UUID id;
                try { id = UUID.fromString(str(a, "e")); } catch (IllegalArgumentException e) { return "Entité inconnue."; }
                Entity e = Bukkit.getEntity(id);
                if (e == null || !e.getWorld().equals(p.getWorld())) return "Entité introuvable (pas chargée, ou dans un autre monde).";
                if (e instanceof Player) return "Une plaque ne se pose pas sur un joueur.";
                Ancre an = ancreEntite(e);
                if (PlaqueRegles.distanceBoite(eye.getX(), eye.getY(), eye.getZ(), an.boite()) > PlaqueRegles.VISEE_STAFF + 1)
                    return "Trop loin : approche-toi à " + (int) PlaqueRegles.VISEE_STAFF + " blocs.";
                return an;
            }
            default -> {
                return Ancre.objet();
            }
        }
    }

    /** Ce que le staff vise (commande) : l'entité à 6 blocs, sinon le bloc à 8 ; null si rien. */
    public Ancre ancreVisee(Player p) {
        Entity e = p.getTargetEntity(6);
        if (e != null && !(e instanceof Player)) return ancreEntite(e);
        Block b = p.getTargetBlockExact((int) PlaqueRegles.VISEE_STAFF);
        if (b == null) return null;
        // la face visée : la plaque ne se lit que de ce côté (une inscription sur la face intérieure d'un mur)
        org.bukkit.block.BlockFace f = p.getTargetBlockFace((int) PlaqueRegles.VISEE_STAFF);
        String face = f == null ? "" : f.name();
        return Ancre.bloc(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), 1, 1, 1, face);
    }

    private static Ancre ancreEntite(Entity e) {
        return Ancre.entite(e.getWorld().getName(), e.getUniqueId(), boiteDe(e));
    }

    /**
     * Boîte de lecture d'une plaque pour ce joueur : bloc tel quel ; entité à sa position du moment ; objet : l'entité
     * porteuse la plus proche (objet au sol, cadre, {@code ItemDisplay}) dans la portée des ancres. <b>null</b> si l'objet
     * n'est pas là : une entité introuvable (meuble cassé ou retiré — à moins de 32 blocs du joueur son tronçon est
     * chargé, donc introuvable veut dire disparue) ne se lit plus et n'est plus signalée ; le halo et le texte naissent
     * et meurent avec l'objet.
     */
    public double[] boite(Plaque pl, Player p) {
        Ancre a = pl.ancre;
        if (a.type() == Ancre.Type.BLOC) return a.boite();
        if (a.type() == Ancre.Type.ENTITE) {
            Entity e = Bukkit.getEntity(a.entite());
            return e != null && e.isValid() ? boiteDe(e) : null;
        }
        List<Entity> l = porteurs(pl.id, p);
        return l.isEmpty() ? null : boiteDe(l.get(0));
    }

    /** Boîte de lecture d'une entité ({@link Ancre#boiteLecture}) : cadre ou tableau autour de son centre. */
    private static double[] boiteDe(Entity e) {
        BoundingBox bb = e.getBoundingBox();
        return Ancre.boiteLecture(bb.getMinX(), bb.getMinY(), bb.getMinZ(), bb.getMaxX(), bb.getMaxY(), bb.getMaxZ(),
                e instanceof org.bukkit.entity.Hanging);
    }

    /** L'entité de l'ancre a disparu alors que son tronçon est chargé (meuble cassé) : signalée au staff. */
    public boolean absente(Plaque pl) {
        Ancre a = pl.ancre;
        if (a.type() != Ancre.Type.ENTITE) return false;
        Entity e = Bukkit.getEntity(a.entite());
        if (e != null && e.isValid()) return false;
        World w = Bukkit.getWorld(a.monde());
        return w != null && w.isChunkLoaded((int) Math.floor(a.ex()) >> 4, (int) Math.floor(a.ez()) >> 4);
    }

    /** Monde de l'ancre (objet : celui du joueur, l'entité porteuse y est cherchée). */
    private static boolean memeMonde(Plaque pl, Player p) {
        Ancre a = pl.ancre;
        if (a.type() == Ancre.Type.OBJET) return true;
        if (a.type() == Ancre.Type.ENTITE) {
            Entity e = Bukkit.getEntity(a.entite());
            if (e != null) return e.getWorld().equals(p.getWorld());
        }
        return a.monde().equals(p.getWorld().getName());
    }

    /**
     * Les entités qui portent l'objet de la plaque {@code id} dans la portée des ancres, la plus proche d'abord. Une
     * plaque d'objet peut avoir plusieurs exemplaires (deux cadres) : la lecture les essaie tous ({@link #verifierLecture}),
     * pas seulement le plus proche (qui peut être derrière le joueur).
     */
    private List<Entity> porteurs(String id, Player p) {
        double r = ancresPortee();
        List<Entity> l = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(r, r, r)) if (id.equals(idPorte(e))) l.add(e);
        Location pl = p.getLocation();
        l.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(pl)));
        return l;
    }

    /** Id de plaque porté par l'objet d'une entité (objet au sol, cadre, ItemDisplay), sinon null. */
    public static String idPorte(Entity e) {
        ItemStack it = e instanceof Item i ? i.getItemStack() : e instanceof ItemFrame f ? f.getItem()
                : e instanceof ItemDisplay d ? d.getItemStack() : null;
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(CLE_OBJET, PersistentDataType.STRING);
    }

    /**
     * Ancres lisibles autour du joueur (32 blocs, sans le texte) : {@code [{"id","rev","k":"bloc","x","y","z","b":[…],"p"}
     * | {"id","rev","k":"entite","x","y","z","w","h","p"}]} ; un objet porteur part comme une entité. {@code sig} reçoit
     * une empreinte de la liste (envoi seulement si elle change).
     */
    public JsonArray ancres(Player p, StringBuilder sig) {
        JsonArray l = new JsonArray();
        long now = System.currentTimeMillis();
        Location loc = p.getEyeLocation();
        double r = ancresPortee();
        boolean objets = false;
        for (Plaque pl : store.plaques.values()) {
            if (!pl.lisible(now)) continue;
            if (pl.ancre.type() == Ancre.Type.OBJET) { objets = true; continue; }
            if (!memeMonde(pl, p)) continue;
            double[] b = boite(pl, p);
            if (b == null || PlaqueRegles.distanceBoite(loc.getX(), loc.getY(), loc.getZ(), b) > r) continue;
            l.add(ancreJson(pl, b));
            sig.append(pl.id).append(':').append(pl.rev).append(':').append(Math.round(b[0] * 10)).append(',')
                    .append(Math.round(b[1] * 10)).append(',').append(Math.round(b[2] * 10)).append(';');
        }
        if (objets) {
            for (Entity e : p.getNearbyEntities(r, r, r)) {
                String id = idPorte(e);
                if (id == null) continue;
                Plaque pl = store.plaques.get(id);
                if (pl == null || !pl.lisible(now) || pl.ancre.type() != Ancre.Type.OBJET) continue;
                double[] b = boiteDe(e);
                l.add(ancreJson(pl, b));
                sig.append(pl.id).append(':').append(pl.rev).append(':').append(Math.round(b[0] * 10)).append(',')
                        .append(Math.round(b[1] * 10)).append(',').append(Math.round(b[2] * 10)).append(';');
            }
        }
        return l;
    }

    private static JsonObject ancreJson(Plaque pl, double[] b) {
        JsonObject o = new JsonObject();
        o.addProperty("id", pl.id);
        o.addProperty("rev", pl.rev);
        if (pl.ancre.type() == Ancre.Type.BLOC) {
            o.addProperty("k", "bloc");
            o.addProperty("x", (int) b[0]);
            o.addProperty("y", (int) b[1]);
            o.addProperty("z", (int) b[2]);
            JsonArray t = new JsonArray();
            t.add(pl.ancre.dx());
            t.add(pl.ancre.dy());
            t.add(pl.ancre.dz());
            o.add("b", t);
            if (!pl.ancre.face().isEmpty()) o.addProperty("f", pl.ancre.face());
        } else {
            o.addProperty("k", "entite");
            o.addProperty("x", arrondi((b[0] + b[3]) / 2));
            o.addProperty("y", arrondi(b[1]));
            o.addProperty("z", arrondi((b[2] + b[5]) / 2));
            o.addProperty("w", arrondi(b[3] - b[0]));
            o.addProperty("h", arrondi(b[4] - b[1]));
            o.addProperty("d", arrondi(b[5] - b[2]));
        }
        o.addProperty("p", pl.portee);
        return o;
    }

    private static double arrondi(double v) { return Math.round(v * 100) / 100.0; }

    /* ================================================================ lecture */

    /**
     * Le texte de {@code pl} peut-il partir chez {@code p} ? null = oui ; sinon le motif (jamais montré au joueur). Ne vérifie
     * pas la concentration (tenue par le canal).
     */
    public String verifierLecture(Player p, Plaque pl) {
        long now = System.currentTimeMillis();
        if (pl == null) return "inconnue";
        if (!pl.lisible(now)) return "inactive";
        if (!memeMonde(pl, p)) return "monde";
        if (pl.ancre.type() == Ancre.Type.OBJET) {
            // plusieurs exemplaires : il suffit que l'un d'eux soit devant le joueur
            List<Entity> l = porteurs(pl.id, p);
            if (l.isEmpty()) return "objet absent";
            String motif = null;
            for (Entity e : l) {
                motif = verifierBoite(p, boiteDe(e), pl.portee, true);
                if (motif == null) return null;
            }
            return motif;
        }
        double[] b = boite(pl, p);
        if (b == null) return "objet absent";
        if (pl.ancre.type() == Ancre.Type.BLOC) {
            Location eye = p.getEyeLocation();
            // la face visée au sélecteur : de l'autre côté du bloc (dehors, face au mur du tombeau), rien
            if (!PlaqueRegles.faceVisible(pl.ancre.face(), eye.getX(), eye.getY(), eye.getZ(), b)) return "face";
        }
        return verifierBoite(p, b, pl.portee, pl.ancre.type() != Ancre.Type.BLOC);
    }

    /** Portée (+ 1,5), cône, ligne de vue des yeux du joueur vers la boîte {@code b} ; null = lisible. */
    private static String verifierBoite(Player p, double[] b, int portee, boolean entite) {
        Location eye = p.getEyeLocation();
        if (!PlaqueRegles.aPortee(eye.getX(), eye.getY(), eye.getZ(), b, portee)) return "loin";
        Vector d = eye.getDirection();
        if (!PlaqueRegles.dansCone(eye.getX(), eye.getY(), eye.getZ(), d.getX(), d.getY(), d.getZ(), b)) return "hors vue";
        if (!enVue(p.getWorld(), eye, b, entite)) return "mur";
        return null;
    }

    /**
     * Ligne de vue des yeux à la boîte : vers le point le plus proche (rentré de 5 cm) ou vers le centre. Les blocs
     * passables sont ignorés ; le rayon doit atteindre la boîte elle-même (impact dans la boîte, 2 cm de marge) sans avoir
     * touché de bloc plein avant. Seule exception, pour une entité : les <b>barrières</b> collées au meuble (hitbox d'un
     * meuble Nexo, à un bloc de sa boîte au plus) sont traversées — jamais un mur, même collé à l'objet.
     */
    private static boolean enVue(World w, Location eye, double[] b, boolean entite) {
        return PlaqueRegles.enVue(traceur(w), eye.getX(), eye.getY(), eye.getZ(), b, entite);
    }

    /** Rayon du monde (blocs passables et fluides ignorés) au format de {@link PlaqueRegles#enVue}. */
    private static PlaqueRegles.Traceur traceur(World w) {
        return (ox, oy, oz, dx, dy, dz, dist) -> {
            RayTraceResult r = w.rayTraceBlocks(new Location(w, ox, oy, oz), new Vector(dx, dy, dz), dist,
                    FluidCollisionMode.NEVER, true);
            if (r == null || r.getHitBlock() == null) return null;
            Vector h = r.getHitPosition();
            Block hb = r.getHitBlock();
            return new PlaqueRegles.Impact(h.getX(), h.getY(), h.getZ(), hb.getX(), hb.getY(), hb.getZ(),
                    hb.getType() == org.bukkit.Material.BARRIER);
        };
    }

    /** Une lecture servie : compteur (« lue 37 fois »), journal facultatif ({@code journal-lectures}). */
    public void lue(Player p, Plaque pl) {
        pl.lectures++;
        pl.derniereLecture = System.currentTimeMillis();
        sale = true;
        if (journalLectures()) journal(JournalService.Categorie.INTERACTION, "PLAQUE_LUE", p, pl, pl.aPlat(80));
    }

    /** Segments au format du canal ({@code [[texte, couleur]…]}). */
    public static JsonArray segsJson(List<Narrations.Segment> segs) {
        JsonArray a = new JsonArray();
        for (Narrations.Segment s : segs) {
            JsonArray x = new JsonArray();
            x.add(s.texte());
            x.add(s.couleur());
            a.add(x);
        }
        return a;
    }

    /* ================================================================ édition */

    /** Résultat d'une action du staff : la plaque, ou l'erreur à lui montrer. */
    public record Resultat(Plaque plaque, String erreur) {
        static Resultat ko(String e) { return new Resultat(null, e); }

        public boolean ok() { return erreur == null; }
    }

    public Resultat creer(Player s, Ancre ancre, List<Narrations.Segment> segs, String titre, int portee, int expireMin) {
        if (store.bloque()) return Resultat.ko(BLOQUE);
        int grade = StaffGrades.of(s);
        boolean anim = animation(s);
        if (!PlaqueRegles.peutCreer(grade, anim)) return refus(s, "créer", "Réservé aux modérateurs (ou au mode animation).");
        String e = PlaqueRegles.validerTexte(Narrations.caracteres(segs));
        if (e != null) return Resultat.ko(e);
        boolean temp = PlaqueRegles.temporaire(grade, anim);
        if (temp && expireMin == 0) expireMin = PlaqueRegles.ANIM_EXPIRE_MAX_MIN;
        e = PlaqueRegles.validerExpire(temp, expireMin);
        if (e != null) return Resultat.ko(e);
        ItemStack main = null;
        if (ancre.type() == Ancre.Type.OBJET) {
            main = s.getInventory().getItemInMainHand();
            if (main.getType().isAir()) return Resultat.ko("Prends en main l'objet qui portera la plaque.");
            ItemMeta m = main.getItemMeta();
            if (m == null) return Resultat.ko("Cet objet ne peut pas porter de plaque.");
            String deja = m.getPersistentDataContainer().get(CLE_OBJET, PersistentDataType.STRING);
            if (deja != null && store.plaques.containsKey(deja)) return Resultat.ko("Cet objet porte déjà la plaque " + deja + ".");
        }
        long now = System.currentTimeMillis();
        Plaque p = new Plaque(store.nouvelId());
        p.titre = titre == null ? "" : titre.strip();
        if (p.titre.length() > 60) p.titre = p.titre.substring(0, 60);
        p.segs = List.copyOf(segs);
        p.ancre = ancre;
        p.portee = PlaqueRegles.bornerPortee(portee <= 0 ? porteeDefaut() : portee);
        p.expire = expireMin > 0 ? now + expireMin * 60_000L : 0;
        p.temporaire = temp;
        p.creeLe = now;
        p.creePar = s.getUniqueId();
        p.creePseudo = s.getName();
        if (main != null) {
            ItemMeta m = main.getItemMeta();
            m.getPersistentDataContainer().set(CLE_OBJET, PersistentDataType.STRING, p.id);
            main.setItemMeta(m);
        }
        store.plaques.put(p.id, p);
        changement();
        journal(JournalService.Categorie.STAFF, "PLAQUE_CREEE", s, p, p.id + " · " + ancre.libelle() + " · portée " + p.portee
                + (p.expire > 0 ? " · expire dans " + expireMin + " min" : "") + (temp ? " · temporaire" : "") + " · « "
                + p.aPlat(TEXTE_JOURNAL) + " »");
        journalTexte(s, p);
        return new Resultat(p, null);
    }

    /** Modification : champs null = inchangés. Le {@code rev} monte (caches des clients). */
    public Resultat modifier(Player s, String id, List<Narrations.Segment> segs, String titre, Integer portee, Integer expireMin,
                             Boolean actif) {
        if (store.bloque()) return Resultat.ko(BLOQUE);
        Plaque p = get(id);
        if (p == null || p.dansCorbeille()) return Resultat.ko("Plaque introuvable : " + id + ".");
        if (!peutModifier(s, p)) return refus(s, "modifier " + p.id, "Réservé aux modérateurs (l'animateur : ses plaques temporaires).");
        String avant = p.aPlat(TEXTE_JOURNAL);
        List<String> quoi = new ArrayList<>();
        if (segs != null) {
            String e = PlaqueRegles.validerTexte(Narrations.caracteres(segs));
            if (e != null) return Resultat.ko(e);
            if (!segs.equals(p.segs)) { p.segs = List.copyOf(segs); quoi.add("texte"); }
        }
        if (expireMin != null) {
            long now = System.currentTimeMillis();
            // animateur : 24 h après la création au plus (pas de prolongation) ; Modérateur : « jamais » rend permanente
            PlaqueRegles.Expiration x = PlaqueRegles.expireModif(StaffGrades.of(s) >= StaffGrades.MODO, p.temporaire, p.creeLe,
                    now, expireMin);
            if (x.erreur() != null) return Resultat.ko(x.erreur());
            boolean levee = p.temporaire && !x.temporaire();
            if (x.expire() != p.expire || levee) {
                p.expire = x.expire();
                p.temporaire = x.temporaire();
                quoi.add((p.expire > 0 ? "expire dans " + Math.max(1, (p.expire - now + 59_999) / 60_000) + " min" : "sans expiration")
                        + (levee ? " · n'est plus temporaire" : ""));
            }
        }
        if (titre != null && !titre.strip().equals(p.titre)) { p.titre = titre.strip(); quoi.add("titre"); }
        if (portee != null && PlaqueRegles.bornerPortee(portee) != p.portee) {
            p.portee = PlaqueRegles.bornerPortee(portee);
            quoi.add("portée " + p.portee);
        }
        if (actif != null && actif != p.actif) { p.actif = actif; quoi.add(actif ? "activée" : "désactivée"); }
        if (quoi.isEmpty()) return new Resultat(p, null);
        p.changer(s.getUniqueId(), s.getName(), System.currentTimeMillis());
        changement();
        journal(JournalService.Categorie.STAFF, "PLAQUE_MODIFIEE", s, p, p.id + " · " + String.join(", ", quoi)
                + (quoi.contains("texte") ? " · « " + avant + " » → « " + p.aPlat(TEXTE_JOURNAL) + " »" : ""));
        if (quoi.contains("texte")) journalTexte(s, p);
        return new Resultat(p, null);
    }

    public Resultat deplacer(Player s, String id, Ancre ancre) {
        if (store.bloque()) return Resultat.ko(BLOQUE);
        Plaque p = get(id);
        if (p == null || p.dansCorbeille()) return Resultat.ko("Plaque introuvable : " + id + ".");
        if (!peutModifier(s, p)) return refus(s, "déplacer " + p.id, "Réservé aux modérateurs (l'animateur : ses plaques temporaires).");
        if (ancre.type() == Ancre.Type.OBJET || p.ancre.type() == Ancre.Type.OBJET)
            return Resultat.ko("Une plaque d'objet suit son objet : elle ne se déplace pas.");
        String avant = p.ancre.libelle();
        p.ancre = ancre;
        p.changer(s.getUniqueId(), s.getName(), System.currentTimeMillis());
        changement();
        journal(JournalService.Categorie.STAFF, "PLAQUE_DEPLACEE", s, p, p.id + " · " + avant + " → " + ancre.libelle());
        return new Resultat(p, null);
    }

    public Resultat supprimer(Player s, String id, String motif) {
        if (store.bloque()) return Resultat.ko(BLOQUE);
        Plaque p = get(id);
        if (p == null || p.dansCorbeille()) return Resultat.ko("Plaque introuvable : " + id + ".");
        if (!peutModifier(s, p)) return refus(s, "supprimer " + p.id, "Réservé aux modérateurs (l'animateur : ses plaques temporaires).");
        if (!PlaqueRegles.motifValide(motif)) return Resultat.ko("Donne un motif (3 caractères au moins).");
        p.supprimee = new Plaque.Suppression(System.currentTimeMillis(), s.getUniqueId(), s.getName(), motif.strip());
        p.changer(s.getUniqueId(), s.getName(), System.currentTimeMillis());
        changement();
        journal(JournalService.Categorie.STAFF, "PLAQUE_SUPPRIMEE", s, p, p.id + " · motif : " + motif.strip() + " · « "
                + p.aPlat(TEXTE_JOURNAL) + " » · corbeille " + PlaqueRegles.CORBEILLE_JOURS + " jours");
        return new Resultat(p, null);
    }

    public Resultat restaurer(Player s, String id) {
        if (store.bloque()) return Resultat.ko(BLOQUE);
        Plaque p = get(id);
        if (p == null || !p.dansCorbeille()) return Resultat.ko("Aucune plaque " + id + " dans la corbeille.");
        if (!PlaqueRegles.peutRestaurer(StaffGrades.of(s))) return refus(s, "restaurer " + p.id, "Réservé aux admins.");
        p.supprimee = null;
        boolean expiree = p.expire > 0 && p.expire <= System.currentTimeMillis();
        if (expiree) { p.expire = 0; p.temporaire = false; }
        p.changer(s.getUniqueId(), s.getName(), System.currentTimeMillis());
        changement();
        journal(JournalService.Categorie.STAFF, "PLAQUE_RESTAUREE", s, p, p.id + (expiree ? " · était expirée : sans expiration désormais" : ""));
        return new Resultat(p, null);
    }

    /**
     * Téléporte le staff devant l'ancre (2,5 blocs du côté où il se trouve), regard sur la plaque, sur une place sûre (pieds
     * et tête libres, sol plein dessous, cherchée de 3 blocs sous l'ancre à 6 au-dessus, puis en tournant autour) ;
     * inscrit au journal {@code STAFF} ({@code PLAQUE_TP}, comme {@code TP_VERS}). Helper et plus.
     */
    public Resultat teleporter(Player s, String id) {
        Plaque p = get(id);
        if (p == null) return Resultat.ko("Plaque introuvable : " + id + ".");
        if (!PlaqueRegles.voirTout(StaffGrades.of(s))) return refus(s, "téléport " + p.id, "Réservé au staff (Helper et plus).");
        if (p.ancre.type() == Ancre.Type.OBJET) return Resultat.ko("Plaque d'objet : elle suit son objet, aucune place fixe.");
        World w = Bukkit.getWorld(p.ancre.monde());
        if (w == null) return Resultat.ko("Monde absent : " + p.ancre.monde() + ".");
        double[] b = p.ancre.type() == Ancre.Type.BLOC ? p.ancre.boite() : boite(p, s);
        if (b == null) b = p.ancre.boite();   // meuble disparu : sa dernière place connue
        double[] c = PlaqueRegles.centre(b);
        Vector dir = s.getWorld().equals(w) ? new Vector(s.getX() - c[0], 0, s.getZ() - c[2]) : new Vector(0, 0, 1);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        dir.normalize();
        double demi = Math.max(b[3] - b[0], b[5] - b[2]) / 2;
        Location to = placeSure(w, c, b, dir, demi + 2.5);
        if (to == null) return Resultat.ko("Aucune place libre devant la plaque " + p.id + " : /plaque info " + p.id + " pour ses coordonnées.");
        to.setDirection(new Vector(c[0] - to.getX(), c[1] - (to.getY() + 1.6), c[2] - to.getZ()));
        if (plugin.outilsStaff() != null) plugin.outilsStaff().memoriser(s);
        s.teleport(to);
        plugin.journal().entree(JournalService.Categorie.STAFF, "PLAQUE_TP").acteur(s).cibleTexte("plaque " + p.id)
                .detail(p.id + " · " + p.ancre.libelle()).lieu(to).ecrire();
        return new Resultat(p, null);
    }

    /**
     * Place où poser les pieds à {@code recul} blocs du centre dans la direction {@code dir} (puis en tournant autour de
     * la plaque par pas de 45°) : bloc des pieds et de la tête passables, bloc dessous plein ; null si rien de sûr.
     */
    private static Location placeSure(World w, double[] c, double[] b, Vector dir, double recul) {
        int y0 = (int) Math.floor(b[1]);
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(45.0 * ((k + 1) / 2) * (k % 2 == 0 ? 1 : -1));
            double dx = dir.getX() * Math.cos(a) - dir.getZ() * Math.sin(a), dz = dir.getX() * Math.sin(a) + dir.getZ() * Math.cos(a);
            int bx = (int) Math.floor(c[0] + dx * recul), bz = (int) Math.floor(c[2] + dz * recul);
            for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, 5, 6}) {
                int y = y0 + dy;
                if (y <= w.getMinHeight() || y + 1 >= w.getMaxHeight()) continue;
                Block pieds = w.getBlockAt(bx, y, bz), tete = w.getBlockAt(bx, y + 1, bz), sol = w.getBlockAt(bx, y - 1, bz);
                if (pieds.isPassable() && !pieds.isLiquid() && tete.isPassable() && !tete.isLiquid() && sol.getType().isSolid())
                    return new Location(w, bx + 0.5, y, bz + 0.5);
            }
        }
        return null;
    }

    private Resultat refus(Player s, String quoi, String msg) {
        plugin.journal().entree(JournalService.Categorie.STAFF, "PLAQUE_REFUS").acteur(s).detail(quoi + " · grade "
                + StaffGrades.name(StaffGrades.of(s)) + (animation(s) ? " · mode animation" : "")).lieu(s.getLocation()).ecrire();
        return Resultat.ko(msg);
    }

    /* ================================================================ tâche, journal */

    private void changement() {
        version++;
        sale = true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long purge = now - PlaqueRegles.CORBEILLE_JOURS * 86_400_000L;
        List<String> retirees = new ArrayList<>();
        for (Plaque p : store.plaques.values()) {
            if (p.supprimee == null && p.expire > 0 && now >= p.expire) {
                p.supprimee = new Plaque.Suppression(now, null, "Système", "expirée");
                p.changer(null, "Système", now);
                changement();
                plugin.journal().entree(JournalService.Categorie.STAFF, "PLAQUE_EXPIREE").acteur(Bukkit.getConsoleSender())
                        .cibleTexte("plaque " + p.id).detail(p.id + " · « " + p.aPlat(TEXTE_JOURNAL) + " »").ecrire();
            } else if (p.supprimee != null && p.supprimee.le() < purge) retirees.add(p.id);
        }
        for (String id : retirees) {
            store.plaques.remove(id);
            changement();
            plugin.journal().entree(JournalService.Categorie.STAFF, "PLAQUE_PURGEE").acteur(Bukkit.getConsoleSender())
                    .cibleTexte("plaque " + id).detail(id + " · corbeille de " + PlaqueRegles.CORBEILLE_JOURS + " jours vidée").ecrire();
        }
        // sauvegarde différée : sérialisée ici (fil principal), écrite en tâche de fond ; la plus récente gagne
        if (sale) {
            String yaml = store.serialiser();
            long n = ++seq;
            sale = false;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> store.ecrire(yaml, n));
        }
    }

    private void journal(JournalService.Categorie cat, String action, Player s, Plaque p, String detail) {
        JournalService.Entree e = plugin.journal().entree(cat, action).acteur(s).cibleTexte("plaque " + p.id).detail(detail);
        Location l = lieu(p);
        if (l != null) e.lieu(l);
        e.ecrire();
    }

    /** Texte entier en morceaux de 550 caractères ({@code PLAQUE_TEXTE}, comme {@code NARRATION_TEXTE}). */
    private void journalTexte(Player s, Plaque p) {
        String t = p.balise();
        int n = (t.length() + MORCEAU - 1) / MORCEAU;
        for (int i = 0, k = 1; i < t.length(); k++) {
            int fin = Math.min(t.length(), i + MORCEAU);
            if (fin < t.length() && Character.isHighSurrogate(t.charAt(fin - 1))) fin--;
            plugin.journal().entree(JournalService.Categorie.STAFF, "PLAQUE_TEXTE").acteur(s).cibleTexte("plaque " + p.id)
                    .detail(p.id + " · " + k + "/" + n + " · " + t.substring(i, fin)).ecrire();
            i = fin;
        }
    }

    public static Location lieu(Plaque p) {
        if (p.ancre == null || p.ancre.type() == Ancre.Type.OBJET) return null;
        World w = Bukkit.getWorld(p.ancre.monde());
        if (w == null) return null;
        double[] c = PlaqueRegles.centre(p.ancre.boite());
        return new Location(w, c[0], c[1], c[2]);
    }

    /**
     * Plaques hors corbeille autour de {@code p} (même monde), triées par distance ; les plaques d'objet en fin. Un
     * animateur sans grade staff ne voit que les siennes ({@link PlaqueRegles#voirTout}).
     */
    public List<Plaque> autour(Player p, int rayon, String q) {
        List<Plaque> l = new ArrayList<>();
        String f = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        Location eye = p.getEyeLocation();
        boolean tout = PlaqueRegles.voirTout(StaffGrades.of(p));
        for (Plaque pl : store.plaques.values()) {
            if (pl.dansCorbeille()) continue;
            if (!tout && !p.getUniqueId().equals(pl.creePar)) continue;
            if (!f.isEmpty() && !(pl.id + " " + pl.titre + " " + pl.aPlat(800)).toLowerCase(java.util.Locale.ROOT).contains(f)) continue;
            if (pl.ancre.type() != Ancre.Type.OBJET) {
                if (!pl.ancre.monde().equals(p.getWorld().getName())) continue;
                if (distance(pl, eye) > rayon) continue;
            }
            l.add(pl);
        }
        l.sort(Comparator.comparingDouble(pl -> pl.ancre.type() == Ancre.Type.OBJET ? Double.MAX_VALUE : distance(pl, eye)));
        return l;
    }

    public List<Plaque> corbeille() {
        List<Plaque> l = new ArrayList<>();
        for (Plaque pl : store.plaques.values()) if (pl.dansCorbeille()) l.add(pl);
        l.sort(Comparator.comparingLong((Plaque pl) -> pl.supprimee.le()).reversed());
        return l;
    }

    public static double distance(Plaque pl, Location eye) {
        double[] b = pl.ancre.boite();
        return b == null ? -1 : PlaqueRegles.distanceBoite(eye.getX(), eye.getY(), eye.getZ(), b);
    }

    private static String str(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; } catch (RuntimeException e) { return ""; }
    }

    private static int num(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsInt() : 0; } catch (RuntimeException e) { return 0; }
    }
}
