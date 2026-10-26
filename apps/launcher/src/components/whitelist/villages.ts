// Emblèmes et descriptions des villages et des clans (vrais logos du mod,
// blancs sur fond transparent, peints par masque CSS : composant Emblem).
//
// La LISTE vient de l'API (GET /whitelist/options : villages, clans) ; ce
// fichier n'ajoute que l'habillage, par valeur exacte du mod. Un village ou
// un clan inconnu ici s'affiche quand même, sans emblème.

import villageKonoha from "./assets/village_konoha.png";
import villageSuna from "./assets/village_suna.png";
import villageKiri from "./assets/village_kiri.png";
import villageKumo from "./assets/village_kumo.png";
import villageIwa from "./assets/village_iwa.png";
import villageAme from "./assets/village_ame.png";
import clanUchiha from "./assets/clan_uchiha.png";
import clanSenju from "./assets/clan_senju.png";
import clanHyuga from "./assets/clan_hyuga.png";
import clanNara from "./assets/clan_nara.png";
import clanSarutobi from "./assets/clan_sarutobi.png";
import clanAburame from "./assets/clan_aburame.png";
import clanAkimichi from "./assets/clan_akimichi.png";
import clanYamanaka from "./assets/clan_yamanaka.png";
import clanHatake from "./assets/clan_hatake.png";
import clanUzumaki from "./assets/clan_uzumaki.png";
import clanKurama from "./assets/clan_kurama.png";
import clanSabaku from "./assets/clan_sabaku.png";
import clanHoki from "./assets/clan_hoki.png";
import clanHozuki from "./assets/clan_hozuki.png";
import clanHoshigaki from "./assets/clan_hoshigaki.png";
import clanKaguya from "./assets/clan_kaguya.png";
import clanYuki from "./assets/clan_yuki.png";
import clanTerumi from "./assets/clan_terumi.png";
import clanYotsuki from "./assets/clan_yotsuki.png";
import clanKamizuru from "./assets/clan_kamizuru.png";
import clanOnoki from "./assets/clan_onoki.png";

export type HabillageVillage = { emblem: string; description: string };

/** Par valeur exacte du mod (CharacterCreateScreen.VILLAGES). Déserteur exclu, comme en jeu. */
export const HABILLAGE_VILLAGES: Record<string, HabillageVillage> = {
  Konohagakure: {
    emblem: villageKonoha,
    description: "La Feuille. Berceau des grands clans et de la Volonté du Feu.",
  },
  Sunagakure: {
    emblem: villageSuna,
    description: "Le Sable. Un désert rude, des marionnettistes endurants.",
  },
  Kirigakure: {
    emblem: villageKiri,
    description: "La Brume. Des îles, des bretteurs, un passé lourd.",
  },
  Kumogakure: {
    emblem: villageKumo,
    description: "Les Nuages. Des sommets, de la foudre et de la fierté.",
  },
  Iwagakure: {
    emblem: villageIwa,
    description: "La Roche. Une défense que rien n'ébranle.",
  },
  Amegakure: {
    emblem: villageAme,
    description: "La Pluie. Secrète, méfiante, marquée par la guerre.",
  },
};

export type HabillageClan = { emblem: string; trait: string };

/** Par nom canonique ASCII (le même que le mod). Pour information seulement (Q4). */
export const HABILLAGE_CLANS: Record<string, HabillageClan> = {
  Uchiha: { emblem: clanUchiha, trait: "Sharingan" },
  Senju: { emblem: clanSenju, trait: "Vitalité du Bois" },
  Hyuga: { emblem: clanHyuga, trait: "Byakugan" },
  Nara: { emblem: clanNara, trait: "Ombres" },
  Sarutobi: { emblem: clanSarutobi, trait: "Feu et devoir" },
  Aburame: { emblem: clanAburame, trait: "Insectes" },
  Akimichi: { emblem: clanAkimichi, trait: "Expansion" },
  Yamanaka: { emblem: clanYamanaka, trait: "Esprit" },
  Hatake: { emblem: clanHatake, trait: "Crocs blancs" },
  Uzumaki: { emblem: clanUzumaki, trait: "Sceaux" },
  Kurama: { emblem: clanKurama, trait: "Illusions" },
  Sabaku: { emblem: clanSabaku, trait: "Sable" },
  Hoki: { emblem: clanHoki, trait: "Marionnettes" },
  Hozuki: { emblem: clanHozuki, trait: "Hydrification" },
  Hoshigaki: { emblem: clanHoshigaki, trait: "Chakra immense" },
  Kaguya: { emblem: clanKaguya, trait: "Ossements" },
  Yuki: { emblem: clanYuki, trait: "Glace" },
  Terumi: { emblem: clanTerumi, trait: "Lave et Vapeur" },
  Yotsuki: { emblem: clanYotsuki, trait: "Foudre" },
  Kamizuru: { emblem: clanKamizuru, trait: "Abeilles" },
  Onoki: { emblem: clanOnoki, trait: "Poussière" },
};
