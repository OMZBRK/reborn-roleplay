/* global React, Panorama, SakuraParticles, LogoSigil,
   IconPlay, IconPause, IconVolume, IconMenu, IconSettings, IconGlobe,
   IconDiscord, IconX */

// =============================================================
// REBORN — Main Menu screen + sub-components
// =============================================================

const TRACKS = [
  { t: "Hollow Moonlight",        s: "Reborn OST · K. Asano",     d: "3:42" },
  { t: "Lanterns at Dusk",        s: "Reborn OST · K. Asano",     d: "4:18" },
  { t: "Threadbare Wind",         s: "Reborn OST · Y. Kuroda",    d: "3:55" },
  { t: "The Elder's Vow",         s: "Reborn OST · K. Asano",     d: "5:02" },
  { t: "Brother's Shadow",        s: "Reborn OST · Y. Kuroda",    d: "4:36" },
  { t: "Iron and Resolve",        s: "Reborn OST · K. Asano",     d: "3:48" },
  { t: "Pulse of the Brave",      s: "Reborn OST · Y. Kuroda",    d: "4:09" },
  { t: "Crimson Cloak",           s: "Reborn OST · K. Asano",     d: "4:51" },
  { t: "Senya (Eve)",             s: "Reborn OST · Y. Kuroda",    d: "3:32" },
  { t: "The Hollow Path",         s: "Reborn OST · K. Asano",     d: "4:24" },
  { t: "March to the Frontier",   s: "Reborn OST · Y. Kuroda",    d: "3:58" },
  { t: "Reborn — Main Theme",     s: "Reborn OST · K. Asano",     d: "5:11" },
];

function CentralLogo() {
  return (
    <div className="logo-wrap">
      <div className="logo-mark">
        <span>RE</span>
        <span className="logo-glyph"><LogoSigil size={86}/></span>
        <span>ORN</span>
      </div>
      <div className="logo-sub">Roleplay · Shinobi Chronicle</div>
    </div>
  );
}

function PressSpacePrompt({ onActivate }) {
  return (
    <button className="prompt" onClick={onActivate}>
      <span className="key-cap">ESPACE</span>
      <span className="prompt-text">Appuyez pour entrer dans Reborn</span>
    </button>
  );
}

function OSTPlayer({ playing, setPlaying, current, onTogglePlaylist, playlistOpen }) {
  const [volOpen, setVolOpen] = React.useState(false);
  const [vol, setVol] = React.useState(62);
  const track = TRACKS[current];
  return (
    <div className="ost">
      <div className={`ost-cover ${playing ? "spin" : ""}`}/>
      <div className="ost-meta">
        <div className="ost-title">{track.t}</div>
        <div className="ost-artist">{track.s}</div>
      </div>
      <div className="ost-controls">
        <button onClick={() => setPlaying(!playing)} className="tip" data-tip={playing ? "Pause" : "Lecture"}>
          {playing ? <IconPause size={16}/> : <IconPlay size={16}/>}
        </button>
        <button
          onMouseEnter={() => setVolOpen(true)}
          onMouseLeave={() => setVolOpen(false)}
          style={{position: "relative"}}>
          <IconVolume size={16}/>
          {volOpen && (
            <div className="vol-popup">
              <div className="vol-track">
                <div className="vol-fill" style={{height: `${vol}%`}}/>
              </div>
              <div className="vol-num">{vol}</div>
            </div>
          )}
        </button>
        <button onClick={onTogglePlaylist} className="tip" data-tip="Playlist"
                style={playlistOpen ? {color: "#fff", background: "rgba(255,255,255,.08)"} : null}>
          <IconMenu size={16}/>
        </button>
      </div>
      <div className="ost-progress"><div className="ost-progress-fill"/></div>
    </div>
  );
}

