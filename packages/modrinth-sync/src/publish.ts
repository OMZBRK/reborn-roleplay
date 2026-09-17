// `publish` : le « 1-clic » local. Signe le manifeste candidat avec la clé
// Ed25519 HORS-LIGNE (secrets/), re-vérifie les URLs CDN (200 + sha256), puis —
// seulement avec --confirm — POST /v1/admin/manifest via manifest-uploader.
//
// Sans --confirm : dry-run (signe + vérifie, mais ne publie pas). Garde-fou pour
// qu'aucune publication prod ne parte par accident.
//
// La signature réutilise `signManifest` de @reborn/manifest-signer (import direct)
// → forme canonique identique à ce que le launcher vérifie, pas de dérive d'octets.
import { readFileSync, writeFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  signManifest,
  loadPrivateKeyFromPem,
  verifyManifest,
  loadPublicKeyFromPem,
} from "../../manifest-signer/src/sign";

function cmpVersion(a: string, b: string): number {
  const pa = a.split(".").map((n) => parseInt(n, 10) || 0);
  const pb = b.split(".").map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < 3; i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d !== 0) return d;
  }
  return 0;
}

function findLatestCandidate(secretsDir: string): string {
  const re = /^manifest-candidate-v(\d+\.\d+\.\d+)\.json$/;
  const cands = readdirSync(secretsDir)
    .map((f) => ({ f, m: f.match(re) }))
    .filter((x) => x.m)
    .map((x) => ({ file: join(secretsDir, x.f), ver: x.m![1] }))
    .sort((a, b) => cmpVersion(b.ver, a.ver));
  if (cands.length === 0) {
    throw new Error("aucun manifest-candidate-v*.json — lance `prepare` d'abord.");
  }
  return cands[0].file;
}

interface FileEntry {
  path: string;
  url: string;
  sha256: string;
  size: number;
}

async function verifyUrls(files: FileEntry[]): Promise<boolean> {
  const targets = files.filter((f) => f.url.includes("cdn.modrinth.com"));
  let ok = true;
  for (const f of targets) {
    try {
      const res = await fetch(f.url, { headers: { "User-Agent": "reborn-roleplay/modrinth-sync" } });
      if (!res.ok) {
        console.log(`  KO  ${f.path.slice(5)} — HTTP ${res.status}`);
        ok = false;
        continue;
      }
      const buf = Buffer.from(await res.arrayBuffer());
      const sha = createHash("sha256").update(buf).digest("hex");
      const good = sha === f.sha256 && buf.length === f.size;
      console.log(`  ${good ? "OK " : "KO "} ${f.path.slice(5)}`);
      ok = ok && good;
    } catch (e) {
      console.log(`  KO  ${f.path.slice(5)} — ${(e as Error).message}`);
      ok = false;
    }
  }
  return ok;
}

export async function publish(
  secretsDir: string,
  repoRoot: string,
  candidatePath: string | undefined,
  confirm: boolean,
): Promise<void> {
  const candPath = candidatePath ?? findLatestCandidate(secretsDir);
  const candidate = JSON.parse(readFileSync(candPath, "utf-8"));
  if (candidate.signature) throw new Error(`${candPath} est déjà signé — attendu un candidat non-signé.`);
  const version: string = candidate.version;
  console.log(`Candidat : ${candPath}  (v${version})`);

  // 1. Signature hors-ligne.
  const priv = loadPrivateKeyFromPem(readFileSync(join(secretsDir, "manifest_ed25519_private.pem"), "utf-8"));
  const signed = signManifest(candidate, priv);
  const pub = loadPublicKeyFromPem(readFileSync(join(secretsDir, "manifest_ed25519_public.pem"), "utf-8"));
  if (!verifyManifest(signed, pub)) throw new Error("signature invalide juste après signature — abandon.");
  const signedPath = join(secretsDir, `manifest-signed-v${version}.json`);
  writeFileSync(signedPath, JSON.stringify(signed, null, 2), "utf-8");
  console.log(`Signé    : ${signedPath}`);

  // 2. Re-vérif des URLs CDN (200 + sha256 + taille).
  console.log("Vérif URLs CDN Modrinth :");
  if (!(await verifyUrls(signed.files as FileEntry[]))) {
    throw new Error("des URLs/sha256 ne concordent pas — publication annulée.");
  }

  // 3. POST (seulement si --confirm).
  if (!confirm) {
    console.log(`\n[dry-run] v${version} prêt. Relance avec --confirm pour POSTer /v1/admin/manifest.`);
    return;
  }
  const uploader = join(repoRoot, "packages", "manifest-uploader", "target", "release", "manifest-uploader.exe");
  console.log(`\nPOST /v1/admin/manifest via manifest-uploader…`);
  const out = execFileSync(uploader, ["manifest", "--file", signedPath], { encoding: "utf-8" });
  console.log(
    out
      .split(/\r?\n/)
      .filter((l) => /HTTP|Refresh|version/.test(l))
      .slice(0, 3)
      .join("\n"),
  );
  console.log(`✅ v${version} publié (isCurrent). Les joueurs auto-updatent au prochain lancement.`);
}
