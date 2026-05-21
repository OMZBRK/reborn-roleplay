/* global React, LogoSigil,
   IconTwitch, IconGift, IconFileText, IconDiscord, IconGlobe, IconXLogo,
   IconCoin, IconExpand, IconRefresh */

// =============================================================
// REBORN — ESC menu (pause) + 5 panels + community floating bar
// =============================================================

function EscTabs({ active, setActive, onResume, onSettings, onQuit }) {
  const tabs = [
    { id: "resume",   label: "Reprendre" },
    { id: "settings", label: "Paramètres" },
  ];
  const tabs2 = [
    { id: "report",     label: "Report" },
    { id: "quit",       label: "Déconnexion" },
  ];
  const handle = (id) => {
    if (id === "resume") return onResume();
    if (id === "settings") return onSettings();
    if (id === "quit") return onQuit();
    setActive(id);
  };
  return (
    <div className="esc-tabs">
      {tabs.map((t, i) => (
        <React.Fragment key={t.id}>
          {i > 0 && <span className="esc-tab-sep">·</span>}
          <button
            className={`esc-tab ${active === t.id ? "active" : ""}`}
            onClick={() => handle(t.id)}>{t.label}</button>
        </React.Fragment>
      ))}
      <div className="esc-tab-logo"><LogoSigil size={40}/></div>
      {tabs2.map((t, i) => (
        <React.Fragment key={t.id}>
          {i > 0 && <span className="esc-tab-sep">·</span>}
          <button
            className={`esc-tab ${active === t.id ? "active" : ""}`}
            onClick={() => handle(t.id)}>{t.label}</button>
        </React.Fragment>
      ))}
    </div>
  );
}

function PanelProfile() {
  return (
    <div className="panel panel-profile">
      <div className="profile-art">
        <div className="profile-art-silhouette">
          {/* Original abstract ninja silhouette — geometric, not derived from any franchise */}
          <svg width="200" height="280" viewBox="0 0 200 280">
            <defs>
              <linearGradient id="ninja" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stopColor="#1a2550"/>
                <stop offset="1" stopColor="#06080f"/>
              </linearGradient>
            </defs>
            {/* head + cloak silhouette */}
            <path fill="url(#ninja)" d="
              M100 30
              C 78 30, 66 50, 70 72
              L 60 90
              C 40 100, 30 130, 36 160
              L 20 200 L 28 280 L 172 280 L 180 200 L 164 160
              C 170 130, 160 100, 140 90
              L 130 72
              C 134 50, 122 30, 100 30 Z"/>
            {/* hood ridge */}
            <path stroke="#3b5bdb" strokeWidth="2" fill="none" opacity=".6" d="M68 78 C 80 60, 120 60, 132 78"/>
            {/* eye glint */}
            <ellipse cx="100" cy="58" rx="14" ry="3" fill="#ef4444" opacity=".85"/>
            <ellipse cx="100" cy="58" rx="20" ry="1.5" fill="#ef4444" opacity=".35"/>
          </svg>
        </div>
      </div>
      <div className="profile-body">
        <div className="profile-currency">
          <IconCoin size={16}/>
          <span>0 ZK</span>
        </div>
        <div className="profile-name">Hikami Yorishiro</div>
        <div className="profile-handle">@hikami · Identifiant #RBN-04217</div>
        <div className="role-badge">
          <span style={{width: 6, height: 6, borderRadius: 999, background: "#3b5bdb"}}/>
          Whitelisted
        </div>
        <div className="profile-shop">
          <span className="profile-shop-label">Boutique</span>
          <span style={{color: "#9fb3ff", fontSize: 14}}>→</span>
        </div>
        <div className="profile-stats">
          <div>
            <div className="profile-stat-label">Temps de jeu</div>
            <div className="profile-stat-value">142 h</div>
          </div>
          <div>
            <div className="profile-stat-label">Dernière session</div>
            <div className="profile-stat-value">Hier · 3 h 12</div>
          </div>
          <div>
            <div className="profile-stat-label">Faction</div>
            <div className="profile-stat-value">Aucune</div>
          </div>
        </div>
      </div>
    </div>
  );
}

