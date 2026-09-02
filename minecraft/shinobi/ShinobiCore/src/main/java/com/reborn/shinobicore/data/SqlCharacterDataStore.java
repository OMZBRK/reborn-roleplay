package com.reborn.shinobicore.data;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/**
 * Embedded-SQL per-character store — same record hooks and lifecycle
 * as {@link YamlCharacterDataStore}, but rows in an H2 table instead
 * of one file per character: {@code (character_id VARCHAR(36) PRIMARY
 * KEY, record TEXT)} where {@code record} is the record's YAML
 * serialization. Cheap on the 3 GB host (pure-Java H2, one shared
 * database file per owning plugin under {@code <dataFolder>/data/store.h2}).
 *
 * <p>This is the backend NEW query-shaped modules (missions, letters,
 * leaderboards, housing) opt into via {@link DataStores}; existing
 * YAML stores are untouched and no data is migrated. {@code query()}
 * is still a full scan at this seam — when a consumer needs indexed
 * lookups, widen the seam with a typed query, don't bypass it.
 *
 * @param <T> the per-character record type
 */
public abstract class SqlCharacterDataStore<T extends CharacterData>
        extends CharacterDataStore<T> {

    private final String table;
    private Connection connection;

    /**
     * @param plugin owning plugin
     * @param table  table name for this store, letters/underscores only
     *               (e.g. {@code "missions"})
     */
    protected SqlCharacterDataStore(JavaPlugin plugin, String table) {
        super(plugin);
        if (!table.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid table name: " + table);
        }
        this.table = table.toLowerCase(Locale.ROOT);
    }

    @Override
    public void start() {
        try {
            openConnection();
        } catch (SQLException ex) {
            plugin().getLogger().severe("Could not open H2 store '" + table
                    + "': " + ex.getMessage());
        }
        super.start();
    }

    @Override
    public void stop() {
        super.stop();   // flushes dirty records through the live connection
        if (connection != null) {
            try { connection.close(); }
            catch (SQLException ignore) { }
            connection = null;
        }
    }

    private void openConnection() throws SQLException {
        if (connection != null) return;
        File dir = new File(plugin().getDataFolder(), "data");
        dir.mkdirs();
        // Instantiate the (relocated) driver directly and connect through
        // it — immune to DriverManager registration/relocation issues.
        String url = "jdbc:h2:" + new File(dir, "store").getAbsolutePath()
                + ";MODE=LEGACY";
        connection = new org.h2.Driver().connect(url, new Properties());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + table
                    + " (character_id VARCHAR(36) PRIMARY KEY, record TEXT NOT NULL)");
        }
    }

    private Connection conn() throws SQLException {
        openConnection();
        return connection;
    }

    /* ------------------------------------------------------ backend hooks */

    @Override
    protected YamlConfiguration loadRaw(UUID characterId) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT record FROM " + table + " WHERE character_id = ?")) {
            ps.setString(1, characterId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                YamlConfiguration yml = new YamlConfiguration();
                yml.loadFromString(rs.getString(1));
                return yml;
            }
        } catch (SQLException | InvalidConfigurationException ex) {
            plugin().getLogger().warning("Could not load " + table + "/"
                    + characterId + ": " + ex.getMessage());
            return null;
        }
    }

    @Override
    protected void writeRaw(UUID characterId, YamlConfiguration yml)
            throws IOException {
        try (PreparedStatement ps = conn().prepareStatement(
                "MERGE INTO " + table + " (character_id, record) VALUES (?, ?)")) {
            ps.setString(1, characterId.toString());
            ps.setString(2, yml.saveToString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    @Override
    protected boolean deleteRaw(UUID characterId) {
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM " + table + " WHERE character_id = ?")) {
            ps.setString(1, characterId.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            plugin().getLogger().warning("Could not delete " + table + "/"
                    + characterId + ": " + ex.getMessage());
            return false;
        }
    }

    @Override
    protected boolean existsRaw(UUID characterId) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT 1 FROM " + table + " WHERE character_id = ?")) {
            ps.setString(1, characterId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            return false;
        }
    }

    @Override
    protected List<UUID> listRawKeys() {
        List<UUID> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT character_id FROM " + table);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    out.add(UUID.fromString(rs.getString(1)));
                } catch (IllegalArgumentException ignore) { }
            }
        } catch (SQLException ex) {
            plugin().getLogger().warning("Could not list " + table + ": "
                    + ex.getMessage());
        }
        return out;
    }
}
