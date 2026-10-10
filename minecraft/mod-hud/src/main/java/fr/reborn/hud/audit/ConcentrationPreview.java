package fr.reborn.hud.audit;

import com.google.gson.JsonObject;
import fr.reborn.hud.parchemin.ParcheminClient;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.runtime.VitalsFeed;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Aperçu, <b>inactif en production</b> ({@code REBORN_CONC_DEBUG=1}) : rejoue des messages {@code {"t":"conc"}} tels
 * que ShinobiAbilities les envoie, ajoute l'aura de particules (côté serveur : visible de tous) et capture quelques
 * états dans run/screenshots.
 */
public final class ConcentrationPreview {

    private record State(String name, String rank, double zone, double center, double pos, double held, double secs,
                         double stab, String st, boolean third, boolean helped) {}

    private static final State[] STATES = {
        new State("conc_1_depart", "D", 0.40, 0, 0.0, 0.2, 8, 1.0, "run", false, false),
        new State("conc_2_D_dans_zone", "D", 0.40, 0, 0.12, 3.4, 8, 0.95, "run", false, false),
        new State("conc_3_B_hors_zone", "B", 0.22, 0, 0.55, 6.1, 14, 0.38, "run", true, false),
        new State("conc_4_S_maitre", "S", 0.15, -0.32, -0.27, 11.2, 18, 0.72, "run", true, true),
        new State("conc_5_S_stabilite_basse", "S", 0.12, 0.25, -0.40, 9.0, 18, 0.22, "run", false, false),
        new State("conc_6_reussite", "C", 0.30, 0, 0.05, 12, 12, 0.9, "ok", true, false),
        new State("conc_7_echec", "B", 0.22, 0, 0.7, 5, 14, 0, "ko", false, false),
    };
    private static final int CHAKRA = 0x6FD3FF, LOST = 0xE0505A, HOLD = 60;

    private static int ticks = -1, index = 0, local = 0;

    private ConcentrationPreview() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_CONC_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(ConcentrationPreview::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        if (ticks == 20) {
            RebornSession.debugForceRp = true;
            VitalsFeed.update(82, 100, 64, 100);
            mc.player.connection.sendCommand("time set 6000");
            mc.player.connection.sendCommand("weather clear");
            mc.gui.hud.getChat().clearMessages(false);
        }
        if (ticks < 60) return;
        if (index >= STATES.length) {
            if (local++ == 10) mc.stop();
            return;
        }
        State s = STATES[index];
        if (local == 0) mc.options.setCameraType(s.third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        boolean end = !s.st.equals("run");
        // « run » est renvoyé à chaque tick (comme le serveur), avec un léger flottement du chakra ; un verdict arrive une fois
        if (!end || local <= 20) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "conc");
            o.addProperty("st", end && local == 20 ? s.st : "run");
            o.addProperty("rank", s.rank);
            o.addProperty("helped", s.helped);
            o.addProperty("zone", s.zone);
            o.addProperty("c", s.center);
            double wob = end ? 0 : 0.05 * Math.sin(local * 0.35);
            o.addProperty("pos", s.pos + wob);
            o.addProperty("held", s.held);
            o.addProperty("secs", s.secs);
            o.addProperty("stab", s.stab);
            o.addProperty("in", Math.abs(s.pos + wob - s.center) <= s.zone);
            if (s.name.contains("stabilite") && local % 8 == 0) o.addProperty("jolt", true);
            ParcheminClient.debugReceive(o.toString());
        }
        aura(mc, s, local);
        int shot = end ? 34 : HOLD - 5;
        if (local == shot) Screenshot.grab(mc.gameDirectory, s.name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        if (++local >= HOLD) {
            local = 0;
            index++;
        }
    }

    private static void aura(Minecraft mc, State s, int t) {
        var p = mc.player;
        boolean in = Math.abs(s.pos - s.center) <= s.zone;
        double time = t * 0.05;
        if (s.st.equals("ok")) {
            if (t >= 20 && t < 26) for (int k = 0; k < 6; k++) mc.level.addParticle(ParticleTypes.END_ROD,
                    p.getX() + (Math.random() - 0.5) * 0.8, p.getY() + 1 + (Math.random() - 0.5) * 1.2,
                    p.getZ() + (Math.random() - 0.5) * 0.8, (Math.random() - 0.5) * 0.1, Math.random() * 0.1, (Math.random() - 0.5) * 0.1);
            return;
        }
        if (s.st.equals("ko")) {
            if (t >= 20 && t < 24) for (int k = 0; k < 5; k++) mc.level.addParticle(ParticleTypes.CLOUD,
                    p.getX() + (Math.random() - 0.5) * 0.8, p.getY() + 1 + (Math.random() - 0.5), p.getZ() + (Math.random() - 0.5) * 0.8, 0, 0.03, 0);
            return;
        }
        if (t % 2 == 0) {
            for (int k = 0; k < 3; k++) {
                double ang = time * 4 + k * Math.PI * 2 / 3;
                mc.level.addParticle(new DustParticleOptions(in ? CHAKRA : LOST, in ? 0.8f : 1.1f),
                        p.getX() + Math.cos(ang) * 0.7, p.getY() + 1 + Math.sin(time * 3 + k) * 0.25,
                        p.getZ() + Math.sin(ang) * 0.7, 0, 0, 0);
            }
            if (!in) mc.level.addParticle(ParticleTypes.SMOKE, p.getX() + (Math.random() - 0.5) * 0.6,
                    p.getY() + 1 + (Math.random() - 0.5) * 0.6, p.getZ() + (Math.random() - 0.5) * 0.6, 0, 0.01, 0);
        }
    }
}
