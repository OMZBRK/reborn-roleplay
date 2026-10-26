package fr.reborn.hud.douane;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.menu.RebornSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client du canal {@code reborn:douane} (protocole v1 : PLUGINS_REFERENCE §4 decies.4 à .7) : JSON UTF-8 brut dans les
 * deux sens, champs inconnus ignorés. Le serveur (ShinobiCore {@code douane/DouaneCanal}) décide de tout et revérifie
 * chaque demande (grade, permission, mode, bureau) ; le mod n'affiche et ne demande que.
 *
 * <ul>
 *   <li><b>Candidat</b> : ligne fine du haut, écran d'attente opaque, appel et fondu, question, « Bienvenue sur Reborn »,
 *       résultat, plaques (« Examinateur », pseudo à la place d'« Inconnu ») — rendus par {@link DouaneHud} ;</li>
 *   <li><b>Examinateur</b> : état de la douane, dossier résumé, question tirée, envoi de la décision, mode examinateur —
 *       lus par l'onglet Douane du Poste de garde ({@code staff/GardeDouane}).</li>
 * </ul>
 * Aucun roster n'arrive à un candidat : l'écran de sélection ne s'ouvre pas pendant la douane.
 */
public final class DouaneClient {

    private DouaneClient() {}

    /* ================================================================ état du candidat */

    /** Ligne du haut ({@code t:"ligne"}) ; {@code convocation} : champ facultatif (absent du protocole v1). */
    record Ligne(String etat, String texte, int rang, int total, String convocation, long at) { }

    static volatile Ligne ligne;
    /** Écran d'attente opaque : texte (null = aucun), début, fin (fondu de sortie). */
    static volatile String attenteTexte;
    static volatile long attenteAt, attenteFinAt;
    /** Appel : début, durée du fondu (ms), arrivée au bureau détectée (0 = pas encore). */
    static volatile long appelAt, appelArriveAt;
    static volatile int appelFonduMs = 500;
    static volatile String appelTexte = "Un examinateur t'appelle";
    private static Vec3 appelPos;
    /** Question montrée ({@code null} = aucune). */
    static volatile String question;
    static volatile long questionAt;
    /** Rappel des règles du hall (30 s à l'arrivée, rouvert par /douane). */
    static volatile long rappelAt;
    private static boolean rappelVu;
    /**
     * « Bienvenue sur Reborn » : opaque jusqu'à {@code fin} (le serveur ouvre la sélection juste après) ou l'ouverture
     * d'un écran de personnage, puis fondu ; le serveur compte en ticks, le mod ne devance pas (jamais le monde derrière).
     */
    static volatile long bienvenueAt, bienvenueFinAt;
    static volatile int bienvenueS = 3;
    static volatile String bienvenueTitre = "Bienvenue sur Reborn";
    static volatile String bienvenueDate = "";
    /** Résultat négatif ou différé : tenu jusqu'à la déconnexion (expulsion par le serveur). */
    static volatile long resultatAt;
    static volatile int resultatS = 8;
    static volatile String resultat, resultatTexte, resultatMotif;
    /** Sécurités si le serveur ne conclut pas : Bienvenue (au moins), résultat (en plus de {@code s}). */
    private static final long SECURITE_BIENVENUE_MS = 10_000L, SECURITE_RESULTAT_MS = 10_000L;
    /** Fondus de sortie (ms) : écran d'attente, Bienvenue. */
    static final long FONDU_ATTENTE_MS = 400L, FONDU_BIENVENUE_MS = 400L;
    /** Notification courte (candidat, ou examinateur Poste de garde fermé). */
    static volatile String toast;
    static volatile boolean toastErreur;
    static volatile long toastAt;
    /**
     * Le joueur est dans la douane en tant que candidat (de la première {@code ligne} ou du premier {@code appel} jusqu'à
     * {@code fin}). L'écran d'attente seul ne le pose pas : « Vérification de l'accès… » peut finir par la sélection,
     * que le serveur ouvre sans {@code fin}.
     */
    static volatile boolean candidat;

    /** Plaques ({@code t:"plaques"}) : examinateurs en mode dans le monde de la douane. */
    private static final Set<UUID> EXAMINATEURS = new HashSet<>();
    private static volatile boolean plaques;
    private static Object mondePlaques;

    /* ================================================================ état de l'examinateur */

    private static volatile JsonObject etat;
    /**
     * Réception du dernier {@code etat}. Le serveur le renvoie toutes les 5 s aux seuls examinateurs : au-delà de
     * {@link #ETAT_VALIDE_MS}, droits perdus ou douane coupée, l'instantané ne vaut plus (onglet et mode retirés).
     */
    private static volatile long etatAt;
    private static final long ETAT_VALIDE_MS = 15_000L;
    private static final Map<String, JsonObject> DOSSIERS = new HashMap<>();
    private static final Map<String, String> ERREURS_DOSSIER = new HashMap<>();
    /** Question tirée pour l'examinateur : {texte, montree, reste, u (candidat de son bureau), n (tirage n°)}. */
    private static volatile JsonObject tirage;
    /** Questions montrées, par candidat : {texte, heure}. */
    private static final Map<String, List<String[]>> POSEES = new HashMap<>();
    private static final Map<String, Integer> TIRAGES = new HashMap<>();
    /** Dernier état d'envoi d'une décision : {etat, resultat, texte, u, at}. */
    private static volatile JsonObject envoi;
    private static final Deque<String[]> HISTORIQUE = new ArrayDeque<>();
    private static volatile boolean modeExaminateur;
    /** Brouillons de décision par candidat (choix, motif, notes), gardés le temps de la connexion. */
    public static final class Brouillon {
        public String choix = "", motif = "", notes = "";
    }
    private static final Map<String, Brouillon> BROUILLONS = new HashMap<>();
    /** Un appel vient d'être demandé : le dossier qui arrive ouvre le volet élargi. */
    private static volatile long appelDemandeAt;
    private static volatile String dossierAOuvrir;

    /** Banc d'essai : les demandes C → S partent ici au lieu du serveur. */
    static Consumer<JsonObject> debugEnvoi;
    /** Banc d'essai : pseudo des mannequins (le vrai jeu ne montre de plaque douane que sur des joueurs). */
    static final Map<UUID, String> DEBUG_NOMS = new HashMap<>();

    /* ================================================================ initialisation */

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(DouanePayload.ID, DouanePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DouanePayload.ID, DouanePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(DouanePayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> recevoir(payload.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> toutEffacer());
        ClientTickEvents.END_CLIENT_TICK.register(DouaneClient::tick);
        // « /douane » (statut du candidat) : la ligne du rappel des règles revient 30 s.
        ClientSendMessageEvents.COMMAND.register(cmd -> {
            if (candidat && cmd != null && cmd.trim().equalsIgnoreCase("douane")) rappelAt = System.currentTimeMillis();
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("reborn-hud", "douane"), (g, dt) -> DouaneHud.render(g));
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> masquerHud());
    }

    private static void toutEffacer() {
        effacerCandidat();
        bienvenueAt = 0;
        bienvenueFinAt = 0;
        resultatAt = 0;
        toast = null;
        etat = null;
        etatAt = 0;
        tirage = null;
        envoi = null;
        modeExaminateur = false;
        dossierAOuvrir = null;
        synchronized (DOSSIERS) { DOSSIERS.clear(); ERREURS_DOSSIER.clear(); }
        synchronized (POSEES) { POSEES.clear(); TIRAGES.clear(); }
        synchronized (HISTORIQUE) { HISTORIQUE.clear(); }
        synchronized (BROUILLONS) { BROUILLONS.clear(); }
    }

    /** {@code fin} : sortie de la douane (ligne, question, écran, plaques). Bienvenue lance son fondu, le résultat reste. */
    private static void effacerCandidat() {
        ligne = null;
        attenteTexte = null;
        attenteFinAt = 0;
        appelAt = 0;
        appelArriveAt = 0;
        question = null;
        rappelAt = 0;
        rappelVu = false;
        candidat = false;
        plaques = false;
        synchronized (EXAMINATEURS) { EXAMINATEURS.clear(); }
    }

    /* ================================================================ réception (S → C) */

    static void recevoir(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); }
        catch (RuntimeException e) { return; }
        long now = System.currentTimeMillis();
        switch (str(o, "t")) {
            case "ligne" -> {
                String e = str(o, "etat");
                Ligne avant = ligne;
                ligne = new Ligne(e, str(o, "texte"), num(o, "rang"), num(o, "total"), str(o, "convocation"),
                        avant != null && avant.etat().equals(e) ? avant.at() : now);
                candidat = true;
                // première arrivée au hall : rappel des règles, 30 s
                if ("attente".equals(e) && !rappelVu) { rappelAt = now; rappelVu = true; }
                if ("bureau".equals(e) && appelAt > 0 && appelArriveAt == 0) appelArriveAt = now;
                if (!"bureau".equals(e)) question = null;
            }
            case "attente" -> {
                // ne touche pas à « candidat » : le HUD reste masqué par l'écran lui-même (masqueHud)
                if (bool(o, "on")) {
                    attenteTexte = str(o, "texte");
                    if (attenteFinAt != 0 || attenteAt == 0) attenteAt = now;
                    attenteFinAt = 0;
                } else if (attenteTexte != null && attenteFinAt == 0) attenteFinAt = now;
            }
            case "appel" -> {
                appelAt = now;
                appelArriveAt = 0;
                appelFonduMs = Math.max(100, (o.has("fondu") ? num(o, "fondu") : 10) * 50);
                String t = str(o, "texte");
                appelTexte = t.isBlank() ? "Un examinateur t'appelle" : t;
                Minecraft mc = Minecraft.getInstance();
                appelPos = mc.player == null ? null : mc.player.position();
                question = null;
                candidat = true;
                RebornSounds.playReborn("stats.light", 1.2f, 0.45f);   // son discret de l'appel
            }
            case "question" -> {
                String t = str(o, "texte");
                question = t.isBlank() ? null : t;
                questionAt = now;
            }
            case "bienvenue" -> {
                bienvenueAt = now;
                bienvenueFinAt = 0;
                bienvenueS = Math.max(1, o.has("s") ? num(o, "s") : 3);
                String t = str(o, "titre");
                bienvenueTitre = t.isBlank() ? "Bienvenue sur Reborn" : t;
                bienvenueDate = "Douane du " + java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM"))
                        + ", " + LocalTime.now().format(DateTimeFormatter.ofPattern("H' h 'mm"));
                attenteTexte = null;
                question = null;
                RebornSounds.confirm();
            }
            case "resultat" -> {
                resultatAt = now;
                resultatS = Math.max(1, o.has("s") ? num(o, "s") : 8);
                resultat = str(o, "resultat");
                resultatTexte = str(o, "texte");
                resultatMotif = str(o, "motif");   // facultatif : absent du protocole v1
                attenteTexte = null;
                question = null;
            }
            case "plaques" -> {
                synchronized (EXAMINATEURS) {
                    EXAMINATEURS.clear();
                    for (JsonElement e : arr(o, "examinateurs")) {
                        try { EXAMINATEURS.add(UUID.fromString(e.getAsString())); } catch (RuntimeException ignored) { }
                    }
                }
                plaques = true;
                Minecraft mc = Minecraft.getInstance();
                mondePlaques = mc.level;
            }
            case "fin" -> {
                effacerCandidat();
                if (bienvenueAt > 0 && bienvenueFinAt == 0) bienvenueFinAt = now;
            }
            case "toast" -> notifier(str(o, "m"), bool(o, "erreur"));
            case "etat" -> { etat = o; etatAt = now; }
            case "dossier" -> {
                String u = str(o, "u");
                synchronized (DOSSIERS) {
                    if (o.has("dossier") && o.get("dossier").isJsonObject()) {
                        DOSSIERS.put(u, o.getAsJsonObject("dossier"));
                        ERREURS_DOSSIER.remove(u);
                    } else ERREURS_DOSSIER.put(u, str(o, "erreur").isBlank() ? "Dossier indisponible." : str(o, "erreur"));
                }
                if (now - appelDemandeAt < 15_000L && u.equals(candidatDeMonBureau())) dossierAOuvrir = u;
            }
            case "tirage" -> {
                JsonObject t = o.deepCopy();
                String u = candidatDeMonBureau();
                t.addProperty("u", u == null ? "" : u);
                JsonObject avant = tirage;
                boolean nouveau = avant == null || !str(avant, "texte").equals(str(o, "texte")) || !str(avant, "u").equals(str(t, "u"));
                synchronized (POSEES) {
                    if (u != null && nouveau && !bool(o, "montree")) TIRAGES.merge(u, 1, Integer::sum);
                    t.addProperty("n", u == null ? 0 : TIRAGES.getOrDefault(u, 1));
                    if (u != null && bool(o, "montree")) {
                        List<String[]> l = POSEES.computeIfAbsent(u, k -> new ArrayList<>());
                        if (l.isEmpty() || !l.get(l.size() - 1)[0].equals(str(o, "texte")))
                            l.add(new String[]{str(o, "texte"), LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))});
                    }
                }
                if (avant != null && !o.has("reste") && avant.has("reste")) t.add("reste", avant.get("reste"));
                tirage = t;
            }
            case "envoi" -> {
                JsonObject e = o.deepCopy();
                String u = candidatDeMonBureau();
                e.addProperty("u", u == null ? (envoi != null ? str(envoi, "u") : "") : u);
                e.addProperty("at", now);
                envoi = e;
                String et = str(o, "etat");
                if (!"en_cours".equals(et)) {
                    synchronized (HISTORIQUE) {
                        HISTORIQUE.addFirst(new String[]{et, str(o, "texte"), LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))});
                        while (HISTORIQUE.size() > 6) HISTORIQUE.pollLast();
                    }
                    notifier(str(o, "texte"), "erreur".equals(et));
                }
                if ("ok".equals(et) && !str(e, "u").isEmpty()) synchronized (BROUILLONS) { BROUILLONS.remove(str(e, "u")); }
            }
            case "mode" -> {
                // le dernier instantané suit : sinon son « mode » garderait l'indicateur et la puce jusqu'au suivant
                boolean m = bool(o, "examinateur");
                modeExaminateur = m;
                JsonObject e = etat;
                if (e != null) e.addProperty("mode", m);
            }
            default -> { }
        }
    }

    /** Toast : dans le Poste de garde s'il est ouvert, sinon une petite ligne sous celle du haut. */
    private static void notifier(String m, boolean erreur) {
        if (m == null || m.isBlank()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof fr.reborn.hud.staff.StaffScreen) {
            fr.reborn.hud.staff.StaffClient.toast(m);
            if (erreur) RebornSounds.deny();
            return;
        }
        toast = m;
        toastErreur = erreur;
        toastAt = System.currentTimeMillis();
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        // Bienvenue : un écran de personnage (sélection, chargement, création) ouvert sans fin reçue lance le fondu
        if (bienvenueAt > 0 && bienvenueFinAt == 0) {
            var sc = mc.gui.screen();
            long now = System.currentTimeMillis();
            if (sc != null && now - bienvenueAt > 300 && sc.getClass().getName().startsWith("fr.reborn.hud.menu.character.")) {
                bienvenueFinAt = now;
            }
        }
        // plaques : valables jusqu'à fin ou jusqu'au changement de monde
        if (plaques && mondePlaques != mc.level) {
            plaques = false;
            synchronized (EXAMINATEURS) { EXAMINATEURS.clear(); }
        }
        // appel : la téléportation au bureau (saut de position) marque l'arrivée ; sinon, délai de secours
        if (appelAt > 0 && appelArriveAt == 0) {
            long now = System.currentTimeMillis();
            Vec3 p = mc.player.position();
            if (appelPos != null && p.distanceToSqr(appelPos) > 36 && now - appelAt > 150) appelArriveAt = now;
            else if (now - appelAt > appelFonduMs + 3000L) appelArriveAt = now;
            appelPos = p;
        }
    }

    /* ================================================================ HUD masqué pendant la douane */

    private static final String[] REBORN = {"vitals", "combat", "combat-endurance", "cooldowns", "concentration",
            "allure", "notifs", "ramasser", "interaction", "duel", "escouade", "nourriture", "boussole"};

    /**
     * Candidat dans la douane, ou écran opaque de la douane (attente neutre, Bienvenue, résultat) : aucun HUD de jeu
     * (barre d'action, cœurs, faim, armure, expérience, effets — la Cécité et l'Obscurité de l'attente comprises —,
     * vitals RP…). Restent le tchat, les noms et la ligne de la douane. Tout se calcule sur les horodatages : rien ne
     * dépend du rendu de {@link DouaneHud} pour se lever.
     */
    public static boolean masqueHud() {
        long now = System.currentTimeMillis();
        return candidat || attenteVisible(now) || bienvenueVisible(now) || resultatVisible(now);
    }

    /** Écran d'attente neutre affiché, fondu de sortie compris. */
    static boolean attenteVisible(long now) {
        long fin = attenteFinAt;
        return attenteTexte != null && (fin == 0 || now - fin < FONDU_ATTENTE_MS);
    }

    /** Début du fondu de Bienvenue : {@code fin} reçu (ou écran de personnage ouvert), sinon la sécurité. */
    static long bienvenueFonduAt() {
        long fin = bienvenueFinAt;
        if (fin > 0) return Math.max(fin, bienvenueAt + 300);
        return bienvenueAt + Math.max(SECURITE_BIENVENUE_MS, bienvenueS * 1000L + 2_000L);
    }

    static boolean bienvenueVisible(long now) {
        return bienvenueAt > 0 && now < bienvenueFonduAt() + FONDU_BIENVENUE_MS;
    }

    /** Résultat : jusqu'à la déconnexion, sécurité de {@code s} + 10 s. */
    static boolean resultatVisible(long now) {
        return resultatAt > 0 && now - resultatAt < resultatS * 1000L + SECURITE_RESULTAT_MS;
    }

    private static void masquerHud() {
        List<Identifier> ids = new ArrayList<>(List.of(VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
                VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR,
                VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
                VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS));
        for (String r : REBORN) ids.add(Identifier.fromNamespaceAndPath("reborn-hud", r));
        for (Identifier id : ids) {
            try {
                HudElementRegistry.replaceElement(id, orig -> (g, dt) -> { if (!masqueHud()) orig.extractRenderState(g, dt); });
            } catch (RuntimeException ignored) {
                // élément absent : on laisse
            }
        }
    }

    /* ================================================================ plaques (lues par nameplate/Nameplates) */

    /** Plaques de la douane en vigueur (jusqu'à {@code fin} ou au changement de monde). */
    public static boolean plaquesActives() { return plaques; }

    /** Examinateur en mode dans le monde de la douane (plaque « Examinateur »). */
    public static boolean examinateur(UUID id) {
        synchronized (EXAMINATEURS) { return plaques && EXAMINATEURS.contains(id); }
    }

    /**
     * Nom de la plaque d'un occupant du monde de la douane : « Examinateur », sinon le pseudo du profil (à la place
     * d'« Inconnu ») ; null hors douane.
     */
    public static String nomPlaque(Entity e) {
        if (!plaques || e == null) return null;
        if (examinateur(e.getUUID())) return "Examinateur";
        String d = DEBUG_NOMS.get(e.getUUID());
        if (d != null) return d;
        if (e instanceof Player p) return p.getGameProfile().name();
        return null;
    }

    /* ================================================================ lecture (onglet Douane du Poste de garde) */

    /** Dernier instantané {@code etat} (null : pas examinateur, ou douane coupée). */
    public static JsonObject etat() { return etat; }

    /**
     * L'onglet Douane se montre : droits d'examinateur (le serveur n'envoie {@code etat} qu'à eux) et douane active,
     * d'après un instantané récent.
     */
    public static boolean ongletVisible() {
        JsonObject e = etat;
        return e != null && etatRecent() && bool(e, "actif");
    }

    /** Dernier {@code etat} reçu il y a moins de 15 s (le serveur le renvoie toutes les 5 s aux examinateurs). */
    private static boolean etatRecent() {
        return System.currentTimeMillis() - etatAt < ETAT_VALIDE_MS;
    }

    /** Compteur de l'onglet : candidats en attente au hall. */
    public static int enAttente() {
        JsonObject e = etat;
        return e == null ? 0 : num(e, "enAttente");
    }

    /** Mode examinateur du joueur local (message {@code mode}, ou champ {@code mode} d'un état récent). */
    public static boolean modeExaminateur() {
        JsonObject e = etat;
        return modeExaminateur || (e != null && etatRecent() && bool(e, "mode"));
    }

    public static JsonObject dossier(String u) { synchronized (DOSSIERS) { return DOSSIERS.get(u); } }

    public static String erreurDossier(String u) { synchronized (DOSSIERS) { return ERREURS_DOSSIER.get(u); } }

    public static JsonObject tirage() { return tirage; }

    public static JsonObject envoi() { return envoi; }

    public static List<String[]> posees(String u) {
        synchronized (POSEES) { return u == null ? List.of() : List.copyOf(POSEES.getOrDefault(u, List.of())); }
    }

    public static List<String[]> historique() { synchronized (HISTORIQUE) { return List.copyOf(HISTORIQUE); } }

    public static Brouillon brouillon(String u) {
        synchronized (BROUILLONS) { return BROUILLONS.computeIfAbsent(u == null ? "" : u, k -> new Brouillon()); }
    }

    /** Candidat assis à mon bureau (champ {@code monBureau} de l'état), ou null. */
    public static String candidatDeMonBureau() {
        JsonObject e = etat;
        String mien = e == null ? null : str(e, "monBureau");
        if (mien == null || mien.isEmpty()) return null;
        for (JsonElement b : arr(e, "bureaux")) {
            JsonObject j = b.getAsJsonObject();
            if (mien.equals(str(j, "id"))) {
                String c = str(j, "candidat");
                return c.isEmpty() ? null : c;
            }
        }
        return null;
    }

    /** Dossier arrivé après un appel : à ouvrir en volet élargi (consommé à la lecture). */
    public static String dossierAOuvrir() {
        String u = dossierAOuvrir;
        dossierAOuvrir = null;
        return u;
    }

    /* ================================================================ demandes (C → S) */

    /** Envoie une demande {@code {"a": …}} (32 Kio au plus ; le serveur ignore au-delà). */
    public static void envoyer(JsonObject o) {
        if ("appeler".equals(str(o, "a"))) appelDemandeAt = System.currentTimeMillis();
        if (debugEnvoi != null) { debugEnvoi.accept(o); return; }
        String s = o.toString();
        if (s.getBytes(StandardCharsets.UTF_8).length > DouanePayload.TAILLE_MAX) return;
        if (ClientPlayNetworking.canSend(DouanePayload.ID)) ClientPlayNetworking.send(new DouanePayload(s));
    }

    public static void envoyer(String action) {
        JsonObject o = new JsonObject();
        o.addProperty("a", action);
        envoyer(o);
    }

    /** Instantané {@code etat} tout de suite (ouverture du Poste de garde, de l'onglet). */
    public static void demanderEtat() {
        if (debugEnvoi != null || ClientPlayNetworking.canSend(DouanePayload.ID)) envoyer("etat");
    }

    /* ================================================================ banc */

    /** Banc d'essai : message comme s'il venait du serveur. */
    public static void debugReceive(String raw) { recevoir(raw); }

    /** Banc d'essai : tout remettre à zéro. */
    static void debugEffacer() {
        toutEffacer();
        DEBUG_NOMS.clear();
    }

    /* ================================================================ JSON */

    static String str(JsonObject o, String k) {
        try { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }
        catch (RuntimeException e) { return ""; }
    }

    static int num(JsonObject o, String k) {
        try { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0; } catch (RuntimeException e) { return 0; }
    }

    static boolean bool(JsonObject o, String k) {
        try { return o != null && o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }

    static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }
}
