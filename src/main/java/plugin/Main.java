package plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Main extends JavaPlugin implements TabCompleter, Listener {

    private static final Logger LOGGER = Logger.getLogger("Minecraft");
    private LanguageManager langManager;
    private VersionHandler versionHandler;
    private DatabaseManager dbManager;

    private int majorVersion;
    private int minorVersion;

    private final Map<UUID, org.bukkit.Location> dynamicLights = new HashMap<>();

    private SettingsGUI settingsGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        copyDefaultLanguages();
        
        this.dbManager = new DatabaseManager(this);
        parseVersion();

        String langCode = getConfig().getString("language", "jp");
        this.langManager = new LanguageManager(this, langCode);

        // バージョンに応じたハンドラーの初期化
        setupVersionHandler();
        this.settingsGUI = new SettingsGUI(this);

        getServer().getPluginManager().registerEvents(this, this);
        
        getCommand("lit").setExecutor(this);
        getCommand("lit").setTabCompleter(this);

        LOGGER.info(langManager.getMessage("enabled"));
    }

    @Override
    public void onDisable() {
        if (dbManager != null) {
            dbManager.close();
        }
        LOGGER.info(langManager.getMessage("disabled"));
    }

    private void parseVersion() {
        try {
            String versionStr = Bukkit.getBukkitVersion().split("-")[0];
            String[] parts = versionStr.split("\\.");
            majorVersion = Integer.parseInt(parts[0]);
            minorVersion = Integer.parseInt(parts[1]);
            LOGGER.info("Detected Minecraft version: " + majorVersion + "." + minorVersion);
        } catch (Exception e) {
            majorVersion = 1;
            minorVersion = 21;
        }
    }

    private void setupVersionHandler() {
        // 1.20.5 以降は 1.21 と同様のハンドラーを使用 (InventoryView等の変更対応)
        boolean isNewVersion = (majorVersion > 1) || (majorVersion == 1 && minorVersion > 20) || 
                               (majorVersion == 1 && minorVersion == 20 && Bukkit.getBukkitVersion().contains("R0.5"));

        if (isNewVersion) {
            this.versionHandler = new VersionHandler_1_21(this);
            LOGGER.info("Using VersionHandler for 1.20.5+ / 1.21");
        } else if (majorVersion == 1 && minorVersion >= 17) {
            this.versionHandler = new VersionHandler_Legacy(this);
            LOGGER.info("Using VersionHandler for 1.17 - 1.20.4");
        } else {
            this.versionHandler = new VersionHandler_Legacy(this);
            LOGGER.warning("This Minecraft version (" + majorVersion + "." + minorVersion + ") is not fully supported. Light block logic requires 1.17+.");
        }
    }

    private void copyDefaultLanguages() {
        String[] langs = {"jp.yml", "en.yml", "ch.yml", "ru.yml"};
        File langFolder = new File(getDataFolder(), "languages");
        if (!langFolder.exists()) langFolder.mkdirs();
        for (String lang : langs) {
            File file = new File(langFolder, lang);
            if (!file.exists()) saveResource("languages/" + lang, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material mat = block.getType();
        
        if (versionHandler.getBaseLight(mat) > 0) {
            double multiplier = getMultiplier(block.getWorld(), mat);
            versionHandler.applyLightAt(block, multiplier, true, event.getPlayer());
        }
    }

    // ... (getMultiplier methods are here)

    public double getMultiplier(World world, Material mat) {
        Double dbMult = dbManager.getWorldMultiplier(world.getName(), mat.name());
        if (dbMult != null) return dbMult;
        
        if (world.getEnvironment() == World.Environment.THE_END) {
            return getConfig().getDouble("global_multipliers." + mat.name(), 2.0);
        }
        return getConfig().getDouble("global_multipliers." + mat.name(), 1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        versionHandler.removeLightAt(block);

        // 周囲5ブロック以内のライトブロックをチェックして、光源がなくなっていれば削除
        int radius = 5;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block nearby = block.getRelative(x, y, z);
                    if (nearby.getType() == Material.LIGHT) {
                        versionHandler.checkAndRemoveLightAt(nearby);
                    }
                }
            }
        }
    }

    private final org.bukkit.NamespacedKey TOOL_KEY = new org.bukkit.NamespacedKey(this, "tool_type");

    @EventHandler
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return;
        
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (hand != null && !hand.getType().isAir() && hand.hasItemMeta()) {
            ItemMeta meta = hand.getItemMeta();
            Integer toolType = meta.getPersistentDataContainer().get(TOOL_KEY, org.bukkit.persistence.PersistentDataType.INTEGER);
            
            // 旧方式のフォールバック (1.21では displayName の取得に注意が必要)
            String name = meta.hasDisplayName() ? meta.getDisplayName() : "";
            
            // 右クリック処理
            if (event.getAction().name().contains("RIGHT_CLICK")) {
                if ((toolType != null && toolType == 0) || name.contains("GUI Opener") || name.contains("GUI オープナー")) {
                    event.setCancelled(true);
                    settingsGUI.open(event.getPlayer(), 0);
                    return;
                } else if ((toolType != null && toolType == 1) || name.contains("Inverter") || name.contains("インバーター")) {
                    if (event.getClickedBlock() != null && getVersionHandler().getBaseLight(event.getClickedBlock().getType()) > 0) {
                        event.setCancelled(true);
                        toggleBlockMultiplier(event.getPlayer(), event.getClickedBlock());
                        return;
                    }
                } else if ((toolType != null && toolType == 2) || name.contains("Data Log") || name.contains("データログ")) {
                    event.setCancelled(true);
                    settingsGUI.openHistory(event.getPlayer(), null, null);
                    return;
                } else if ((toolType != null && toolType == 3) || name.contains("Eraser") || name.contains("イレイザー")) {
                    event.setCancelled(true);
                    versionHandler.clearNearbyLight(event.getPlayer(), event.getPlayer().getLocation().getBlock(), 15);
                    return;
                }
            }
            
            // 左クリック処理
            if (event.getAction().name().contains("LEFT_CLICK_BLOCK")) {
                if ((toolType != null && toolType == 3) || name.contains("Eraser") || name.contains("イレイザー")) {
                    event.setCancelled(true);
                    versionHandler.clearNearbyLight(event.getPlayer(), event.getClickedBlock(), 15);
                    return;
                }
            }
        }

        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            if (event.getPlayer().getInventory().getItemInMainHand().getType().isBlock() && !event.getPlayer().isSneaking()) return;
            
            Block b = event.getClickedBlock();
            if (b == null) return;
            
            String info = dbManager.getInfo(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            if (info != null) {
                event.getPlayer().sendMessage("§6[BlockLight Info]");
                event.getPlayer().sendMessage(info);
            }
        }
    }

    private void toggleBlockMultiplier(Player player, Block block) {
        Material mat = block.getType();
        double current = getMultiplier(block.getWorld(), mat);
        double next;
        String status;
        if (current != 1.0) {
            next = 1.0;
            status = "§c無効 (1.0x)";
        } else {
            next = getConfig().getDouble("global_multipliers." + mat.name(), 2.0);
            status = "§a有効 (" + next + "x)";
        }
        dbManager.setWorldMultiplier(block.getWorld().getName(), mat.name(), next);
        versionHandler.refreshNearbyLight(player, mat, next);
        player.sendMessage("§e" + mat.name() + " を " + status + " §eに切り替えました。");
    }

    private void toggleDynamicLighting(Player player) {
        boolean current = getConfig().getBoolean("dynamic_lighting.enabled", true);
        getConfig().set("dynamic_lighting.enabled", !current);
        saveConfig();
        String status = !current ? "§a有効" : "§c無効";
        player.sendMessage("§eダイナミックライトを " + status + " §eに切り替えました。");
        if (current) removeDynamicLight(player);
    }

    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) return;
        Player player = event.getPlayer();
        
        // 自動クリーンアップ: プレイヤーの周囲5ブロックのライトブロックをチェック
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) { // エンドは負荷軽減のため除外など必要なら調整
             Block toBlock = event.getTo().getBlock();
             versionHandler.checkAndRemoveLightAt(toBlock);
             // 上下左右もチェック
             versionHandler.checkAndRemoveLightAt(toBlock.getRelative(0, 1, 0));
             versionHandler.checkAndRemoveLightAt(toBlock.getRelative(0, -1, 0));
        }

        if (!getConfig().getBoolean("dynamic_lighting.enabled", false)) return;

        Material hand = player.getInventory().getItemInMainHand().getType();
        List<String> items = getConfig().getStringList("dynamic_lighting.items");
        if (items.contains(hand.name())) {
            updateDynamicLight(player, event.getTo().getBlock());
        } else {
            removeDynamicLight(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        removeDynamicLight(event.getPlayer());
    }

    private void updateDynamicLight(Player player, Block newBlock) {
        removeDynamicLight(player);
        double mult = getConfig().getDouble("dynamic_lighting.multiplier", 1.2);
        versionHandler.applyLightAt(newBlock, mult, false, player);
        dynamicLights.put(player.getUniqueId(), newBlock.getLocation());
    }

    private void removeDynamicLight(Player player) {
        org.bukkit.Location loc = dynamicLights.remove(player.getUniqueId());
        if (loc != null) {
            versionHandler.removeLightAt(loc.getBlock());
        }
    }
    
    // パーソナルライト設定用コマンド
    private void handlePersonalCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(langManager.getMessage("usage")); return; }
        Player player = (Player) sender;
        if (args.length < 2) { 
            Double current = dbManager.getPersonalMultiplier(player.getUniqueId().toString());
            player.sendMessage("§e現在のパーソナル倍率: " + (current != null ? current : "未設定 (デフォルト)"));
            return; 
        }
        try {
            double mult = Double.parseDouble(args[1]);
            if (mult < 0.0 || mult > 5.0) { player.sendMessage(langManager.getMessage("invalid_number")); return; }
            dbManager.setPersonalMultiplier(player.getUniqueId().toString(), mult);
            player.sendMessage("§aパーソナル倍率を " + mult + " に設定しました。再ログインまたはエリア移動で適用されます。");
        } catch (NumberFormatException e) { player.sendMessage(langManager.getMessage("invalid_number")); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(langManager.getMessage("usage"));
            return true;
        }
        String firstArg = args[0].toLowerCase();
        
        if (firstArg.equals("gui") && sender instanceof Player) {
            if (!sender.hasPermission("lit.gui") && !sender.hasPermission("lit.admin")) {
                sender.sendMessage(langManager.getMessage("no_permission"));
                return true;
            }
            settingsGUI.open((Player) sender, 0);
            return true;
        } else if (firstArg.equals("lang")) {
            handleLangCommand(sender, args);
            return true;
        } else if (firstArg.equals("reload")) {
            reloadConfig();
            langManager.loadLanguage(getConfig().getString("language", "jp"));
            sender.sendMessage("§a" + langManager.getMessage("config_reloaded"));
            return true;
        } else if (firstArg.equals("list")) {
            handleListCommand(sender);
            return true;
        } else if (firstArg.equals("clear") && sender instanceof Player) {
            if (!sender.hasPermission("lit.admin")) {
                sender.sendMessage(langManager.getMessage("no_permission"));
                return true;
            }
            Player p = (Player) sender;
            versionHandler.clearNearbyLight(p, p.getLocation().getBlock(), 15);
            return true;
        }
 else if (firstArg.equals("block")) {
            handleBlockSubCommand(sender, args);
            return true;
        }
        
        // サブコマンドに一致しない場合、直接 /lit <material> <mult> として処理を試みる
        if (args.length >= 2) {
            handleDirectBlockCommand(sender, args);
            return true;
        }

        sender.sendMessage(langManager.getMessage("usage"));
        return true;
    }

    private void handleDirectBlockCommand(CommandSender sender, String[] args) {
        // block サブコマンドを補完した形で再利用
        String[] newArgs = new String[args.length + 1];
        newArgs[0] = "block";
        System.arraycopy(args, 0, newArgs, 1, args.length);
        handleBlockSubCommand(sender, newArgs);
    }

    private void handleBlockSubCommand(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(langManager.getMessage("usage")); return; }
        String worldName = null;
        String id;
        String multStr;
        if (Bukkit.getWorld(args[1]) != null) {
            if (args.length < 4) { sender.sendMessage(langManager.getMessage("usage")); return; }
            worldName = args[1];
            id = args[2].toUpperCase();
            multStr = args[3];
        } else {
            id = args[1].toUpperCase();
            multStr = args[2];
        }
        Material mat = Material.matchMaterial(id);
        if (mat == null || mat == Material.LIGHT) { sender.sendMessage(langManager.getMessage("invalid_block")); return; }
        
        // 詳細な権限チェック
        if (!sender.hasPermission("lit.admin") && !sender.hasPermission("lit.block." + mat.name().toLowerCase())) {
            sender.sendMessage("§c権限がありません！ この操作には §e lit.block." + mat.name().toLowerCase() + " §cの権限が必要です。");
            return;
        }

        try {
            double mult = Double.parseDouble(multStr);
            if (mult < 0.0 || mult > 16.0) { sender.sendMessage(langManager.getMessage("invalid_number")); return; }
            String displayWorld = (worldName != null) ? worldName : "Global";
            
            final String finalWorldName = worldName;
            final double finalMult = mult;
            final Material finalMat = mat;

            // データベース操作の非同期化
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                if (finalWorldName != null) dbManager.setWorldMultiplier(finalWorldName, finalMat.name(), finalMult);
                else { getConfig().set("global_multipliers." + finalMat.name(), finalMult); saveConfig(); }
                
                // メインスレッドでのライト更新
                Bukkit.getScheduler().runTask(this, () -> {
                    if (sender instanceof Player) versionHandler.refreshNearbyLight((Player) sender, finalMat, finalMult);
                    sender.sendMessage(String.format(langManager.getMessage("success"), displayWorld, langManager.getBlockName(finalMat.name()), String.valueOf(finalMult)));
                });
            });
        } catch (NumberFormatException e) { sender.sendMessage(langManager.getMessage("invalid_number")); }
    }

    private void handleLangCommand(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("gui") && sender instanceof Player) {
            settingsGUI.openLanguageMenu((Player) sender);
            return;
        }
        if (args.length < 2) return;
        String newLang = args[1].toLowerCase();
        if (!Arrays.asList("jp", "en", "ch", "ru").contains(newLang)) return;
        getConfig().set("language", newLang);
        saveConfig();
        langManager.loadLanguage(newLang);
        sender.sendMessage(langManager.getMessage("language_changed"));
    }

    private void handleListCommand(CommandSender sender) {
        sender.sendMessage("§e=== BlockLightMultiplier List ===");
        if (getConfig().contains("global_multipliers")) {
            for (String key : getConfig().getConfigurationSection("global_multipliers").getKeys(false)) {
                sender.sendMessage("§f- [Global] " + langManager.getBlockName(key) + "§7: §e" + getConfig().getDouble("global_multipliers." + key) + " 倍");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("block", "gui", "lang", "reload", "list", "clear").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args[0].equalsIgnoreCase("block")) {
            if (args.length == 2) {
                List<String> suggestions = new ArrayList<>();
                suggestions.addAll(Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList()));
                List<String> excluded = getConfig().getStringList("excluded_from_tab_complete");
                suggestions.addAll(versionHandler.getLuminousMaterials().stream()
                    .filter(m -> !excluded.contains(m.name()))
                    .map(m -> m.name().toLowerCase())
                    .collect(Collectors.toList()));
                return suggestions.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (args.length == 3) {
                if (Bukkit.getWorld(args[1]) != null) {
                    List<String> excluded = getConfig().getStringList("excluded_from_tab_complete");
                    return versionHandler.getLuminousMaterials().stream()
                        .filter(m -> !excluded.contains(m.name()))
                        .map(m -> m.name().toLowerCase())
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                }
                return Arrays.asList("0.5", "1.0", "1.5", "2.0").stream().filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
            }
            if (args.length == 4 && Bukkit.getWorld(args[1]) != null) {
                return Arrays.asList("0.5", "1.0", "1.5", "2.0").stream().filter(s -> s.startsWith(args[3])).collect(Collectors.toList());
            }
        }
        if (args[0].equalsIgnoreCase("lang")) {
            if (args.length == 2) {
                return Arrays.asList("jp", "en", "ch", "ru", "gui").stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

    public ItemStack getToolItem(int type) {
        if (type == 0) {
            ItemStack item = new ItemStack(Material.COMPASS);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(langManager.getMessage("item_gui_opener_name"));
            meta.setLore(Arrays.asList(langManager.getMessage("item_gui_opener_lore")));
            meta.getPersistentDataContainer().set(TOOL_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
            item.setItemMeta(meta);
            return item;
        } else if (type == 1) {
            ItemStack item = new ItemStack(Material.STICK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(langManager.getMessage("item_inverter_name"));
            meta.setLore(Arrays.asList(langManager.getMessage("item_inverter_lore")));
            meta.getPersistentDataContainer().set(TOOL_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
            return item;
        } else if (type == 2) {
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(langManager.getMessage("item_data_log_name"));
            meta.setLore(Arrays.asList(langManager.getMessage("item_data_log_lore")));
            meta.getPersistentDataContainer().set(TOOL_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, 2);
            item.setItemMeta(meta);
            return item;
        } else {
            ItemStack item = new ItemStack(Material.GOLDEN_AXE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(langManager.getMessage("item_eraser_name"));
            String loreStr = langManager.getMessage("item_eraser_lore");
            meta.setLore(Arrays.asList(loreStr.split("\n")));
            meta.getPersistentDataContainer().set(TOOL_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, 3);
            item.setItemMeta(meta);
            return item;
        }
    }

    public LanguageManager getLangManager() { return langManager; }
    public VersionHandler getVersionHandler() { return versionHandler; }
    public DatabaseManager getDatabaseManager() { return dbManager; }
}
