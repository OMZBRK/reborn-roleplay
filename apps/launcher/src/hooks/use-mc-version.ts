import { useEffect, useState } from "react";
import { getGameInfo } from "../lib/launcher";

// Version Minecraft ciblee par le launcher (REBORN_MC_VERSION cote Rust),
// lue une seule fois par session via launcher_game_info. Remplace les
// libelles codes en dur (le footer affichait encore « 1.21.1 »).
// Fallback = defaut de game.rs tant que l'IPC n'a pas repondu.
const FALLBACK = "26.2";
let cached: Promise<string> | null = null;

export function useMcVersion(): string {
  const [version, setVersion] = useState(FALLBACK);
  useEffect(() => {
    cached ??= getGameInfo()
      .then((g) => g?.mcVersion || FALLBACK)
      .catch(() => {
        cached = null;
        return FALLBACK;
      });
    let alive = true;
    void cached.then((v) => alive && setVersion(v));
    return () => {
      alive = false;
    };
  }, []);
  return version;
}
