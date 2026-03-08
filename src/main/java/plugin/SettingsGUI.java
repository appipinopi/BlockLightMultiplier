package plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.BlockDataMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SettingsGUI implements Listener {

    private final Main plugin;
    private final String titlePrefix = "§0BlockLight Settings - Page ";
    private final String detailTitle = "§0Detail: ";
    private final String langTitle = "§0Language Settings";
    private final String historyTitle = "§0History: ";
    private final String whoTitle = "§0Select Player (Who)";
    private final String whatTitle = "§0Select Block (What)";

    public SettingsGUI(Main plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, int page) {
        List<Material> allMaterials = plugin.getVersionHandler().getLuminousMaterials();
        List<Material> itemsToShow = new ArrayList<>();
        for (Material m : allMaterials) {
            if (m != null && m.isItem()) itemsToShow.add(m);
        }

        int maxPerPage = 28;
        int totalPages = (int) Math.ceil((double) itemsToShow.size() / maxPerPage);
        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        Inventory inv = Bukkit.createInventory(null, 54, titlePrefix + (page + 1));

        ItemStack border = createButton(" ", Material.GRAY_STAINED_GLASS_PANE, null);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        int start = page * maxPerPage;
        int end = Math.min(start + maxPerPage, itemsToShow.size());
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        for (int i = 0; i < (end - start); i++) {
            inv.setItem(slots[i], createItem(player, itemsToShow.get(start + i)));
        }

        if (page > 0) inv.setItem(45, createButton("§e前のページ", Material.ARROW, null));
        inv.setItem(48, createButton("§7Page " + (page + 1) + " / " + (totalPages == 0 ? 1 : totalPages), Material.PAPER, null));
        inv.setItem(49, createButton("§cこのワールドの全設定をリセット", Material.TNT, null));
        inv.setItem(47, createButton("§a言語設定を開く", Material.BOOK, null));
        inv.setItem(51, createButton("§6設置履歴を表示", Material.CLOCK, null));
        inv.setItem(50, createButton("§b特殊アイテムを取得", Material.NETHER_STAR, null));
        if (page < totalPages - 1) inv.setItem(53, createButton("§e次のページ", Material.ARROW, null));

        player.openInventory(inv);
    }

    public void openDetail(Player player, Material mat) {
        Inventory inv = Bukkit.createInventory(null, 27, detailTitle + mat.name());
        inv.setItem(13, createItem(player, mat));
        inv.setItem(4, createButton("§a+1.0", Material.OAK_STAIRS, org.bukkit.block.BlockFace.NORTH));
        inv.setItem(22, createButton("§c-1.0", Material.OAK_STAIRS, org.bukkit.block.BlockFace.SOUTH));
        inv.setItem(14, createButton("§b+0.1", Material.OAK_STAIRS, org.bukkit.block.BlockFace.EAST));
        inv.setItem(12, createButton("§d-0.1", Material.OAK_STAIRS, org.bukkit.block.BlockFace.WEST));
        inv.setItem(16, createButton("§71.0 にリセット", Material.REDSTONE, null));
        inv.setItem(18, createButton("§7戻る", Material.ARROW, null));
        player.openInventory(inv);
    }

    public void openLanguageMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, langTitle);
        String current = " §e(現在適用中)";
        String code = plugin.getConfig().getString("language", "jp");

        inv.setItem(1, createButton("§f日本語 (jp)" + (code.equals("jp") ? current : ""), Material.WHITE_BANNER, null));
        inv.setItem(3, createButton("§fEnglish (en)" + (code.equals("en") ? current : ""), Material.BLUE_BANNER, null));
        inv.setItem(5, createButton("§fChinese (ch)" + (code.equals("ch") ? current : ""), Material.RED_BANNER, null));
        inv.setItem(7, createButton("§fRussian (ru)" + (code.equals("ru") ? current : ""), Material.BLACK_BANNER, null));
        inv.setItem(0, createButton("§7戻る", Material.ARROW, null));
        player.openInventory(inv);
    }

    public void openHistory(Player player, String category, String value) {
        String filter = (category == null) ? "All" : category + "=" + value;
        Inventory inv = Bukkit.createInventory(null, 54, historyTitle + filter);
        ItemStack border = createButton(" ", Material.GRAY_STAINED_GLASS_PANE, null);
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        List<String> history = plugin.getDatabaseManager().searchHistory(category, value);
        for (String record : history) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(record);
            List<String> lore = new ArrayList<>();
            lore.add("§7クリックでこの光源をUndo (削除)");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.addItem(item);
        }

        inv.setItem(46, createButton("§bWho (Player Head)", Material.PLAYER_HEAD, null));
        inv.setItem(47, createButton("§aWhere (X,Y,Z)", Material.GRASS_BLOCK, null));
        inv.setItem(48, createButton("§eWhat (Block Icon)", Material.TORCH, null));
        inv.setItem(49, createButton("§fReset Filter", Material.MILK_BUCKET, null));
        inv.setItem(45, createButton("§7戻る", Material.ARROW, null));
        player.openInventory(inv);
    }

    public void openWhoSearch(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, whoTitle);
        Map<String, String> placers = plugin.getDatabaseManager().getDistinctPlacers();
        for (Map.Entry<String, String> entry : placers.entrySet()) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            try {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(entry.getValue())));
            } catch (Exception e) {}
            meta.setDisplayName("§b" + entry.getKey());
            head.setItemMeta(meta);
            inv.addItem(head);
        }
        inv.setItem(45, createButton("§7戻る", Material.ARROW, null));
        player.openInventory(inv);
    }

    public void openWhatSearch(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, whatTitle);
        List<String> materials = plugin.getDatabaseManager().getDistinctMaterials();
        for (String matName : materials) {
            Material mat = Material.getMaterial(matName);
            if (mat != null && mat.isItem()) {
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§e" + matName);
                item.setItemMeta(meta);
                inv.addItem(item);
            }
        }
        inv.setItem(45, createButton("§7戻る", Material.ARROW, null));
        player.openInventory(inv);
    }

    public void openToolsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "§0Special Tools");
        inv.setItem(1, plugin.getToolItem(0));
        inv.setItem(3, plugin.getToolItem(1));
        inv.setItem(5, plugin.getToolItem(2));
        inv.setItem(7, plugin.getToolItem(3));
        inv.setItem(0, createButton("§7戻る", Material.ARROW, null));
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        
        // --- 1.21 以前と以降のバイナリ互換性を吸収してタイトルを取得 ---
        String vTitle = ReflectionUtils.getInventoryTitle(event);

        // プラグインが管理しているGUIであることを確認
        boolean isOurGui = vTitle.startsWith(titlePrefix) || vTitle.startsWith(detailTitle) || 
                          vTitle.equals(langTitle) || vTitle.startsWith(historyTitle) || 
                          vTitle.equals("§0Special Tools") || vTitle.equals(whoTitle) || 
                          vTitle.equals(whatTitle);

        if (!isOurGui) return;

        // --- アイテムの奪取を最優先で防止 ---
        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        
        // 背景用のパネルをクリックした場合は何もしない
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        
        Player player = (Player) event.getWhoClicked();
        
        if (vTitle.startsWith(titlePrefix)) {
            int currentPage = Integer.parseInt(vTitle.substring(titlePrefix.length())) - 1;
            String btnName = event.getCurrentItem().getItemMeta().getDisplayName();
            if (btnName.contains("前のページ")) open(player, currentPage - 1);
            else if (btnName.contains("次のページ")) open(player, currentPage + 1);
            else if (btnName.contains("リセット")) {
                plugin.getDatabaseManager().clearWorldSettings(player.getWorld().getName());
                player.sendMessage("§aこのワールドの全設定をリセットしました。");
                open(player, currentPage);
            } else if (btnName.contains("言語設定")) openLanguageMenu(player);
            else if (btnName.contains("設置履歴")) openHistory(player, null, null);
            else if (btnName.contains("特殊アイテム")) openToolsMenu(player);
            else if (event.getCurrentItem().getType().isBlock()) openDetail(player, event.getCurrentItem().getType());
        } else if (vTitle.startsWith(historyTitle)) {
            handleHistoryClick(event, player);
        } else if (vTitle.equals(whoTitle)) {
            if (event.getCurrentItem().getType() == Material.ARROW) openHistory(player, null, null);
            else openHistory(player, "who", event.getCurrentItem().getItemMeta().getDisplayName().substring(2));
        } else if (vTitle.equals(whatTitle)) {
            if (event.getCurrentItem().getType() == Material.ARROW) openHistory(player, null, null);
            else openHistory(player, "what", event.getCurrentItem().getItemMeta().getDisplayName().substring(2));
        } else if (vTitle.equals("§0Special Tools")) {
            String btnName = event.getCurrentItem().getItemMeta().getDisplayName();
            if (btnName.contains("戻る")) open(player, 0);
            else player.getInventory().addItem(event.getCurrentItem().clone());
        } else if (vTitle.equals(langTitle)) {
            handleLangClick(event, player);
        } else if (vTitle.startsWith(detailTitle)) {
            handleDetailClick(event, player, vTitle.substring(detailTitle.length()));
        }
    }

    private void handleHistoryClick(InventoryClickEvent event, Player player) {
        String btnName = event.getCurrentItem().getItemMeta().getDisplayName();
        if (btnName.contains("戻る")) open(player, 0);
        else if (btnName.contains("Who")) openWhoSearch(player);
        else if (btnName.contains("Where")) startSearchChat(player, "where");
        else if (btnName.contains("What")) openWhatSearch(player);
        else if (btnName.contains("Reset Filter")) openHistory(player, null, null);
        else if (btnName.startsWith("§8#")) {
            try {
                int id = Integer.parseInt(btnName.split(" ")[0].substring(3));
                String[] outLoc = new String[2];
                List<String> links = plugin.getDatabaseManager().removeSourceById(id, outLoc);
                if (outLoc[0] != null) {
                    World w = Bukkit.getWorld(outLoc[0]);
                    if (w != null) {
                        for (String l : links) {
                            String[] p = l.split(",");
                            Block b = w.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                            if (b.getType().name().equals("LIGHT")) b.setType(Material.AIR);
                        }
                    }
                    player.sendMessage("§a光源 ID #" + id + " を削除しました。");
                    openHistory(player, null, null);
                }
            } catch (Exception e) {}
        }
    }

    private void handleLangClick(InventoryClickEvent event, Player player) {
        String btnName = event.getCurrentItem().getItemMeta().getDisplayName();
        String lang = null;
        if (btnName.contains("jp")) lang = "jp";
        else if (btnName.contains("en")) lang = "en";
        else if (btnName.contains("ch")) lang = "ch";
        else if (btnName.contains("ru")) lang = "ru";
        else if (btnName.contains("戻る")) { open(player, 0); return; }

        if (lang != null) {
            plugin.getConfig().set("language", lang);
            plugin.saveConfig();
            plugin.getLangManager().loadLanguage(lang);
            player.sendMessage(plugin.getLangManager().getMessage("language_changed"));
            openLanguageMenu(player);
        }
    }

    private void handleDetailClick(InventoryClickEvent event, Player player, String matName) {
        Material mat = Material.getMaterial(matName);
        if (mat == null) return;
        double currentMult = plugin.getMultiplier(player.getWorld(), mat);
        double newMult = currentMult;
        String btnName = event.getCurrentItem().getItemMeta().getDisplayName();
        if (btnName.contains("+1.0")) newMult += 1.0;
        else if (btnName.contains("-1.0")) newMult -= 1.0;
        else if (btnName.contains("+0.1")) newMult += 0.1;
        else if (btnName.contains("-0.1")) newMult -= 0.1;
        else if (btnName.contains("1.0 にリセット")) newMult = 1.0;
        else if (btnName.contains("戻る")) { open(player, 0); return; }

        if (newMult < 0) newMult = 0;
        if (newMult > 16) newMult = 16;
        newMult = Math.round(newMult * 10.0) / 10.0;

        if (newMult != currentMult) {
            plugin.getDatabaseManager().setWorldMultiplier(player.getWorld().getName(), mat.name(), newMult);
            plugin.getVersionHandler().refreshNearbyLight(player, mat, newMult);
            event.getInventory().setItem(13, createItem(player, mat));
        }
    }

    private void startSearchChat(Player player, String category) {
        player.closeInventory();
        String hint = category.equals("where") ? "X,Y,Z (例: 100,64,200)" : category;
        player.sendMessage("§e検索したい " + hint + " をチャットに入力してください（キャンセルは 'cancel'）");
        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @EventHandler
            public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
                if (e.getPlayer().equals(player)) {
                    e.setCancelled(true);
                    String msg = e.getMessage();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!msg.equalsIgnoreCase("cancel")) openHistory(player, category, msg);
                        else openHistory(player, null, null);
                    });
                    org.bukkit.event.HandlerList.unregisterAll(this);
                }
            }
        }, plugin);
    }

    private ItemStack createItem(Player player, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        double mult = plugin.getMultiplier(player.getWorld(), mat);
        meta.setDisplayName("§e" + plugin.getLangManager().getBlockName(mat.name()));
        List<String> lore = new ArrayList<>();
        lore.add("§7World: §f" + player.getWorld().getName());
        lore.add("§7Multiplier: §a" + mult + "x");
        lore.add("");
        lore.add("§7クリックで詳細設定へ");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createButton(String name, Material mat, org.bukkit.block.BlockFace face) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (face != null && meta instanceof BlockDataMeta) {
            try {
                org.bukkit.block.data.type.Stairs stairs = (org.bukkit.block.data.type.Stairs) Bukkit.createBlockData(mat);
                stairs.setFacing(face);
                ((BlockDataMeta) meta).setBlockData(stairs);
            } catch (Exception e) {}
        }
        item.setItemMeta(meta);
        return item;
    }
}
