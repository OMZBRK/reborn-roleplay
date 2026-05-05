// Splash affiché pendant que App.tsx attend le retour de resumeSession()
// au boot. Visuellement aligné sur le préload HTML d'index.html (REBORN
// title + barre indéterminée), pour que la transition entre les deux
// soit invisible côté user. Le préload disparaît quand React monte ; ce
// composant prend le relais jusqu'à ce que le résumé soit terminé.
export function ResumeSplash() {
  return (
    <div
      className="reborn-resume-splash flex h-full w-full flex-col items-center justify-center gap-7"
      style={{
        background:
          "radial-gradient(ellipse 60% 50% at 50% 30%, rgba(59,91,219,0.18) 0%, rgba(59,91,219,0.05) 40%, transparent 75%), var(--color-background)",
      }}
    >
      <h1 className="reborn-resume-title">REBORN</h1>
      <p className="reborn-resume-tagline">Roleplay · Reprise de session</p>
      <div className="reborn-resume-bar" role="progressbar" aria-label="Reprise" />
    </div>
  );
}
