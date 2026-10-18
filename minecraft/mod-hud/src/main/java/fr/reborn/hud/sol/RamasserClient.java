package fr.reborn.hud.sol;

import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.inventory.InventoryData;
import fr.reborn.hud.menu.inventory.InventoryPayload;
import fr.reborn.hud.menu.inventory.SlotItem;
import fr.reborn.hud.notif.NotifClient;
import fr.reborn.hud.ui.DaKit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.Optional;

/**
 * <b>Ramassage volontaire</b> côté client (GAMEPLAN §3.3, maquette 04_ramassage) :
 * <ul>
 *   <li>chaque tick, l'objet au sol <b>visé</b> (rayon de la caméra, objet à 3 blocs au plus des yeux, non caché par
 *       un bloc) ;</li>
 *   <li>la touche <b>Ramasser</b> (F par défaut, rebindable) n'est interceptée <b>que</b> quand un objet est visé et
 *       que le serveur gère le ramassage : sinon F garde son rôle (techniques, tyrolienne, grappin, main gauche) ;</li>
 *   <li>une <b>invite</b> discrète ancrée au-dessus de l'objet : « [F] Ramasser Kunai ×3 », ou « … · trop lourd » /
 *       « … · plus de place » (le serveur revérifie tout et répond par une notification) ;</li>
 *   <li>la touche <b>Dégainer</b> (Z par défaut) envoie {@code arme:basculer}.</li>
 * </ul>
 * Message C2S : {@code pick:<uuid de l'objet>} sur {@code reborn:inventory}.
 */
public final class RamasserClient {

    private static final Identifier TOUCHE = DaKit.tex("inventaire", "touche"), VOILE = DaKit.tex("inventaire", "voile_centre");
    private static final double PORTEE = 3.0;
    private static final int CREME_DOUX = 0xFFC2B59A, REFUS = 0xFFE06A55;

    private static ItemEntity cible;
    private static long cibleDepuis;
    /** Banc d'essai : cible imposée (null = visée réelle). */
    public static ItemEntity debugCible;

    private RamasserClient() {}

