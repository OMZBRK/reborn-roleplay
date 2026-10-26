'use client';

import { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/States';
import { apiBlob } from '@/lib/api';
import { dateHeure, dureeAudio, jourLong } from '@/lib/candidature';
import type { WhitelistDetail } from '@/lib/types';

/** Événement « lire / mettre en pause la voix » (touche Espace de la fiche). */
export const EVENEMENT_VOIX = 'reborn:voix-basculer';

/**
 * Lecteur de l'enregistrement vocal (onglet Voix de la candidature, §2.3).
 * Le fichier est privé : lu avec le jeton du staff par
 * GET /admin/whitelist/:id/voix, puis joué depuis une adresse locale du
 * navigateur. Aucune adresse publique. Chargé sur demande : chaque écoute est
 * inscrite au journal côté API.
 */
export function VoixLecteur({
  applicationId,
  voix,
  voiceDeletedAt,
  voiceConsentAt,
}: {
  applicationId: string;
  voix: WhitelistDetail['voix'];
  voiceDeletedAt: string | null;
  voiceConsentAt: string | null;
}) {
  const [url, setUrl] = useState<string | null>(null);
  const [chargement, setChargement] = useState(false);
  const [erreur, setErreur] = useState<unknown>(null);
  const urlRef = useRef<string | null>(null);
  const audio = useRef<HTMLAudioElement>(null);

  // Libère l'adresse locale en quittant la fiche (ou en changeant de candidature).
  useEffect(() => {
    return () => {
      if (urlRef.current) URL.revokeObjectURL(urlRef.current);
      urlRef.current = null;
      setUrl(null);
    };
  }, [applicationId]);

  async function charger() {
    setChargement(true);
    setErreur(null);
    try {
      const blob = await apiBlob(`/admin/whitelist/${applicationId}/voix`);
      if (urlRef.current) URL.revokeObjectURL(urlRef.current);
      const u = URL.createObjectURL(blob);
      urlRef.current = u;
      setUrl(u);
    } catch (e) {
      setErreur(e);
    } finally {
      setChargement(false);
    }
  }

  // Espace : charge la voix au premier appui, puis lecture / pause.
  const chargerRef = useRef(charger);
  chargerRef.current = charger;
  useEffect(() => {
    function basculer() {
      const a = audio.current;
      if (!a) {
        if (voix) void chargerRef.current();
        return;
      }
      if (a.paused) void a.play();
      else a.pause();
    }
    window.addEventListener(EVENEMENT_VOIX, basculer);
    return () => window.removeEventListener(EVENEMENT_VOIX, basculer);
  }, [voix]);

  if (!voix) {
    return (
      <p className="text-[13px] text-fg-3">
        {voiceDeletedAt
          ? `Enregistrement effacé le ${jourLong(voiceDeletedAt)} (fin de la durée de garde).`
          : 'Aucun enregistrement vocal (ancien formulaire).'}
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px] text-fg-3">
        {voix.durationSec !== null && (
          <span className="tabular font-medium text-fg">{dureeAudio(voix.durationSec)} / 1:00</span>
        )}
        {voix.uploadedAt && <span>Envoyée le {dateHeure(voix.uploadedAt)}</span>}
        {voiceConsentAt && <span>Consentement donné</span>}
      </div>

      {url ? (
        <audio ref={audio} controls autoPlay src={url} className="w-full [color-scheme:dark]" preload="auto" />
      ) : (
        <Button onClick={() => void charger()} enCours={chargement} icone="play" raccourci="Espace">
          Écouter la voix
        </Button>
      )}
      {erreur ? <ErrorState erreur={erreur} onReessayer={() => void charger()} /> : null}
      <p className="help">
        Fichier privé, réservé au staff de la whitelist ; chaque écoute est inscrite au journal.
        {voix.purgeAt && <> Effacement prévu le {jourLong(voix.purgeAt)}.</>}
      </p>
    </div>
  );
}
