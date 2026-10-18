package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.EtatService;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.ModificateurService;
import com.reborn.shinobicore.api.StunService;
import com.reborn.shinobicore.api.etat.Cadre;
import com.reborn.shinobicore.api.etat.Capacite;
import com.reborn.shinobicore.api.etat.Capacites;
import com.reborn.shinobicore.api.etat.Controle;
import com.reborn.shinobicore.api.etat.Couche;
import com.reborn.shinobicore.api.etat.Etat;
import com.reborn.shinobicore.api.etat.Etiquette;
import com.reborn.shinobicore.api.etat.Famille;
import com.reborn.shinobicore.api.etat.Lien;
import com.reborn.shinobicore.api.etat.Modificateur;
import com.reborn.shinobicore.api.etat.ModificateurSpec;
import com.reborn.shinobicore.api.etat.Principal;
import com.reborn.shinobicore.api.etat.Resultat;
import com.reborn.shinobicore.api.etat.Source;
import com.reborn.shinobicore.api.etat.Verdict;
import com.reborn.shinobicore.api.etat.Vital;
import com.reborn.shinobicore.api.event.EtatChangeEvent;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.ko.KoState;
import com.reborn.shinobicore.ko.ata.AtaManager;
import com.reborn.shinobicore.modificateur.ModificateurRegistry;
import com.reborn.shinobicore.modificateur.TypeModificateur;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Implémentation de {@link EtatService} (docs/technique/ETATS_JOUEUR.md §8, étapes 1 à 8).
 *
 * <p><b>Une façade</b> qui calcule les trois couches en lisant les détenteurs existants (la chaîne KO pour le vital,
 * les duels / zones / staff pour le cadre, le portage, l'exécution, ShinobiCombat pour le contrôle) et tient elle-même ce
 * qui n'a pas d'autre détenteur : les valeurs de contrôle <b>posées par l'API</b> (étourdi, garde, entrave, contrôle,
 * corps vide — avec lien et durée), les facteurs physiques des add-ons, les actions longues ({@link Occupations}), la
 * prison ({@link Prison}). Aucune donnée n'est stockée deux fois : un état dérivé (ATA, surcharge, en combat, gel…)
 * est relu chez son système.
 *
 * <p>La matrice ({@code etats.yml}) donne les capacités ; les lignes actives sont prises <b>dans l'ordre du fichier</b>
 * (= la priorité de l'état principal), puis les lignes synthétiques (types du registre des modificateurs, facteurs).
 *
 * <p>Thread principal seulement. Calcul à la demande (≈ 20 lectures de tables + la matrice) : pas de cache, donc jamais
 * d'état périmé juste après une écriture.
 */
public final class EtatServiceImpl implements EtatService {

    /** Une valeur de contrôle tenue par le service. */
    record Tenu(Controle valeur, String variante, Lien lien, long finEpoch, Source source, UUID persoSource) {
        boolean expire(long now) { return finEpoch > 0 && now >= finEpoch; }
    }

    private final ShinobiCore plugin;
    private final File fichier;
    private volatile Matrice matrice = new Matrice(Map.of(), 0.25);
    private final List<String> erreurs = new ArrayList<>();
    private final Map<UUID, Tenu> tenus = new ConcurrentHashMap<>();
    /** compte → clé → {vitesse, saut}. */
    private final Map<UUID, Map<String, double[]>> facteurs = new ConcurrentHashMap<>();
    private final Occupations occupations;
    private final Prison prison;
    private boolean sync = true, journal = true, applicateur = true;
    private StunService stun;
    private long stunCherche;

    public EtatServiceImpl(ShinobiCore plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "etats.yml");
        this.occupations = new Occupations(plugin, this);
        this.prison = new Prison(plugin);
    }

    /* ================================================================ cycle */

    public void charger() {
        if (!fichier.exists()) plugin.saveResource("etats.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        // Lignes ajoutées par une version plus récente : on complète depuis le jar (jamais d'écrasement).
        try (var in = plugin.getResource("etats.yml")) {
            if (in != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                y.setDefaults(def);
            }
        } catch (Exception ignored) { }
        erreurs.clear();
        Map<String, Object> racine = new LinkedHashMap<>();
        racine.put("vitesse-plancher", y.getDouble("vitesse-plancher", 0.25));
        Map<String, Object> lignes = new LinkedHashMap<>();
        ConfigurationSection ls = y.getConfigurationSection("lignes");
        // Ordre : celui du jar (priorité), les lignes du disque remplacent les mêmes clés.
        ConfigurationSection def = y.getDefaults() != null ? y.getDefaults().getConfigurationSection("lignes") : null;
        if (def != null) for (String k : def.getKeys(false)) lignes.put(k, versMap(ls != null && ls.isConfigurationSection(k)
                ? ls.getConfigurationSection(k) : def.getConfigurationSection(k)));
        if (ls != null) for (String k : ls.getKeys(false)) if (!lignes.containsKey(k)) lignes.put(k, versMap(ls.getConfigurationSection(k)));
        racine.put("lignes", lignes);
        matrice = Matrice.lire(racine, erreurs);
        sync = y.getBoolean("sync", true);
        journal = y.getBoolean("journal", true);
        applicateur = y.getBoolean("applicateur", true);
        for (String e : erreurs) plugin.getLogger().warning("[etats.yml] " + e);
        plugin.getLogger().info("[etats] matrice chargée : " + matrice.lignes().size() + " lignes"
                + (erreurs.isEmpty() ? "" : ", " + erreurs.size() + " avertissement(s)") + ".");
        prison.charger();
    }

    public void arreter() {
        prison.sauver();
        occupations.toutArreter("arrêt du serveur");
    }

    private static Map<String, Object> versMap(ConfigurationSection s) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (s == null) return m;
        for (String k : s.getKeys(false)) {
            Object v = s.get(k);
            m.put(k, v instanceof ConfigurationSection cs ? versMap(cs) : v);
        }
        return m;
    }

    public Matrice matrice() { return matrice; }

    public List<String> erreurs() { return List.copyOf(erreurs); }

    public boolean syncActif() { return sync; }

    public boolean journalActif() { return journal; }

    public boolean applicateurActif() { return applicateur; }

    public Occupations occupations() { return occupations; }

    public Prison prison() { return prison; }

    /* ================================================================ lecture */

    @Override
    public Etat etat(UUID compte) {
        return calculer(compte);
    }

    @Override
    public Verdict verifier(UUID compte, Capacite c, Object param) {
        Verdict v = etat(compte).verdict(c);
        // Entravé : techniques sans mudra seulement (§4.6 note ⁴). Une technique à mudras (défaut) est refusée.
        if (c == Capacite.TECHNIQUE && v.limite() && !(param instanceof Boolean b && !b)) {
            return new Verdict(0, v.raison(), v.ligne());
        }
        return v;
    }

    @Override
    public boolean peutCibler(Entity source, Entity cible, Set<Etiquette> etiquettes) {
        if (cible == null) return false;
        if (cible instanceof ArmorStand) return false;   // faux corps, sièges, décor
        if (!(cible instanceof Player p)) return true;
        if (p.isInvulnerable()) return false;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false;
        return etat(p.getUniqueId()).peut(Capacite.CIBLABLE);
    }

    /** Vrai si l'entité est à ignorer par les effets de zone (ancien {@code Verrous.horsCible}). */
    public boolean horsCible(Entity e) {
        return !peutCibler(null, e, Set.of());
    }

    /* ================================================================ calcul */

    StunService stun() {
        long now = System.currentTimeMillis();
        if (stun == null && now > stunCherche) {
            stun = Bukkit.getServicesManager().load(StunService.class);
            if (stun == null) stunCherche = now + 10_000L;   // ShinobiCombat peut s'activer après nous
        }
        return stun;
    }

    /** Vital lu chez la chaîne KO. */
    Vital vital(UUID id) {
        if (plugin.deathScene() != null && plugin.deathScene().isDead(id)) return Vital.MORT;
        KoState st = plugin.ko() != null ? plugin.ko().getKo(id) : null;
        if (st != null) return st.isDowned() ? Vital.A_TERRE : Vital.INCONSCIENT;
        if (plugin.douleur() != null && plugin.douleur().isInPain(id)) return Vital.DOULEUR;
        return Vital.VALIDE;
    }

    /** Cadre : staff, cinématique, sélection / sans personnage, construction, prison, duel, entraînement. */
    private String[] cadre(UUID id, Player p, ShinobiCharacter c) {
        var o = plugin.outilsStaff();
        if (o != null && o.mode().horsJeu(id)) return new String[] {Cadre.STAFF.name(), null};
        if (p != null && plugin.cinematics() != null && plugin.cinematics().isPlaying(p))
            return new String[] {Cadre.HORS_JEU.name(), "CINEMATIQUE"};
        if (c == null || (plugin.characterSelect() != null && plugin.characterSelect().isSelecting(id)))
            return new String[] {Cadre.HORS_JEU.name(), null};
        if (plugin.isStaffBuilding(id)) return new String[] {Cadre.CONSTRUCTION.name(), null};
        if (prison.enPrison(c.id())) return new String[] {Cadre.PRISON.name(), null};
        if (plugin.duels() != null && plugin.duels().inDuel(id)) return new String[] {Cadre.DUEL.name(), null};
        if (p != null && plugin.trainingZones() != null && plugin.trainingZones().isTraining(p))
            return new String[] {Cadre.ENTRAINEMENT.name(), null};
        return new String[] {Cadre.MONDE.name(), null};
    }

    /** Contrôle : valeur la plus prioritaire entre ce que disent les détenteurs et ce que tient le service. */
    private Tenu controle(UUID id, long now) {
        Tenu best = null;
        var porter = plugin.porter();
        UUID carrier = porter != null ? porter.carrierOf(id) : null;
        if (carrier != null) best = new Tenu(Controle.PORTE, null, Lien.de("PORTE", carrier, nom(carrier)), 0,
                Source.systeme("portage"), null);
        var corps = plugin.corps();
        UUID holder = corps != null ? corps.holderOf(id) : null;
        if (holder != null) {
            Tenu t = corps.isExecuting(holder)
                    ? new Tenu(Controle.ENTRAVE, "EXECUTION", Lien.de("EXECUTION", holder, nom(holder)), 0,
                            Source.systeme("exécution"), null)
                    : new Tenu(Controle.PORTE, "GORGE", Lien.de("GORGE", holder, nom(holder)), 0,
                            Source.systeme("prise à la gorge"), null);
            best = mieux(best, t);
        }
        StunService s = stun();
        if (s != null) {
            try {
                if (s.isStunned(id)) best = mieux(best, new Tenu(Controle.ETOURDI, null, null, 0, Source.systeme("combat"), null));
                else if (s.isGuarding(id)) best = mieux(best, new Tenu(Controle.GARDE, null, null, 0, Source.systeme("combat"), null));
            } catch (RuntimeException ignored) { }
        }
        Tenu t = tenus.get(id);
        if (t != null && !t.expire(now)) best = mieux(best, t);
        return best;
    }

    private static Tenu mieux(Tenu a, Tenu b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.valeur().priorite() > a.valeur().priorite() ? b : a;
    }

    private String nom(UUID compte) {
        if (compte == null) return "?";
        ShinobiCharacter c = plugin.characters() != null ? plugin.characters().getActive(compte) : null;
        if (c != null) return c.name();
        Player p = Bukkit.getPlayer(compte);
        return p != null ? p.getName() : "?";
    }

    /** Un calcul et sa trace (lignes actives, dans l'ordre). */
    record Calcul(Etat etat, List<String> lignes) {}

    Etat calculer(UUID id) { return calcul(id).etat(); }

    Calcul calcul(UUID id) {
        long now = System.currentTimeMillis();
        Player p = Bukkit.getPlayer(id);
        ShinobiCharacter c = plugin.characters() != null ? plugin.characters().getActive(id) : null;
        Matrice m = matrice;

        Vital vital = c == null ? Vital.VALIDE : vital(id);
        String[] cadreV = cadre(id, p, c);
        Cadre cadre = Cadre.valueOf(cadreV[0]);
        Tenu tenu = controle(id, now);
        Controle ctrl = tenu == null ? Controle.LIBRE : tenu.valeur();

        // ------------------------------------------------------------ lignes actives (id → paramètre)
        Map<String, String> actives = new LinkedHashMap<>();
        Map<String, double[]> vitesses = new LinkedHashMap<>();
        List<Modificateur> mods = new ArrayList<>();
        activer(actives, m, "cadre." + cadre.name(), cadreV[1], null);
        activer(actives, m, "vital." + vital.name(), null, null);
        if (vital == Vital.DOULEUR && plugin.douleur() != null) {
            vitesses.put("vital.DOULEUR", new double[] {plugin.douleur().vitesse(), 1.0});
        }
        if (ctrl != Controle.LIBRE) activer(actives, m, "controle." + ctrl.name(), tenu.variante(), null);
        if (ctrl == Controle.PORTE && tenu.variante() == null) actives.put("ctx.se_debattre", null);

        KoState st = plugin.ko() != null ? plugin.ko().getKo(id) : null;
        if (st != null && c != null) {
            if (st.isDowned() && !st.isBeingCarried() && plugin.volonte() != null && plugin.volonte().allowed(c)
                    && plugin.volonte().left(c.id()) > 0) actives.put("ctx.volonte", null);
            if (st.cause() == KoState.Cause.HP && st.hospitalOffered()) actives.put("ctx.hopital", null);
        }

        var o = plugin.outilsStaff();
        if (o != null && o.gels().gele(id)) {
            actives.put("mod.gel_staff", null);
            var g = o.gels().gel(id);
            mods.add(new Modificateur("gel_staff", "Gelé par le staff", 1, 0, g != null ? g.par() : "staff", true));
        }
        if (c != null && plugin.ata() != null) {
            AtaManager.View v = plugin.ata().view(c.id());
            if (v != null) {
                actives.put("mod.ata", null);
                mods.add(new Modificateur("ata", v.level() == AtaManager.Level.PLEINE ? "ATA pleine" : "ATA allégée",
                        v.level() == AtaManager.Level.PLEINE ? 2 : 1, 0, "KO", true));
            }
        }
        if (plugin.rpInventory() != null && c != null) {
            int pal = plugin.rpInventory().palier(id);
            if (pal > 0) {
                String lid = "mod.surcharge_" + Math.min(3, pal);
                actives.put(lid, null);
                double[] f = plugin.rpInventory().facteursSurcharge(id);
                if (f != null) vitesses.put(lid, f);
                mods.add(new Modificateur("surcharge", pal == 1 ? "Chargé" : pal == 2 ? "Surchargé" : "Immobile", pal, 0,
                        "sacoche", true));
            }
        }
        long combat = EnCombat.resteMs(id);
        if (combat > 0) {
            actives.put("mod.en_combat", String.valueOf(combat / 1000 + 1));
            mods.add(new Modificateur("en_combat", "En combat", 1, combat, "combat", true));
        }
        boolean modeStaff = o != null && o.mode().horsJeu(id);
        if (!modeStaff && plugin.vanish() != null && plugin.vanish().isVanished(id)) {
            actives.put("mod.invisible", null);
            mods.add(new Modificateur("invisible", "Invisible", 1, 0, "staff", true));
        }
        if (plugin.porter() != null && plugin.porter().isCarrying(id)) {
            actives.put("mod.porteur", null);
            mods.add(new Modificateur("porteur", "Porte quelqu'un", 1, 0, "portage", true));
        }
        if (plugin.corps() != null) {
            if (plugin.corps().isExecuting(id)) activer(actives, m, "mod.tient", "EXECUTION", null);
            else if (plugin.corps().holdsThroat(id)) activer(actives, m, "mod.tient", "GORGE", null);
        }
        Occupations.Occ occ = occupations.de(id);
        if (occ != null) {
            actives.put("mod.occupe", occ.action());
            mods.add(new Modificateur("occupe", "Occupé (" + occ.action() + ")", 1, 0, occ.action(), true));
        }
        if (plugin.duels() != null) {
            String d = plugin.duels().techniqueDenial(id);
            if (d != null) actives.put("ctx.duel_technique", d);
        }

        // ------------------------------------------------------------ registre des modificateurs
        List<Matrice.Activation> synth = new ArrayList<>();
        ModificateurRegistry reg = registre();
        if (reg != null && c != null) {
            for (ModificateurService.Vue v : reg.actifs(id)) {
                TypeModificateur t = reg.type(v.type());
                if (t == null) continue;
                if (t.etatLigne() != null && m.ligne(t.etatLigne()) != null) actives.put(t.etatLigne(), null);
                Set<Capacite> refuse = EnumSet.noneOf(Capacite.class);
                for (String r : t.refuse()) {
                    Capacite cap = Capacite.lire(r);
                    if (cap != null) refuse.add(cap);
                }
                if (!refuse.isEmpty()) synth.add(Matrice.Activation.de(Matrice.Ligne.simple("reg." + t.id(), t.nom(), refuse)));
                mods.add(new Modificateur(t.id(), t.nom(), v.intensite(), v.resteMs(), v.source(), false));
            }
            double rv = reg.multiplicateur(id, "vitesse"), rs = reg.multiplicateur(id, "saut");
            if (Math.abs(rv - 1) > 1e-6 || Math.abs(rs - 1) > 1e-6) {
                synth.add(Matrice.Activation.de(Matrice.Ligne.simple("reg.vitesse", "modificateurs", Set.of()).avecVitesse(rv, rs)));
            }
        }
        Map<String, double[]> f = facteurs.get(id);
        if (f != null) {
            for (var e : f.entrySet()) {
                synth.add(Matrice.Activation.de(Matrice.Ligne.simple("facteur." + e.getKey(), e.getKey(), Set.of())
                        .avecVitesse(e.getValue()[0], e.getValue()[1])));
            }
        }

        // ------------------------------------------------------------ matrice, dans l'ordre du fichier
        List<Matrice.Activation> liste = m.ordonner(actives, vitesses);
        liste.addAll(synth);
        Capacites caps = m.calculer(vital, liste);

        Principal principal = Principal.resoudre(vital, ctrl, cadre, combat > 0);
        List<String> trace = new ArrayList<>(liste.size());
        for (Matrice.Activation a : liste) trace.add(a.ligne().id() + (a.param() != null ? " (" + a.param() + ")" : ""));
        return new Calcul(new Etat(principal, vital, ctrl, cadre, tenu != null ? tenu.lien() : null, List.copyOf(mods), caps),
                trace);
    }

    /** Active {@code base.variante} si la matrice la connaît, sinon {@code base}. */
    private static void activer(Map<String, String> actives, Matrice m, String base, String variante, String param) {
        if (variante != null && m.ligne(base + "." + variante) != null) actives.put(base + "." + variante, param);
        else actives.put(base, param);
    }

    ModificateurRegistry registre() {
        return plugin.nourriture() != null ? plugin.nourriture().modifs() : null;
    }

    /** Lignes de la matrice actives pour ce joueur, dans l'ordre de décision (pour {@code /etats}). */
    public List<String> lignesActives(UUID id) {
        return calcul(id).lignes();
    }

    /* ================================================================ écriture */

    @Override
    public Resultat transition(Player p, Couche couche, String valeur, Source source, Lien lien, long dureeMs) {
        if (p == null || couche == null || valeur == null) return Resultat.REFUSEE;
        UUID id = p.getUniqueId();
        Source src = source != null ? source : Source.systeme("?");
        String v = valeur.trim().toUpperCase(Locale.ROOT);
        Etat avant = calculer(id);
        switch (couche) {
            case VITAL -> {
                Vital nv;
                try { nv = Vital.valueOf(v); } catch (IllegalArgumentException e) { return Resultat.REFUSEE; }
                if (nv == avant.vital()) return Resultat.INCHANGEE;
                if (!annoncer(p, couche, avant.vital().name(), nv.name(), src, lien)) return Resultat.VETO;
                return Resultat.APPLIQUEE;   // la chaîne KO applique (détenteur unique du vital)
            }
            case CADRE -> {
                Cadre nc;
                try { nc = Cadre.valueOf(v); } catch (IllegalArgumentException e) { return Resultat.REFUSEE; }
                if (nc == avant.cadre()) return Resultat.INCHANGEE;
                if (!annoncer(p, couche, avant.cadre().name(), nc.name(), src, lien)) return Resultat.VETO;
                return Resultat.APPLIQUEE;   // zones, duels, staff, prison appliquent
            }
            case CONTROLE -> {
                if (v.startsWith("-")) {   // lever seulement cette valeur (ex. « -GARDE »)
                    Tenu actuel0 = tenus.get(id);
                    String quoi = v.substring(1);
                    if (actuel0 == null || !actuel0.valeur().name().equals(quoi.contains(".") ? quoi.substring(0, quoi.indexOf('.')) : quoi)) {
                        return Resultat.INCHANGEE;
                    }
                    tenus.remove(id);
                    Applicateur.reappliquer(plugin, p);
                    return Resultat.APPLIQUEE;
                }
                Controle nc;
                String variante = null;
                int dot = v.indexOf('.');
                if (dot > 0) { variante = v.substring(dot + 1); v = v.substring(0, dot); }
                try { nc = Controle.valueOf(v); } catch (IllegalArgumentException e) { return Resultat.REFUSEE; }
                Tenu actuel = tenus.get(id);
                if (nc == Controle.LIBRE) {
                    if (actuel == null) return Resultat.INCHANGEE;
                    if (!annoncer(p, couche, actuel.valeur().name(), nc.name(), src, lien)) return Resultat.VETO;
                    tenus.remove(id);
                    Applicateur.reappliquer(plugin, p);
                    return Resultat.APPLIQUEE;
                }
                // §5.2 règle 1 : à terre / inconscient / mort, on n'étourdit ni n'entrave plus (PORTE reste permis).
                if (avant.ko() || avant.vital() == Vital.MORT) {
                    if (nc == Controle.GARDE || nc == Controle.ETOURDI || nc == Controle.ENTRAVE || nc == Controle.CONTROLE) {
                        return Resultat.REFUSEE;
                    }
                }
                // §5.2 règle 3 : un seul détenteur de la couche — une entrave / un contrôle d'un AUTRE refuse.
                if (lien != null && lien.source() != null && avant.lien() != null && avant.lien().source() != null
                        && !avant.lien().source().equals(lien.source())
                        && (avant.controle() == Controle.ENTRAVE || avant.controle() == Controle.CONTROLE
                        || avant.controle() == Controle.PORTE)
                        && (nc == Controle.ENTRAVE || nc == Controle.CONTROLE || nc == Controle.PORTE)) {
                    return Resultat.REFUSEE;
                }
                // Une valeur moins prioritaire n'écrase pas une plus forte encore valable (un étourdi n'efface pas une
                // entrave posée par une technique) : la plus forte reste, la demande est refusée.
                if (actuel != null && !actuel.expire(System.currentTimeMillis()) && nc.priorite() < actuel.valeur().priorite()) {
                    return Resultat.REFUSEE;
                }
                if (actuel != null && actuel.valeur() == nc && java.util.Objects.equals(actuel.variante(), variante)) {
                    // même valeur : on prolonge (étourdi qui se cumule) et on garde le premier lien
                    long fin = dureeMs > 0 ? Math.max(actuel.finEpoch(), System.currentTimeMillis() + dureeMs) : 0;
                    tenus.put(id, new Tenu(nc, variante, actuel.lien(), actuel.finEpoch() == 0 ? 0 : fin, actuel.source(),
                            actuel.persoSource()));
                    return Resultat.INCHANGEE;
                }
                if (!annoncer(p, couche, avant.controle().name(), nc.name(), src, lien)) return Resultat.VETO;
                UUID persoSrc = null;
                if (lien != null && lien.source() != null && plugin.characters() != null) {
                    ShinobiCharacter sc = plugin.characters().getActive(lien.source());
                    persoSrc = sc != null ? sc.id() : null;
                }
                tenus.put(id, new Tenu(nc, variante, lien, dureeMs > 0 ? System.currentTimeMillis() + dureeMs : 0, src, persoSrc));
                Applicateur.reappliquer(plugin, p);
                return Resultat.APPLIQUEE;
            }
            default -> { return Resultat.REFUSEE; }
        }
    }

    /** Envoie l'{@link EtatChangeEvent} : vrai si aucun plugin ne s'y oppose. */
    boolean annoncer(Player p, Couche couche, String avant, String apres, Source src, Lien lien) {
        EtatChangeEvent ev = new EtatChangeEvent(p, couche, avant, apres, src, lien);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled() && journal) {
            Journal.entree(Categorie.ETAT, "VETO").cible(p)
                    .detail(couche.name().toLowerCase(Locale.ROOT) + " " + avant + " → " + apres + " refusé · " + src).ecrire();
        }
        return !ev.isCancelled();
    }

    /** Valeur de contrôle tenue par le service (pour {@code /etats}), ou null. */
    public Tenu tenu(UUID id) { return tenus.get(id); }

    /**
     * Tick (appelé par {@link EtatSuivi}) : valeurs tenues expirées, liens tombés (§5.2 règle 2), vital qui prime
     * (règle 1). Renvoie vrai si quelque chose a été levé.
     */
    void entretenir(Player p, Vital vital, long now) {
        UUID id = p.getUniqueId();
        Tenu t = tenus.get(id);
        if (t == null) return;
        String fin = null;
        if (t.expire(now)) fin = "durée";
        else if ((vital == Vital.A_TERRE || vital == Vital.INCONSCIENT || vital == Vital.MORT)
                && t.valeur() != Controle.PORTE && t.valeur() != Controle.CORPS_VIDE) fin = "le corps a lâché";
        else if (t.lien() != null && t.lien().source() != null) {
            Player s = Bukkit.getPlayer(t.lien().source());
            if (s == null) fin = "la source est partie";
            else {
                Vital vs = vital(s.getUniqueId());
                ShinobiCharacter sc = plugin.characters() != null ? plugin.characters().getActive(s.getUniqueId()) : null;
                if (vs == Vital.A_TERRE || vs == Vital.INCONSCIENT || vs == Vital.MORT) fin = "la source est tombée";
                else if (t.persoSource() != null && (sc == null || !sc.id().equals(t.persoSource()))) fin = "la source a changé de personnage";
                else if (t.lien().porteeBlocs() > 0 && (!s.getWorld().equals(p.getWorld())
                        || s.getLocation().distanceSquared(p.getLocation()) > t.lien().porteeBlocs() * t.lien().porteeBlocs()))
                    fin = "hors de portée";
            }
        }
        if (fin == null) return;
        tenus.remove(id);
        if (journal && t.valeur() != Controle.GARDE && t.valeur() != Controle.ETOURDI) {
            Journal.entree(Categorie.ETAT, "LIEN_ROMPU").cible(p)
                    .detail(t.valeur().name() + (t.lien() != null ? " · " + t.lien().type() + " " + t.lien().nom() : "")
                            + " · " + fin).ecrire();
        }
        Applicateur.reappliquer(plugin, p);
    }

    /** Déconnexion / changement de personnage : la couche Contrôle tenue et les facteurs tombent (§7.3). */
    public void oublier(UUID id) {
        tenus.remove(id);
        facteurs.remove(id);
        // les entraves et contrôles dont ce joueur était la source tombent au prochain tick (lien absent)
    }

    @Override
    public boolean poser(Player p, ModificateurSpec spec, Source source) {
        ModificateurRegistry reg = registre();
        if (reg == null || p == null || spec == null) return false;
        // Un type à effets physiques (potions, vitesse, saut) n'a de sens qu'avec l'applicateur : sinon l'appelant
        // garde son ancien effet (repli) — false.
        TypeModificateur t = reg.type(spec.id());
        if (t != null && !applicateur && (!t.potions().isEmpty() || t.effets().containsKey("vitesse")
                || t.effets().containsKey("saut"))) return false;
        return reg.poser(p, spec.id(), spec.intensite(), spec.dureeMs(), source != null ? source.nom() : "?");
    }

    /** Pose un effet de technique / de système sur un joueur s'il a un personnage, sinon false (l'appelant garde la potion). */
    public boolean affliger(Player p, String type, long dureeMs, String source) {
        return p != null && poser(p, new ModificateurSpec(type, 1.0, dureeMs), Source.systeme(source));
    }

    @Override
    public void retirer(Player p, String id, Source source) {
        ModificateurRegistry reg = registre();
        if (reg != null && p != null) reg.retirer(p, id, source != null ? source.nom() : "?");
    }

    @Override
    public int dissiper(Player p, Famille famille, int puissance, Source source) {
        ModificateurRegistry reg = registre();
        if (reg == null || p == null || famille == null) return 0;
        int n = 0;
        for (ModificateurService.Vue v : reg.actifs(p.getUniqueId())) {
            if (n >= Math.max(1, puissance)) break;
            if (v.bonus() || !famille.name().equalsIgnoreCase(v.famille())) continue;
            reg.retirer(p, v.type(), "dissipé · " + (source != null ? source.nom() : "?"));
            n++;
        }
        return n;
    }

    @Override
    public boolean appliqueEffetsPhysiques() { return applicateur; }

    @Override
    public void facteur(Player p, String cle, double vitesse, double saut) {
        if (p == null || cle == null) return;
        UUID id = p.getUniqueId();
        if (Math.abs(vitesse - 1) < 1e-6 && Math.abs(saut - 1) < 1e-6) {
            Map<String, double[]> f = facteurs.get(id);
            if (f != null && f.remove(cle) != null) Applicateur.reappliquer(plugin, p);
            return;
        }
        facteurs.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(cle, new double[] {vitesse, saut});
        Applicateur.reappliquer(plugin, p);
    }

    /** Facteurs posés par les add-ons (pour {@code /etats}). */
    public Map<String, double[]> facteurs(UUID id) {
        Map<String, double[]> f = facteurs.get(id);
        return f == null ? Map.of() : Map.copyOf(f);
    }

    @Override
    public Occupation occuper(Player p, String action, Entity cible, double toleranceBlocs, Set<FinSi> finSi,
                              Consumer<String> surInterruption) {
        return occupations.commencer(p, action, cible, toleranceBlocs, finSi, surInterruption);
    }

    @Override
    public Occupation occupation(UUID compte) { return occupations.de(compte); }

    @Override
    public boolean emprisonner(Player p, long dureeMs, String zone, String motif, Source source) {
        if (p == null) return false;
        ShinobiCharacter c = plugin.characters() != null ? plugin.characters().getActive(p.getUniqueId()) : null;
        if (c == null || dureeMs <= 0) return false;
        if (!prison.enPrison(c.id()) && !annoncer(p, Couche.CADRE, calculer(p.getUniqueId()).cadre().name(), "PRISON",
                source != null ? source : Source.systeme("?"), null)) return false;
        prison.poser(c.id(), System.currentTimeMillis() + dureeMs, zone, motif, source != null ? source.nom() : "?");
        Journal.entree(Categorie.ETAT, "PRISON").cible(p)
                .detail(dureeMs / 60_000 + " min" + (zone != null ? " · zone " + zone : "") + (motif != null ? " · " + motif : "")
                        + " · " + (source != null ? source.nom() : "?")).lieu(p.getLocation()).ecrire();
        return true;
    }

    @Override
    public boolean liberer(UUID compte, Source source) {
        ShinobiCharacter c = plugin.characters() != null ? plugin.characters().getActive(compte) : null;
        if (c == null || !prison.enPrison(c.id())) return false;
        prison.lever(c.id());
        Player p = Bukkit.getPlayer(compte);
        var e = Journal.entree(Categorie.ETAT, "PRISON_FIN").detail("libéré · " + (source != null ? source.nom() : "?"));
        if (p != null) e.cible(p); else e.cible(compte);
        e.ecrire();
        return true;
    }

    @Override
    public Peine peine(UUID compte) {
        ShinobiCharacter c = plugin.characters() != null ? plugin.characters().getActive(compte) : null;
        return c == null ? null : prison.peine(c.id());
    }

    /* ================================================================ outils */

    /** Ensemble des ids de lignes connues (complétion de {@code /etats poser}). */
    public Set<String> idsLignes() { return new HashSet<>(matrice.lignes().keySet()); }
}