    public static ItemEntity cible() {
        return cible;
    }

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(RamasserClient::avantTouches);
        ClientTickEvents.END_CLIENT_TICK.register(RamasserClient::apresTick);
    }

    private static boolean actif(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return false;
        // Survie, aventure et créatif (15/10 : le staff et le build sont souvent en créatif) ; pas le spectateur.
        if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return false;
        // État du serveur (reborn:etat) : RAMASSER refusé (à terre, étourdi, porté, garde…) — pas d'invite.
        if (fr.reborn.hud.etat.EtatClient.enJeu() && fr.reborn.hud.etat.EtatClient.refuse("ram")) return false;
        return NotifClient.ramassageServeur() && (NotifClient.debugActif || ClientPlayNetworking.canSend(InventoryPayload.ID));
    }

    /** START : avant que Minecraft ne traite F (main gauche) — on ne la prend que si un objet est visé. */
    private static void avantTouches(Minecraft mc) {
        KeyMapping k = HudKeybinds.PICKUP;
        if (k == null) return;
        boolean pris = false;
        while (k.consumeClick()) {
            // On me tend quelque chose (nourriture, 14/10) : F prend, si aucun objet au sol n'est visé.
            if ((cible == null || !cible.isAlive()) && mc.gui.screen() == null
                    && fr.reborn.hud.nourriture.NourritureClient.aOffre()) {
                fr.reborn.hud.nourriture.NourritureClient.prendre();
                pris = true;
                continue;
            }
            if (cible != null && cible.isAlive() && mc.gui.screen() == null && actif(mc)) {
                pris = true;
                if (ClientPlayNetworking.canSend(InventoryPayload.ID)) {
                    ClientPlayNetworking.send(new InventoryPayload("pick:" + cible.getUUID()));
                }
            }
        }
        if (pris && mc.options != null && k.same(mc.options.keySwapOffhand)) {
            while (mc.options.keySwapOffhand.consumeClick()) { /* F servait à ramasser : pas d'échange de main */ }
        }
        KeyMapping d = HudKeybinds.DEGAINER;
        if (d != null) {
            while (d.consumeClick()) {
                if (mc.gui.screen() == null && mc.player != null && ClientPlayNetworking.canSend(InventoryPayload.ID)) {
                    ClientPlayNetworking.send(new InventoryPayload("arme:basculer"));
                }
            }
        }
    }

    /** END : objet visé pour le tick suivant (et la lueur « visée »). */
    private static void apresTick(Minecraft mc) {
        ItemEntity avant = cible;
        cible = debugCible != null ? debugCible : (actif(mc) ? viser(mc) : null);
        if (cible != avant) cibleDepuis = System.currentTimeMillis();
    }

    private static ItemEntity viser(Minecraft mc) {
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 o = cam.position();
        Vector3fc f = cam.forwardVector();
        Vec3 dir = new Vec3(f.x(), f.y(), f.z());
        Vec3 oeil = mc.player.getEyePosition();
        double longueur = o.distanceTo(oeil) + PORTEE + 0.6;
        Vec3 fin = o.add(dir.scale(longueur));
        HitResult bloc = mc.level.clip(new ClipContext(o, fin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        double maxD = bloc.getType() == HitResult.Type.MISS ? longueur : bloc.getLocation().distanceTo(o) + 0.25;
        ItemEntity best = null;
        double bestD = Double.MAX_VALUE;
        AABB zone = mc.player.getBoundingBox().inflate(PORTEE + 1.5);
        for (ItemEntity e : mc.level.getEntitiesOfClass(ItemEntity.class, zone)) {
            if (!e.isAlive() || e.getItem().isEmpty()) continue;
            if (e.position().add(0, 0.25, 0).distanceTo(oeil) > PORTEE + 0.6) continue;
            AABB box = e.getBoundingBox().inflate(0.3);
            Optional<Vec3> hit = box.clip(o, fin);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceTo(o);
            if (d > maxD || d >= bestD) continue;
            best = e;
            bestD = d;
        }
        return best;
    }

    /* ================================================================ invite (HUD) */

    public static void render(GuiGraphicsExtractor g) {
        ItemEntity e = cible;
        Minecraft mc = Minecraft.getInstance();
        if (e == null || mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        if (!e.isAlive()) return;
        var win = mc.getWindow();
        int km = DaKit.echelleMenu();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double[] px = projeter(mc, e.getPosition(pt).add(0, 0.62, 0));
        if (px == null) return;
        float cx = (float) (px[0] / km), bas = (float) (px[1] / km);
        float a = Math.min(1f, (System.currentTimeMillis() - cibleDepuis) / 150f);

        ItemStack s = e.getItem();
        Rarete r = Rarete.of(s);
        String nom = DaKit.ellipse(mc.font, s.getHoverName().getString(), 140);
        int n = s.getCount();
        String refus = refus(s, n);

        Font f = mc.font;
        Component cRam = DaKit.inter("Ramasser"), cNom = DaKit.interGras(nom);
        Component cQ = n > 1 ? DaKit.inter("×" + n) : null;
        Component cRefus = refus != null ? DaKit.inter("· " + refus) : null;
        float w = 11 + 4 + DaKit.largeur8(f, cRam) + 3 + DaKit.largeur8(f, cNom)
            + (cQ != null ? 3 + DaKit.largeur8(f, cQ) : 0) + (cRefus != null ? 3 + DaKit.largeur8(f, cRefus) : 0);
        float x0 = cx - w / 2f, ym = bas - 6;

        DaKit.unites(g, km);
        DaKit.etire(g, VOILE, Math.round(x0) - 12, Math.round(ym) - 9, Math.round(w) + 24, 18, 64, 16, DaKit.blanc(a));
        // touche
        DaKit.blit(g, TOUCHE, Math.round(x0), Math.round(ym) - 6, 0, 0, 11, 11, 11, 11, DaKit.blanc(a));
        Component lettre = DaKit.arcade(touche(HudKeybinds.PICKUP));
        DaKit.arcFin(g, f, lettre, x0 + 5.5f, ym - 2.5f, DaKit.alpha(DaKit.CREME, a), 1, false);
        float x = x0 + 15;
        x += DaKit.inter8(g, f, cRam, x, ym, DaKit.alpha(CREME_DOUX, a), 0) + 3;
        int colNom = r.ordinal() >= Rarete.PEU_COMMUN.ordinal() ? r.argb() : DaKit.CREME;
        x += DaKit.inter8(g, f, cNom, x, ym, DaKit.alpha(colNom, a), 0);
        if (cQ != null) x += 3 + DaKit.inter8(g, f, cQ, x + 3, ym, DaKit.alpha(DaKit.OR, a), 0);
        if (cRefus != null) x += 3 + DaKit.inter8(g, f, cRefus, x + 3, ym, DaKit.alpha(REFUS, a), 0);
        // filet d'1 px écran sous l'invite (or ; vermillon si refus)
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        g.fill(Math.round((x0 + 3) * k), Math.round((ym + 6) * k), Math.round((x - 4) * k), Math.round((ym + 6) * k) + 1,
            DaKit.argb(refus != null ? 0xD2523E : 0xD9A95E, 0.6f * a));
        DaKit.finPx(g);
        DaKit.finUnites(g);
    }

    /** Raison prévisible d'un refus (« trop lourd », « plus de place ») d'après l'état poussé par le serveur. */
    private static String refus(ItemStack s, int n) {
        NotifClient.Poids p = NotifClient.poids();
        if (p == null) return null;
        Minecraft mc = Minecraft.getInstance();
        boolean creatif = mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.CREATIVE;
        double unit = creatif ? 0 : PoidsObjet.unite(s);   // pas de limite de poids en créatif (serveur idem)
        double plafond = p.max() * InventoryData.get().poidsMax();
        if (unit > 0 && p.cur() + unit > plafond + 1e-6) return "trop lourd";
        if (p.libres() <= 0 && !fusionPossible(s)) return "plus de place";
        return null;
    }

    private static boolean fusionPossible(ItemStack s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack h = mc.player.getInventory().getItem(i);
            if (!h.isEmpty() && ItemStack.isSameItemSameComponents(h, s) && h.getCount() < h.getMaxStackSize()) return true;
        }
        InventoryData.Snapshot snap = InventoryData.get();
        if (snap.bag() != null) {
            for (SlotItem it : snap.bag()) {
                if (it == null) continue;
                ItemStack b = it.toStack();
                if (b.getItem() == s.getItem() && b.getCount() < b.getMaxStackSize()) return true;
            }
        }
        return false;
    }

    /** Projection monde → pixels de la fenêtre (null si derrière la caméra). */
    public static double[] projeter(Minecraft mc, Vec3 p) {
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 d = p.subtract(cam.position());
        Vector3fc f = cam.forwardVector(), u = cam.upVector(), l = cam.leftVector();
        double z = d.x * f.x() + d.y * f.y() + d.z * f.z();
        if (z < 0.1) return null;
        double x = -(d.x * l.x() + d.y * l.y() + d.z * l.z());
        double y = d.x * u.x() + d.y * u.y() + d.z * u.z();
        var win = mc.getWindow();
        double W = win.getWidth(), H = win.getHeight();
        double tanY = Math.tan(Math.toRadians(cam.getFov() / 2.0));
        double sx = W / 2 + (x / z) / (tanY * (W / H)) * (W / 2);
        double sy = H / 2 - (y / z) / tanY * (H / 2);
        if (sx < -50 || sx > W + 50 || sy < -50 || sy > H + 50) return null;
        return new double[] {sx, sy};
    }

    /** Lettre courte de la touche (F, Z…). */
    static String touche(KeyMapping km) {
        if (km == null) return "F";
        String s = km.getTranslatedKeyMessage().getString();
        if (s.isEmpty() || "key.keyboard.unknown".equals(km.saveString())) return "—";
        return s.length() > 3 ? s.substring(0, 3) : s.toUpperCase(java.util.Locale.ROOT);
    }
}
