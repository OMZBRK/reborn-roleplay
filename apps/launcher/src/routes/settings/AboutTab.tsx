import { useNavigate } from "react-router";
import { Check, Info, Link2, RefreshCw, User2 } from "lucide-react";
import pkg from "../../../package.json";
import { Card, Row } from "./ui";

const LINK_BUTTON =
  "inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-background px-3 text-xs font-medium text-foreground transition hover:border-accent hover:bg-surface-elevated";

const CREDITS = [
  { name: "OMZ", role: "Founder · Backend" },
  { name: "Hikami", role: "Design · Frontend" },
  { name: "Kazama", role: "Game systems" },
  { name: "Yumi", role: "Lore · Writing" },
];

export function AboutTab() {
  const navigate = useNavigate();

  return (
    <>
      <Card
        title="Version"
        description="État de ton installation client."
        icon={<Info className="h-4 w-4" />}
      >
        <Row label="Version du launcher">
          <code className="rounded-md bg-background px-2.5 py-1 font-mono text-[11px] text-foreground-subtle">
            v{pkg.version}
          </code>
        </Row>
        <Row label="Canal de build">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-success-soft px-3 py-1 text-[11px] font-semibold tracking-wide text-success">
            <Check className="h-3 w-3" strokeWidth={3} />
            Stable
          </span>
        </Row>
        <Row
          label="Mise à jour automatique"
          hint="Vérification toutes les 30 minutes ; la nouvelle version s'installe au redémarrage du launcher."
        >
          <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-foreground-subtle">
            <RefreshCw className="h-3 w-3" />
            Active
          </span>
        </Row>
      </Card>
      <Card
        title="Support"
        description="Trouve de l'aide si quelque chose ne va pas."
        icon={<Link2 className="h-4 w-4" />}
      >
        <Row
          label="Ouvrir un ticket"
          hint="Le moyen le plus rapide d'avoir une réponse du staff. En cas de crash, joins le journal du jeu (Paramètres › Jeu › Journal du jeu)."
        >
          <button type="button" className={LINK_BUTTON} onClick={() => navigate("/tickets")}>
            Voir les tickets
          </button>
        </Row>
        <Row label="Documentation" hint="Règlement complet, lore, guides RP.">
          <button type="button" className={LINK_BUTTON} onClick={() => navigate("/rules")}>
            Consulter
          </button>
        </Row>
      </Card>
      <Card
        title="Crédits"
        description="Construit avec amour par l'équipe Reborn."
        icon={<User2 className="h-4 w-4" />}
      >
        <div className="grid grid-cols-2 gap-3 p-6 sm:grid-cols-4">
          {CREDITS.map((c) => (
            <div
              key={c.name}
              className="flex flex-col items-center gap-2 rounded-[12px] border border-border bg-background/60 p-3 text-center"
            >
              <div
                aria-hidden="true"
                className="flex h-10 w-10 items-center justify-center rounded-full text-sm font-semibold text-white"
                style={{
                  background:
                    "linear-gradient(135deg, var(--color-accent) 0%, var(--color-accent-pressed) 100%)",
                }}
              >
                {c.name[0]}
              </div>
              <div className="text-xs font-semibold text-foreground">{c.name}</div>
              <div className="text-2xs uppercase tracking-wider text-foreground-muted">
                {c.role}
              </div>
            </div>
          ))}
        </div>
      </Card>
    </>
  );
}
