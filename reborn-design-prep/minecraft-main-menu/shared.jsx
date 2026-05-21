/* global React */
// =============================================================
// REBORN — Shared icons & primitives
// All SVG icons inline (no lucide import — keeps Babel-only setup clean).
// =============================================================

const Icon = ({ d, size = 18, stroke = 2, fill = "none", children, style }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke="currentColor"
       strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" style={style}>
    {d ? <path d={d}/> : children}
  </svg>
);

const IconPlay = (p) => <Icon {...p}><polygon points="6 4 20 12 6 20 6 4" fill="currentColor" stroke="none"/></Icon>;
const IconPause = (p) => <Icon {...p}><rect x="6" y="4" width="4" height="16" fill="currentColor" stroke="none"/><rect x="14" y="4" width="4" height="16" fill="currentColor" stroke="none"/></Icon>;
const IconVolume = (p) => <Icon {...p}><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" fill="currentColor" stroke="none"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14"/></Icon>;
const IconMenu = (p) => <Icon {...p}><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></Icon>;
const IconSettings = (p) => <Icon {...p}><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></Icon>;
const IconGlobe = (p) => <Icon {...p}><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></Icon>;
const IconDiscord = (p) => (
  <svg width={p.size||18} height={p.size||18} viewBox="0 0 24 24" fill="currentColor">
    <path d="M20.317 4.37a19.79 19.79 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028c.462-.63.874-1.295 1.226-1.994a.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.84 19.84 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.946 2.418-2.157 2.418z"/>
  </svg>
);
const IconTwitch = (p) => (
  <svg width={p.size||18} height={p.size||18} viewBox="0 0 24 24" fill="currentColor">
    <path d="M2.149 0l-1.612 4.119v16.836h5.731v3.045h3.224l3.045-3.045h4.657l6.269-6.269V0H2.149zm19.164 13.612l-3.582 3.582h-5.731l-3.045 3.045v-3.045H4.119V1.792h17.194v11.82zM17.731 5.731h-1.792v5.731h1.792V5.731zm-4.836 0h-1.792v5.731h1.792V5.731z"/>
  </svg>
);
const IconX = (p) => <Icon {...p}><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></Icon>;
const IconXLogo = (p) => (
  <svg width={p.size||18} height={p.size||18} viewBox="0 0 24 24" fill="currentColor">
    <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/>
  </svg>
);
const IconChevronLeft = (p) => <Icon {...p}><polyline points="15 18 9 12 15 6"/></Icon>;
const IconChevronRight = (p) => <Icon {...p}><polyline points="9 18 15 12 9 6"/></Icon>;
const IconGift = (p) => <Icon {...p}><polyline points="20 12 20 22 4 22 4 12"/><rect x="2" y="7" width="20" height="5"/><line x1="12" y1="22" x2="12" y2="7"/><path d="M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z"/><path d="M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z"/></Icon>;
const IconFileText = (p) => <Icon {...p}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></Icon>;
const IconShield = (p) => <Icon {...p}><path d="M12 2L4 6v6c0 5 3.5 9.5 8 10 4.5-.5 8-5 8-10V6l-8-4z"/></Icon>;
const IconMic = (p) => <Icon {...p}><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/></Icon>;
const IconMonitor = (p) => <Icon {...p}><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></Icon>;
const IconKey = (p) => <Icon {...p}><path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/></Icon>;
const IconUser = (p) => <Icon {...p}><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></Icon>;
const IconBroadcast = (p) => <Icon {...p}><circle cx="12" cy="12" r="2"/><path d="M16.24 7.76a6 6 0 0 1 0 8.49m-8.48-.01a6 6 0 0 1 0-8.49m11.31-2.82a10 10 0 0 1 0 14.14m-14.14 0a10 10 0 0 1 0-14.14"/></Icon>;
const IconAward = (p) => <Icon {...p}><circle cx="12" cy="8" r="7"/><polyline points="8.21 13.89 7 23 12 20 17 23 15.79 13.88"/></Icon>;
const IconExpand = (p) => <Icon {...p}><polyline points="15 3 21 3 21 9"/><polyline points="9 21 3 21 3 15"/><line x1="21" y1="3" x2="14" y2="10"/><line x1="3" y1="21" x2="10" y2="14"/></Icon>;
const IconCoin = (p) => <Icon {...p}><circle cx="12" cy="12" r="9"/><path d="M9 9h4.5a2 2 0 0 1 0 4H9m0 0v3m0-3h5"/></Icon>;
const IconRefresh = (p) => <Icon {...p}><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></Icon>;
const IconAlertTriangle = (p) => <Icon {...p}><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></Icon>;

