package com.reborn.shinobicore.fiche;

import com.reborn.shinobicore.api.ServiceRecordService;

/**
 * Une ligne des états de service. Le texte est figé au moment de l'ajout :
 * c'est un historique, il ne se recalcule pas si le grade ou le village change.
 *
 * @param type     nature (choisit le sceau côté mod)
 * @param text     texte affiché
 * @param rang     lettre de rang d'une mission, sinon {@code null}
 * @param at       date réelle (ms epoch) ; 0 = rattrapage sans date
 * @param loreDate date du calendrier lore au moment de l'ajout, ou {@code null}
 * @param by       auteur de l'entrée, ou {@code null}
 * @param hidden   masquée par le staff (jamais supprimée)
 * @param hiddenBy qui l'a masquée, ou {@code null}
 */
public record ServiceEntry(ServiceRecordService.Type type, String text, String rang, long at,
                           String loreDate, String by, boolean hidden, String hiddenBy) {

    /** Alias de lecture : le type de l'API. */
    public static ServiceRecordService.Type parseType(String raw) {
        if (raw == null) return ServiceRecordService.Type.AUTRE;
        String s = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(java.util.Locale.ROOT);
        try {
            return ServiceRecordService.Type.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Copie masquée. */
    public ServiceEntry asHidden(String who) {
        return new ServiceEntry(type, text, rang, at, loreDate, by, true, who);
    }
}
