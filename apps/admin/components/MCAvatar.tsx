'use client';

/**
 * Visage du joueur : tête de son skin Minecraft via mc-heads.net (API
 * publique, cache CDN). Rond, pixel net (PANEL_V2 §5.3). Un UUID inconnu
 * (compte de développement hors ligne) renvoie la tête de Steve.
 *
 * <img> simple plutôt que next/image : pas de domaine distant à déclarer, et
 * la différence est nulle à cette taille.
 */
export function MCAvatar({
  uuid,
  username,
  size = 24,
  className = '',
  decoratif = true,
}: {
  uuid: string;
  username?: string;
  size?: number;
  className?: string;
  /** false : l'image porte le nom (visage seul, sans pseudo à côté). */
  decoratif?: boolean;
  /** Conservé pour les anciens appels (forme toujours ronde). */
  rounded?: 'full' | 'lg' | 'md';
}) {
  const url = `https://mc-heads.net/avatar/${uuid}/${Math.max(16, size * 2)}.png`;
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={url}
      alt={decoratif ? '' : username ? `Visage de ${username}` : 'Visage du joueur'}
      width={size}
      height={size}
      loading="lazy"
      className={`face ${className}`}
      style={{ width: size, height: size }}
    />
  );
}
