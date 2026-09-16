package com.reborn.shinobicore.technique;

import com.reborn.shinobicore.character.Affinity;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.skill.Skill;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A single access prerequisite of a technique — the piece that was missing from
 * {@link Ability} and made things like « Rasengan exige maîtrise ≥ 60 en Contrôle
 * du Chakra + rang Chūnin » impossible to express.
 *
 * <p>Data-driven so the Technique Creator can emit it verbatim in
 * {@code abilities.yml} under {@code requires:}. Each YAML entry is a small map:
 *
 * <pre>
 * requires:
 *   - { type: ability,  id: controle_chakra }
 *   - { type: mastery,  id: controle_chakra, min: 60 }
 *   - { type: rank,     id: chunin }
 *   - { type: skill,    id: ninjutsu, min: 3 }
 *   - { type: level,    min: 10 }
 *   - { type: clan,     id: uchiha }
 *   - { type: nature,   id: katon }
 * </pre>
 *
 * <p><b>Philosophy:</b> conservative on the unknown. A malformed or not-yet-wired
 * requirement never hard-locks content — {@link #check} logs and passes rather than
 * blocking a technique on data that does not exist yet ({@code exam}, {@code mentor}).
 */
public record Requirement(Type type, String id, int min) {

    public enum Type {
        ABILITY,          // id = ability id ; must be known
        MASTERY,          // id = ability id ; abilityMastery(id) >= min
        RANK,             // id = rank name  ; rank ordinal >= from(id) ordinal
        SKILL,            // id = skill name ; skill(id) >= min
        LEVEL,            // min = character level
        CLAN,             // id = clan name (case-insensitive)
        VILLAGE,          // id = village name (case-insensitive)
        AFFINITY,         // id = stat archetype: strength / intelligence / agility
        NATURE,           // id = chakra nature: katon / suiton / futon / doton / raiton
        EXAM,             // id = exam name — deferred (no exam data yet), passes
        MENTOR            // id = mentor rank — deferred (no mentor data yet), passes
    }

    public Requirement {
        id = id == null ? "" : id.trim();
    }

    /** Tolerant parse of one YAML {@code requires:} entry. Returns empty on garbage. */
    public static Optional<Requirement> parse(Map<?, ?> raw) {
        if (raw == null) return Optional.empty();
        Object t = raw.get("type");
        if (t == null) return Optional.empty();
        Type type;
        try {
            type = Type.valueOf(t.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        Object idObj = raw.get("id");
        String id = idObj == null ? "" : idObj.toString();
        int min = 0;
        Object minObj = raw.get("min");
        if (minObj instanceof Number n) min = n.intValue();
        else if (minObj != null) {
            try { min = Integer.parseInt(minObj.toString().trim()); }
            catch (NumberFormatException ignore) { /* min stays 0 */ }
        }
        return Optional.of(new Requirement(type, id, min));
    }

    /**
     * Evaluate against a character. Empty = satisfied ; a present value is the
     * player-facing reason it failed (shown on the action bar by the gate).
     * Never throws — any evaluation error is treated as satisfied, so a bad
     * requirement can't brick a technique.
     */
    public Optional<String> check(ShinobiCharacter c) {
        if (c == null) return Optional.empty();
        try {
            return switch (type) {
                case ABILITY -> c.knowsAbility(id)
                        ? Optional.empty()
                        : Optional.of("Requiert la technique : " + id);
                case MASTERY -> c.abilityMastery(id) >= min
                        ? Optional.empty()
                        : Optional.of("Maîtrise insuffisante en " + id + " (" + min + " requis)");
                case RANK -> {
                    Rank need = Rank.from(id);
                    yield c.rank().ordinal() >= need.ordinal()
                            ? Optional.empty()
                            : Optional.of("Rang " + need.displayName() + " requis");
                }
                case SKILL -> {
                    Skill s;
                    try { s = Skill.valueOf(id.toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException ex) { yield Optional.empty(); }
                    yield c.skill(s) >= min
                            ? Optional.empty()
                            : Optional.of("Compétence " + id + " ≥ " + min + " requise");
                }
                case LEVEL -> c.level() >= min
                        ? Optional.empty()
                        : Optional.of("Niveau " + min + " requis");
                case CLAN -> id.equalsIgnoreCase(c.clan())
                        ? Optional.empty()
                        : Optional.of("Réservé au clan " + id);
                case VILLAGE -> id.equalsIgnoreCase(c.village())
                        ? Optional.empty()
                        : Optional.of("Réservé au village " + id);
                case AFFINITY -> {
                    Affinity aff = c.affinity();
                    yield aff != null && id.equalsIgnoreCase(aff.name())
                            ? Optional.empty()
                            : Optional.of("Affinité " + id + " requise");
                }
                case NATURE -> {
                    ChakraAffinity need;
                    try { need = ChakraAffinity.valueOf(id.toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException ex) { yield Optional.empty(); }
                    yield c.hasChakraAffinity(need)
                            ? Optional.empty()
                            : Optional.of("Nature " + id + " requise");
                }
                // Deferred: no exam / mentor data model yet. Recognised so the
                // compiler can emit them; passes until the data lands.
                case EXAM, MENTOR -> Optional.empty();
            };
        } catch (Throwable ex) {
            return Optional.empty();
        }
    }
}
