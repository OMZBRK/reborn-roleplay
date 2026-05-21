/* global React, IconChevronLeft, IconMonitor, IconVolume, IconKey, IconDiscord, IconUser */

// =============================================================
// REBORN — Settings sub-screen (Vidéo · Audio · Contrôles · Discord · Compte)
// =============================================================

function Slider({ value, min = 0, max = 100, suffix = "" }) {
  const pct = ((value - min) / (max - min)) * 100;
  return (
    <div className="slider">
      <div className="slider-track">
        <div className="slider-fill" style={{width: `${pct}%`}}/>
        <div className="slider-thumb" style={{left: `${pct}%`}}/>
      </div>
      <div className="slider-value">{value}{suffix}</div>
    </div>
  );
}

function Seg({ options, value, onChange }) {
  return (
    <div className="seg">
      {options.map(o => (
        <button key={o.value} className={`seg-btn ${value === o.value ? "active" : ""}`}
                onClick={() => onChange(o.value)}>{o.label}</button>
      ))}
    </div>
  );
}

function Row({ label, hint, control }) {
  return (
    <div className="settings-row">
      <div>
        <div className="settings-label">{label}</div>
        {hint && <div className="settings-hint">{hint}</div>}
      </div>
      <div>{control}</div>
    </div>
  );
}

function VideoTab() {
  const [res, setRes] = React.useState("fhd");
  const [mode, setMode] = React.useState("fullscreen");
  const [fps, setFps] = React.useState(144);
  const [chunks, setChunks] = React.useState(16);
  const [vsync, setVsync] = React.useState(true);
  return (
    <div className="settings-group">
      <div className="settings-group-title">Affichage</div>
      <Row label="Résolution" hint="Affecte uniquement l'affichage en jeu"
        control={<Seg value={res} onChange={setRes} options={[
          { value: "hd",   label: "HD 720" },
          { value: "fhd",  label: "FHD 1080" },
          { value: "qhd",  label: "QHD 1440" },
          { value: "4k",   label: "4K" },
        ]}/>}/>
      <Row label="Mode fenêtre"
        control={<Seg value={mode} onChange={setMode} options={[
          { value: "fullscreen", label: "Plein écran" },
          { value: "borderless", label: "Sans bordure" },
          { value: "windowed",   label: "Fenêtré" },
        ]}/>}/>
      <Row label="FPS Max"
        control={<Slider value={fps} min={30} max={240} suffix=" fps"/>}/>
      <Row label="Distance de rendu" hint="Plus haut = serveur plus lourd à charger"
        control={<Slider value={chunks} min={4} max={32} suffix=" chunks"/>}/>
      <Row label="V-Sync"
        control={<div className={`toggle-big ${vsync ? "on" : ""}`} onClick={() => setVsync(!vsync)}/>}/>

      <div style={{
        marginTop: 24, padding: 16,
        background: "rgba(59,91,219,.08)",
        border: "1px solid rgba(59,91,219,.25)",
        borderRadius: 10, fontSize: 13, color: "var(--color-foreground-subtle)",
        display: "flex", alignItems: "center", gap: 12,
      }}>
        <span style={{
          padding: "2px 8px", borderRadius: 4,
          background: "rgba(59,91,219,.25)", color: "#a8b9ff",
          fontSize: 10, fontWeight: 700, letterSpacing: ".14em",
        }}>INFO</span>
        Le pack de textures Reborn est appliqué automatiquement. Les paramètres
        Resource Packs vanilla sont désactivés.
      </div>
    </div>
  );
}

function AudioTab() {
  const [master, setMaster] = React.useState(80);
  const [music, setMusic]   = React.useState(62);
  const [sfx, setSfx]       = React.useState(75);
  const [voice, setVoice]   = React.useState(90);
  const [muteUnfocus, setMU] = React.useState(true);
  return (
    <div className="settings-group">
      <div className="settings-group-title">Volumes</div>
      <Row label="Volume principal" control={<Slider value={master} suffix="%"/>}/>
      <Row label="Musique" hint="Contrôle aussi le lecteur du menu principal"
        control={<Slider value={music} suffix="%"/>}/>
      <Row label="Effets sonores" control={<Slider value={sfx} suffix="%"/>}/>
      <Row label="Voix" hint="Chat vocal RP de proximité" control={<Slider value={voice} suffix="%"/>}/>
      <Row label="Mute si fenêtre inactive"
        control={<div className={`toggle-big ${muteUnfocus ? "on" : ""}`} onClick={() => setMU(!muteUnfocus)}/>}/>
    </div>
  );
}

function ControlsTab() {
  const binds = [
    { label: "Avancer",            key: "Z" },
    { label: "Reculer",            key: "S" },
    { label: "Gauche",             key: "Q" },
    { label: "Droite",             key: "D" },
    { label: "Sauter",             key: "ESPACE" },
    { label: "Sprint",             key: "CTRL G." },
    { label: "Chat RP",            key: "T" },
    { label: "Chat OOC",           key: "U" },
    { label: "Emote / Action",     key: "K" },
    { label: "Fiche personnage",   key: "F1" },
    { label: "Carte régionale",    key: "M" },
    { label: "Push-to-Talk",       key: "V" },
  ];
  return (
    <div className="settings-group">
      <div className="settings-group-title">Touches essentielles · RP</div>
      {binds.map(b => (
        <Row key={b.label} label={b.label}
          control={<span className="keybind">{b.key}</span>}/>
      ))}
      <div style={{marginTop: 20, textAlign: "right"}}>
        <button className="btn btn-ghost">Voir tous les contrôles avancés</button>
      </div>
    </div>
  );
}

