'use client';

/**
 * Skin Minecraft via mc-heads.net (API publique gratuite, cache CDN).
 * Renvoie un PNG carre 8x8 -> ${size}x${size} de la tete du joueur.
 *
 * Fallback : si l'UUID n'est pas reconnu (compte offline-mode dev, par
 * exemple), mc-heads renvoie l'avatar de Steve, donc pas besoin de gerer
 * un onError nous-meme.
 *
 * On passe par <img> plain (et pas next/image) pour eviter d'avoir a
 * declarer mc-heads.net dans next.config.images.remotePatterns. La perf
 * difference est negligeable a cette taille.
 */
export function MCAvatar({
  uuid,
  username,
  size = 40,
  className = '',
  rounded = 'full',
}: {
  uuid: string;
  /** Affiche en alt + initiale fallback si image fail au chargement. */
  username?: string;
  size?: number;
  className?: string;
  rounded?: 'full' | 'lg' | 'md';
}) {
  const roundedCls =
    rounded === 'full'
      ? 'rounded-full'
      : rounded === 'lg'
        ? 'rounded-[14px]'
        : 'rounded-[10px]';
  // mc-heads accepte un UUID dashed ou undashed, on transmet tel quel.
  const url = `https://mc-heads.net/avatar/${uuid}/${size}.png`;
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={url}
      alt={username ? `Avatar de ${username}` : 'Avatar joueur'}
      width={size}
      height={size}
      loading="lazy"
      className={`${roundedCls} bg-[var(--color-surface-elevated)] object-cover ${className}`}
    />
  );
}