// =============================================================
// Sakura particles (CSS-driven, no JS animation loop)
// =============================================================
function SakuraParticles({ count = 32 }) {
  const petals = React.useMemo(() => {
    const arr = [];
    for (let i = 0; i < count; i++) {
      arr.push({
        id: i,
        left: Math.random() * 100,
        size: 8 + Math.random() * 8,
        opacity: 0.25 + Math.random() * 0.5,
        duration: 14 + Math.random() * 12,
        delay: -Math.random() * 26,
        drift: (Math.random() - 0.5) * 220,
        spin: 360 + Math.random() * 540,
        hue: Math.random() * 16 - 4, // -4 to 12 (pink range)
      });
    }
    return arr;
  }, [count]);

  return (
    <div className="sakura-layer">
      {petals.map(p => (
        <div key={p.id}
          className="petal"
          style={{
            left: `${p.left}%`,
            opacity: p.opacity,
            animationDuration: `${p.duration}s`,
            animationDelay: `${p.delay}s`,
            "--drift": `${p.drift}px`,
            "--spin": `${p.spin}deg`,
          }}>
          <svg width={p.size} height={p.size} viewBox="0 0 20 20">
            <path
              d="M10 1 C 14 4, 18 7, 16 11 C 14 15, 11 16, 10 19 C 9 16, 6 15, 4 11 C 2 7, 6 4, 10 1 Z"
              fill={`hsl(${340 + p.hue}, 78%, 78%)`}
              stroke={`hsl(${330 + p.hue}, 60%, 60%)`}
              strokeWidth=".4"
              opacity=".95"
            />
          </svg>
        </div>
      ))}
    </div>
  );
}

