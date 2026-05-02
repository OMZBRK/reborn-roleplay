#!/usr/bin/env node
/**
 * CLI Reborn manifest-signer.
 *
 *   reborn-manifest gen-keys [--out-dir secrets]
 *   reborn-manifest sign <unsigned.json> --key <private.pem> [--out signed.json]
 *   reborn-manifest verify <signed.json> --pub <public.pem>
 */
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import {
  generateKeyPair,
  loadPrivateKeyFromPem,
  loadPublicKeyFromPem,
  signManifest,
  verifyManifest,
} from "./sign.js";
import type { SignedManifest, UnsignedManifest } from "./manifest.js";

function help(): never {
  console.log(`Reborn manifest-signer

Usage:
  reborn-manifest gen-keys [--out-dir <dir>]
      Generate a fresh Ed25519 keypair. Writes:
        <dir>/manifest_ed25519_private.pem
        <dir>/manifest_ed25519_public.pem
        <dir>/manifest_ed25519_public.hex   (32-byte public key, hex)

  reborn-manifest sign <input.json> --key <private.pem> [--out <output.json>]
      Sign an unsigned manifest. Default output is "<input>-signed.json".

  reborn-manifest verify <signed.json> --pub <public.pem>
      Verify a signed manifest. Exits 0 on success, 1 on mismatch.
`);
  process.exit(0);
}

function arg(flag: string, args: string[]): string | undefined {
  const i = args.indexOf(flag);
  return i >= 0 ? args[i + 1] : undefined;
}

function ensureDir(file: string): void {
  mkdirSync(dirname(file), { recursive: true });
}

async function main(): Promise<void> {
  const [, , subcmd, ...rest] = process.argv;
  if (!subcmd || subcmd === "--help" || subcmd === "-h") help();

  switch (subcmd) {
    case "gen-keys": {
      const outDir = resolve(arg("--out-dir", rest) ?? "secrets");
      const { privatePem, publicPem, publicKeyHex } = generateKeyPair();

      const privPath = resolve(outDir, "manifest_ed25519_private.pem");
      const pubPath = resolve(outDir, "manifest_ed25519_public.pem");
      const hexPath = resolve(outDir, "manifest_ed25519_public.hex");

      ensureDir(privPath);
      writeFileSync(privPath, privatePem, { mode: 0o600 });
      writeFileSync(pubPath, publicPem);
      writeFileSync(hexPath, publicKeyHex + "\n");

      console.log(`Keypair generated in ${outDir}`);
      console.log(`  private : ${privPath}  (mode 600)`);
      console.log(`  public  : ${pubPath}`);
      console.log(`  pubhex  : ${hexPath}`);
      console.log(``);
      console.log(`Public key (hex, copy into Rust binary):`);
      console.log(publicKeyHex);
      break;
    }

    case "sign": {
      const inputPath = rest[0];
      const keyPath = arg("--key", rest);
      const outPath = arg("--out", rest) ?? inputPath?.replace(/\.json$/i, "-signed.json");
      if (!inputPath || !keyPath || !outPath) {
        console.error("Missing arguments. See --help.");
        process.exit(2);
      }

      const unsigned = JSON.parse(readFileSync(inputPath, "utf8")) as UnsignedManifest;
      const privateKey = loadPrivateKeyFromPem(readFileSync(keyPath, "utf8"));
      const signed = signManifest(unsigned, privateKey);

      writeFileSync(outPath, JSON.stringify(signed, null, 2) + "\n");
      console.log(`Signed manifest written to ${outPath}`);
      console.log(`  signature: ${signed.signature.slice(0, 24)}…`);
      break;
    }

    case "verify": {
      const inputPath = rest[0];
      const pubPath = arg("--pub", rest);
      if (!inputPath || !pubPath) {
        console.error("Missing arguments. See --help.");
        process.exit(2);
      }

      const signed = JSON.parse(readFileSync(inputPath, "utf8")) as SignedManifest;
      const publicKey = loadPublicKeyFromPem(readFileSync(pubPath, "utf8"));
      const ok = verifyManifest(signed, publicKey);

      if (ok) {
        console.log(`OK : signature valide pour ${inputPath}`);
        process.exit(0);
      } else {
        console.error(`FAIL : signature invalide pour ${inputPath}`);
        process.exit(1);
      }
      break;
    }

    default:
      console.error(`Commande inconnue : ${subcmd}`);
      help();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
