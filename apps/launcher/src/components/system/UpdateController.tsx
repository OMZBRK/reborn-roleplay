import { useUpdater } from "../../hooks/use-updater";
import { UpdateModal } from "./UpdateModal";

// Orchestre l'updater Tauri. Remplace l'ancien UpdateChecker (toast
// bottom-right) par la UpdateModal du chantier B. Pas de UI directement
// rendue ici — useUpdater() encapsule tout l'effet de polling +
// downloadAndInstall + relaunch, UpdateModal en deduit l'affichage.
export function UpdateController() {
  const { state, install, postpone, ignoreVersion } = useUpdater();
  return (
    <UpdateModal
      state={state}
      onInstall={() => {
        void install();
      }}
      onPostpone={postpone}
      onIgnoreVersion={ignoreVersion}
    />
  );
}