function DiscordTab() {
  const [rp, setRp] = React.useState(true);
  const [char, setChar] = React.useState(true);
  const [map, setMap] = React.useState(false);
  return (
    <div className="settings-group">
      <div className="settings-group-title">Discord Rich Presence</div>
      <Row label="Activer Rich Presence" hint="Affiche ton activité Reborn sur ton profil Discord"
        control={<div className={`toggle-big ${rp ? "on" : ""}`} onClick={() => setRp(!rp)}/>}/>
      <Row label="Afficher mon personnage RP"
        control={<div className={`toggle-big ${char ? "on" : ""}`} onClick={() => setChar(!char)}/>}/>
      <Row label="Afficher la map actuelle"
        control={<div className={`toggle-big ${map ? "on" : ""}`} onClick={() => setMap(!map)}/>}/>

      <div className="settings-group-title" style={{marginTop: 32}}>Aperçu</div>
      <div className="discord-preview">
        <div className="discord-avatar">RB</div>
        <div className="discord-meta">
          <strong>Joue à Reborn Roleplay</strong>
          {char && <div>Personnage · Hikami Yorishiro</div>}
          {map && <div>Région · Vallée des Lanternes</div>}
          <span className="ts">Joue depuis 2 h 12</span>
        </div>
      </div>
    </div>
  );
}

function AccountTab() {
  return (
    <div className="settings-group">
      <div className="settings-group-title">Identité</div>
      <Row label="Pseudo Minecraft"
        control={<div className="readonly-field">hikami_yori</div>}/>
      <Row label="UUID Minecraft"
        control={<div className="readonly-field">a4f7e3c0-9b2d-4f1c-bd44-7e2a91c0fe18</div>}/>
      <Row label="Identifiant Reborn"
        control={<div className="readonly-field">RBN-04217</div>}/>

      <div style={{marginTop: 32, padding: 18, background: "rgba(239,68,68,.05)",
                   border: "1px solid rgba(239,68,68,.2)", borderRadius: 10,
                   display: "flex", alignItems: "center", justifyContent: "space-between"}}>
        <div>
          <div style={{fontSize: 14, color: "#fff", fontWeight: 600}}>Quitter le serveur</div>
          <div style={{fontSize: 12, color: "var(--color-foreground-subtle)", marginTop: 4}}>
            Tu seras renvoyé au menu principal. Ta progression est sauvegardée.
          </div>
        </div>
        <button className="btn btn-danger-ghost">Se déconnecter</button>
      </div>
    </div>
  );
}

const SETTINGS_TABS = [
  { id: "video",    label: "Vidéo",    Cmp: VideoTab },
  { id: "audio",    label: "Audio",    Cmp: AudioTab },
  { id: "controls", label: "Contrôles", Cmp: ControlsTab },
  { id: "discord",  label: "Discord",  Cmp: DiscordTab },
  { id: "account",  label: "Compte",   Cmp: AccountTab },
];

function SettingsScreen({ onBack }) {
  const [tab, setTab] = React.useState("video");
  const Active = SETTINGS_TABS.find(t => t.id === tab).Cmp;
  return (
    <div className="settings">
      <div className="settings-header">
        <button className="settings-back" onClick={onBack}>
          <IconChevronLeft size={16}/>
          <span>Retour</span>
        </button>
        <div className="settings-title">Paramètres Reborn</div>
        <div style={{width: 90}}/>
      </div>

      <div className="settings-tabs">
        {SETTINGS_TABS.map(t => (
          <button key={t.id} className={`settings-tab ${tab === t.id ? "active" : ""}`}
                  onClick={() => setTab(t.id)}>{t.label}</button>
        ))}
      </div>

      <div className="settings-body">
        <Active/>
      </div>
    </div>
  );
}

// =============================================================
// Connecting screen
// =============================================================
function ConnectingScreen({ onCancel }) {
  const steps = [
    "Authentification…",
    "Téléchargement du pack de textures… 47 %",
    "Vérification de l'intégrité…",
    "Téléportation au spawn…",
  ];
  const [stepIdx, setStepIdx] = React.useState(1);
  React.useEffect(() => {
    const id = setInterval(() => setStepIdx(i => (i + 1) % steps.length), 2200);
    return () => clearInterval(id);
  }, []);
  return (
    <div className="connect">
      <div className="connect-blur"><Panorama/></div>
      <div className="connect-overlay"/>
      <div className="connect-card">
        <div className="spinner"/>
        <div className="connect-title">Connexion à Reborn Roleplay…</div>
        <div className="connect-step">Étape {stepIdx + 1} / 4 · <b>{steps[stepIdx]}</b></div>
        <div className="connect-bar"><div className="connect-bar-fill"/></div>
        <div className="connect-sub">Cela peut prendre quelques secondes</div>
        <button className="btn btn-ghost connect-cancel" onClick={onCancel}>Annuler</button>
      </div>
    </div>
  );
}

Object.assign(window, { SettingsScreen, ConnectingScreen, Slider, Seg, Row });
