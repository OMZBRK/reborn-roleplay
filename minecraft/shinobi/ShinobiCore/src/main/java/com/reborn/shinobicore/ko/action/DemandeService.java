package com.reborn.shinobicore.ko.action;

import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.etat.Capacite;
import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.etat.Verrous;
import com.reborn.shinobicore.ko.KoJournal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demandes d'accord du menu R (GAMEPLAN §14, décision du 24/10) : <b>porter</b> ou <b>fouiller</b> une personne
 * consciente. {@code /corps demander porter|fouiller <joueur>} envoie la demande ; la personne répond avec
 * {@code /corps accepter} (Entrée) ou {@code /corps refuser} (Retour) dans le délai ; tout est revérifié à
 * l'acceptation (distance, états, matrice des capacités) et journalisé. Une seule demande en attente par personne
 * sollicitée ; un court délai par couple évite d'insister.
 *
 * <p>Canal {@code reborn:corps} : {@code {"t":"demande","de","nom","quoi","s"}} puis {@code {"t":"demande_fin"}}. Un
 * client sans le mod reçoit la demande dans le tchat.
 */
public final class DemandeService {

    private static final double PORTEE = 3.5;
    private static final int DELAI_S = 15;
    private static final long INSISTER_MS = 8_000;

    private record Demande(UUID de, String quoi, long expire) {}

    private final ShinobiCore plugin;
    private final CorpsService corps;
    /** personne sollicitée → demande en attente. */
    private final Map<UUID, Demande> enAttente = new ConcurrentHashMap<>();
    /** « demandeur:cible » → dernière demande (anti-insistance). */
    private final Map<String, Long> derniere = new ConcurrentHashMap<>();

    DemandeService(ShinobiCore plugin, CorpsService corps) {
        this.plugin = plugin;
        this.corps = corps;
    }

