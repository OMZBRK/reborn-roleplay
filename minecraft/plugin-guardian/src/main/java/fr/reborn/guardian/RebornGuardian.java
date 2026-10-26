package fr.reborn.guardian;

import fr.reborn.guardian.auth.AttestationPolicy;
import fr.reborn.guardian.auth.AuthSessionState;
import fr.reborn.guardian.auth.PlayTokenVerifier;
import fr.reborn.guardian.auth.ScopeMetadata;
import fr.reborn.guardian.listener.AuthChannelListener;
import fr.reborn.guardian.listener.ClosedModeListener;
import fr.reborn.guardian.listener.PendingAuthListener;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reborn Guardian — plugin Paper qui valide les clients connectes au serveur.
 *
 * <p>Boucle d'attestation (cf {@code PLAN_CONCEPTION_LAUNCHER.md §9.5}) :
 * <ol>
 *   <li>Le launcher fetche un play-token aupres de l'API (signe HMAC-SHA256
 *       avec {@code REBORN_PLAY_TOKEN_SECRET}, le meme secret est lu ici).</li>
 *   <li>Le launcher passe le token a la JVM via sysprop, le mod
 *       Reborn Integrity le lit et l'envoie au serveur via le custom payload
 *       {@code reborn:auth} au moment du JOIN.</li>
 *   <li>Ce plugin recoit le payload, verifie la signature HMAC, l'expiration,
 *       que l'UUID du token matche celui du joueur connecte, la portee
 *       ({@code scope}) et le serveur vise ({@code aud}).</li>
 *   <li>Si la verif passe, le joueur reste et recoit la marque transitoire
 *       {@code reborn.scope} ({@code play} ou {@code douane}). Sinon, kick avec
 *       message actionnable. Si rien n'arrive en 8s, kick automatique.</li>
 * </ol>
 *
 * <p>Le secret est lu depuis l'env var {@code REBORN_PLAY_TOKEN_SECRET}, sinon
 * depuis {@code plugins/RebornGuardian/config.yml} (cle {@code play-token-secret}),
 * pour les hebergeurs qui ne permettent pas de variable d'environnement.
 * Sans secret valide, le plugin ne se desactive plus : il passe en
 * <b>mode ferme</b> (seul le staff entre), sauf si {@code closed-mode: false}.
 * Un {@code config.yml} illisible (YAML invalide) n'est jamais reecrit : mode
 * ferme, sans repli sur les valeurs par defaut.
 *
 * <p>Reference complete (cles, transition, mode ferme) :
 * {@code docs/technique/PLUGINS_REFERENCE.md}, section Guardian.
 */
public final class RebornGuardian extends JavaPlugin {

    private static final String SECRET_ENV = "REBORN_PLAY_TOKEN_SECRET";
    private static final String CONFIG_FILE = "config.yml";

    // Cles de config.yml (valeurs par defaut dans src/main/resources/config.yml).
    private static final String KEY_SECRET = "play-token-secret";
    private static final String KEY_SERVER_ID = "server-id";
    private static final String KEY_ACCEPT_NO_AUD = "accept-tokens-without-aud";
    private static final String KEY_ACCEPT_DOUANE = "accept-douane-tokens";
    private static final String KEY_CLOSED_MODE = "closed-mode";
    private static final String KEY_STAFF_PERMISSIONS = "closed-mode-staff-permissions";

    /** Rappel du mode ferme dans la console : toutes les 10 min. */
    private static final long CLOSED_MODE_REMINDER_TICKS = 20L * 60L * 10L;

    /** Position d'une erreur dans les messages du parseur YAML (« line 14, column 30 »). */
    private static final Pattern YAML_POSITION = Pattern.compile("line (\\d+), column (\\d+)");

