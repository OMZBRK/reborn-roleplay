import { Bell } from "lucide-react";
import { useSettingsStore, type NotifChannel } from "../../stores/settings-store";
import { Card, PrettySelect, Row, Toggle } from "./ui";

// Préférences de notification : persistées localement (settings-store) ;
// l'envoi côté serveur arrivera avec /v1/notifications.

export function NotificationsTab() {
  const notif = useSettingsStore((s) => s.notif);
  const setNotif = useSettingsStore((s) => s.setNotif);

  return (
    <Card
      title="Notifications"
      description="Choisis ce que tu veux recevoir et par quel canal. Enregistré sur ce PC pour l'instant : l'envoi par le serveur arrivera dans une prochaine version."
      icon={<Bell className="h-4 w-4" />}
    >
      <Row label="Whitelist" hint="Étapes de candidature et résultats.">
        <div className="flex items-center gap-2">
          <PrettySelect
            ariaLabel="Canal des notifications de whitelist"
            value={notif.whitelistChannel}
            onChange={(v) => setNotif("whitelistChannel", v as NotifChannel)}
            disabled={!notif.whitelistEnabled}
            options={[
              { value: "push", label: "Push" },
              { value: "email", label: "E-mail" },
              { value: "both", label: "Les deux" },
            ]}
          />
          <Toggle
            label="Notifications de whitelist"
            checked={notif.whitelistEnabled}
            onChange={(v) => setNotif("whitelistEnabled", v)}
          />
        </div>
      </Row>
      <Row label="Tickets staff" hint="Réponses à tes signalements et demandes.">
        <Toggle
          label="Notifications des tickets staff"
          checked={notif.ticketsEnabled}
          onChange={(v) => setNotif("ticketsEnabled", v)}
        />
      </Row>
      <Row label="Patch notes" hint="Reçois un résumé à chaque nouvelle version client.">
        <Toggle
          label="Notifications des patch notes"
          checked={notif.patchEnabled}
          onChange={(v) => setNotif("patchEnabled", v)}
        />
      </Row>
      <Row label="Événements RP" hint="Tournois, arcs narratifs et annonces en jeu.">
        <Toggle
          label="Notifications des événements RP"
          checked={notif.eventsEnabled}
          onChange={(v) => setNotif("eventsEnabled", v)}
        />
      </Row>
    </Card>
  );
}
