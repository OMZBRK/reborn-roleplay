package fr.reborn.hud.interaction;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Construit le menu d'interaction (style GTA) selon la cible visée et
 * dispatche les actions vers des commandes serveur.
 *
 * <p><b>Dépendance serveur</b> : les commandes (saluer/porter/fouiller…) doivent
 * exister côté plugin (ShinobiCore &amp; co). Tant qu'elles n'existent pas, elles
 * apparaîtront comme « commande inconnue ». Le framework client (menu +
 * sous-menus + dispatch) est complet ; il suffira de mapper les vraies commandes.
 */
public final class InteractionMenus {

    private InteractionMenus() {}

    /** Envoie une commande au serveur (sans le slash). */
    public static void sendCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(command);
        }
    }

    /** Affiche un message d'info côté client (chat local, pas envoyé au serveur). */
    public static void info(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§6[Reborn] §f" + text));
        }
    }

    /** Copie du texte dans le presse-papiers. */
    public static void copy(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        info("Copié : §e" + text);
    }

    /** Menu d'un joueur ; sur un corps à terre / KO / mort, celui du corps (KO-7) ; en douleur, {@link #forPain}. */
    public static List<InteractionItem> forPlayer(String name, java.util.UUID id) {
        fr.reborn.hud.ko.CorpsClient.Body b = fr.reborn.hud.ko.CorpsClient.body(id);
        if (b != null && b.phase() == fr.reborn.hud.ko.CorpsClient.PHASE_DOULEUR) return forPain(name);
        return b != null ? forBody(name, b) : conscient(name, id);
    }

    /** Je suis moi-même en douleur : porter, attraper, exécuter me sont fermés. */
    private static final String A_BOUT = "à bout de forces";

    /**
     * Personne <b>en douleur</b> (PV sous 5 %, encore debout) : on peut la <b>porter sans son accord</b> ; l'exécuter
     * se fait à la touche (F maintenue, {@link fr.reborn.hud.ko.ExecuterClient}) — le menu ne garde que le choix de
     * l'animation, si l'on en possède. Le reste du menu d'un joueur conscient demeure.
     */
    public static List<InteractionItem> forPain(String name) {
        boolean moi = fr.reborn.hud.ko.CorpsClient.douleur();
        java.util.List<InteractionItem> l = new java.util.ArrayList<>();
        l.add(InteractionItem.action("Ausculter (état)", () -> sendCommand("osculter")));
        l.add(moi ? InteractionItem.disabled("Porter", A_BOUT)
            : InteractionItem.action("Porter (sans accord)", () -> sendCommand("corps porter " + name)));
        InteractionItem anim = moi ? null : animation();
        if (anim != null) l.add(anim);
        l.add(InteractionItem.action("Saluer", () -> sendCommand("me salue " + name)));
        l.add(InteractionItem.action("Se présenter", () -> sendCommand("rencontrer")));
        l.add(InteractionItem.action("Message privé…", () -> openChat("/msg " + name + " ")));
        l.add(soinsDesYeux(name));
        return l;
    }

    /**
     * « Animation d'exécution ▸ » : choisir l'exécution jouée à la touche F (« sans animation » ou une exécution
     * possédée ; la courante est marquée). Null si l'on n'en possède aucune : l'entrée disparaît.
     */
    public static InteractionItem animation() {
        var ex = fr.reborn.hud.ko.CorpsClient.executions();
        if (ex.isEmpty()) return null;
        String cur = fr.reborn.hud.ko.ExecuterClient.choix();
        java.util.List<InteractionItem> ch = new java.util.ArrayList<>();
        ch.add(InteractionItem.action(("sobre".equals(cur) ? "› " : "") + "Sans animation",
            () -> fr.reborn.hud.ko.ExecuterClient.choisir("sobre")));
        for (fr.reborn.hud.ko.CorpsClient.Exec e : ex) {
            ch.add(InteractionItem.action((e.id().equals(cur) ? "› " : "") + e.name(),
                () -> fr.reborn.hud.ko.ExecuterClient.choisir(e.id())));
        }
        return InteractionItem.submenu("Animation d'exécution", ch);
    }

    /**
     * Corps à terre ou KO : ausculter, fouiller, porter, attraper à la gorge, et la mise à mort RP. Avant la
     * validation du staff : « Demander un RPK », puis « RPK demandé » (grisé). Une fois le RPK validé, l'exécution se
     * fait à la touche F (deux appuis : armer, puis maintenir) ; le menu ne garde que le choix de l'animation.
     */
    public static List<InteractionItem> forBody(String name, fr.reborn.hud.ko.CorpsClient.Body b) {
        boolean moi = fr.reborn.hud.ko.CorpsClient.douleur();
        if (b.phase() == 3) {
            return List.of(
                    InteractionItem.action("Constater la mort", () -> info("Ce corps ne respire plus.")),
                    InteractionItem.action("Porter", () -> sendCommand("corps porter " + name)),
                    InteractionItem.action("Fouiller", () -> sendCommand("corps fouiller " + name)),
                    prelever(name));
        }
        java.util.List<InteractionItem> l = new java.util.ArrayList<>();
        l.add(InteractionItem.action("Ausculter (état)", () -> sendCommand("osculter")));
        l.add(moi ? InteractionItem.disabled("Attraper à la gorge", A_BOUT)
            : InteractionItem.action("Attraper à la gorge", () -> sendCommand("corps gorge " + name)));
        l.add(moi ? InteractionItem.disabled("Porter", A_BOUT)
            : InteractionItem.action("Porter", () -> sendCommand("corps porter " + name)));
        l.add(InteractionItem.action("Fouiller", () -> sendCommand("corps fouiller " + name)));
        // Radio (14/10) : couper ou prendre l'oreillette d'un joueur à terre / inconscient.
        InteractionItem ore = fr.reborn.hud.radio.RadioMenus.oreillette(name, b.phase() == 2);
        if (ore != null) l.add(ore);
        switch (b.rpk()) {
            case 2 -> {
                l.add(InteractionItem.disabled("RPK validé", "exécuter : F"));
                InteractionItem anim = moi ? null : animation();
                if (anim != null) l.add(anim);
            }
            case 1 -> l.add(InteractionItem.disabled("RPK demandé", "en attente du staff"));
            default -> l.add(InteractionItem.action("Demander un RPK", () -> sendCommand("corps rpk " + name)));
        }
        // Yeux : prélever (RPK validé), soigner (médic rang 2 : greffe ; médic : bandage) — le serveur tranche.
        l.add(b.rpk() == 2 ? prelever(name) : InteractionItem.submenu("Prélever un œil", List.of(
                InteractionItem.action("Il faut un RPK validé", () -> info("Prélever un œil sur un vivant exige un RPK validé par le staff.")))));
        l.add(soinsDesYeux(name));
        return l;
    }

    /** Sous-menu « Prélever un œil » (corps mort, mannequin au sol, ou KO avec RPK validé). {@code cible} : nom ou {@code corps:<uuid>}. */
    static InteractionItem prelever(String cible) {
        return InteractionItem.submenu("Prélever un œil", List.of(
                InteractionItem.action("Œil gauche", () -> sendCommand("yeux prelever " + cible + " gauche")),
                InteractionItem.action("Œil droit", () -> sendCommand("yeux prelever " + cible + " droit"))));
    }

    /** Sous-menu « Soins des yeux » : greffe (fiole en main, médic rang 2) et bandage des orbites (médic). */
    static InteractionItem soinsDesYeux(String name) {
        return InteractionItem.submenu("Soins des yeux", List.of(
                InteractionItem.action("Greffer l'œil gauche", () -> sendCommand("yeux greffer " + name + " gauche")),
                InteractionItem.action("Greffer l'œil droit", () -> sendCommand("yeux greffer " + name + " droit")),
                InteractionItem.action("Bander les yeux", () -> sendCommand("bandage " + name)),
                InteractionItem.action("Retirer le bandage", () -> sendCommand("bandage " + name + " retirer")),
                InteractionItem.action("Examiner les yeux", () -> sendCommand("yeux etat " + name))));
    }

    /**
     * Duel consenti : « Proposer un duel » ; si ce joueur m'a proposé un duel, l'accepter ou le refuser ; si je suis
     * en duel contre lui, l'abandonner. Le serveur revérifie tout (distance, KO, ATA, anti-spam).
     */
    static InteractionItem duel(String name) {
        if (fr.reborn.hud.duel.DuelClient.inDuelWith(name)) {
            return InteractionItem.action("Abandonner le duel", () -> sendCommand("duel abandonner"));
        }
        if (fr.reborn.hud.duel.DuelClient.invitedBy(name)) {
            return InteractionItem.submenu("Duel proposé", List.of(
                InteractionItem.action("Accepter le duel", () -> sendCommand("duel accepter " + name)),
                InteractionItem.action("Refuser le duel", () -> sendCommand("duel refuser " + name))));
        }
        return InteractionItem.action("Proposer un duel", () -> sendCommand("duel proposer " + name));
    }

    /** Menu d'une personne consciente, par son pseudo (bancs d'essai) : l'UUID est cherché dans le monde. */
    public static List<InteractionItem> forPlayer(String name) {
        return conscient(name, uuidDe(name));
    }

    private static java.util.UUID uuidDe(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) for (var p : mc.level.players()) if (p.getGameProfile().name().equals(name)) return p.getUUID();
        return java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Personne consciente. Porter et fouiller se <b>demandent</b> (GAMEPLAN §14, 24/10) : l'autre accepte (Entrée) ou
     * refuse (Retour) ; on la porte jusqu'à ce qu'elle descende (Espace) ; la fouille est en lecture seule. Le serveur
     * revérifie tout (états, distance) et journalise. Le sous-menu Staff n'apparaît qu'au staff.
     */
    private static List<InteractionItem> conscient(String name, java.util.UUID id) {
        // NB : les commandes ShinobiCore /rencontrer /amitier /osculter ciblent
        // « la personne regardée » (pas d'argument) → fonctionnent si la cible est à peu près devant.
        java.util.List<InteractionItem> l = new java.util.ArrayList<>();
        // Banque (15/10) : le guichetier en service ouvre le compte de son client (le client valide lui-même).
        if (fr.reborn.hud.monnaie.MonnaieClient.guichetier())
            l.add(InteractionItem.action("Ouvrir son compte", () -> sendCommand("banque client " + name)));
        l.addAll(tendre(name));
        // Radio en escouades (14/10) : créer / ajouter face à face, voir l'escouade.
        InteractionItem radio = fr.reborn.hud.radio.RadioMenus.radio(name);
        if (radio != null) l.add(radio);
        // Faction (16/10) : « Inviter dans la Police » si mon grade de section le permet (le serveur revérifie).
        InteractionItem faction = fr.reborn.hud.faction.FactionClient.menuR(name);
        if (faction != null) l.add(0, faction);
        l.addAll(List.of(
            InteractionItem.action("Saluer", () -> sendCommand("me salue " + name)),
            InteractionItem.action("Se présenter", () -> sendCommand("rencontrer")),
            InteractionItem.action("Message privé…", () -> openChat("/msg " + name + " ")),
            InteractionItem.action("Demander en ami", () -> sendCommand("amitier")),
            InteractionItem.action("Ausculter (état)", () -> sendCommand("osculter")),
            InteractionItem.action("Porter (avec son accord)", () -> sendCommand("corps demander porter " + name)),
            InteractionItem.action("Fouiller (avec son accord)", () -> sendCommand("corps demander fouiller " + name)),
            duel(name),
            soinsDesYeux(name),
            InteractionItem.submenu("Animations", List.of(
                InteractionItem.action("Saluer", () -> sendCommand("playemote salut")),
                InteractionItem.action("Applaudir", () -> sendCommand("playemote applaudir")),
                InteractionItem.action("Pointer", () -> sendCommand("playemote pointer")),
                InteractionItem.action("Pleurer", () -> sendCommand("playemote pleurer")),
                InteractionItem.action("Toutes les emotes…", InteractionMenus::openEmoteMenu),
                InteractionItem.action("Arrêter", () -> sendCommand("stopemote"))
            )),
            volume(id)
        ));
        if (fr.reborn.hud.menu.tablist.TablistData.selfStaff()) l.add(InteractionItem.submenu("Staff", List.of(
            InteractionItem.action("Fiche (Poste de garde)", () -> fr.reborn.hud.staff.StaffClient.ouvrirFiche(id)),
            InteractionItem.action("Aller à", () -> sendCommand("aller " + name)),
            InteractionItem.action("Ramener", () -> sendCommand("ramener " + name)),
            InteractionItem.action("Geler", () -> sendCommand("geler " + name))
        )));
        return l;
    }

    /** Niveaux proposés : on baisse une voix, on ne la coupe jamais (le RP doit rester audible). */
    private static final int[] VOLUMES = {100, 75, 50, 25};

    /** « Volume de sa voix » : chez moi seulement, réglé dans Plasmo Voice. */
    static InteractionItem volume(java.util.UUID id) {
        int actuel = (int) Math.round(fr.reborn.hud.voice.VoiceState.playerVolume(id) * 100);
        java.util.List<InteractionItem> l = new java.util.ArrayList<>();
        for (int v : VOLUMES) {
            if (Math.abs(v - actuel) < 5) l.add(InteractionItem.disabled(v + " %", "actuel"));
            else l.add(InteractionItem.action(v + " %", () -> {
                fr.reborn.hud.voice.VoiceState.setPlayerVolume(id, v / 100.0);
                info("Sa voix est réglée à §e" + v + " %§r chez toi.");
            }));
        }
        l.add(InteractionItem.disabled("Jamais coupée", "on baisse, on ne coupe pas"));
        return InteractionItem.submenu("Volume de sa voix", l);
    }

    /**
     * <b>Tendre</b> (décision du 14/10 : un geste, pas une fenêtre d'échange) : « Tendre &lt;objet en main&gt; » et
     * « Donner des ryo » (montants courants, ou un montant tapé). L'autre prend avec F ou refuse avec Retour ; le serveur
     * revérifie tout (distance, place, poids, solde) et journalise le don.
     */
    static List<InteractionItem> tendre(String name) {
        java.util.List<InteractionItem> l = new java.util.ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.item.ItemStack main = mc.player == null ? net.minecraft.world.item.ItemStack.EMPTY
            : mc.player.getMainHandItem();
        if (!main.isEmpty()) {
            long ryo = fr.reborn.hud.monnaie.BourseClient.montant(main);
            String quoi = ryo >= 0 ? "Bourse · " + fr.reborn.hud.monnaie.BourseClient.fmt(ryo) + " ryo"   // la bourse se tend entière
                : main.getHoverName().getString() + (main.getCount() > 1 ? " ×" + main.getCount() : "");
            l.add(InteractionItem.action("Tendre " + quoi, () -> sendCommand("tendre " + name)));
        } else {
            l.add(InteractionItem.disabled("Tendre", "rien en main"));
        }
        java.util.List<InteractionItem> ryo = new java.util.ArrayList<>();
        for (int n : new int[] {10, 50, 100, 120, 150, 200, 500}) {
            ryo.add(InteractionItem.action(n + " ryo", () -> sendCommand("tendre " + name + " ryo " + n)));
        }
        ryo.add(InteractionItem.action("Autre montant…", () -> openChat("/tendre " + name + " ryo ")));
        l.add(InteractionItem.submenu("Donner des ryo", ryo));
        return l;
    }

    public static List<InteractionItem> forEntity(Entity e) {
        String type = e.getType().getDescription().getString();
        if (fr.reborn.hud.ko.CorpsClient.isCorpse(e.getUUID())) {
            return List.of(
                    InteractionItem.action("Constater la mort", () -> info("Ce corps est froid.")),
                    // Monnaie (15/10) : le corps laissé au sol se fouille même si le joueur est parti.
                    InteractionItem.action("Fouiller", () -> sendCommand("corps fouiller corps:" + e.getUUID())),
                    prelever("corps:" + e.getUUID()));
        }
        // Cloche et volières (16/10) : le meuble Nexo visé (entité d'interaction) d'une cloche ou d'un perchoir.
        java.util.List<InteractionItem> comm = fr.reborn.hud.communication.CommClient.menuR(e.getX(), e.getY(), e.getZ());
        if (!comm.isEmpty()) return comm;
        return List.of(
            InteractionItem.action("Inspecter (" + type + ")", () -> {
                String hp = (e instanceof LivingEntity le)
                    ? " §7| PV " + (int) le.getHealth() + "/" + (int) le.getMaxHealth() : "";
                info("§e" + type + " §7| pos " + e.blockPosition().toShortString() + hp);
            }),
            InteractionItem.action("Copier la position",
                () -> copy(e.getBlockX() + " " + e.getBlockY() + " " + e.getBlockZ()))
        );
    }

    public static List<InteractionItem> forBlock(BlockPos pos) {
        // Cloche et volières (16/10) : « Sonner la cloche », « Écrire une missive », « Missives ici » sur le meuble visé.
        java.util.List<InteractionItem> comm = fr.reborn.hud.communication.CommClient.menuR(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (!comm.isEmpty()) return comm;
        return List.of(
            InteractionItem.action("Inspecter le bloc", () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) return;
                BlockState bs = mc.level.getBlockState(pos);
                info("§e" + BuiltInRegistries.BLOCK.getKey(bs.getBlock())
                    + " §7@ " + pos.toShortString());
            }),
            InteractionItem.action("Copier les coordonnées",
                () -> copy(pos.getX() + " " + pos.getY() + " " + pos.getZ())),
            InteractionItem.submenu("Outils de debug", List.of(
                InteractionItem.action("Afficher l'état complet", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null) info("§7" + mc.level.getBlockState(pos).toString());
                })
            ))
        );
    }

    /** Menu « sur soi » (clic dans le vide). Mappe de vraies commandes ShinobiCore. */
    public static List<InteractionItem> forSelf() {
        return List.of(
            InteractionItem.action("Ma fiche personnage", () -> sendCommand("character")),
            InteractionItem.action("Mon état", () -> sendCommand("etat")),
            InteractionItem.action("Méditer", () -> sendCommand("meditation")),
            InteractionItem.submenu("Mes emotes", List.of(
                InteractionItem.action("Saluer", () -> sendCommand("playemote salut")),
                InteractionItem.action("Applaudir", () -> sendCommand("playemote applaudir")),
                InteractionItem.action("Pointer", () -> sendCommand("playemote pointer")),
                InteractionItem.action("Pleurer", () -> sendCommand("playemote pleurer")),
                InteractionItem.action("Toutes les emotes…", InteractionMenus::openEmoteMenu),
                InteractionItem.action("Arrêter", () -> sendCommand("stopemote"))
            )),
            InteractionItem.action("Action RP (/me)…", () -> openChat("/me "))
        );
    }

    /**
     * Ouvre le chat pré-rempli (le joueur tape la suite) — pour les commandes qui attendent un
     * texte (message privé, action RP) au lieu de les envoyer vides.
     */
    public static void openChat(String prefill) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreenAndShow(new net.minecraft.client.gui.screens.ChatScreen(prefill, false)));
    }

    /** Ouvre le menu Reborn directement sur l'onglet ANIMATIONS (liste d'emotes). */
    public static void openEmoteMenu() {
        Minecraft.getInstance().setScreenAndShow(
            fr.reborn.hud.animation.AnimationMenuScreen.emotesTab(null));
    }

    public static List<InteractionItem> generic() {
        return List.of(
            InteractionItem.action("Rien à proximité", () -> {})
        );
    }
}
