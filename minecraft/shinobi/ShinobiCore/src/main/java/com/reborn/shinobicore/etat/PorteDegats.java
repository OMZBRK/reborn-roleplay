package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.ModificateurService;
import com.reborn.shinobicore.api.etat.Capacite;
import com.reborn.shinobicore.api.etat.DegatContexte;
import com.reborn.shinobicore.api.etat.Etat;
import com.reborn.shinobicore.api.etat.Etiquette;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.modificateur.ModificateurRegistry;
import com.reborn.shinobicore.modificateur.TypeModificateur;
import com.reborn.shinobicore.stats.CastAttributionImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>La porte des dégâts</b> (docs/technique/ETATS_JOUEUR.md §6.3, étape 6) — une seule porte pour tous les plugins.
 *
 * <ul>
 *   <li><b>LOW</b> — la cible doit être {@code CIBLABLE} (mode staff, construction, hors jeu, mort en scène, monde
 *       intérieur, invisible : annulé — c'est ce qui remplace le drapeau {@code Invulnerable} en NBT) ; l'attaquant doit
 *       pouvoir frapper ({@code FRAPPER_MAINS / ARME / PROJECTILE} : douleur, garde, étourdi, porté, menottes…). Le vide
 *       et {@code /kill} passent toujours.</li>
 *   <li><b>NORMAL</b> — les <b>défenses</b> de la cible (types du registre avec {@code defense:}) confrontées aux
 *       <b>étiquettes</b> de l'attaque : intangible, réduction… sauf si une étiquette les <b>contourne</b> (×
 *       faiblesse). NORMAL : après le M1 de ShinobiCombat (LOW) qui pose le montant, avant le calcul du coup mortel
 *       (KoListener, HIGH) — « mortel ? » se calcule sur le montant final.</li>
 * </ul>
 * L'ATA (½, mains nues) reste dans {@code AtaManager} (NORMAL, déjà au bon endroit depuis l'étape 0), la garde dans
 * ShinobiCombat, la résistance du bijū dans ShinobiTail : mêmes résultats, multiplications qui commutent.
 *
 * <p>Étiquettes : technique ({@code etiquettes:} d'abilities.yml, sinon nature + catégorie), M1 ({@code MELEE},
 * {@code PHYSIQUE}, + {@code ARME} et le PDC {@code reborn:etiquettes} de l'arme), projectile ({@code PROJECTILE},
 * {@code PHYSIQUE}), environnement ({@code ENVIRONNEMENT}, + {@code KATON} pour le feu).
 */
public final class PorteDegats implements Listener {

    public static final NamespacedKey PDC_ETIQUETTES = new NamespacedKey("reborn", "etiquettes");

    private final ShinobiCore plugin;
    private final EtatServiceImpl service;
    private final Map<EntityDamageEvent, DegatContexte> contextes = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<UUID, Long> avis = new ConcurrentHashMap<>();
    /** « cible|défense|attaquant » → dernière ligne de journal (une par combat, §7.4). */
    private final Map<String, Long> journalises = new ConcurrentHashMap<>();
    private static volatile PorteDegats instance;

    PorteDegats(ShinobiCore plugin, EtatServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
        instance = this;
    }

    /** Contexte d'un dégât évalué (pour {@code CharacterDamageEvent}), ou null. */
    public static DegatContexte contexte(EntityDamageEvent ev) {
        PorteDegats p = instance;
        return p == null || ev == null ? null : p.contextes.get(ev);
    }

