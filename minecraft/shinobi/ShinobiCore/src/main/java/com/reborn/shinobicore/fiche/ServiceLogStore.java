package com.reborn.shinobicore.fiche;

import com.reborn.shinobicore.api.ServiceRecordService;
import com.reborn.shinobicore.data.YamlCharacterDataStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistance YAML des états de service — un fichier par personnage sous
 * {@code data/service/<idPerso>.yml}, sur le modèle d'{@code AcademyStore}.
 *
 * <pre>
 * entrees:
 *   - type: PROMOTION
 *     texte: "Promu Genin, sur examen"
 *     at: 1790000000000
 *     lore: "12/03/495"
 *     par: "Kakashi"
 *     masque: true          # seulement si masquée
 *     masque-par: "Admin"
 * </pre>
 */
public final class ServiceLogStore extends YamlCharacterDataStore<ServiceLog> {

    public ServiceLogStore(JavaPlugin plugin) {
        super(plugin, "data/service");
    }

    @Override
    protected ServiceLog create(UUID characterId) {
        return new ServiceLog(characterId);
    }

    @Override
    protected void read(ServiceLog data, YamlConfiguration yml) {
        List<ServiceEntry> out = new ArrayList<>();
        for (Map<?, ?> raw : yml.getMapList("entrees")) {
            try {
                ServiceRecordService.Type type = ServiceEntry.parseType(str(raw.get("type")));
                if (type == null) type = ServiceRecordService.Type.AUTRE;
                String text = str(raw.get("texte"));
                if (text == null) continue;
                long at = raw.get("at") instanceof Number n ? n.longValue() : 0L;
                boolean hidden = Boolean.TRUE.equals(raw.get("masque"));
                out.add(new ServiceEntry(type, text, str(raw.get("rang")), at, str(raw.get("lore")),
                        str(raw.get("par")), hidden, str(raw.get("masque-par"))));
            } catch (RuntimeException ignored) {
                // ligne abîmée : ignorée, le reste du journal est gardé
            }
        }
        data.load(out);
    }

    @Override
    protected void write(ServiceLog data, YamlConfiguration yml) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ServiceEntry e : data.all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.type().name());
            m.put("texte", e.text());
            if (e.rang() != null) m.put("rang", e.rang());
            m.put("at", e.at());
            if (e.loreDate() != null) m.put("lore", e.loreDate());
            if (e.by() != null) m.put("par", e.by());
            if (e.hidden()) {
                m.put("masque", true);
                if (e.hiddenBy() != null) m.put("masque-par", e.hiddenBy());
            }
            rows.add(m);
        }
        yml.set("entrees", rows);
    }

    @Override
    protected boolean shouldPersist(ServiceLog data, boolean fileExists) {
        return !data.isEmpty() || fileExists;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
