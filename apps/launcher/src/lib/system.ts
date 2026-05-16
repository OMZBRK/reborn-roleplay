import { invoke } from "./tauri";

export type SystemSpecs = {
  totalRamMb: number;
  freeRamMb: number;
  cpuBrand: string;
  cpuCores: number;
  osName: string;
  /** RAM JVM recommandee : moitie de la RAM totale, dans [2048, 12288] Mo. */
  recommendedRamMb: number;
};

export async function getSystemSpecs(): Promise<SystemSpecs> {
  return invoke<SystemSpecs>("system_specs_get");
}