function PanelStream() {
  return (
    <div className="panel panel-stream">
      <div className="panel-head">
        <IconTwitch size={16}/>
        <span>Stream Reborn</span>
        <span className="panel-head-right" style={{fontSize: 10, color: "var(--color-foreground-muted)", letterSpacing: ".12em"}}>LIVE 0</span>
      </div>
      <div className="stream-thumb">
        <div className="stream-empty">
          <strong>Aucun stream en cours</strong>
          Reviens plus tard, ou lance le tien.
        </div>
      </div>
      <button className="panel-foot-btn">Voir tous les streams</button>
    </div>
  );
}

function PanelBlog() {
  return (
    <div className="panel panel-blog">
      <div className="panel-head">
        <IconFileText size={16}/>
        <span>Dev · Blog</span>
      </div>
      <div className="blog-thumb"/>
      <div className="blog-date">12 mai 2026</div>
      <div className="blog-title">Patch 1.0.5 — Refonte du système d'arts</div>
      <div className="blog-excerpt">
        On a réécrit le moteur des combats à mains nues. Combos plus fluides,
        nouvelles animations, et un système de fatigue qui force à respirer.
      </div>
      <button className="panel-foot-btn">Lire l'article</button>
    </div>
  );
}

function PanelRewards() {
  const [rewards, setRewards] = React.useState([
    { id: "premium", name: "Premium", perk: "+5 ZK / h", on: true  },
    { id: "booster", name: "Booster", perk: "+5 ZK / h", on: true  },
    { id: "tag",     name: "Tag",     perk: "+5 ZK / h", on: false },
    { id: "bio",     name: "Bio",     perk: "+5 ZK / h", on: true  },
    { id: "steam",   name: "Steam",   perk: "+5 ZK / h", on: false },
  ]);
  const toggle = (id) => setRewards(rs => rs.map(r => r.id === id ? {...r, on: !r.on} : r));
  return (
    <div className="panel panel-rewards">
      <div className="panel-head">
        <IconGift size={16}/>
        <span>Récompenses</span>
        <span className="panel-head-right" style={{fontSize: 10, color: "var(--color-foreground-muted)", letterSpacing: ".12em"}}>3 / 5 ACTIVES</span>
      </div>
      <div className="rewards-list">
        {rewards.map(r => (
          <div key={r.id} className="reward-row">
            <div className={`reward-toggle ${r.on ? "on" : ""}`} onClick={() => toggle(r.id)}/>
            <div>
              <div className="reward-name">{r.name}</div>
              <div className="reward-perk">Lié à ton compte launcher</div>
            </div>
            <div className="reward-multiplier">{r.perk}</div>
            <button className="icon-btn" title="Actualiser"><IconRefresh size={14}/></button>
          </div>
        ))}
      </div>
      <div className="rewards-foot">
        <div>
          <div className="rewards-timer">01:00:00</div>
          <div className="rewards-timer-sub">Prochaine récompense</div>
        </div>
        <button className="panel-foot-btn" style={{margin: 0, padding: "10px 18px"}}>Réclamer maintenant</button>
      </div>
    </div>
  );
}

function CommunityPanel() {
  return (
    <div className="panel-community">
      <div className="community-text">
        <div className="community-handle">Reborn Roleplay · @RebornOff</div>
        <div className="community-quote">
          On pourrait te supplier de nous suivre. Mais on préfère te laisser prendre
          une bonne décision tout seul. <strong>#REBORN</strong>
        </div>
      </div>
      <div className="community-bubbles">
        <button className="bubble tip tip-below" data-tip="Discord"><IconDiscord size={22}/></button>
        <button className="bubble tip tip-below" data-tip="X / Twitter"><IconXLogo size={20}/></button>
        <button className="bubble tip tip-below" data-tip="Site web"><IconGlobe size={22}/></button>
        <button className="bubble tip tip-below" data-tip="Twitch"><IconTwitch size={22}/></button>
      </div>
    </div>
  );
}

function EscMenu({ onResume, onSettings, onQuit }) {
  const [tab, setTab] = React.useState("resume");
  return (
    <>
      <div className="esc-backdrop"/>
      <div className="esc-pattern"/>
      <EscTabs active={tab} setActive={setTab} onResume={onResume} onSettings={onSettings} onQuit={onQuit}/>

      <div className="esc-grid">
        <PanelProfile/>
        <PanelStream/>
        <PanelRewards/>
        <PanelBlog/>
      </div>

      <CommunityPanel/>
    </>
  );
}

Object.assign(window, { EscMenu, EscTabs, PanelProfile, PanelStream, PanelBlog, PanelRewards, CommunityPanel });
