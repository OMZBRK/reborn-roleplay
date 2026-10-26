'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

/**
 * « Mes prises » fusionne dans À traiter (PANEL_V2 §3.4) : l'ancienne
 * adresse reste valable et ouvre la file filtrée sur « À moi ».
 */
export default function MesPrisesPage() {
  const router = useRouter();
  useEffect(() => {
    router.replace('/dashboard?filtre=moi');
  }, [router]);
  return null;
}
