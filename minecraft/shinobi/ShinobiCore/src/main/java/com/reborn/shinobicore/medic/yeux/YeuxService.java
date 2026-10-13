package com.reborn.shinobicore.medic.yeux;

import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.fiche.FichePhrases;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Chirurgie des yeux — le modèle et le canal. Chaque personnage a deux orbites ({@link Oeil} gauche/droit :
 * type, état NORMAL/MANQUANT, intégrité) et peut porter un bandage. Un œil jamais touché n'est pas stocké : c'est
 * l'œil du clan ({@code Uchiha} → sharingan, {@code Hyūga} → byakugan, sinon normal), intact.
 *
 * <p>API pour les autres plugins : {@code ShinobiCore.yeux().eyes(uuid)}, {@link #hasWorkingEye(UUID, String)} —
 * pour, plus tard, bloquer un dōjutsu sans l'œil qui va avec.
 *
 * <p>Canal {@code reborn:yeux} (S2C, JSON UTF-8 brut, sans préfixe de longueur) :
 * <ul>
 *   <li>{@code {"t":"cecite","g":bool,"d":bool,"b":bool}} — g/d : cet œil ne voit pas (orbite vide ou intégrité
 *       sous {@code yeux.seuil-cecite}) ; b : bandage. Envoyé à la connexion (après 2 s), au changement de
 *       personnage et à chaque modification.</li>
 *   <li>{@code {"t":"ouvrir",…}} — l'écran de chirurgie (voir {@link YeuxCommand}).</li>
 * </ul>
 * Client sans mod : quelques messages texte de repli.
 */
public final class YeuxService implements Listener {

    public static final String CHANNEL = "reborn:yeux";

    /** Instantané des yeux d'un personnage. */
    public record Yeux(Oeil gauche, Oeil droit, boolean bandage) {
        public Oeil get(Cote c) { return c == Cote.GAUCHE ? gauche : droit; }
    }

    private final ShinobiCore plugin;

    public YeuxService(ShinobiCore plugin) {
        this.plugin = plugin;
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    /* ============================================================ réglages */

    public double portee() { return plugin.getConfig().getDouble("yeux.portee", 3.0); }

    public int seuilCecite() { return plugin.getConfig().getInt("yeux.seuil-cecite", 25); }

    public long jetonMillis() { return Math.max(10, plugin.getConfig().getLong("yeux.jeton-secondes", 120)) * 1000L; }

    public long consentementMillis() {
        return Math.max(10, plugin.getConfig().getLong("yeux.consentement-secondes", 60)) * 1000L;
    }

    /* ============================================================== modèle */

    /** Type d'œil de naissance du personnage (dōjutsu du clan, sinon {@code normal}). */
    public static String typeNatif(ShinobiCharacter c) {
        String d = c == null ? null : FichePhrases.dojutsuOf(c.clan());
        return d == null ? Oeil.NORMAL_TYPE : Oeil.normType(d);
    }

    /** L'œil {@code cote} du personnage (jamais {@code null} : défaut = œil du clan, intact). */
    public Oeil oeil(ShinobiCharacter c, Cote cote) {
        Oeil o = cote == Cote.GAUCHE ? c.oeilGauche() : c.oeilDroit();
        return o != null ? o : Oeil.intact(typeNatif(c));
    }

    public Yeux eyes(ShinobiCharacter c) {
        return new Yeux(oeil(c, Cote.GAUCHE), oeil(c, Cote.DROIT), c.yeuxBandes());
    }

    /** Yeux du personnage actif du joueur, ou {@code null} s'il n'en joue aucun. */
    public Yeux eyes(UUID player) {
        ShinobiCharacter c = plugin.characters().getActive(player);
        return c == null ? null : eyes(c);
    }

    /**
     * Le personnage actif du joueur a-t-il au moins un œil de ce type, en place et qui voit (intégrité au moins
     * égale au seuil de cécité) ? Le bandage n'est pas pris en compte (il ne couvre en pratique que des orbites
     * blessées). Faux sans personnage actif.
     */
    public boolean hasWorkingEye(UUID player, String type) {
        Yeux y = eyes(player);
        if (y == null) return false;
        String t = Oeil.normType(type);
        int seuil = seuilCecite();
        for (Oeil o : new Oeil[]{y.gauche(), y.droit()}) {
            if (o.type().equals(t) && !o.aveugle(seuil)) return true;
        }
        return false;
    }

    /** Nombre d'yeux qui fonctionnent de ce type (0, 1 ou 2). */
    public int workingEyes(UUID player, String type) {
        Yeux y = eyes(player);
        if (y == null) return 0;
        String t = Oeil.normType(type);
        int n = 0;
        for (Oeil o : new Oeil[]{y.gauche(), y.droit()}) if (o.type().equals(t) && !o.aveugle(seuilCecite())) n++;
        return n;
    }

    /** Pose l'œil (null = retour à l'œil du clan), persiste, et met à jour la cécité du porteur s'il le joue. */
    public void setOeil(ShinobiCharacter c, Cote cote, Oeil o) {
        if (cote == Cote.GAUCHE) c.setOeilGauche(o); else c.setOeilDroit(o);
        plugin.characters().save(c);
        refreshOwner(c);
    }

    public void setBandage(ShinobiCharacter c, boolean on) {
        c.setYeuxBandes(on);
        plugin.characters().save(c);
        refreshOwner(c);
    }

    private void refreshOwner(ShinobiCharacter c) {
        Player p = Bukkit.getPlayer(c.ownerId());
        if (p == null) return;
        ShinobiCharacter act = plugin.characters().getActive(p.getUniqueId());
        if (act != null && act.id().equals(c.id())) pushCecite(p, true);
    }

    /* =============================================================== canal */

    public boolean modded(Player p) { return p.getListeningPluginChannels().contains(CHANNEL); }

    public void send(Player p, JsonObject o) {
        p.sendPluginMessage(plugin, CHANNEL, o.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Envoie l'état de cécité du personnage actif ; sans mod, un message texte s'il y a quelque chose à dire. */
    public void pushCecite(Player p, boolean annoncer) {
        if (p == null || !p.isOnline()) return;
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        int seuil = seuilCecite();
        boolean g = false, d = false, b = false;
        if (c != null) {
            g = oeil(c, Cote.GAUCHE).aveugle(seuil);
            d = oeil(c, Cote.DROIT).aveugle(seuil);
            b = c.yeuxBandes();
        }
        if (modded(p)) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "cecite");
            o.addProperty("g", g);
            o.addProperty("d", d);
            o.addProperty("b", b);
            send(p, o);
        } else if (annoncer && (g || d)) {
            String txt = g && d ? "Tu ne vois plus rien : tes deux yeux sont perdus."
                    : "Tu ne vois plus de l'œil " + (g ? "gauche" : "droit") + ".";
            p.sendMessage(Component.text(txt + (b ? " Un bandage couvre tes orbites." : ""), NamedTextColor.GRAY));
        }
    }

    /* ========================================================== événements */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> pushCecite(p, true), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitch(CharacterSwitchEvent ev) {
        Player p = ev.player();
        // l'événement part avant la bascule : on lit le nouveau personnage actif un peu après
        Bukkit.getScheduler().runTaskLater(plugin, () -> pushCecite(p, true), 5L);
    }
}
