import type { ReactNode } from "react";
import { motion } from "framer-motion";
import { Stepper } from "./shared/Stepper";

type Props = {
  step: 1 | 2 | 3;
  badge: ReactNode;
  title: string;
  subtitle: string;
  headerRight?: ReactNode;
  children: ReactNode;
  footer: ReactNode;
  // motion key — distingue les 3 wizards lors de leur transition (slide-in droite).
  motionKey: string;
};

// Layout commun aux 3 étapes du wizard : stepper en haut, header (badge +
// titre + subtitle [+ optionnellement un slot droite]), corps stagger-animé,
// footer sticky en bas avec border-top. Reproduit fidèlement le design
// whitelist-pages.jsx (Step1HRP / Step2RP / Step3Validation).
export function WizardLayout({
  step,
  badge,
  title,
  subtitle,
  headerRight,
  children,
  footer,
  motionKey,
}: Props) {
  return (
    <motion.div
      key={motionKey}
      className="wl-wizard"
      initial={{ opacity: 0, x: 30 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
    >
      <Stepper current={step} />

      <div className="wl-wizard-header">
        <div className="wl-wizard-header-left">
          {badge}
          <div>
            <h2 className="wl-wizard-title">{title}</h2>
            <p className="wl-wizard-subtitle">{subtitle}</p>
          </div>
        </div>
        {headerRight}
      </div>

      <motion.div
        className="wl-wizard-body"
        initial="hidden"
        animate="show"
        variants={{
          hidden: {},
          show: { transition: { staggerChildren: 0.06 } },
        }}
      >
        {children}
      </motion.div>

      <div className="wl-wizard-footer">{footer}</div>
    </motion.div>
  );
}

// Wrapper de field utilisé pour le stagger dans le wizard body — chaque champ
// rentre avec un fade+translate Y, déclenché par le parent variants="show".
export function FieldWrap({ children }: { children: ReactNode }) {
  return (
    <motion.div
      variants={{
        hidden: { opacity: 0, y: 8 },
        show: {
          opacity: 1,
          y: 0,
          transition: { duration: 0.36, ease: [0.16, 1, 0.3, 1] },
        },
      }}
    >
      {children}
    </motion.div>
  );
}
