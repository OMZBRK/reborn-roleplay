import { motion } from "framer-motion";
import { AlertTriangle, Info } from "lucide-react";
import {
  StepIlluHRP,
  StepIlluRP,
  StepIlluOral,
} from "./shared/StepIllu";

type Props = {
  onStart: () => void;
};

const CARDS = [
  {
    n: 1,
    title: "Hors-Roleplay",
    illu: <StepIlluHRP />,
    desc: (
      <>
        Évaluation de vos <b>disponibilités</b>, de votre <b>âge</b> et de vos{" "}
        <b>motivations</b> à rejoindre le serveur
      </>
    ),
  },
  {
    n: 2,
    title: "Roleplay",
    illu: <StepIlluRP />,
    desc: (
      <>
        Compréhension du <b>roleplay</b>, qualité d'<b>écriture</b> et{" "}
        <b>cohérence</b> du personnage
      </>
    ),
  },
  {
    n: 3,
    title: "Oral",
    illu: <StepIlluOral />,
    desc: (
      <>
        Vérification <b>aisance à l'oral</b>, cohérence RP et compréhension des{" "}
        <b>règles</b> de Reborn
      </>
    ),
  },
];

export function IntroStepsPage({ onStart }: Props) {
  return (
    <div className="wl-intro-page reborn-radial-bg">
      <div className="wl-intro-inner">
        <motion.h1
          className="wl-intro-title"
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
        >
          En Trois <em>Étapes</em>
        </motion.h1>
        <motion.p
          className="wl-intro-lead"
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.1 }}
        >
          L'accès à la Whitelist n'est ni automatique, ni simple. Elle a été
          pensée comme un véritable filtre de qualité, afin de garantir une
          expérience roleplay sérieuse, cohérente et immersive pour l'ensemble
          des joueurs.
        </motion.p>
        <motion.p
          className="wl-intro-sub"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.5, delay: 0.18 }}
        >
          La lecture du lore et du règlement est <b>obligatoire</b>.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.25 }}
        >
          <span className="wl-callout wl-callout-warning wl-callout-pill">
            <AlertTriangle size={14} />
            La whitelist n'est pas autorisée aux moins de 16 ans
          </span>
        </motion.div>

        <div className="wl-intro-grid">
          {CARDS.map((c, i) => (
            <motion.div
              key={c.n}
              className="wl-step-card reborn-hover-lift"
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{
                duration: 0.5,
                delay: 0.3 + i * 0.08,
                ease: [0.16, 1, 0.3, 1],
              }}
            >
              <div className="wl-step-card-badge">{c.n}</div>
              <div className="wl-step-card-illu">{c.illu}</div>
              <div className="wl-step-card-title">{c.title}</div>
              <div className="wl-step-card-desc">{c.desc}</div>
            </motion.div>
          ))}
        </div>

        <motion.div
          className="wl-callout wl-callout-info"
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.6 }}
        >
          <Info size={14} />
          <span>
            On te recommande fortement d'aller jeter un coup d'œil au salon{" "}
            <b>Aide-Candidature</b> sur le Discord Reborn !
          </span>
        </motion.div>

        <motion.button
          type="button"
          className="wl-btn-primary glow wl-btn-cta"
          onClick={onStart}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.7 }}
        >
          Let's go
        </motion.button>
      </div>
    </div>
  );
}