// =============================================================
// Panorama (background sprite simulation)
// =============================================================
function Panorama() {
  // Stylized far-pine silhouette + 2 torii silhouettes via SVG
  return (
    <div className="panorama">
      <div className="moon"/>
      <div className="panorama-mist"/>

      {/* Far pine silhouette */}
      <div className="silhouette">
        <div className="silhouette-row" style={{height: "55%", opacity: .8}}>
          <svg viewBox="0 0 1920 300" preserveAspectRatio="none">
            <path fill="#06080f" d="
              M0,300 L0,180
              L60,140 L100,170 L140,120 L180,160 L210,100 L260,150 L290,110 L340,170 L380,130 L430,170
              L460,90 L520,140 L560,180 L610,110 L660,160 L700,130 L740,180 L790,140 L830,90 L890,160
              L940,120 L980,180 L1020,140 L1060,100 L1110,160 L1150,130 L1200,170 L1240,110 L1290,160
              L1330,130 L1380,180 L1430,140 L1470,100 L1520,170 L1560,130 L1610,160 L1660,110 L1700,150
              L1750,130 L1800,170 L1860,120 L1920,160 L1920,300 Z"/>
          </svg>
        </div>
        {/* Closer silhouette + torii */}
        <div className="silhouette-row" style={{height: "75%"}}>
          <svg viewBox="0 0 1920 400" preserveAspectRatio="none">
            <path fill="#020308" d="
              M0,400 L0,260
              L100,200 L180,250 L240,180 L320,240 L380,200 L460,260 L520,210 L600,250
              L680,180 L760,240 L840,200 L920,250 L1000,210 L1080,240 L1160,200 L1240,260
              L1320,210 L1400,250 L1480,200 L1560,260 L1640,210 L1720,240 L1800,200 L1920,260
              L1920,400 Z"/>
            {/* Torii silhouette left */}
            <g fill="#020308">
              <rect x="220" y="210" width="14" height="180"/>
              <rect x="290" y="210" width="14" height="180"/>
              <rect x="200" y="190" width="124" height="14"/>
              <rect x="210" y="175" width="104" height="10"/>
              <rect x="240" y="270" width="44" height="6"/>
            </g>
            {/* Torii right (smaller, farther) */}
            <g fill="#03040a" opacity=".85">
              <rect x="1480" y="240" width="10" height="140"/>
              <rect x="1540" y="240" width="10" height="140"/>
              <rect x="1466" y="226" width="98" height="10"/>
              <rect x="1474" y="216" width="82" height="7"/>
            </g>
          </svg>
        </div>
      </div>

      {/* Stone lanterns */}
      <div className="lantern" style={{left: "12%", top: "78%"}}/>
      <div className="lantern" style={{left: "22%", top: "82%", width: 10, height: 10}}/>
      <div className="lantern" style={{left: "76%", top: "78%"}}/>
      <div className="lantern" style={{left: "84%", top: "82%", width: 10, height: 10}}/>
      <div className="lantern" style={{left: "48%", top: "86%", width: 8, height: 8}}/>
      <div className="lantern" style={{left: "60%", top: "84%", width: 11, height: 11, animationDelay: "1.2s"}}/>
      <div className="lantern" style={{left: "30%", top: "88%", width: 9, height: 9, animationDelay: ".6s"}}/>

      <div className="panorama-overlay"/>
      <div className="vignette"/>
    </div>
  );
}

// =============================================================
// Reborn logo glyph — original circular sigil (NOT a real franchise emblem)
// Stylized 'R' inside a brushed ring, with a small radial spark.
// =============================================================
function LogoSigil({ size = 86 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" fill="none">
      <defs>
        <radialGradient id="sigil-glow" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#3b5bdb" stopOpacity=".6"/>
          <stop offset="60%" stopColor="#3b5bdb" stopOpacity="0"/>
        </radialGradient>
      </defs>
      <circle cx="50" cy="50" r="48" fill="url(#sigil-glow)"/>
      {/* outer ring (brush stroke) */}
      <circle cx="50" cy="50" r="42" stroke="#f4f1ea" strokeWidth="2.2" strokeDasharray="225 30" strokeLinecap="round" transform="rotate(-18 50 50)"/>
      {/* inner stroke */}
      <circle cx="50" cy="50" r="34" stroke="#3b5bdb" strokeWidth="1.2" opacity=".55"/>
      {/* Small slash mark */}
      <path d="M22 22 L34 30" stroke="#ef4444" strokeWidth="2.6" strokeLinecap="round" opacity=".85"/>
      {/* Center kanji-like brushed glyph (original abstract mark — two strokes) */}
      <g stroke="#f4f1ea" strokeLinecap="round" fill="none">
        <path d="M36 38 L64 38" strokeWidth="3.2"/>
        <path d="M40 50 C 48 50, 56 56, 60 64" strokeWidth="3.6"/>
        <path d="M50 38 L46 64" strokeWidth="3"/>
      </g>
    </svg>
  );
}

Object.assign(window, {
  Icon, IconPlay, IconPause, IconVolume, IconMenu, IconSettings, IconGlobe,
  IconDiscord, IconTwitch, IconX, IconXLogo, IconChevronLeft, IconChevronRight,
  IconGift, IconFileText, IconShield, IconMic, IconMonitor, IconKey, IconUser,
  IconBroadcast, IconAward, IconExpand, IconCoin, IconRefresh, IconAlertTriangle,
  SakuraParticles, Panorama, LogoSigil,
});