    @Override
    public void onEnable() {
        getLogger().info("Reborn Guardian " + getPluginMeta().getVersion() + " démarre.");

        // Cree plugins/RebornGuardian/config.yml depuis les resources si
        // absent (secret vide par defaut). N'ecrit jamais un fichier existant.
        saveDefaultConfig();

        // Un fichier illisible ne doit ni etre complete (saveConfig l'ecraserait
        // avec les valeurs par defaut), ni servir de base : mode ferme.
        String unreadable = findConfigProblem();
        if (unreadable != null) {
            enterUnreadableConfigMode(unreadable);
            return;
        }

        // Complete un ancien fichier avec les cles ajoutees depuis, sans toucher
        // aux valeurs deja posees.
        completeConfig();

        SecretLookup secret = readSecret();
        if (!PlayTokenVerifier.isUsableSecret(secret.value())) {
            enterNoSecretMode(secret);
            return;
        }

        PlayTokenVerifier verifier = new PlayTokenVerifier(secret.value());
        getLogger().info("Secret play-token chargé (source : " + secret.source() + ").");

        FileConfiguration config = getConfig();
        AttestationPolicy.Settings settings = new AttestationPolicy.Settings(
            config.getString(KEY_SERVER_ID, ""),
            readBoolean(config, KEY_ACCEPT_NO_AUD, false),
            readBoolean(config, KEY_ACCEPT_DOUANE, false)
        );
        logSettings(settings);

        AttestationPolicy policy = new AttestationPolicy(verifier, settings);
        AuthSessionState state = new AuthSessionState();

        // Canal entrant — le mod Reborn Integrity envoie ses paquets ici.
        getServer().getMessenger().registerIncomingPluginChannel(
            this,
            AuthChannelListener.CHANNEL,
            new AuthChannelListener(this, policy, state)
        );

        getServer().getPluginManager().registerEvents(
            new PendingAuthListener(this, state),
            this
        );

        getLogger().info("[guardian] canal " + AuthChannelListener.CHANNEL + " enregistré, attestation active.");
    }

    @Override
    public void onDisable() {
        // La marque ne doit pas survivre au plugin qui l'a posee.
        for (Player player : getServer().getOnlinePlayers()) {
            ScopeMetadata.clear(this, player);
        }
        getLogger().info("Reborn Guardian arrêté.");
    }

    /**
     * Secret lu et sa provenance (jamais la valeur dans les journaux). {@code fromEnv} :
     * la variable d'environnement est posee, elle passe avant config.yml.
     */
    private record SecretLookup(String value, String source, boolean fromEnv) {}

    /**
     * Priorite a la variable d'environnement. Si elle est posee mais invalide,
     * on ne retombe PAS sur la config : deux secrets differents ne doivent pas
     * coexister sans que l'admin le voie.
     */
    private SecretLookup readSecret() {
        String fromEnv = System.getenv(SECRET_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return new SecretLookup(fromEnv, "variable d'environnement " + SECRET_ENV, true);
        }
        // FileConfiguration#getString peut renvoyer null si la cle est
        // presente mais vide (`play-token-secret:`), donc on guarde.
        String fromConfig = getConfig().getString(KEY_SECRET);
        String value = fromConfig != null ? fromConfig.trim() : "";
        return new SecretLookup(value, "config.yml, clé " + KEY_SECRET, false);
    }