function OSTPlaylist({ current, setCurrent, shuffle, setShuffle }) {
  return (
    <div className="playlist">
      <div className="playlist-head">
        <h4>Bande-son · Reborn</h4>
        <span>12 pistes</span>
      </div>
      <div className="playlist-body">
        {TRACKS.map((t, i) => (
          <div key={i} className={`track ${i === current ? "active" : ""}`} onClick={() => setCurrent(i)}>
            <div className="track-indicator">{i === current ? "▶" : ""}</div>
            <div className="track-cover"/>
            <div className="track-meta">
              <div className="track-title">{t.t}</div>
              <div className="track-source">{t.s}</div>
            </div>
            <div className="track-dur">{t.d}</div>
          </div>
        ))}
      </div>
      <div className="playlist-foot">
        <div className={`toggle-mini ${shuffle ? "on" : ""}`} onClick={() => setShuffle(!shuffle)}>
          <span className="dot"/>
          <span>Lecture aléatoire</span>
        </div>
        <span>12 pistes · 47 min</span>
      </div>
    </div>
  );
}

function ServerInfoMini() {
  return (
    <div className="srv">
      <div className="srv-line">
        <span className="srv-dot"/>
        <span style={{fontWeight: 600, color: "#fff"}}>Reborn · En ligne</span>
        <span className="srv-sep">·</span>
        <span>23 / 200 joueurs</span>
        <span className="srv-sep">·</span>
        <span>28 ms</span>
      </div>
      <div className="srv-line muted">Patch 1.0.5 · Fabric 1.21.3</div>
    </div>
  );
}

function BottomRightIcons({ onSettings }) {
  return (
    <div className="icons">
      <button className="icon-btn tip" data-tip="Paramètres" onClick={onSettings}><IconSettings size={20}/></button>
      <button className="icon-btn tip" data-tip="Site web"><IconGlobe size={20}/></button>
      <button className="icon-btn tip" data-tip="Discord"><IconDiscord size={20}/></button>
    </div>
  );
}

function TopRightQuit({ onClick }) {
  return (
    <button className="quit-btn tip tip-below" data-tip="Quitter Reborn" onClick={onClick}>
      <IconX size={18}/>
    </button>
  );
}

function QuitConfirmModal({ onCancel }) {
  return (
    <div className="modal-backdrop">
      <div className="modal-card">
        <h3>Quitter Reborn ?</h3>
        <p>Tu vas être déconnecté du serveur et fermer Minecraft.</p>
        <div className="modal-actions">
          <button className="btn btn-ghost" onClick={onCancel}>Annuler</button>
          <button className="btn btn-danger">Quitter</button>
        </div>
      </div>
    </div>
  );
}

function CreditsCorner() {
  return (
    <div className="credits">
      <div>Minecraft 1.21.3 · Fabric Loader 0.15.7</div>
      <div>Not affiliated with Mojang AB or Microsoft Corporation</div>
      <div>Reborn Roleplay © 2026 Reborn Studios</div>
    </div>
  );
}

function MainMenuView({ overlay, setOverlay, onSettings, onConnect }) {
  const [playing, setPlaying] = React.useState(true);
  const [current, setCurrent] = React.useState(0);
  const [shuffle, setShuffle] = React.useState(false);
  const playlistOpen = overlay === "playlist";
  const quitOpen = overlay === "quit";

  return (
    <>
      <Panorama/>
      <SakuraParticles count={34}/>

      <CentralLogo/>
      <PressSpacePrompt onActivate={onConnect}/>

      <TopRightQuit onClick={() => setOverlay("quit")}/>

      <div className="footer">
        <OSTPlayer
          playing={playing} setPlaying={setPlaying}
          current={current}
          onTogglePlaylist={() => setOverlay(playlistOpen ? null : "playlist")}
          playlistOpen={playlistOpen}/>
        <div style={{display: "grid", placeItems: "center"}}>
          <ServerInfoMini/>
        </div>
        <BottomRightIcons onSettings={onSettings}/>
      </div>

      <CreditsCorner/>

      {playlistOpen && (
        <OSTPlaylist
          current={current} setCurrent={(i) => { setCurrent(i); setPlaying(true); }}
          shuffle={shuffle} setShuffle={setShuffle}/>
      )}

      {quitOpen && <QuitConfirmModal onCancel={() => setOverlay(null)}/>}
    </>
  );
}

Object.assign(window, {
  MainMenuView, CentralLogo, PressSpacePrompt, OSTPlayer, OSTPlaylist,
  ServerInfoMini, BottomRightIcons, TopRightQuit, QuitConfirmModal, CreditsCorner,
  TRACKS,
});
