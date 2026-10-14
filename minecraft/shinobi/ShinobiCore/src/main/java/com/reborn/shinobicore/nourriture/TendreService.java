package com.reborn.shinobicore.nourriture;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.inventory.InventoryManager;
import com.reborn.shinobicore.inventory.Notifs;
import com.reborn.shinobicore.inventory.RareteItems;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.journal.RyoJournal;
import com.reborn.shinobicore.staff.outils.ModeStaff;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Tendre</b> de la main à la main (docs/gameplay/INVENTAIRE_ET_COSMETIQUES.md « Tendre », NOURRITURE.md §5) : un
 * geste, pas une fenêtre d'échange (décision du 14/10). Le menu d'interaction (R) sur un joueur tout proche envoie
 * {@code /tendre <joueur>} (l'objet en main) ou {@code /tendre <joueur> ryo <montant>} ; l'autre voit deux lignes
 * (« F prendre · Retour refuser », 10 s) et répond par {@code /tendre prendre|refuser}. À la prise, tout est revérifié
 * (distance, objet toujours en main, place et poids, solde) ; journal DON (objet : catégorie Objet ; ryo : Économie).
 */
public final class TendreService implements TabExecutor {

    private static final class Offre {
        final UUID de, a;
        final ItemStack objet;   // null = ryo
        final int slot;
        final long ryo;
        final long fin;

        Offre(UUID de, UUID a, ItemStack objet, int slot, long ryo, long fin) {
            this.de = de;
            this.a = a;
            this.objet = objet;
            this.slot = slot;
            this.ryo = ryo;
            this.fin = fin;
        }
    }

    private final ShinobiCore plugin;
    private final NourritureModule module;
    /** destinataire → offre (une à la fois). */
    private final Map<UUID, Offre> offres = new HashMap<>();

    TendreService(ShinobiCore plugin, NourritureModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    /* ================================================================ commande */

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] a) {
        if (!(s instanceof Player p)) return true;
        if (a.length == 0) { module.refus(p, "Tendre", "/tendre <joueur> [ryo <montant>]"); return true; }
        switch (a[0].toLowerCase(java.util.Locale.ROOT)) {
            case "prendre", "accepter" -> prendre(p);
            case "refuser" -> refuser(p, true);
            default -> {
                Player cible = PlayerIds.online(p, a[0]);
                if (cible == null) { module.refus(p, "Personne", "introuvable"); return true; }
                if (a.length >= 3 && a[1].equalsIgnoreCase("ryo")) {
                    long n;
                    try { n = Long.parseLong(a[2].replace(" ", "")); } catch (NumberFormatException e) { n = -1; }
                    if (n <= 0) { module.refus(p, "Montant", "invalide"); return true; }
                    proposer(p, cible, null, n);
                } else {
                    proposer(p, cible, p.getInventory().getItemInMainHand(), 0);
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String al, @NotNull String[] a) {
        List<String> r = new ArrayList<>();
        if (a.length == 1) {
            r.add("prendre");
            r.add("refuser");
            if (s instanceof Player p) {
                for (Player o : p.getWorld().getPlayers()) {
                    if (o != p && o.getLocation().distanceSquared(p.getLocation()) < 36) r.add(o.getName());
                }
            }
        } else if (a.length == 2) r.add("ryo");
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(java.util.Locale.ROOT);
        r.removeIf(x -> !x.toLowerCase(java.util.Locale.ROOT).startsWith(last));
        return r;
    }

    /* ================================================================ proposer */

    private void proposer(Player de, Player a, ItemStack main, long ryo) {
        NourritureConfig k = module.cfg();
        String pb = obstacle(de, a, k.distanceTendre + 0.5);
        if (pb != null) { module.refus(de, "Impossible de tendre", pb); return; }
        ShinobiCharacter cd = plugin.characters().getActive(de.getUniqueId());
        // Monnaie (15/10) : « Donner des ryo » paie avec la bourse ; la bourse en main se tend entière.
        var monnaie = plugin.monnaie();
        if (ryo <= 0 && monnaie != null && com.reborn.shinobicore.monnaie.Bourse.est(main))
            ryo = com.reborn.shinobicore.monnaie.Bourse.montant(main);
        long porte = monnaie != null ? monnaie.bourses().montant(de) : cd.ryo();
        if (ryo > 0) {
            if (porte < ryo) { module.refus(de, "Pas assez dans ta bourse", "tu as " + porte + " ryo sur toi"); return; }
        } else if (main == null || main.getType().isAir()) {
            module.refus(de, "Main vide", "prends l'objet en main");
            return;
        }
        Offre ex = offres.get(a.getUniqueId());
        if (ex != null && ex.fin > System.currentTimeMillis() && !ex.de.equals(de.getUniqueId())) {
            module.refus(de, "Déjà occupé", nom(a, de) + " a une offre en cours");
            return;
        }
        long fin = System.currentTimeMillis() + k.delaiTendre * 1000L;
        Offre o = new Offre(de.getUniqueId(), a.getUniqueId(), ryo > 0 ? null : main.clone(),
                de.getInventory().getHeldItemSlot(), ryo, fin);
        offres.put(a.getUniqueId(), o);
        String quoi = quoi(o);
        NourritureCanal.J j = new NourritureCanal.J().s("t", "offre").s("de", nom(de, a))
                .s("quoi", quoi).n("s", k.delaiTendre);
        if (o.objet != null) {
            String etat = etat(o.objet);
            if (etat != null) j.s("etat", etat);
            j.s("mat", o.objet.getType().getKey().toString());
            ItemMeta m = o.objet.hasItemMeta() ? o.objet.getItemMeta() : null;
            if (m != null && m.hasItemModel() && m.getItemModel() != null) j.s("model", m.getItemModel().toString());
        } else j.s("ic", "ryo");
        NourritureCanal.envoyer(a, j);
        if (!NourritureCanal.ecoute(a)) {
            a.sendMessage(net.kyori.adventure.text.Component.text(nom(de, a) + " te tend " + quoi
                    + " — /tendre prendre ou /tendre refuser (" + k.delaiTendre + " s)", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        }
        NourritureCanal.envoyer(de, new NourritureCanal.J().s("t", "attente").s("a", nom(a, de)).s("quoi", quoi)
                .n("s", k.delaiTendre));
    }

    /* ================================================================ réponses */

    private void prendre(Player a) {
        Offre o = offres.remove(a.getUniqueId());
        if (o == null || o.fin < System.currentTimeMillis()) {
            NourritureCanal.envoyer(a, new NourritureCanal.J().s("t", "offre_fin"));
            return;
        }
        Player de = plugin.getServer().getPlayer(o.de);
        fin(a, de);
        if (de == null) { module.refus(a, "Trop tard", "la personne est partie"); return; }
        String pb = obstacle(de, a, module.cfg().distanceTendre + 1.5);
        if (pb != null) { module.refus(a, "Impossible de prendre", pb); module.refus(de, "Don annulé", pb); return; }
        if (o.objet == null) donnerRyo(de, a, o.ryo);
        else donnerObjet(de, a, o);
    }

    private void refuser(Player a, boolean dire) {
        Offre o = offres.remove(a.getUniqueId());
        if (o == null) return;
        Player de = plugin.getServer().getPlayer(o.de);
        fin(a, de);
        if (dire && de != null) module.info(de, null, nom(a, de), "refuse");
    }

    private void fin(Player a, Player de) {
        NourritureCanal.envoyer(a, new NourritureCanal.J().s("t", "offre_fin"));
        if (de != null) NourritureCanal.envoyer(de, new NourritureCanal.J().s("t", "attente_fin"));
    }

    private void donnerObjet(Player de, Player a, Offre o) {
        ItemStack enMain = de.getInventory().getItem(o.slot);
        if (enMain == null || !enMain.isSimilar(o.objet) || enMain.getAmount() < o.objet.getAmount()) {
            module.refus(a, "Don annulé", "l'objet n'est plus en main");
            module.refus(de, "Don annulé", "l'objet n'est plus en main");
            return;
        }
        String refus = plugin.rpInventory() == null ? null : plugin.rpInventory().refusReception(a, o.objet);
        if (refus != null) {
            module.refus(a, "Impossible de prendre", refus);
            module.refus(de, nom(a, de) + " ne peut pas prendre", refus);
            return;
        }
        int n = o.objet.getAmount();
        de.getInventory().setItem(o.slot, enMain.getAmount() == n ? null : moins(enMain, n));
        int reste = plugin.rpInventory() == null ? 0 : plugin.rpInventory().recevoir(a, o.objet);
        if (plugin.rpInventory() == null) a.getInventory().addItem(o.objet);
        if (reste > 0) {   // ne devrait pas arriver (vérifié) : on rend au donneur
            ItemStack r = o.objet.clone();
            r.setAmount(reste);
            InventoryManager.donner(de, r);
        }
        if (plugin.rpInventory() != null) plugin.rpInventory().recalc(de);
        String etat = etat(o.objet);
        Notifs.objet(a, true, o.objet, n - reste, "de " + nom(de, a) + (etat != null ? " · " + etat : ""));
        Notifs.objet(de, false, o.objet, n - reste, "tendu à " + nom(a, de));
        a.playSound(a.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.35f, 1.1f);
        Journal.entree(Categorie.OBJET, "DON").acteur(de).cible(a)
                .detail((n - reste) + " × " + RareteItems.nom(o.objet) + (etat != null ? " · " + etat : ""))
                .lieu(a.getLocation()).ecrire();
    }

    private void donnerRyo(Player de, Player a, long n) {
        ShinobiCharacter cd = plugin.characters().getActive(de.getUniqueId());
        ShinobiCharacter ca = plugin.characters().getActive(a.getUniqueId());
        if (cd == null || ca == null) return;
        var monnaie = plugin.monnaie();
        if (monnaie != null) {   // Monnaie (15/10) : de bourse à bourse
            if (!monnaie.bourses().debiter(de, n, "DON", "don à " + PlayerIds.prefix(ca) + ca.name(), de)) {
                module.refus(a, "Don annulé", "pas assez de ryo");
                module.refus(de, "Pas assez dans ta bourse", "tu as " + monnaie.bourses().montant(de) + " ryo sur toi");
                return;
            }
            if (!monnaie.bourses().crediter(a, n, "DON", "don de " + PlayerIds.prefix(cd) + cd.name(), de)) {
                monnaie.bourses().crediter(de, n, "DON", "don rendu (plus de place chez " + PlayerIds.prefix(ca) + ca.name() + ")", de);
                module.refus(a, "Plus de place pour ta bourse", "libère une case");
                module.refus(de, "Don annulé", nom(a, de) + " n'a plus de place");
                return;
            }
            module.info(a, "ryo", "+" + n + " ryo", "de " + nom(de, a));
            module.info(de, "ryo", "−" + n + " ryo", "à " + nom(a, de));
            a.playSound(a.getLocation(), Sound.BLOCK_CHAIN_STEP, SoundCategory.PLAYERS, 0.5f, 1.6f);
            return;
        }
        RyoJournal.contexte("don à " + PlayerIds.prefix(ca) + ca.name(), de);
        boolean ok = cd.trySpendRyo(n);
        RyoJournal.fin();
        if (!ok) {
            module.refus(a, "Don annulé", "pas assez de ryo");
            module.refus(de, "Pas assez de ryo", "tu as " + cd.ryo() + " ryo");
            return;
        }
        RyoJournal.contexte("don de " + PlayerIds.prefix(cd) + cd.name(), de);
        ca.addRyo(n);
        RyoJournal.fin();
        plugin.characters().save(cd);
        plugin.characters().save(ca);
        module.info(a, "ryo", "+" + n + " ryo", "de " + nom(de, a));
        module.info(de, "ryo", "−" + n + " ryo", "à " + nom(a, de));
        a.playSound(a.getLocation(), Sound.BLOCK_CHAIN_STEP, SoundCategory.PLAYERS, 0.5f, 1.6f);
        Journal.entree(Categorie.ECONOMIE, "DON").acteur(de).cible(a).detail(n + " ryo").lieu(a.getLocation()).ecrire();
    }

    /* ================================================================ outils */

    /** Raison qui empêche le geste, ou null. */
    private String obstacle(Player de, Player a, double distance) {
        if (de.equals(a)) return "à soi-même";
        ShinobiCharacter cd = plugin.characters().getActive(de.getUniqueId());
        ShinobiCharacter ca = plugin.characters().getActive(a.getUniqueId());
        if (cd == null || ca == null) return "pas de personnage";
        if (ModeStaff.horsJeu(de) || ModeStaff.horsJeu(a)) return "mode staff";
        if (de.getWorld() != a.getWorld() || de.getLocation().distance(a.getLocation()) > distance) return "trop loin";
        if (!de.hasLineOfSight(a)) return "hors de vue";
        if (plugin.ko() != null && (plugin.ko().isKo(de.getUniqueId()) || plugin.ko().isKo(a.getUniqueId()))) return "KO";
        if (plugin.rpInventory() != null) {
            String v = plugin.rpInventory().lockReason(de);
            if (v == null) v = plugin.rpInventory().lockReason(a);
            if (v != null) return v;
        }
        return null;
    }

    private String quoi(Offre o) {
        if (o.objet == null) return o.ryo + " ryo";
        int n = o.objet.getAmount();
        String nom = RareteItems.nom(o.objet);
        return n > 1 ? n + " × " + nom : (feminin(nom) ? "une " : "un ") + nom;
    }

    private static boolean feminin(String nom) {
        String n = nom.toLowerCase(java.util.Locale.FRANCE);
        return n.startsWith("ration") || n.startsWith("pilule") || n.startsWith("fiole") || n.startsWith("carte");
    }

    /** « chaud » / « froid » pour un plat, sinon null. */
    private static String etat(ItemStack it) {
        if (!NourritureItems.PLAT.equals(NourritureItems.genre(it))) return null;
        return NourritureItems.chaud(it) > System.currentTimeMillis() ? "chaud" : "froid";
    }

    private static ItemStack moins(ItemStack s, int n) {
        ItemStack c = s.clone();
        c.setAmount(s.getAmount() - n);
        return c;
    }

    /**
     * Le nom de {@code sujet} tel que {@code vue} le connaît (système « rencontrer » : un inconnu reste « Quelqu'un »,
     * jamais son vrai nom).
     */
    private String nom(Player sujet, Player vue) {
        ShinobiCharacter s = plugin.characters().getActive(sujet.getUniqueId());
        ShinobiCharacter v = vue == null ? null : plugin.characters().getActive(vue.getUniqueId());
        if (s == null) return sujet.getName();
        String t = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(com.reborn.shinobicore.character.CharacterDisplay.styledNameFor(s, v)).trim();
        return t.isEmpty() || t.contains(com.reborn.shinobicore.character.CharacterDisplay.UNKNOWN_LABEL) ? "Quelqu'un" : t;
    }

    void tick() {
        long now = System.currentTimeMillis();
        for (Offre o : new ArrayList<>(offres.values())) {
            if (o.fin >= now) continue;
            offres.remove(o.a);
            Player a = plugin.getServer().getPlayer(o.a), de = plugin.getServer().getPlayer(o.de);
            if (a != null) fin(a, de);
            else if (de != null) NourritureCanal.envoyer(de, new NourritureCanal.J().s("t", "attente_fin"));
        }
    }

    void oublier(UUID id) {
        offres.remove(id);
        offres.values().removeIf(o -> o.de.equals(id));
    }
}
