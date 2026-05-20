import { ExternalLink } from "lucide-react";
import pkg from "../../../package.json";

// Mega-footer de la Home : brand + tech stack + social + links.
//
// Volontairement statique pour cette PR :
//   - les ancres "Site web" / "Status serveur" sont des # (no nav)
//   - le bloc social pointe vers des # (icones SVG inline car lucide n'a
//     pas Discord/Twitch officiellement dans son set core, on dessine
//     des glyphs simples)
// TODO(links): brancher les ancres sur les vrais URLs quand le site
// public + status page seront en ligne.
export function FooterMega() {
  return (
    <footer className="reborn-home-footer">
      <div className="reborn-home-footer-col">
        <div className="reborn-home-footer-brand">
          <div className="reborn-home-footer-logo">R</div>
          <div>
            <div className="reborn-home-footer-brand-name">Reborn Roleplay</div>
            <div className="reborn-home-footer-brand-sub">
              Le serveur Naruto RP français
            </div>
          </div>
        </div>
        <div className="reborn-home-footer-copy">
          v{pkg.version} · © 2026 Reborn
        </div>
      </div>

      <div className="reborn-home-footer-col">
        <div className="reborn-home-footer-col-title">Powered by</div>
        <div className="reborn-home-footer-tech">
          <div className="reborn-home-footer-tech-row">
            <div className="reborn-home-footer-tech-mark reborn-home-footer-tech-mark--mc">
              MC
            </div>
            <div>
              <div className="reborn-home-footer-tech-name">Minecraft Java</div>
              <div className="reborn-home-footer-tech-ver">1.21.1</div>
            </div>
          </div>
          <div className="reborn-home-footer-tech-row">
            <div className="reborn-home-footer-tech-mark reborn-home-footer-tech-mark--fb">
              F
            </div>
            <div>
              <div className="reborn-home-footer-tech-name">Fabric Loader</div>
              <div className="reborn-home-footer-tech-ver">stable</div>
            </div>
          </div>
        </div>
      </div>

      <div className="reborn-home-footer-col">
        <div className="reborn-home-footer-col-title">Communauté</div>
        <div className="reborn-home-footer-socials">
          <SocialLink label="Discord">
            <DiscordGlyph />
          </SocialLink>
          <SocialLink label="TikTok">
            <TiktokGlyph />
          </SocialLink>
          <SocialLink label="YouTube">
            <YoutubeGlyph />
          </SocialLink>
          <SocialLink label="X">
            <XGlyph />
          </SocialLink>
        </div>
        <div className="reborn-home-footer-server">
          <span className="reborn-home-footer-server-dot" />
          <span>play.reborn-rp.com:27106</span>
        </div>
      </div>

      <div className="reborn-home-footer-col">
        <div className="reborn-home-footer-col-title">Liens</div>
        <ul className="reborn-home-footer-links">
          <li>
            <a href="#" aria-disabled>
              Site web <ExternalLink className="h-2.5 w-2.5" />
            </a>
          </li>
          <li>
            <a href="#" aria-disabled>
              Statut serveur <ExternalLink className="h-2.5 w-2.5" />
            </a>
          </li>
        </ul>
      </div>
    </footer>
  );
}

function SocialLink({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <a
      href="#"
      aria-label={label}
      className="reborn-home-footer-social"
      aria-disabled
    >
      {children}
    </a>
  );
}

// Glyphs SVG minimalistes — lucide-react ne livre pas Discord/Twitch/TikTok
// officiellement, on dessine nous-memes pour eviter une dep brand-icons.
function DiscordGlyph() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className="h-4 w-4">
      <path d="M20 4.5A18 18 0 0 0 15.5 3l-.4.8a13 13 0 0 0-6.2 0L8.5 3A18 18 0 0 0 4 4.5C1.5 8.5.8 12.4 1.2 16.2A17 17 0 0 0 6.4 19l1.1-1.7c-1-.3-1.9-.8-2.7-1.4l.7-.5a13 13 0 0 0 13 0l.7.5c-.8.6-1.7 1-2.7 1.4L17.6 19a17 17 0 0 0 5.2-2.8c.4-4.3-.5-8.2-2.8-11.7zM9 14.4c-1 0-1.9-.9-1.9-2.1S8 10.2 9 10.2s1.9 1 1.9 2.1-.8 2.1-1.9 2.1zm6 0c-1 0-1.9-.9-1.9-2.1s.9-2.1 1.9-2.1 1.9 1 1.9 2.1-.8 2.1-1.9 2.1z" />
    </svg>
  );
}
function TiktokGlyph() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className="h-4 w-4">
      <path d="M14 2v11.4a3 3 0 1 1-3-3v-3.4A6.4 6.4 0 1 0 17.4 13V8.3c1.1.5 2.3.8 3.6.8V5.5A5.2 5.2 0 0 1 16.3 2H14z" />
    </svg>
  );
}
function YoutubeGlyph() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className="h-4 w-4">
      <path d="M23 7c-.2-1.6-.9-2.3-2.4-2.5C18 4 12 4 12 4s-6 0-8.6.5C1.9 4.7 1.2 5.4 1 7 .5 9.6.5 14.4 1 17c.2 1.6.9 2.3 2.4 2.5 2.6.5 8.6.5 8.6.5s6 0 8.6-.5c1.5-.2 2.2-.9 2.4-2.5.5-2.6.5-7.4 0-10zM10 15.5v-7L16 12l-6 3.5z" />
    </svg>
  );
}
function XGlyph() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className="h-3.5 w-3.5">
      <path d="M18.5 2H22l-7.5 8.6L23 22h-7l-5-6.5L5 22H1.5l8-9.2L1 2h7.2l4.5 6 5.8-6zM17 20h1.7L7.2 4H5.4L17 20z" />
    </svg>
  );
}
