import { useEffect, useLayoutEffect, useRef } from "react";
import { onTauriEvent, type TauriEventMap, type TauriEventName, type UnlistenFn } from "../lib/tauri";

// Abonnement React a un evenement Tauri, sans fuite :
//   - `listen` est asynchrone : si le composant est demonte avant que la
//     promesse ne resolve, le listener est retire des sa resolution (sinon
//     il restait actif pour toujours, et chaque remontage en empilait un) ;
//   - le callback est lu via une ref : toujours la derniere version, sans
//     re-souscrire a chaque rendu (pas besoin de useCallback cote appelant) ;
//   - hors Tauri (vite dev navigateur), ne fait rien.
//
// Les noms connus (TauriEventMap) typent le payload automatiquement :
//   useTauriEvent("game:log-batch", (p) => append(p.lines));
// Pour un evenement hors table : useTauriEvent<MonPayload>("x:y", cb).

export function useTauriEvent<K extends TauriEventName>(
  name: K,
  cb: (payload: TauriEventMap[K]) => void,
  enabled?: boolean,
): void;
export function useTauriEvent<T>(name: string, cb: (payload: T) => void, enabled?: boolean): void;
export function useTauriEvent<T>(name: string, cb: (payload: T) => void, enabled = true): void {
  const cbRef = useRef(cb);
  // Layout effect : la ref est a jour avant qu'un evenement puisse etre
  // traite apres le rendu qui a change le callback.
  useLayoutEffect(() => {
    cbRef.current = cb;
  });

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    let unlisten: UnlistenFn | null = null;
    onTauriEvent<T>(name, (payload) => {
      if (!cancelled) cbRef.current(payload);
    })
      .then((fn) => {
        if (cancelled) fn();
        else unlisten = fn;
      })
      .catch((e: unknown) => console.warn(`[tauri] écoute de « ${name} » impossible :`, e));
    return () => {
      cancelled = true;
      unlisten?.();
    };
  }, [name, enabled]);
}
