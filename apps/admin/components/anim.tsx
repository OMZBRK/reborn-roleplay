'use client';

import { motion } from 'motion/react';
import type { ComponentProps, ReactNode } from 'react';

/**
 * Fade-up tres leger pour les containers de page : entree de bas
 * (8px) + opacite 0→1 sur 300ms. Le delay permet de cascader les
 * blocs en sequence.
 */
export function FadeUp({
  children,
  delay = 0,
  className,
  ...rest
}: {
  children: ReactNode;
  delay?: number;
  className?: string;
} & Omit<ComponentProps<typeof motion.div>, 'initial' | 'animate' | 'transition'>) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, delay, ease: 'easeOut' }}
      className={className}
      {...rest}
    >
      {children}
    </motion.div>
  );
}

/**
 * Stagger pour les listes : chaque enfant entre avec un petit retard
 * incremental sur l'index, donne une sensation de "carte qui se pose".
 * Le caller wrap chaque item dans <StaggerItem index={i}>.
 */
export function StaggerItem({
  index,
  children,
  className,
}: {
  index: number;
  children: ReactNode;
  className?: string;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, delay: Math.min(index * 0.03, 0.4), ease: 'easeOut' }}
      className={className}
    >
      {children}
    </motion.div>
  );
}