    /* ================================================================ LOW : ciblable, peut frapper */

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCible(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Player cible)) return;
        String cause = ev.getCause().name();
        if (cause.equals("VOID") || cause.equals("KILL") || cause.equals("WORLD_BORDER") || cause.equals("SUICIDE")) return;
        Etat e = service.calculer(cible.getUniqueId());
        if (!e.peut(Capacite.CIBLABLE)) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttaquant(EntityDamageByEntityEvent ev) {
        // Seulement les êtres vivants : un builder qui casse un support d'armure ou un cadre n'est pas un coup.
        if (!(ev.getEntity() instanceof org.bukkit.entity.LivingEntity) || ev.getEntity() instanceof org.bukkit.entity.ArmorStand) return;
        Entity d = ev.getDamager();
        Player a = null;
        Capacite cap = null;
        CastAttributionImpl ca = plugin.castAttributionImpl();
        if (ca != null && ca.attribution(ev) != null) return;          // technique : vérifiée au lancer
        if (d instanceof Player p) {
            a = p;
            String c = ev.getCause().name();
            if (!c.equals("ENTITY_ATTACK") && !c.equals("ENTITY_SWEEP_ATTACK")) return;
            ItemStack main = p.getInventory().getItemInMainHand();
            cap = main.getType().isAir() ? Capacite.FRAPPER_MAINS : Capacite.FRAPPER_ARME;
        } else if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            a = p;
            cap = Capacite.FRAPPER_PROJECTILE;
        }
        if (a == null || a.equals(ev.getEntity())) return;
        var v = service.calculer(a.getUniqueId()).verdict(cap);
        if (v.ok()) return;
        ev.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = avis.get(a.getUniqueId());
        if (v.raison() != null && !v.raison().isEmpty() && (last == null || now - last > 1500)) {
            avis.put(a.getUniqueId(), now);
            a.sendActionBar(Component.text(v.raison(), NamedTextColor.RED));
        }
    }

    /* ================================================================ NORMAL : défenses */

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDefenses(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Player cible)) return;
        Set<Etiquette> et = EnumSet.noneOf(Etiquette.class);
        String technique = null;
        UUID attaquant = null;
        if (ev instanceof EntityDamageByEntityEvent ee) {
            CastAttributionImpl ca = plugin.castAttributionImpl();
            CastAttributionImpl.Attribution at = ca != null ? ca.attribution(ee) : null;
            Entity d = ee.getDamager();
            if (at != null) {
                technique = at.technique();
                attaquant = at.lanceur().getUniqueId();
                etiquettesTechnique(technique, at.nature(), et);
                if (d instanceof Projectile) et.add(Etiquette.PROJECTILE);
            } else if (d instanceof Player p) {
                attaquant = p.getUniqueId();
                et.add(Etiquette.MELEE);
                et.add(Etiquette.PHYSIQUE);
                ItemStack main = p.getInventory().getItemInMainHand();
                if (!main.getType().isAir()) {
                    et.add(Etiquette.ARME);
                    etiquettesArme(main, et);
                }
            } else if (d instanceof Projectile pr) {
                if (pr.getShooter() instanceof Player p) attaquant = p.getUniqueId();
                et.add(Etiquette.PROJECTILE);
                et.add(Etiquette.PHYSIQUE);
            } else {
                et.add(Etiquette.MELEE);
                et.add(Etiquette.PHYSIQUE);
            }
        } else {
            et.add(Etiquette.ENVIRONNEMENT);
            String c = ev.getCause().name();
            if (c.startsWith("FIRE") || c.equals("LAVA") || c.equals("HOT_FLOOR")) et.add(Etiquette.KATON);
            if (c.equals("POISON") || c.equals("WITHER")) et.add(Etiquette.INTERNE);
        }

        double mult = 1.0;
        String defense = null;
        Etiquette contourne = null;
        ModificateurRegistry reg = service.registre();
        if (reg != null) {
            Set<String> conditions = conditions(cible, reg);
            for (ModificateurService.Vue v : reg.actifs(cible.getUniqueId())) {
                TypeModificateur t = reg.type(v.type());
                if (t == null || t.defense() == null) continue;
                Defense.Effet r = t.defense().evaluer(et, conditions);
                switch (r.issue()) {
                    case APPLIQUEE -> {
                        mult *= r.multiplicateur();
                        defense = t.nom();
                        journaliser(cible, attaquant, t.nom(), "défendu par " + t.nom());
                        avisAttaquant(attaquant, t.defense().type() == Defense.Type.INTANGIBLE ? "Traverse" : "Défendu");
                    }
                    case CONTOURNEE -> {
                        mult *= r.multiplicateur();
                        contourne = r.par();
                        journaliser(cible, attaquant, t.nom(), t.nom() + " contourné par " + r.par());
                    }
                    default -> { }
                }
            }
        }
        if (mult <= 0) ev.setCancelled(true);
        else if (Math.abs(mult - 1) > 1e-6) ev.setDamage(ev.getDamage() * mult);
        contextes.put(ev, new DegatContexte(Set.copyOf(et), technique, attaquant, defense, contourne, mult));
    }

    /** Conditions vraies chez la cible (§6.2 : chakra > 0, pas menotté, pas déshydraté). */
    private Set<String> conditions(Player p, ModificateurRegistry reg) {
        Set<String> s = new HashSet<>();
        ShinobiCharacter c = plugin.characters() != null ? plugin.characters().getActive(p.getUniqueId()) : null;
        if (c != null && c.chakra().current() > 0.0001) s.add("CHAKRA_POSITIF");
        if (!reg.actif(p.getUniqueId(), "menottes")) s.add("PAS_MENOTTE");
        if (!reg.actif(p.getUniqueId(), "deshydrate")) s.add("PAS_DESHYDRATE");
        return s;
    }

    private void etiquettesTechnique(String technique, ChakraAffinity nature, Set<Etiquette> et) {
        et.add(Etiquette.TECHNIQUE);
        var reg = plugin.techniques();
        var declarees = reg != null ? reg.etiquettes(technique) : java.util.List.<String>of();
        if (!declarees.isEmpty()) {
            et.addAll(Etiquette.lireTout(declarees));
            return;
        }
        if (nature != null && nature != ChakraAffinity.NONE) {
            Etiquette n = Etiquette.lire(nature.name());
            if (n != null) et.add(n);
        }
        var ab = reg != null ? reg.byId(technique) : null;
        String cat = ab != null ? ab.category() : "";
        if (cat.startsWith("taijutsu") || cat.startsWith("kenjutsu")) {
            et.add(Etiquette.MELEE);
            et.add(Etiquette.PHYSIQUE);
        } else {
            et.add(Etiquette.CHAKRA);
        }
        if (cat.contains("senjutsu")) et.add(Etiquette.SENJUTSU);
        if (cat.contains("fuinjutsu")) et.add(Etiquette.SCELLEMENT);
        if (cat.contains("genjutsu")) et.add(Etiquette.MENTAL);
        for (String part : cat.split("/")) {
            Etiquette n = Etiquette.lire(part.toUpperCase(Locale.ROOT));
            if (n != null && n.ordinal() >= Etiquette.KATON.ordinal() && n.ordinal() <= Etiquette.YANG.ordinal()) et.add(n);
        }
    }

    private static void etiquettesArme(ItemStack arme, Set<Etiquette> et) {
        var meta = arme.getItemMeta();
        if (meta == null) return;
        String s = meta.getPersistentDataContainer().get(PDC_ETIQUETTES, PersistentDataType.STRING);
        if (s == null || s.isBlank()) return;
        for (String x : s.split("[,; ]+")) {
            Etiquette e = Etiquette.lire(x);
            if (e != null) et.add(e);
        }
    }

    private void journaliser(Player cible, UUID attaquant, String defense, String detail) {
        if (!service.journalActif()) return;
        String k = cible.getUniqueId() + "|" + defense + "|" + attaquant;
        long now = System.currentTimeMillis();
        Long last = journalises.get(k);
        if (last != null && now - last < 60_000L) return;
        journalises.put(k, now);
        var e = Journal.entree(Categorie.ETAT, "DEFENSE").cible(cible).detail(detail).lieu(cible.getLocation());
        if (attaquant != null) e.acteur(attaquant);
        e.ecrire();
    }

    private void avisAttaquant(UUID attaquant, String mot) {
        if (attaquant == null) return;
        Player a = org.bukkit.Bukkit.getPlayer(attaquant);
        if (a == null) return;
        long now = System.currentTimeMillis();
        Long last = avis.get(attaquant);
        if (last != null && now - last < 1200) return;
        avis.put(attaquant, now);
        a.sendActionBar(Component.text(mot, NamedTextColor.GRAY));
    }
}
