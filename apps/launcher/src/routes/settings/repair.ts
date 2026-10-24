import { applyUpdate, requestFullVerify, type UpdateStatus } from "../../lib/launcher";

// « Réparer les fichiers du jeu » (Paramètres › Jeu › Intégrité).
//
// Deux volets :
//  1. Minecraft de base (Java, bibliothèques, ressources, Fabric) :
//     `launcher_repair` efface les témoins de vérification
//     (<game_dir>/.reborn-verify/, cf mojang::force_full_verify) ; le
//     « Jouer » suivant refait alors une vérification complète par hash et
//     retélécharge ce qui est abîmé.
//  2. Modpack Reborn : `launcher_apply_update` recalcule le plan (SHA-256 de
//     chaque mod, toujours complet) et retélécharge tout de suite les
//     fichiers manquants ou corrompus, purge les orphelins et les résidus.

export type RepairResult = {
  /** Résultat de la resynchronisation du modpack (undefined hors Tauri). */
  status: UpdateStatus | undefined;
  /** True : Minecraft sera entièrement revérifié au prochain « Jouer ». */
  fullVerify: boolean;
};

export async function repairGameFiles(): Promise<RepairResult> {
  await requestFullVerify();
  const status = await applyUpdate();
  return { status, fullVerify: true };
}
