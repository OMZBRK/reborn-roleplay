import { motion } from "framer-motion";

// Partenaires : 6 tiles mock, hover lift. Pas d'API derriere — quand un
// endpoint /v1/partners existera, on remplacera PARTNERS par un fetch
// + cache du store.
//
// TODO(partners): brancher sur endpoint API + remplacer mock par fetch.

type Partner = {
  id: string;
  label: string;
};

const PARTNERS: Partner[] = [
  { id: "p1", label: "NRT" },
  { id: "p2", label: "FNG" },
  { id: "p3", label: "KZK" },
  { id: "p4", label: "OBI" },
  { id: "p5", label: "SHN" },
  { id: "p6", label: "URZ" },
];

export function PartnersRow() {
  return (
    <section className="reborn-home-section">
      <div className="reborn-home-section-head">
        <h2 className="reborn-home-section-title">Partenaires</h2>
      </div>
      <div className="reborn-home-partners">
        {PARTNERS.map((p, i) => (
          <motion.button
            type="button"
            key={p.id}
            whileHover={{ y: -3 }}
            transition={{ type: "spring", stiffness: 360, damping: 22 }}
            className="reborn-home-partner"
          >
            <span
              className="reborn-home-partner-glyph"
              style={{ filter: `hue-rotate(${i * 47}deg)` }}
            >
              {p.label}
            </span>
          </motion.button>
        ))}
      </div>
    </section>
  );
}
