package fr.reborn.hud.combat;

import fr.reborn.hud.runtime.VitalsFeed;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;

/**
 * Banc d'essai visuel du HUD combat, <b>inactif en production</b> : ne fait rien sauf si {@code REBORN_COMBAT_DEBUG=1}
 * (client de dev). Fait apparaître une cible immobile, simule coups portés, dépense d'endurance et garde,
 * capture chaque état dans run/screenshots/, puis ferme le jeu.
 */
public final class CombatDebug {

    private static int ticks = -1;
    private static int target = -1;

    private CombatDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_COMBAT_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(CombatDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        CombatState st = CombatState.INSTANCE;
        long now = System.currentTimeMillis();
        switch (ticks) {
            case 30 -> {
                fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
                mc.player.connection.sendCommand("time set 1000");
                mc.player.connection.sendCommand("summon husk ^ ^ ^4 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
                VitalsFeed.update(100, 100, 80, 100);
            }
            case 45 -> {
                mc.gui.hud.getChat().clearMessages(false);
                double best = Double.MAX_VALUE;
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (e instanceof net.minecraft.world.entity.Mob && e.distanceToSqr(mc.player) < best) {
                        best = e.distanceToSqr(mc.player);
                        target = e.getId();
                    }
                }
            }
            case 52 -> hit(st, 4, 86, now);
            case 55 -> hit(st, 9, 72, now);
            case 58 -> hit(st, 21, 50, now);
            case 61 -> shot(mc);                                   // trois paliers + combo + dépense en laque
            case 140 -> hit(st, 6, 14, now);
            case 146 -> shot(mc);                                  // endurance presque vide (pulsation)
            case 220 -> { CombatHud.debugGuard = true; st.onStamina(62, 100, now); }
            case 226 -> shot(mc);                                  // garde
            case 240 -> mc.stop();
            default -> { }
        }
    }

    private static void hit(CombatState st, float dmg, float stamina, long now) {
        if (target >= 0) st.onHit(target, dmg, now);
        st.onStamina(stamina, 100, now);
    }

    private static void shot(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), msg -> { });
    }
}
