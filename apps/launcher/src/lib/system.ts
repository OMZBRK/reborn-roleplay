import { invoke } from "./tauri";

export type SystemSpecs = {
  totalRamMb: number;
  freeRamMb: number;
  cpuBrand: string;
  cpuCores: number;
  osName: string;
  /** RAM JVM recommandée : 4 Go, ramenée à (RAM système − 2 Go) sur les
   *  petites machines, jamais sous 2 Go (hardware/mod.rs). */
  recommendedRamMb: number;
  /** RAM JVM maximale : RAM système − 2 Go (plancher 2 Go). Au lancement, un
   *  choix supérieur est ramené à cette valeur. */
  maxRamMb: number;
};

export async function getSystemSpecs(): Promise<SystemSpecs> {
  return invoke<SystemSpecs>("system_specs_get");
}
