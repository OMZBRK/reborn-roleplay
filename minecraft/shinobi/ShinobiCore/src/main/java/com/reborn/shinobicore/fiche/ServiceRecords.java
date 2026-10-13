package com.reborn.shinobicore.fiche;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.ServiceRecordService;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * États de service des personnages (fiche shinobi, onglet Identité) : façade
 * métier au-dessus de {@link ServiceLogStore}, et implémentation de l'API
 * {@link ServiceRecordService} publiée aux addons.
 *
 * <p>Rattrapage : un personnage antérieur au journal reçoit, à la première
 * lecture, une entrée ENREGISTREMENT sans date ({@code at = 0}).
 */
public final class ServiceRecords implements ServiceRecordService {

    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final ShinobiCore plugin;
    private final ServiceLogStore store;

    public ServiceRecords(ShinobiCore plugin, ServiceLogStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public ServiceLogStore store() { return store; }

    /** Journal complet d'un personnage (jamais null). */
    public ServiceLog log(ShinobiCharacter c) {
        return store.of(c.id());
    }

    /* ------------------------------------------------------------ écriture */

    /** À la création du personnage : « Enregistré à &lt;village&gt; ». */
    public void enregistrement(ShinobiCharacter c) {
        add(c, Type.ENREGISTREMENT, enregistrementText(c), null, null, System.currentTimeMillis());
    }

    /** Passage de grade (montée ou descente), après {@code /grade}. */
    public void promotion(ShinobiCharacter c, Rank from, Rank to, boolean exam, String by) {
        if (c == null || to == null) return;
        boolean up = from == null || to.ordinal() > from.ordinal();
        if (up) {
            add(c, Type.PROMOTION, "Promu " + to.displayName() + (exam ? ", sur examen" : ", au mérite"),
                    null, by, System.currentTimeMillis());
        } else {
            add(c, Type.RETROGRADATION, "Rétrogradé au rang de " + to.displayName(),
                    null, by, System.currentTimeMillis());
        }
    }

    /** Mission accomplie ; texte par défaut « Mission de rang X accomplie ». */
    public void mission(ShinobiCharacter c, String rang, String texte, String by, long at) {
        String r = rang == null ? null : rang.trim().toUpperCase(Locale.ROOT);
        String t = texte == null || texte.isBlank() ? "Mission de rang " + r + " accomplie" : texte.trim();
        add(c, Type.MISSION, t, r, by, at);
    }

    /** Ajoute une entrée quelconque, datée de {@code at} (ms epoch ; 0 = sans date). */
    public void add(ShinobiCharacter c, Type type, String texte, String rang, String by, long at) {
        if (c == null) return;
        ServiceLog log = store.of(c.id());
        catchUp(log, c);
        log.append(new ServiceEntry(type == null ? Type.AUTRE : type, texte == null ? "" : texte.trim(),
                rang, Math.max(0L, at), loreDate(), by, false, null));
        store.save(log);
    }

    /** Masque l'entrée n° {@code n} (1 = la plus ancienne, numérotation de {@code /fiche service}). */
    public boolean hide(ShinobiCharacter c, int n, String by) {
        if (c == null) return false;
        ServiceLog log = store.of(c.id());
        boolean ok = log.hide(n - 1, by);
        if (ok) store.save(log);
        return ok;
    }

    @Override
    public void append(UUID charId, Type type, String text, String rang, String by) {
        if (charId == null) return;
        ShinobiCharacter c = null;
        CharacterService cs = plugin == null ? null : plugin.characters();
        if (cs != null) {
            CharacterService.ResolvedCharacter r = cs.resolveOwnedById(charId);
            if (r != null) c = r.character();
        }
        if (c != null) {
            add(c, type, text, rang, by, System.currentTimeMillis());
            return;
        }
        // Personnage introuvable en mémoire : on écrit quand même au journal.
        ServiceLog log = store.of(charId);
        log.append(new ServiceEntry(type == null ? Type.AUTRE : type, text == null ? "" : text.trim(),
                rang, System.currentTimeMillis(), loreDate(), by, false, null));
        store.save(log);
    }

    /* ------------------------------------------------------------- lecture */

    /** Les {@code n} dernières entrées visibles, de la plus ancienne à la plus récente. */
    public List<ServiceEntry> recent(ShinobiCharacter c, int n) {
        ServiceLog log = store.of(c.id());
        if (catchUp(log, c)) store.save(log);
        List<ServiceEntry> vis = log.visible();
        int from = Math.max(0, vis.size() - Math.max(0, n));
        return new ArrayList<>(vis.subList(from, vis.size()));
    }

    /** Nombre d'entrées visibles. */
    public int visibleCount(ShinobiCharacter c) {
        return store.of(c.id()).visible().size();
    }

    /* ------------------------------------------------------------- outils */

    /** Rattrapage d'un journal vide : ENREGISTREMENT sans date. Vrai si ajouté. */
    private boolean catchUp(ServiceLog log, ShinobiCharacter c) {
        if (!log.isEmpty()) return false;
        log.append(new ServiceEntry(Type.ENREGISTREMENT, enregistrementText(c), null, 0L,
                null, null, false, null));
        return true;
    }

    private static String enregistrementText(ShinobiCharacter c) {
        String v = c == null ? "" : c.village();
        return v == null || v.isBlank() ? "Enregistré" : "Enregistré à " + v;
    }

    private String loreDate() {
        try {
            return plugin == null || plugin.loreCalendar() == null ? null : plugin.loreCalendar().compact();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Date affichée sur la fiche : « 4 septembre », « 1er octobre », et l'année
     * si ce n'est pas l'année en cours (« 28 septembre 2025 »). Vide si {@code at <= 0}.
     */
    public static String formatDate(long at) {
        return formatDate(at, LocalDate.now(ZONE).getYear());
    }

    static String formatDate(long at, int currentYear) {
        if (at <= 0) return "";
        ZonedDateTime d = Instant.ofEpochMilli(at).atZone(ZONE);
        String day = d.getDayOfMonth() == 1 ? "1er" : Integer.toString(d.getDayOfMonth());
        String month = d.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH);
        String s = day + " " + month;
        return d.getYear() == currentYear ? s : s + " " + d.getYear();
    }

    /** Lit « jj/mm » ou « jj/mm/aaaa » (midi, heure de Paris) ; -1 si illisible. */
    public static long parseDate(String raw) {
        if (raw == null) return -1;
        String[] p = raw.trim().split("[/.-]");
        if (p.length < 2 || p.length > 3) return -1;
        try {
            int d = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            int y = p.length == 3 ? Integer.parseInt(p[2]) : LocalDate.now(ZONE).getYear();
            if (y < 100) y += 2000;
            return LocalDate.of(y, m, d).atTime(12, 0).atZone(ZONE).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
