package plugin;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private final Main plugin;
    private HikariDataSource dataSource;
    private String type;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        setupDataSource();
        init();
    }

    private void setupDataSource() {
        HikariConfig config = new HikariConfig();
        if (type.equals("mysql")) {
            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String db = plugin.getConfig().getString("database.name", "minecraft");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db);
            config.setUsername(plugin.getConfig().getString("database.user", "root"));
            config.setPassword(plugin.getConfig().getString("database.pass", ""));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder() + "/data.db");
            config.setDriverClassName("org.sqlite.JDBC");
        }
        
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setLeakDetectionThreshold(2000);
        
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);
    }

    private synchronized void init() {
        String lightSources = "CREATE TABLE IF NOT EXISTS light_sources (" +
                         (type.equals("mysql") ? "id INT AUTO_INCREMENT PRIMARY KEY," : "id INTEGER PRIMARY KEY AUTOINCREMENT,") +
                         "world VARCHAR(64), x INT, y INT, z INT," +
                         "material VARCHAR(64), placer_name VARCHAR(64), placer_uuid VARCHAR(64), multiplier DOUBLE, time TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";
        String lightLinks = "CREATE TABLE IF NOT EXISTS light_links (" +
                         "source_id INT, x INT, y INT, z INT," +
                         "FOREIGN KEY(source_id) REFERENCES light_sources(id) ON DELETE CASCADE);";
        String worldSettings = "CREATE TABLE IF NOT EXISTS world_settings (" +
                         "world VARCHAR(64), material VARCHAR(64), multiplier DOUBLE, PRIMARY KEY(world, material));";
        String personalSettings = "CREATE TABLE IF NOT EXISTS personal_settings (" +
                         "uuid VARCHAR(64) PRIMARY KEY, multiplier DOUBLE);";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(lightSources);
            stmt.execute(lightLinks);
            stmt.execute(worldSettings);
            stmt.execute(personalSettings);
            
            try {
                stmt.execute("ALTER TABLE light_sources ADD COLUMN material VARCHAR(64);");
            } catch (SQLException ignored) {}
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public void setPersonalMultiplier(String uuid, double mult) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = (type.equals("mysql") ? "INSERT INTO personal_settings (uuid, multiplier) VALUES (?, ?) ON DUPLICATE KEY UPDATE multiplier = ?" : "INSERT OR REPLACE INTO personal_settings (uuid, multiplier) VALUES (?, ?)");
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, uuid);
                pstmt.setDouble(2, mult);
                if (type.equals("mysql")) pstmt.setDouble(3, mult);
                pstmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public Double getPersonalMultiplier(String uuid) {
        String sql = "SELECT multiplier FROM personal_settings WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public void addSource(String world, int x, int y, int z, String material, String name, String uuid, double mult, List<String> links) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO light_sources (world, x, y, z, material, placer_name, placer_uuid, multiplier) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                int sourceId;
                try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setString(1, world);
                    pstmt.setInt(2, x);
                    pstmt.setInt(3, y);
                    pstmt.setInt(4, z);
                    pstmt.setString(5, material);
                    pstmt.setString(6, name);
                    pstmt.setString(7, uuid);
                    pstmt.setDouble(8, mult);
                    pstmt.executeUpdate();
                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) sourceId = rs.getInt(1);
                        else { conn.rollback(); return; }
                    }
                }
                String linkSql = "INSERT INTO light_links (source_id, x, y, z) VALUES (?, ?, ?, ?)";
                try (PreparedStatement lpstmt = conn.prepareStatement(linkSql)) {
                    for (String link : links) {
                        String[] p = link.split(",");
                        lpstmt.setInt(1, sourceId);
                        lpstmt.setInt(2, Integer.parseInt(p[0]));
                        lpstmt.setInt(3, Integer.parseInt(p[1]));
                        lpstmt.setInt(4, Integer.parseInt(p[2]));
                        lpstmt.addBatch();
                    }
                    lpstmt.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public List<String> removeSourceAndGetLinks(String world, int x, int y, int z) {
        List<String> links = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String getSql = "SELECT l.x, l.y, l.z FROM light_links l JOIN light_sources s ON l.source_id = s.id " +
                            "WHERE s.world = ? AND s.x = ? AND s.y = ? AND s.z = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(getSql)) {
                pstmt.setString(1, world);
                pstmt.setInt(2, x);
                pstmt.setInt(3, y);
                pstmt.setInt(4, z);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) links.add(rs.getInt(1) + "," + rs.getInt(2) + "," + rs.getInt(3));
                }
            }
            // 削除処理は即座に行う必要があるため同期的に実行するが、メインスレッドをブロックしすぎないよう最小限にする
            String delSql = "DELETE FROM light_sources WHERE world = ? AND x = ? AND y = ? AND z = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(delSql)) {
                pstmt.setString(1, world);
                pstmt.setInt(2, x);
                pstmt.setInt(3, y);
                pstmt.setInt(4, z);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return links;
    }

    public String getInfo(String world, int x, int y, int z) {
        String sql = "SELECT placer_name, time, multiplier, material FROM light_sources WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, world);
            pstmt.setInt(2, x);
            pstmt.setInt(3, y);
            pstmt.setInt(4, z);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return "§e設置者: §f" + rs.getString(1) + " §7(" + rs.getString(2) + ") §e倍率: §f" + rs.getDouble(3) + " §b[" + rs.getString(4) + "]";
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public void setWorldMultiplier(String world, String mat, double mult) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = (type.equals("mysql") ? "INSERT INTO world_settings (world, material, multiplier) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE multiplier = ?" : "INSERT OR REPLACE INTO world_settings (world, material, multiplier) VALUES (?, ?, ?)");
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, world);
                pstmt.setString(2, mat);
                pstmt.setDouble(3, mult);
                if (type.equals("mysql")) pstmt.setDouble(4, mult);
                pstmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public Double getWorldMultiplier(String world, String mat) {
        String sql = "SELECT multiplier FROM world_settings WHERE world = ? AND material = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, world);
            pstmt.setString(2, mat);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }
    
    public void clearWorldSettings(String world) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "DELETE FROM world_settings WHERE world = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, world);
                pstmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public List<String> searchHistory(String category, String value) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT id, world, x, y, z, material, placer_name, time FROM light_sources ";
        if (category != null && value != null) {
            switch (category.toLowerCase()) {
                case "who": sql += "WHERE placer_name = ? "; break;
                case "where": 
                    String[] parts = value.split(",");
                    List<String> conds = new ArrayList<>();
                    if (parts.length > 0 && !parts[0].isEmpty()) conds.add("x = " + parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) conds.add("y = " + parts[1]);
                    if (parts.length > 2 && !parts[2].isEmpty()) conds.add("z = " + parts[2]);
                    if (!conds.isEmpty()) sql += "WHERE " + String.join(" AND ", conds) + " ";
                    break;
                case "what": sql += "WHERE material = ? "; break;
            }
        }
        sql += "ORDER BY id DESC LIMIT 54";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (category != null && value != null && !category.equals("where")) {
                pstmt.setString(1, value);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(String.format("§8#%d §f%s §7@ §e%s(%d,%d,%d) §b[%s] §7- %s", 
                        rs.getInt(1), rs.getString(7), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6), rs.getString(8)));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return history;
    }

    public Map<String, String> getDistinctPlacers() {
        Map<String, String> placers = new HashMap<>();
        String sql = "SELECT DISTINCT placer_name, placer_uuid FROM light_sources WHERE placer_uuid IS NOT NULL";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement(); 
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) placers.put(rs.getString(1), rs.getString(2));
        } catch (SQLException e) { e.printStackTrace(); }
        return placers;
    }

    public List<String> getDistinctMaterials() {
        List<String> materials = new ArrayList<>();
        String sql = "SELECT DISTINCT material FROM light_sources";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement(); 
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) materials.add(rs.getString(1));
        } catch (SQLException e) { e.printStackTrace(); }
        return materials;
    }

    public List<String> removeSourceById(int id, String[] outLoc) {
        List<String> links = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String getInfo = "SELECT world, x, y, z FROM light_sources WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(getInfo)) {
                pstmt.setInt(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        outLoc[0] = rs.getString(1);
                        outLoc[1] = rs.getInt(2) + "," + rs.getInt(3) + "," + rs.getInt(4);
                    }
                }
            }
            String getSql = "SELECT x, y, z FROM light_links WHERE source_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(getSql)) {
                pstmt.setInt(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) links.add(rs.getInt(1) + "," + rs.getInt(2) + "," + rs.getInt(3));
                }
            }
            String delSql = "DELETE FROM light_sources WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(delSql)) {
                pstmt.setInt(1, id);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return links;
    }
}