    /** Pas de secret utilisable : mode ferme (defaut) ou desactivation, toujours tres visible. */
    private void enterNoSecretMode(SecretLookup secret) {
        boolean closedMode = readBoolean(getConfig(), KEY_CLOSED_MODE, true);
        String cause = secret.value() == null || secret.value().isBlank()
            ? "absent (ni variable d'environnement " + SECRET_ENV + ", ni clé " + KEY_SECRET + " dans config.yml)"
            : "trop court dans " + secret.source() + " (" + PlayTokenVerifier.MIN_SECRET_LENGTH
                + " caractères minimum)";

        List<String> banner = new ArrayList<>();
        banner.add("################################################################");
        banner.add("#  REBORN GUARDIAN : SECRET DU JETON DE JEU INVALIDE");
        banner.add("#  Secret " + cause + ".");
        if (closedMode) {
            banner.add("#  MODE FERMÉ : seuls les op et le staff peuvent se connecter.");
        } else {
            banner.add("#  closed-mode: false -> PLUGIN DÉSACTIVÉ : PLUS AUCUN CLIENT N'EST VÉRIFIÉ.");
        }
        String staffAdvice;
        if (secret.fromEnv()) {
            // La variable passe avant config.yml, sans repli : corriger la config ne suffirait pas.
            banner.add("#  Corrige ou retire la variable d'environnement " + SECRET_ENV);
            banner.add("#  du serveur : elle passe avant config.yml. Même valeur que");
            banner.add("#  REBORN_PLAY_TOKEN_SECRET côté API, puis redémarre.");
            staffAdvice = "Corrige ou retire la variable d'environnement " + SECRET_ENV
                + " du serveur, puis redémarre-le.";
        } else {
            banner.add("#  Corrige : plugins/RebornGuardian/config.yml, clé '" + KEY_SECRET + "'");
            banner.add("#  (même valeur que REBORN_PLAY_TOKEN_SECRET côté API), puis redémarre.");
            staffAdvice = "Renseigne " + KEY_SECRET + " dans plugins/RebornGuardian/config.yml"
                + " puis redémarre le serveur.";
        }
        banner.add("################################################################");
        banner.forEach(getLogger()::severe);

        if (!closedMode) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        startClosedMode(
            getConfig().getStringList(KEY_STAFF_PERMISSIONS),
            "secret du jeton de jeu " + cause,
            "secret du jeton de jeu absent ou invalide",
            staffAdvice
        );
    }

    /**
     * config.yml illisible : mode ferme impose ({@code closed-mode} ne peut pas etre lu), fichier
     * laisse tel quel. Les permissions du staff sont celles du jar : elles n'ouvrent qu'au staff.
     * getConfig() n'est pas appele ici : Bukkit recopierait dans la console l'extrait fautif du
     * fichier, qui peut etre la ligne du secret.
     */
    private void enterUnreadableConfigMode(String problem) {
        List<String> banner = new ArrayList<>();
        banner.add("################################################################");
        banner.add("#  REBORN GUARDIAN : config.yml ILLISIBLE (" + problem + ")");
        banner.add("#  Le fichier n'a PAS été modifié, et aucune valeur par défaut");
        banner.add("#  ne remplace la tienne (secret, server-id, transition, douane).");
        banner.add("#  MODE FERMÉ : seuls les op et le staff peuvent se connecter.");
        banner.add("#  Corrige plugins/RebornGuardian/config.yml : une valeur qui");
        banner.add("#  contient « : » ou « # » doit être entre guillemets. Puis redémarre.");
        banner.add("################################################################");
        banner.forEach(getLogger()::severe);

        startClosedMode(
            defaultStaffPermissions(),
            "config.yml illisible (" + problem + ")",
            "config.yml illisible",
            "Corrige plugins/RebornGuardian/config.yml (une valeur avec « : » ou « # » se met entre"
                + " guillemets) puis redémarre le serveur."
        );
    }

    private void startClosedMode(List<String> staffPermissions, String reminderCause, String reason,
                                 String staffAdvice) {
        ClosedModeListener listener = new ClosedModeListener(this, staffPermissions, reason, staffAdvice);
        getServer().getPluginManager().registerEvents(listener, this);
        listener.applyToOnlinePlayers();
        getServer().getScheduler().runTaskTimer(
            this,
            () -> getLogger().severe("[guardian] MODE FERMÉ toujours actif : " + reminderCause + "."),
            CLOSED_MODE_REMINDER_TICKS,
            CLOSED_MODE_REMINDER_TICKS
        );
    }

