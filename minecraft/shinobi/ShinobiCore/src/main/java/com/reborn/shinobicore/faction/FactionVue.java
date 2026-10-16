package com.reborn.shinobicore.faction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.faction.FactionData.Annonce;
import com.reborn.shinobicore.faction.FactionData.Charge;
import com.reborn.shinobicore.faction.FactionData.Faction;
import com.reborn.shinobicore.faction.FactionData.GradeSection;
import com.reborn.shinobicore.faction.FactionData.Ligne;
import com.reborn.shinobicore.faction.FactionData.Membre;
import com.reborn.shinobicore.faction.FactionData.Section;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Construit ce que le volet J reçoit (FACTION.md §4) : <b>seulement ce que le lecteur a le droit de voir</b>. Un
 * inconnu n'est jamais nommé (ni son UUID, ni son N°) ; une section cachée est un « ? » sans rien d'autre pour qui
 * n'en est pas ; aucune caisse ni journal sans le droit. Fouiller le mod ne révèle rien. La vue staff (Poste de garde)
 * est à part : {@link #staff}.
 */
final class FactionVue {

    private final ShinobiCore plugin;
    private final FactionService svc;

    FactionVue(ShinobiCore plugin, FactionService svc) {
        this.plugin = plugin;
        this.svc = svc;
    }

    /** Contexte de lecture : le lecteur, son personnage (contacts), ses rôles. */
    private record Ctx(Membre moi, ShinobiCharacter perso, Faction f, Section s, Regles.Lecteur l) {
        String appris(UUID id) {
            ShinobiCharacter.KnownName k = perso == null ? null : perso.knownNameOf(id);
            return k == null ? null : k.display();
        }
    }

    private static void prop(JsonObject o, String k, String v) { if (v != null && !v.isEmpty()) o.addProperty(k, v); }

    private static JsonArray lignes(List<Ligne> l, int n, boolean montants) {
        JsonArray a = new JsonArray();
        for (int i = 0; i < Math.min(n, l.size()); i++) {
            Ligne x = l.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("t", x.texte());
            o.addProperty("le", x.le());
            if (montants) o.addProperty("n", x.montant());
            a.add(o);
        }
        return a;
    }

    /** Le nom vu, ou null si inconnu (registre : chef de section sur sa section, chef de faction partout). */
    private String nom(Ctx c, Membre m, boolean registre) {
        boolean publique = c.f != null && m.perso.equals(c.f.chef);
        if (!publique && c.f != null) for (Charge k : c.f.charges.values()) if (k.publique && k.titulaires.contains(m.perso)) publique = true;
        return Regles.nom(m, c.moi.perso, c.appris(m.perso), publique, registre || c.l.chefFaction());
    }

    /** Une personne telle que le lecteur la voit : nom (ou « Inconnu »), en ligne, visage (UUID Minecraft) si nommée. */
    private JsonObject personne(Ctx c, Membre m, boolean registre) {
        JsonObject o = new JsonObject();
        String n = nom(c, m, registre);
        boolean moi = m.perso.equals(c.moi.perso);
        if (n == null) {
            o.addProperty("nom", Regles.inconnu(m));
            o.addProperty("connu", false);
            return o;
        }
        o.addProperty("nom", n);
        o.addProperty("connu", true);
        o.addProperty("id", m.perso.toString());
        if (moi) o.addProperty("moi", true);
        Player p = svc.enLigne(m);
        o.addProperty("online", p != null);
        if (p != null) o.addProperty("u", p.getUniqueId().toString());
        if (c.f != null && m.perso.equals(c.f.chef)) o.addProperty("publique", true);
        return o;
    }

    /* ================================================================ vue du joueur */

    JsonObject joueur(Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "vue");
        ShinobiCharacter pc = svc.perso(p);
        Membre moi = pc == null ? null : svc.synchro(pc, true);
        Faction f = moi == null ? null : svc.faction(moi.faction);
        if (moi == null || f == null) {
            o.addProperty("ok", false);
            o.addProperty("raison", pc == null ? "Choisis d'abord un personnage." : "Ton personnage n'appartient à aucune faction.");
            return o;
        }
        Section s = svc.section(moi.section);
        Ctx c = new Ctx(moi, pc, f, s, svc.lecteur(moi));
        o.addProperty("ok", true);
        JsonObject fo = new JsonObject();
        fo.addProperty("tag", f.tag);
        fo.addProperty("nom", f.nom);
        fo.addProperty("type", f.village() ? "village" : "organisation");
        fo.addProperty("embleme", f.embleme);
        fo.addProperty("n", svc.membres(f).size());
        fo.addProperty("kage", FactionService.kage(f));
        o.add("faction", fo);

        JsonObject mo = new JsonObject();
        mo.addProperty("nom", moi.nom);
        mo.addProperty("rang", moi.rang.name());
        mo.addProperty("rangNom", moi.rang.displayName());
        mo.addProperty("profil", Regles.profil(c.l, f).name());
        mo.addProperty("chefFaction", c.l.chefFaction());
        mo.addProperty("chefSection", c.l.chefSection());
        Charge ch = svc.chargeDe(f, moi.perso);
        if (ch != null) mo.addProperty("charge", ch.nom);
        mo.addProperty("titre", svc.titre(moi, f, s));
        o.add("moi", mo);

        boolean gestion = c.l.chefFaction() || ch != null && !ch.droits.isEmpty();
        boolean voitSections = Regles.voitSections(c.l, f);
        JsonArray onglets = new JsonArray();
        if (s != null) onglets.add("section");
        onglets.add("village");
        if (voitSections) onglets.add("sections");
        onglets.add("annonces");
        if (gestion) onglets.add("gestion");
        o.add("onglets", onglets);

        if (s != null) o.add("section", section(c, s));
        o.add("village", village(c));
        if (voitSections) o.add("sections", sections(c));
        o.add("annonces", annonces(c));
        if (gestion) o.add("gestion", gestion(c, ch));
        JsonObject peut = new JsonObject();
        peut.addProperty("annoncerVillage", Regles.seuil(c.l, f, "cloche_village") || svc.charge(f, moi.perso, Droits.C_ANNONCES));
        peut.addProperty("annoncerSection", s != null && svc.droit(moi, Droits.CLOCHE_SECTION));
        o.add("peut", peut);
        return o;
    }

    private JsonObject section(Ctx c, Section s) {
        JsonObject o = new JsonObject();
        boolean chef = c.l.chefSection(), chefF = c.l.chefFaction(), registre = Regles.registre(s, c.l);
        o.addProperty("id", s.id);
        o.addProperty("nom", s.nom);
        o.addProperty("insigne", s.insigne);
        o.addProperty("cachee", s.cachee);
        o.addProperty("places", s.places);
        int n = svc.effectif(s);
        o.addProperty("n", n);
        o.addProperty("chef", chef);
        GradeSection mg = s.grade(c.moi.grade);
        o.addProperty("monGrade", mg == null ? "" : mg.nom);
        o.addProperty("monRang", chef ? 0 : s.rang(c.moi.grade));
        Membre mc = svc.membre(s.chef);
        if (mc != null) {
            String cn = nom(c, mc, registre);
            o.addProperty("chefNom", cn == null ? Regles.inconnu(mc) : cn);
            if (cn != null && !chef) o.addProperty("chefFem", mc.feminin());
        }
        JsonArray droits = new JsonArray();
        for (String[] d : Droits.SECTION) if (svc.droit(c.moi, d[0])) droits.add(d[0]);
        o.add("droits", droits);

        JsonArray ms = new JsonArray();
        for (Membre m : svc.membres(s)) {
            JsonObject x = personne(c, m, registre);
            GradeSection g = s.grade(m.grade);
            x.addProperty("grade", g == null ? "" : g.nom);
            x.addProperty("gradeId", m.grade);
            x.addProperty("rang", m.perso.equals(s.chef) ? 0 : s.rang(m.grade));
            if (m.perso.equals(s.chef)) x.addProperty("chef", true);
            if (registre || chefF) x.addProperty("rangVillage", m.rang.displayName());
            ms.add(x);
        }
        o.add("membres", ms);

        JsonObject t = new JsonObject();
        t.addProperty("id", s.tenue);
        t.addProperty("nom", s.tenueNom);
        t.addProperty("accessoire", s.accessoire);
        o.add("tenue", t);

        JsonArray lieux = new JsonArray();
        for (String l : s.lieux) {
            String[] p = l.split("\\|", 3);
            JsonObject x = new JsonObject();
            x.addProperty("nom", p[0]);
            x.addProperty("type", p.length > 1 ? p[1] : "porte");
            x.addProperty("acces", p.length > 2 ? p[2] : "");
            lieux.add(x);
        }
        o.add("lieux", lieux);

        if (Regles.voitCaisse(s, c.l, svc.droit(c.moi, Droits.VOIR_CAISSE))) {
            JsonObject k = new JsonObject();
            k.addProperty("solde", s.caisse);
            k.addProperty("taux", svc.taux(c.f));
            k.add("lignes", lignes(s.mouvements, 6, true));
            o.add("caisse", k);
        }
        if (chef || chefF || svc.droit(c.moi, Droits.MONTER)) {
            JsonArray gs = new JsonArray();
            for (int i = 0; i < s.grades.size(); i++) {
                GradeSection g = s.grades.get(i);
                JsonObject x = new JsonObject();
                x.addProperty("id", g.id);
                x.addProperty("nom", g.nom);
                JsonArray d = new JsonArray();
                for (String k : g.droits) d.add(k);
                x.add("droits", d);
                int cpt = 0;
                for (Membre m : svc.membres(s)) if (m.grade.equals(g.id)) cpt++;
                x.addProperty("n", cpt);
                gs.add(x);
            }
            o.add("grades", gs);
        }
        if (chef || chefF || svc.droit(c.moi, Droits.JOURNAL)) o.add("journal", lignes(s.journal, 12, false));
        return o;
    }

    private static final Rank[] ORDRE = {Rank.KAGE, Rank.COMMANDANT_JONIN, Rank.JONIN, Rank.TOKUBETSU_JONIN, Rank.KONIN,
            Rank.CHUNIN, Rank.GENIN_CONFIRME, Rank.GENIN, Rank.ACADEMICIEN};

    private JsonObject village(Ctx c) {
        JsonObject o = new JsonObject();
        List<Membre> tous = svc.membres(c.f);
        o.addProperty("total", tous.size());
        JsonArray groupes = new JsonArray();
        for (Rank r : c.f.village() ? ORDRE : new Rank[0]) {
            List<Membre> l = new ArrayList<>();
            for (Membre m : tous) if (m.rang == r) l.add(m);
            if (l.isEmpty()) continue;
            JsonObject g = new JsonObject();
            g.addProperty("rang", r.name());
            g.addProperty("nom", r == Rank.KAGE ? FactionService.kage(c.f) : r.displayName());
            g.addProperty("total", l.size());
            JsonArray connus = new JsonArray();
            int inconnus = 0;
            // nommés d'abord (en ligne en tête), les autres comptés
            List<JsonObject> nommes = new ArrayList<>();
            for (Membre m : l) {
                JsonObject p = personne(c, m, false);
                if (p.get("connu").getAsBoolean()) nommes.add(p);
                else inconnus++;
            }
            nommes.sort((a, b) -> Boolean.compare(b.has("online") && b.get("online").getAsBoolean(), a.has("online") && a.get("online").getAsBoolean()));
            for (JsonObject p : nommes) connus.add(p);
            g.add("connus", connus);
            g.addProperty("inconnus", inconnus);
            groupes.add(g);
        }
        if (!c.f.village()) {
            // organisation : pas de grade du village, un seul groupe
            JsonObject g = new JsonObject();
            g.addProperty("rang", "");
            g.addProperty("nom", "Membres");
            g.addProperty("total", tous.size());
            JsonArray connus = new JsonArray();
            int inconnus = 0;
            for (Membre m : tous) {
                JsonObject p = personne(c, m, false);
                if (p.get("connu").getAsBoolean()) connus.add(p); else inconnus++;
            }
            g.add("connus", connus);
            g.addProperty("inconnus", inconnus);
            groupes.add(g);
        }
        o.add("groupes", groupes);
        return o;
    }

    private JsonArray sections(Ctx c) {
        JsonArray a = new JsonArray();
        List<Section> l = svc.sections(c.f);
        // les sections cachées illisibles d'abord, comme sur la maquette : « ? »
        l.sort((x, y) -> Boolean.compare(!Regles.lisible(y, c.l, false), !Regles.lisible(x, c.l, false)));
        for (Section s : l) {
            JsonObject o = new JsonObject();
            if (!Regles.lisible(s, c.l, false)) {
                o.addProperty("q", true);
                a.add(o);
                continue;
            }
            o.addProperty("id", s.id);
            o.addProperty("nom", s.nom);
            o.addProperty("insigne", s.insigne);
            o.addProperty("cachee", s.cachee);
            o.addProperty("n", svc.effectif(s));
            boolean registre = Regles.registre(s, c.l);
            Membre mc = svc.membre(s.chef);
            if (mc != null) {
                JsonObject ch = personne(c, mc, registre);
                o.add("chef", ch);
            }
            JsonArray connus = new JsonArray();
            for (Membre m : svc.membres(s)) {
                if (m.perso.equals(s.chef)) continue;
                JsonObject p = personne(c, m, registre);
                if (!p.get("connu").getAsBoolean()) continue;
                GradeSection g = s.grade(m.grade);
                p.addProperty("grade", g == null ? "" : g.nom);
                connus.add(p);
            }
            o.add("connus", connus);
            if (Regles.voitCaisse(s, c.l, false)) o.addProperty("caisse", s.caisse);
            a.add(o);
        }
        return a;
    }

    private JsonArray annonces(Ctx c) {
        JsonArray a = new JsonArray();
        for (Annonce an : c.f.annonces) {
            if (!an.section.isEmpty() && !an.section.equals(c.moi.section)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("le", an.le);
            o.addProperty("texte", an.texte);
            o.addProperty("titre", an.titre);
            Membre au = svc.membre(an.auteur);
            String n = au == null ? null : nom(c, au, false);
            if (n != null) o.addProperty("auteur", n);
            Section s = svc.section(an.section);
            if (s != null) o.addProperty("section", s.nom);
            a.add(o);
            if (a.size() >= 20) break;
        }
        return a;
    }

    private JsonObject gestion(Ctx c, Charge ch) {
        Faction f = c.f;
        boolean chefF = c.l.chefFaction();
        java.util.function.Predicate<String> a = d -> chefF || ch != null && ch.droits.contains(d);
        JsonObject o = new JsonObject();
        JsonArray mes = new JsonArray();
        for (String[] d : Droits.CHARGE) if (a.test(d[0])) mes.add(d[0]);
        o.add("droits", mes);
        o.addProperty("chef", chefF);
        o.addProperty("taux", svc.taux(f));
        o.addProperty("plafond", svc.plafond());
        if (a.test(Droits.C_CAISSES) || a.test(Droits.C_TAXE) || chefF) {
            var mo = plugin.monnaie();
            if (mo != null) o.addProperty("tresor", mo.banque().tresor(f.village.isEmpty() ? f.nom : f.village));
            o.addProperty("taxeSemaine", svc.taxeSemaine(f));
        }
        JsonArray seuils = new JsonArray();
        for (Object[] s : Droits.SEUILS) {
            JsonObject x = new JsonObject();
            x.addProperty("id", (String) s[0]);
            x.addProperty("nom", (String) s[1]);
            x.addProperty("rang", f.seuils.getOrDefault((String) s[0], (Rank) s[2]).name());
            seuils.add(x);
        }
        o.add("seuils", seuils);
        JsonArray rangs = new JsonArray();
        for (Rank r : Rank.values()) {
            JsonObject x = new JsonObject();
            x.addProperty("id", r.name());
            x.addProperty("nom", r.displayName());
            rangs.add(x);
        }
        o.add("rangs", rangs);
        JsonArray ss = new JsonArray();
        for (Section s : svc.sections(f)) {
            JsonObject x = new JsonObject();
            x.addProperty("id", s.id);
            x.addProperty("nom", s.nom);
            x.addProperty("insigne", s.insigne);
            x.addProperty("cachee", s.cachee);
            x.addProperty("n", svc.effectif(s));
            x.addProperty("places", s.places);
            Membre mc = svc.membre(s.chef);
            if (mc != null) x.addProperty("chefNom", mc.nom);
            if (a.test(Droits.C_CAISSES)) x.addProperty("caisse", s.caisse);
            ss.add(x);
        }
        o.add("sections", ss);
        JsonArray charges = new JsonArray();
        for (Charge k : f.charges.values()) {
            JsonObject x = new JsonObject();
            x.addProperty("id", k.id);
            x.addProperty("nom", k.nom);
            x.addProperty("publique", k.publique);
            JsonArray d = new JsonArray();
            for (String s : k.droits) d.add(s);
            x.add("droits", d);
            JsonArray t = new JsonArray();
            for (UUID u : k.titulaires) {
                Membre m = svc.membre(u);
                if (m == null) continue;
                JsonObject y = new JsonObject();
                y.addProperty("id", u.toString());
                y.addProperty("nom", m.nom);
                t.add(y);
            }
            x.add("titulaires", t);
            charges.add(x);
        }
        o.add("charges", charges);
        if (a.test(Droits.C_JOURNAL)) o.add("journal", lignes(f.journal, 15, false));
        JsonArray modeles = new JsonArray();
        for (Droits.Modele m : Droits.MODELES) {
            JsonObject x = new JsonObject();
            x.addProperty("id", m.id());
            x.addProperty("nom", m.nom());
            x.addProperty("insigne", m.insigne());
            x.addProperty("cachee", m.cachee());
            modeles.add(x);
        }
        o.add("modeles", modeles);
        if (a.test(Droits.C_SECTIONS) || chefF) {
            // candidats chef de section / charge : membres de la faction (le chef de faction tient le registre)
            JsonArray cand = new JsonArray();
            List<Membre> l = svc.membres(f);
            l.sort((x, y) -> y.rang.ordinal() - x.rang.ordinal());
            for (Membre m : l) {
                JsonObject x = new JsonObject();
                x.addProperty("id", m.perso.toString());
                x.addProperty("nom", m.nom);
                x.addProperty("rang", m.rang.displayName());
                if (!m.section.isEmpty()) x.addProperty("section", true);
                cand.add(x);
                if (cand.size() >= 200) break;
            }
            o.add("candidats", cand);
        }
        return o;
    }

    /* ================================================================ personnes proches (inviter depuis le panneau) */

    JsonObject proches(Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "proches");
        Membre moi = svc.membre(p);
        ShinobiCharacter pc = svc.perso(p);
        Section s = moi == null ? null : svc.section(moi.section);
        JsonArray pres = new JsonArray(), loin = new JsonArray();
        boolean distance = moi != null && svc.droit(moi, Droits.INVITER_DISTANCE);
        o.addProperty("distance", distance);
        if (s != null && pc != null) {
            for (Player q : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (q.equals(p)) continue;
                ShinobiCharacter qc = svc.perso(q);
                if (qc == null || !pc.knows(qc.id())) continue;      // un inconnu n'est pas dans la liste
                Membre m = svc.membre(q);
                if (m == null) continue;
                double d = q.getWorld().equals(p.getWorld()) ? q.getLocation().distance(p.getLocation()) : Double.MAX_VALUE;
                boolean pres30 = d <= FactionService.PORTEE_PANNEAU;
                if (!pres30 && (!distance || !s.faction.equals(m.faction))) continue;
                JsonObject x = new JsonObject();
                x.addProperty("id", m.perso.toString());
                x.addProperty("u", q.getUniqueId().toString());
                ShinobiCharacter.KnownName kn = pc.knownNameOf(m.perso);
                x.addProperty("nom", kn == null ? m.nom : kn.display());
                x.addProperty("rang", m.rang.displayName());
                if (pres30) x.addProperty("dist", (int) Math.round(d));
                String r = svc.refusCible(s, m);
                if (r != null) x.addProperty("refus", r);
                (pres30 ? pres : loin).add(x);
            }
        }
        o.add("pres", pres);
        o.add("loin", loin);
        if (s != null) {
            o.addProperty("n", svc.effectif(s));
            o.addProperty("places", s.places);
        }
        return o;
    }

    /* ================================================================ vue staff (Poste de garde › Outils › Factions) */

    JsonObject staff(int grade) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "staff");
        o.addProperty("grade", grade);
        JsonArray fs = new JsonArray();
        for (Faction f : svc.factions()) {
            JsonObject x = new JsonObject();
            x.addProperty("tag", f.tag);
            x.addProperty("nom", f.nom);
            x.addProperty("type", f.village() ? "village" : "organisation");
            x.addProperty("embleme", f.embleme);
            x.addProperty("serveur", f.serveur);
            x.addProperty("n", svc.membres(f).size());
            x.addProperty("taux", svc.taux(f));
            Membre ch = svc.membre(f.chef);
            if (ch != null) {
                JsonObject c = new JsonObject();
                c.addProperty("id", ch.perso.toString());
                c.addProperty("nom", ch.nom);
                prop(c, "pid", ch.pid >= 0 ? "#" + com.reborn.shinobicore.character.PlayerIdRegistry.format(ch.pid) : null);
                x.add("chef", c);
            }
            JsonArray ss = new JsonArray();
            for (Section s : svc.sections(f)) {
                JsonObject y = new JsonObject();
                y.addProperty("id", s.id);
                y.addProperty("nom", s.nom);
                y.addProperty("insigne", s.insigne);
                y.addProperty("cachee", s.cachee);
                y.addProperty("n", svc.effectif(s));
                y.addProperty("places", s.places);
                y.addProperty("caisse", s.caisse);
                Membre mc = svc.membre(s.chef);
                if (mc != null) y.addProperty("chefNom", mc.nom);
                JsonArray gs = new JsonArray();
                for (GradeSection g : s.grades) gs.add(g.nom);
                y.add("grades", gs);
                ss.add(y);
            }
            x.add("sections", ss);
            JsonArray ks = new JsonArray();
            for (Charge k : f.charges.values()) ks.add(k.nom + " (" + k.titulaires.size() + ")");
            x.add("charges", ks);
            x.add("journal", lignes(f.journal, 20, false));
            fs.add(x);
        }
        o.add("factions", fs);
        return o;
    }
}
