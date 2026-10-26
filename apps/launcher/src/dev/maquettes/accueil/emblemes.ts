// MAQUETTES : les VRAIS emblèmes du mod (DA §1, « vrais logos »), lus sur
// place dans minecraft/mod-hud, sans copie dans le launcher. 48 × 48, blanc
// sur fond transparent : l'écran les teinte par masque CSS (or, ivoire…).
//
// Vite les sert en dev (le dépôt est la racine de l'espace de travail pnpm,
// autorisée par server.fs). Ce fichier n'est jamais importé par le build de
// production : le vrai écran passera par des copies dans src/assets, ou par
// une liste servie par l'API (§2.3, GET /v1/whitelist/options).

import clanAutre from "../../../../../../minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/clans/clan_autre.png";
import clanHozuki from "../../../../../../minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/clans/clan_hozuki.png";
import clanNara from "../../../../../../minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/clans/clan_nara.png";
import villageAme from "../../../../../../minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/esc/village_ame.png";
import villageIwa from "../../../../../../minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/esc/village_iwa.png";
import villageKiri from "../../../../../../minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/esc/village_kiri.png";
import villageKonoha from "../../../../../../minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/esc/village_konoha.png";
import villageKumo from "../../../../../../minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/esc/village_kumo.png";
import villageSuna from "../../../../../../minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/esc/village_suna.png";
import type { VillageId } from "./donnees";

const CLANS: Record<string, string> = {
  autre: clanAutre,
  hozuki: clanHozuki,
  nara: clanNara,
};

const VILLAGES: Record<VillageId, string> = {
  ame: villageAme,
  iwa: villageIwa,
  kiri: villageKiri,
  konoha: villageKonoha,
  kumo: villageKumo,
  suna: villageSuna,
};

/** Blason du clan ; « Sans clan » (null) = clan « Autre » du mod (§4.2). */
export const emblemeClan = (clan: string | null): string => CLANS[clan ?? "autre"] ?? clanAutre;

export const emblemeVillage = (village: VillageId): string => VILLAGES[village];