    /** Permissions du staff livrees dans le jar (config.yml par defaut), sans lire le fichier du serveur. */
    private List<String> defaultStaffPermissions() {
        InputStream resource = getResource(CONFIG_FILE);
        if (resource == null) return List.of();
        try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader).getStringList(KEY_STAFF_PERMISSIONS);
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Relit config.yml avec un parseur YAML qui signale les erreurs (getConfig() les avale et
     * rend une configuration vide). Renvoie {@code null} si le fichier est lisible ou absent,
     * sinon la nature et la position de l'erreur, sans recopier le contenu du fichier, qui peut
     * contenir le secret.
     */
    private String findConfigProblem() {
        File file = new File(getDataFolder(), CONFIG_FILE);
        if (!file.isFile()) return null;
        try {
            new YamlConfiguration().load(file);
            return null;
        } catch (InvalidConfigurationException e) {
            return "YAML invalide" + yamlPosition(e);
        } catch (IOException e) {
            return "lecture impossible : " + e.getClass().getSimpleName();
        }
    }

    /** « , ligne 14, colonne 30 » d'apres le message du parseur, ou vide s'il n'en donne pas. */
    private static String yamlPosition(Throwable error) {
        String found = "";
        for (Throwable t = error; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message == null) continue;
            Matcher m = YAML_POSITION.matcher(message);
            // La derniere position citee est celle du probleme (la premiere peut etre le contexte).
            while (m.find()) {
                found = ", ligne " + m.group(1) + ", colonne " + m.group(2);
            }
            if (!found.isEmpty()) return found;
        }
        return found;
    }

    /**
     * Lit une cle booleenne. {@code getBoolean} rendrait la valeur par defaut des que la valeur
     * n'est pas un vrai booleen YAML ({@code 'false'} entre guillemets, faute de frappe) : une
     * transition crue fermee resterait ouverte. Ici, une valeur presente mais invalide est
     * signalee en SEVERE et remplacee par {@code strictest}, la valeur la plus prudente.
     */
    private boolean readBoolean(Configuration config, String key, boolean strictest) {
        Object raw = config.get(key);
        if (raw instanceof Boolean value) return value;
        String found = raw == null ? "absente" : describeInvalid(raw);
        getLogger().severe("[guardian] config.yml, clé '" + key + "' : valeur invalide (" + found
            + "), booléen attendu : true ou false, sans guillemets. Valeur retenue : " + strictest + ".");
        return strictest;
    }

    private static String describeInvalid(Object raw) {
        if (raw instanceof String text) {
            String shown = text.length() > 40 ? text.substring(0, 40) + "…" : text;
            return "texte '" + shown + "'";
        }
        return raw.getClass().getSimpleName();
    }

    private void logSettings(AttestationPolicy.Settings settings) {
        if (settings.audChecked()) {
            getLogger().info("[guardian] server-id : " + settings.serverId()
                + " — jetons sans aud : " + (settings.acceptTokensWithoutAud() ? "acceptés (transition)" : "refusés"));
        } else {
            getLogger().warning("[guardian] server-id vide : la claim aud n'est pas contrôlée et la douane est "
                + "refusée. Renseigne server-id (même valeur que REBORN_SERVER_ID côté API).");
        }
        getLogger().info("[guardian] portée douane : "
            + (settings.acceptDouaneTokens() ? "acceptée" : "refusée (accept-douane-tokens: false)"));
    }

    /**
     * Ajoute a un config.yml existant et lisible les cles apparues depuis sa creation.
     * Les valeurs deja posees (dont le secret) ne sont jamais modifiees. N'est appele
     * qu'apres {@link #findConfigProblem()} : un fichier illisible n'est jamais reecrit.
     */
    private void completeConfig() {
        FileConfiguration config = getConfig();
        Configuration defaults = config.getDefaults();
        if (defaults == null) return;
        List<String> missing = new ArrayList<>();
        for (String key : defaults.getKeys(false)) {
            if (!config.isSet(key)) missing.add(key);
        }
        if (missing.isEmpty()) return;
        config.options().copyDefaults(true);
        saveConfig();
        getLogger().info("config.yml complété avec : " + String.join(", ", missing) + ".");
    }
}
