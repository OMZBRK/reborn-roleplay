package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

/**
 * Banc d'essai de la monnaie ({@code REBORN_MONNAIE_DEBUG}, inactif en production) : le Poste de garde ouvert sur la
 * fiche d'exemple, section « Économie » dépliée (maquette 06), avec les données d'exemple de {@link StaffDebug}.
 */
public final class GardeEconomieDebug {

    private GardeEconomieDebug() {}

    public static void ouvrir(Minecraft mc, boolean formulaire) {
        String me = mc.player.getUUID().toString();
        StaffClient.debugReceive(StaffDebug.snap(me, false, "players"));
        JsonObject p = JsonParser.parseString(StaffDebug.profile(me, false)).getAsJsonObject();
        p.remove("ryo");
        JsonObject e = new JsonObject();
        e.addProperty("bourse", 1240);
        e.addProperty("compte", 8600);
        e.addProperty("palier", 2);
        e.addProperty("palierNom", "Compte");
        e.addProperty("plafond", 20000);
        e.addProperty("cases", 6);
        e.addProperty("occupees", 4);
        e.addProperty("coffre", "Ninjatō, Tenue, Ration ×4, Shuriken ×20");
        e.addProperty("solde", 200);
        e.addProperty("grade", "Genin");
        e.addProperty("soldeRecue", true);
        e.addProperty("dernier", -500);
        e.addProperty("dernierTexte", "virement à Aki U. · hier 21:14");
        p.add("eco", e);
        StaffClient.debugReceive(p.toString());
        GardeScreen s = new GardeScreen("alerts");
        mc.setScreenAndShow(s);
        s.debugTab("players");
        s.debugProfile(me);
        s.fiche.ouvrirSection("economie");
        if (formulaire) {
            s.formulaire.ouvrir("noter", "Donner des ryo à Kazuki Uchiha", "Sur la bourse (joueur en ligne) ou le compte. Motif obligatoire.",
                "Donner", false, null, java.util.List.of(
                    GardeFormulaire.Champ.texte("m", "Montant", "500", true),
                    GardeFormulaire.Champ.choix("o", "Sur", new String[][]{{"compte", "compte"}, {"bourse", "bourse"}}, 0),
                    GardeFormulaire.Champ.texte("motif", "Motif (obligatoire)", "récompense mission C (Tora)", true)),
                java.util.List.of("Journalisé (catégorie Économie), visible des autres staffs."), v -> { });
        }
    }
}
