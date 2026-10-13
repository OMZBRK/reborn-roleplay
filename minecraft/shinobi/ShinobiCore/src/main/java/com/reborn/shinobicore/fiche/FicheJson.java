package com.reborn.shinobicore.fiche;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.ApprenticeshipService;
import com.reborn.shinobicore.api.EnduranceService;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.LearnOrigin;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.KoState;
import com.reborn.shinobicore.ko.ata.AtaManager;
import com.reborn.shinobicore.ko.injury.Injury;
import com.reborn.shinobicore.ko.injury.InjuryFeel;
import com.reborn.shinobicore.ko.injury.Severity;
import com.reborn.shinobicore.stats.StatFormulas;
import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.JutsuRank;
import com.reborn.shinobicore.technique.Mudra;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Sections v2 du JSON de {@code reborn:stats} (fiche shinobi « portrait en
 * scène ») : {@code rankId}, {@code identite}, {@code lignee},
 * {@code apprentissage}, {@code etat}, plus les clés ajoutées à chaque
 * technique. Construit avec Gson (échappement correct), puis collé derrière
 * les clés historiques, qui restent octet pour octet celles d'avant.
 *
 * <p>Toutes les clés sont facultatives pour le client ; chaque bloc est
 * construit dans son propre {@code try} : un bloc en échec est omis seul.
 *
 * <p>Tolère {@code plugin == null} et {@code player == null} (banc d'essai) :
 * les blocs qui en dépendent sont alors réduits ou absents.
 */
public final class FicheJson {

    private final ShinobiCore plugin;

    public FicheJson(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /* ============================================================ façade */

    /** Écrit {@code ,"clé":valeur} pour chaque section v2 (sans accolade). */
    public void appendSections(StringBuilder sb, Player p, ShinobiCharacter c,
                               ApprenticeshipService ap, EnduranceService end) {
        appendMembers(sb, sections(p, c, ap, end));
    }

    /** Les sections v2, dans l'ordre du protocole. */
    public JsonObject sections(Player p, ShinobiCharacter c, ApprenticeshipService ap, EnduranceService end) {
        JsonObject o = new JsonObject();
        o.addProperty("rankId", c.rank().name());
        // Player ID : la fiche ne part qu'au joueur lui-même (pastille « N° 00042 », utile pour un ticket).
        if (c.hasPlayerId()) o.addProperty("pid", com.reborn.shinobicore.character.PlayerIds.format(c.playerId()));
        put(o, "identite", () -> identite(c));
        put(o, "lignee", () -> lignee(c));
        put(o, "apprentissage", () -> apprentissage(p, c, ap));
        put(o, "etat", () -> etat(p, c, end));
        return o;
    }

    /** Colle les membres de {@code o} derrière un objet JSON en cours d'écriture. */
    public static void appendMembers(StringBuilder sb, JsonObject o) {
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            sb.append(",\"").append(e.getKey()).append("\":").append(e.getValue().toString());
        }
    }

    private interface Block { JsonElement build(); }

    private void put(JsonObject o, String key, Block b) {
        try {
            JsonElement v = b.build();
            if (v != null) o.add(key, v);
        } catch (RuntimeException | LinkageError ex) {
            if (plugin != null) plugin.getLogger().log(Level.WARNING, "[fiche] bloc « " + key + " » omis", ex);
        }
    }

    /* ========================================================== identité */

    JsonObject identite(ShinobiCharacter c) {
        JsonObject o = new JsonObject();
        o.addProperty("age", c.age());
        o.addProperty("taille", String.format(Locale.FRANCE, "%.2f m", 1.8 * c.size()));
        o.addProperty("sexe", c.sexe());
        if (c.chakraAffinities().isEmpty()) o.addProperty("naturesNote", FichePhrases.NATURE_AUCUNE);
        else if (c.chakraAffinitiesRemaining() > 0) o.addProperty("naturesNote", FichePhrases.NATURE_RESTE);
        if (!c.traits().isEmpty() || c.citation() != null) {
            JsonObject car = new JsonObject();
            JsonArray tr = new JsonArray();
            // Les traits renseignés seulement (3 au plus) ; le mod n'affiche que les non vides.
            for (String t : c.traits()) {
                if (t != null && !t.isBlank() && tr.size() < 3) tr.add(t.trim());
            }
            car.add("traits", tr);
            car.addProperty("citation", c.citation() == null ? "" : c.citation());
            o.add("caractere", car);
        }
        return o;
    }

    /* ============================================================ lignée */

    JsonObject lignee(ShinobiCharacter c) {
        JsonObject o = new JsonObject();
        String doj = FichePhrases.dojutsuOf(c.clan());
        if (doj != null) {
            JsonObject d = new JsonObject();
            d.addProperty("nom", doj);
            d.addProperty("stade", c.dojutsuStage());
            d.addProperty("txt", FichePhrases.dojutsuTxt(doj, c.dojutsuStage()));
            o.add("dojutsu", d);
        }
        int palier = FichePhrases.meritePalier(StatFormulas.meritPoints(c.rank()));
        JsonObject m = new JsonObject();
        m.addProperty("palier", palier);
        m.addProperty("txt", FichePhrases.merite(palier));
        o.add("merite", m);

        ServiceRecords sr = plugin == null ? null : plugin.serviceRecords();
        JsonArray svc = new JsonArray();
        int total = 0;
        if (sr != null) {
            for (ServiceEntry e : sr.recent(c, 6)) svc.add(serviceJson(e));
            total = sr.visibleCount(c);
        }
        o.add("service", svc);
        o.addProperty("serviceTotal", total);
        return o;
    }

    static JsonObject serviceJson(ServiceEntry e) {
        JsonObject s = new JsonObject();
        s.addProperty("type", e.type().name());
        if (e.rang() != null) s.addProperty("rang", e.rang());
        s.addProperty("txt", e.text());
        s.addProperty("date", ServiceRecords.formatDate(e.at()));
        return s;
    }

    /* ===================================================== apprentissage */

    JsonArray apprentissage(Player p, ShinobiCharacter c, ApprenticeshipService ap) {
        if (ap == null || p == null) return null;
        List<ApprenticeshipService.Learning> list = ap.inProgress(p, c);
        if (list == null || list.isEmpty()) return null;
        JsonArray arr = new JsonArray();
        long now = System.currentTimeMillis();
        for (ApprenticeshipService.Learning l : list) {
            Ability a = plugin == null || plugin.techniques() == null ? null : plugin.techniques().byId(l.techId());
            JsonObject o = new JsonObject();
            o.addProperty("id", l.techId());
            o.addProperty("name", a == null ? l.techId() : a.name());
            o.addProperty("letter", a == null ? "D" : letter(a.rank()));
            o.addProperty("nature", a == null ? ChakraAffinity.NONE.name() : a.profile().nature().name());
            o.addProperty("cat", a == null ? "" : a.category());
            o.addProperty("done", l.done());
            o.addProperty("total", l.required());
            String etat;
            String txt;
            if (l.awaitingValidation()) {
                etat = "VALIDATION";
                txt = FichePhrases.APP_VALIDATION;
            } else if (l.nextSessionAtMillis() > now) {
                etat = "REPOS";
                txt = FichePhrases.repos(l.nextSessionAtMillis() - now);
            } else if (!l.scrollCarried()) {
                etat = "SANS_ROULEAU";
                txt = FichePhrases.APP_SANS_ROULEAU;
            } else if (ap.masterNearby(p, l.techId())) {
                etat = "MAITRE";
                txt = FichePhrases.APP_MAITRE;
            } else {
                etat = "PRET";
                txt = FichePhrases.APP_PRET;
            }
            o.addProperty("etat", etat);
            o.addProperty("txt", txt);
            arr.add(o);
        }
        return arr;
    }

    /* ========================================================= techniques */

    /** Clés v2 ajoutées à une technique connue. */
    public static JsonObject techniqueExtras(ShinobiCharacter c, Ability a, ApprenticeshipService ap) {
        JsonObject o = new JsonObject();
        o.addProperty("letter", letter(a.rank()));
        o.addProperty("groupe", groupe(a));
        int palier = FichePhrases.maitrisePalier(c.abilityMastery(a.id()));
        o.addProperty("palier", palier);
        o.addProperty("palierTxt", FichePhrases.maitriseTxt(palier));
        LearnOrigin origin = c.learnOrigin(a.id());
        if (origin == LearnOrigin.INCONNU && ap != null && ap.learnedAt(c.id(), a.id()) != null) {
            origin = LearnOrigin.PARCHEMIN;
        }
        o.addProperty("origin", origin.name());
        o.addProperty("teach", ap != null && ap.canTeach(c, a.id()));
        String desc = a.description();
        if (desc != null && !desc.isBlank()) o.addProperty("desc", desc.trim());
        List<Mudra> mudras = a.mudras();
        if (mudras != null && !mudras.isEmpty()) {
            JsonArray m = new JsonArray();
            for (Mudra x : mudras) m.add(x.display());
            o.add("mudras", m);
        }
        return o;
    }

    /** E, D, C, B, A ; HIDEN → S. */
    public static String letter(JutsuRank r) {
        if (r == null) return "E";
        return r == JutsuRank.HIDEN ? "S" : r.name();
    }

    /** NINJUTSU, TAIJUTSU, KENJUTSU, FONDAMENTAUX ou AUTRES. */
    public static String groupe(Ability a) {
        if (FichePhrases.fondamental(a.id())) return "FONDAMENTAUX";
        String cat = a.category() == null ? "" : a.category().toLowerCase(Locale.ROOT);
        String root = cat.contains("/") ? cat.substring(0, cat.indexOf('/')) : cat;
        return switch (root) {
            case "ninjutsu" -> "NINJUTSU";
            case "taijutsu" -> "TAIJUTSU";
            case "bukijutsu", "kenjutsu" -> "KENJUTSU";
            // Kekkei genkai élémentaires (mokuton, hyoton…) : du ninjutsu ; dōjutsu : à part.
            case "kekkei" -> cat.startsWith("kekkei/dojutsu") ? "AUTRES" : "NINJUTSU";
            default -> "AUTRES";
        };
    }

    /* ============================================================== état */

    JsonObject etat(Player p, ShinobiCharacter c, EnduranceService end) {
        JsonObject o = new JsonObject();

        KoState ko = plugin == null || plugin.ko() == null || p == null ? null : plugin.ko().getKo(p.getUniqueId());
        AtaManager.View ata = plugin == null || plugin.ata() == null ? null : plugin.ata().view(c.id());

        double hpRatio = hpRatio(p, c);
        Severity worst = null;
        boolean anyVisible = false;
        boolean healableNow = false;
        for (Injury i : c.injuries()) {
            if (i == null || i.hidden()) continue;
            anyVisible = true;
            if (worst == null || (i.severity() != null && i.severity().ordinal() > worst.ordinal())) worst = i.severity();
            if (i.isHealable()) healableNow = true;
        }

        String titre;
        if (ko != null && ko.phase() == KoState.Phase.DOWNED) titre = "A_TERRE";
        else if (ko != null) titre = "INCONSCIENT";
        else if (ata != null) titre = "ATA";
        else if ((worst != null && worst.ordinal() >= Severity.IMPORTANT.ordinal()) || hpRatio < FichePhrases.titreGraveSous) titre = "GRAVE";
        else if (anyVisible || hpRatio < FichePhrases.titreBlesseSous) titre = "BLESSE";
        else titre = "INDEMNE";
        o.addProperty("titre", titre);
        o.addProperty("titreTxt", FichePhrases.titreTxt(titre));
        o.addProperty("sousTxt", FichePhrases.sousTxt(titre));

        o.add("vie", step(FichePhrases.pick(FichePhrases.vie(), hpRatio)));
        double ckMax = c.chakra().max();
        o.add("chakra", step(FichePhrases.pick(FichePhrases.chakra(), ckMax <= 0 ? 0 : c.chakra().current() / ckMax)));
        if (end != null && p != null) {
            double max = end.max(p);
            o.add("endurance", step(FichePhrases.pick(FichePhrases.endurance(), max <= 0 ? 0 : end.current(p) / max)));
        }
        if (plugin != null && plugin.volonte() != null && plugin.volonte().allowed(c)) {
            o.add("volonte", step(FichePhrases.volonte(plugin.volonte().left(c.id()))));
        }

        JsonObject soins = new JsonObject();
        if (!anyVisible) {
            soins.addProperty("lvl", 0);
            soins.addProperty("txt", FichePhrases.SOINS_AUCUN);
        } else if (healableNow) {
            soins.addProperty("lvl", 1);
            soins.addProperty("txt", FichePhrases.SOINS_ATTENTE);
        } else {
            soins.addProperty("lvl", 1);
            soins.addProperty("txt", FichePhrases.SOINS_EN_COURS);
        }
        o.add("soins", soins);

        if (ata != null) {
            JsonObject a = new JsonObject();
            a.addProperty("niveau", ata.level().name());
            a.addProperty("txt", FichePhrases.ata(ata.progress()));
            o.add("ata", a);
        } else {
            o.add("ata", JsonNull.INSTANCE);
        }
        if (ko != null) {
            JsonObject k = new JsonObject();
            k.addProperty("phase", ko.phase().name());
            o.add("ko", k);
        } else {
            o.add("ko", JsonNull.INSTANCE);
        }

        JsonArray bl = new JsonArray();
        for (InjuryFeel.Felt f : InjuryFeel.grouped(c.injuries())) {
            JsonObject b = new JsonObject();
            b.addProperty("zone", f.zone().name());
            b.addProperty("label", f.zone().label());
            b.addProperty("type", f.worst().type().name());
            b.addProperty("txt", f.txt());
            b.addProperty("forte", f.forte());
            bl.add(b);
        }
        o.add("blessures", bl);

        JsonObject ech = new JsonObject();
        ech.add("vie", scale(FichePhrases.vie()));
        ech.add("chakra", scale(FichePhrases.chakra()));
        ech.add("endurance", scale(FichePhrases.endurance()));
        o.add("echelles", ech);
        return o;
    }

    /** Vie live (vanilla, comme reborn:vitals) si le joueur est là, sinon la valeur sauvegardée. */
    private static double hpRatio(Player p, ShinobiCharacter c) {
        if (p != null) {
            double max = c.maxHp();
            var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (attr != null) max = attr.getValue();
            return max <= 0 ? 0 : p.getHealth() / max;
        }
        double max = c.maxHp();
        return max <= 0 ? 0 : c.currentHp() / max;
    }

    private static JsonObject step(FichePhrases.Step s) {
        JsonObject o = new JsonObject();
        o.addProperty("lvl", s.lvl());
        o.addProperty("txt", s.txt());
        return o;
    }

    private static JsonArray scale(List<FichePhrases.Step> steps) {
        JsonArray arr = new JsonArray();
        for (FichePhrases.Step s : steps) {
            JsonArray row = new JsonArray();
            row.add(s.min());
            row.add(s.txt());
            row.add(s.lvl());
            arr.add(row);
        }
        return arr;
    }
}
