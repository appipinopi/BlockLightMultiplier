package plugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class LanguageManager {
    private final JavaPlugin plugin;
    private final String langCode;
    private FileConfiguration langConfig;
    private final Map<String, String> messages = new HashMap<>();
    private final Map<String, String> blocks = new TreeMap<>();

    public LanguageManager(JavaPlugin plugin, String langCode) {
        this.plugin = plugin;
        this.langCode = langCode.toLowerCase();
        loadLanguage(this.langCode);
    }

    public void loadLanguage(String langCode) {
        File langFolder = new File(plugin.getDataFolder(), "languages");
        if (!langFolder.exists()) langFolder.mkdirs();

        File langFile = new File(langFolder, langCode.toLowerCase() + ".yml");
        if (!langFile.exists()) {
            plugin.saveResource("languages/" + langCode.toLowerCase() + ".yml", false);
        }
        
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        
        messages.clear();
        if (langConfig.contains("messages")) {
            for (String key : langConfig.getConfigurationSection("messages").getKeys(false)) {
                messages.put(key, langConfig.getString("messages." + key));
            }
        }

        blocks.clear();
        if (langConfig.contains("blocks")) {
            for (String key : langConfig.getConfigurationSection("blocks").getKeys(false)) {
                blocks.put(key.toUpperCase(), langConfig.getString("blocks." + key));
            }
        }
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "§c[Message Missing: " + key + "]").replace("&", "§");
    }

    /**
     * ブロックの表示名を返します。
     * 翻訳がない場合は ID を目立たせて表示します。
     */
    public String getBlockName(String blockId) {
        String name = blocks.get(blockId.toUpperCase());
        if (name == null || name.equalsIgnoreCase(blockId)) {
            return "§8[ID: §7" + blockId + "§8] §e(未翻訳)";
        }
        return name;
    }

    public java.util.Set<String> getRegisteredBlockIds() {
        return blocks.keySet();
    }

    public void addBlockIfMissing(String blockId, String defaultName) {
        if (!blocks.containsKey(blockId.toUpperCase())) {
            blocks.put(blockId.toUpperCase(), defaultName);
        }
    }

    public void save() {
        File langFile = new File(plugin.getDataFolder(), "languages/" + langCode + ".yml");
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            langConfig.set("messages." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            langConfig.set("blocks." + entry.getKey(), entry.getValue());
        }
        try {
            langConfig.save(langFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save language file: " + langFile.getName());
        }
    }
}