    /** {@code /corps demander <porter|fouiller> <joueur>}. */
    void demander(Player p, String quoi, String cibleNom) {
        if (!quoi.equals("porter") && !quoi.equals("fouiller")) return;
        Player t = Bukkit.getPlayerExact(cibleNom);
        if (t == null || t == p) { barre(p, "Personne ici."); return; }
        String r = obstacle(p, t, quoi, PORTEE);
        if (r != null) { barre(p, r); return; }
        Demande d = enAttente.get(t.getUniqueId());
        if (d != null && d.expire() > System.currentTimeMillis() && !d.de().equals(p.getUniqueId())) {
            barre(p, "On lui demande déjà autre chose.");
            return;
        }
        String cle = p.getUniqueId() + ":" + t.getUniqueId();
        Long der = derniere.get(cle);
        long now = System.currentTimeMillis();
        if (der != null && now - der < INSISTER_MS && d == null) { barre(p, "Laisse-lui un instant."); return; }
        derniere.put(cle, now);
        enAttente.put(t.getUniqueId(), new Demande(p.getUniqueId(), quoi, now + DELAI_S * 1000L));
        String nom = nom(p, t);
        if (t.getListeningPluginChannels().contains(CorpsService.CHANNEL)) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "demande");
            o.addProperty("de", p.getUniqueId().toString());
            o.addProperty("nom", nom);
            o.addProperty("quoi", quoi);
            o.addProperty("s", DELAI_S);
            corps.send(t, o);
        } else {
            t.sendMessage(Component.text(nom + (quoi.equals("porter") ? " veut te porter" : " veut te fouiller")
                    + " : /corps accepter ou /corps refuser.", NamedTextColor.GRAY));
        }
        barre(p, "Demande envoyée à " + nom(t, p) + ".");
    }

    /** {@code /corps accepter} : la personne sollicitée accepte la demande en attente. */
    void accepter(Player t) {
        Demande d = retirer(t);
        if (d == null) return;
        Player p = Bukkit.getPlayer(d.de());
        if (p == null) { barre(t, "Cette personne est partie."); return; }
        String r = obstacle(p, t, d.quoi(), PORTEE + 1.5);
        if (r != null) {
            barre(t, "Impossible maintenant.");
            barre(p, r);
            return;
        }
        if (d.quoi().equals("porter")) {
            if (!plugin.porter().carryConsenti(p, t)) barre(p, "Impossible de la porter maintenant.");
            else com.reborn.shinobicore.character.AutoMe.broadcast(plugin, p, "prend " + nom(t, null) + " sur son dos.");
        } else {
            if (plugin.monnaie() == null || !plugin.monnaie().canal().modde(p)) { barre(p, "La fouille demande le mod Reborn à jour."); return; }
            plugin.monnaie().fouille().commencerConsentie(p, t);
            KoJournal.log(plugin, p, "Fouille", KoJournal.who(plugin, t) + " (avec son accord, lecture seule)");
        }
    }

    /** {@code /corps refuser}. */
    void refuser(Player t) {
        Demande d = retirer(t);
        if (d == null) return;
        Player p = Bukkit.getPlayer(d.de());
        if (p != null) barre(p, nom(t, p) + " refuse.");
    }

    /** Délais écoulés, joueurs partis (appelé chaque seconde par {@link CorpsService}). */
    void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Demande> e : new ArrayList<>(enAttente.entrySet())) {
            Player t = Bukkit.getPlayer(e.getKey());
            Player p = Bukkit.getPlayer(e.getValue().de());
            if (t != null && p != null && e.getValue().expire() > now) continue;
            enAttente.remove(e.getKey());
            if (t != null) fin(t);
            if (p != null && t != null) barre(p, "Pas de réponse.");
        }
        derniere.values().removeIf(v -> now - v > 60_000);
    }

    private Demande retirer(Player t) {
        Demande d = enAttente.remove(t.getUniqueId());
        fin(t);
        return d != null && d.expire() > System.currentTimeMillis() ? d : null;
    }

    private void fin(Player t) {
        if (!t.getListeningPluginChannels().contains(CorpsService.CHANNEL)) return;
        JsonObject o = new JsonObject();
        o.addProperty("t", "demande_fin");
        corps.send(t, o);
    }

    /**
     * Ce qui empêche la demande, ou null : distance, monde, états des deux (KO, douleur, porté, gel, cinématique,
     * mode staff, invisible), matrice des capacités (PORTER / FOUILLER, ETRE_PORTE / ETRE_FOUILLE).
     */
    private String obstacle(Player p, Player t, String quoi, double portee) {
        UUID a = p.getUniqueId(), c = t.getUniqueId();
        if (plugin.characters().getActive(a) == null || plugin.characters().getActive(c) == null) return "Pas maintenant.";
        if (!t.getWorld().equals(p.getWorld()) || t.getLocation().distance(p.getLocation()) > portee) return "Trop loin.";
        if (Verrous.horsJeu(c) || (plugin.vanish() != null && plugin.vanish().isVanished(c))) return "Personne ici.";
        if (Verrous.gele(a) || Verrous.cinematique(a) || Verrous.horsJeu(a)) return "Tu ne peux pas maintenant.";
        if (Verrous.gele(c)) return "Gelé par le staff : on n'y touche pas.";
        if (plugin.ko().isKo(a) || plugin.ko().isKo(c)) return "Pas maintenant.";
        if (plugin.deathScene() != null && (plugin.deathScene().isDead(a) || plugin.deathScene().isDead(c))) return "Pas maintenant.";
        boolean douleurActeur = plugin.douleur() != null && plugin.douleur().isInPain(a);
        if (douleurActeur && quoi.equals("porter")) return "À bout de forces : tu ne peux pas.";
        if (plugin.porter().carrierOf(a) != null || plugin.porter().carrierOf(c) != null
                || plugin.porter().isCarrying(a) || plugin.porter().isCarrying(c)) return "Pas maintenant.";
        if (corps.isHolding(a) || corps.holderOf(c) != null) return "Pas maintenant.";
        Capacite capA = quoi.equals("porter") ? Capacite.PORTER : Capacite.FOUILLER;
        Capacite capC = quoi.equals("porter") ? Capacite.ETRE_PORTE : Capacite.ETRE_FOUILLE;
        String r = Verrous.refus(a, capA);
        if (r != null) return r;
        r = Verrous.refusSur(a, c, capC);
        return r != null ? "Impossible : " + r + "." : null;
    }

    /** Le nom de {@code sujet} tel que {@code vue} le connaît (un inconnu reste « Quelqu'un »). */
    private String nom(Player sujet, Player vue) {
        ShinobiCharacter s = plugin.characters().getActive(sujet.getUniqueId());
        if (s == null) return sujet.getName();
        if (vue == null) return s.name();
        ShinobiCharacter v = plugin.characters().getActive(vue.getUniqueId());
        String n = PlainTextComponentSerializer.plainText().serialize(CharacterDisplay.styledNameFor(s, v)).trim();
        return n.isEmpty() || n.contains(CharacterDisplay.UNKNOWN_LABEL) ? "Quelqu'un" : n;
    }

    private static void barre(Player p, String msg) {
        p.sendActionBar(Component.text(msg, NamedTextColor.GRAY));
    }
}
