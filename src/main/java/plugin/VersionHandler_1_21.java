package plugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VersionHandler_1_21 implements VersionHandler {

    private final Main plugin;

    public VersionHandler_1_21(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void setLightMultiplier(Material material, double multiplier) {
        // 全域へのNMS反映が必要な場合に実装
    }

    @Override
    public void applyLightAt(Block sourceBlock, double multiplier, boolean persistent, org.bukkit.entity.Player placer) {
        Location sourceLoc = sourceBlock.getLocation();
        World world = sourceLoc.getWorld();
        int baseLight = getBaseLight(sourceBlock.getType());
        if (baseLight <= 0) return;
        
        List<String> lightLocs = new ArrayList<>();
        int maxDist = (int) (baseLight * multiplier);
        if (maxDist > 15) maxDist = 15;
        
        if (persistent) {
            plugin.getLogger().info(String.format("Applying light multiplier %.1f to %s at %s by %s. Max distance: %d", 
                multiplier, sourceBlock.getType().name(), locToString(sourceLoc), 
                (placer != null ? placer.getName() : "System"), maxDist));
        }

        for (int dx = -maxDist; dx <= maxDist; dx++) {
            for (int dy = -maxDist; dy <= maxDist; dy++) {
                for (int dz = -maxDist; dz <= maxDist; dz++) {
                    int dist = (int) Math.sqrt(dx*dx + dy*dy + dz*dz);
                    if (dist == 0 || dist >= maxDist) continue;

                    int targetLevel = (int) (baseLight - (dist / multiplier));
                    if (targetLevel <= 0 || targetLevel > 15) continue;

                    Block target = sourceBlock.getRelative(dx, dy, dz);
                    if (target.getType() == Material.AIR) {
                        target.setType(Material.LIGHT);
                        Levelled data = (Levelled) target.getBlockData();
                        data.setLevel(targetLevel);
                        target.setBlockData(data);
                        lightLocs.add(target.getX() + "," + target.getY() + "," + target.getZ());
                        
                        // パーティクル演出 (5%の確率で表示、または光源ブロックの近くのみ)
                        if (Math.random() < 0.1) {
                            world.spawnParticle(org.bukkit.Particle.WAX_ON, target.getLocation().add(0.5, 0.5, 0.5), 1, 0.2, 0.2, 0.2, 0);
                        }
                    }
                }
            }
        }
        
        if (persistent && !lightLocs.isEmpty()) {
            plugin.getDatabaseManager().addSource(world.getName(), sourceLoc.getBlockX(), sourceLoc.getBlockY(), sourceLoc.getBlockZ(), 
                sourceBlock.getType().name(), (placer != null ? placer.getName() : "System"), 
                (placer != null ? placer.getUniqueId().toString() : null), multiplier, lightLocs);
        }
    }

    @Override
    public void removeLightAt(Block sourceBlock) {
        List<String> links = plugin.getDatabaseManager().removeSourceAndGetLinks(sourceBlock.getWorld().getName(), sourceBlock.getX(), sourceBlock.getY(), sourceBlock.getZ());
        for (String link : links) {
            String[] p = link.split(",");
            Block b = sourceBlock.getWorld().getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            if (b.getType() == Material.LIGHT) {
                b.setType(Material.AIR);
                // 削除演出
                if (Math.random() < 0.1) b.getWorld().spawnParticle(org.bukkit.Particle.ASH, b.getLocation().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0);
            }
        }
    }

    @Override
    public void checkAndRemoveLightAt(Block lightBlock) {
        if (lightBlock.getType() != Material.LIGHT) return;
        
        int radius = 5; // 周囲5ブロックをチェック
        boolean found = false;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = lightBlock.getRelative(x, y, z);
                    if (getBaseLight(b.getType()) > 0 && b.getType() != Material.LIGHT) {
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
            if (found) break;
        }
        
        if (!found) {
            lightBlock.setType(Material.AIR);
            // 自動削除演出
            lightBlock.getWorld().spawnParticle(org.bukkit.Particle.ASH, lightBlock.getLocation().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0);
        }
    }

    @Override
    public void refreshNearbyLight(org.bukkit.entity.Player player, Material material, double multiplier) {
        Location loc = player.getLocation();
        int radius = 16;
        List<Block> targetBlocks = new ArrayList<>();
        
        // 更新対象の収集
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.getBlock().getRelative(x, y, z);
                    if (b.getType() == material) {
                        targetBlocks.add(b);
                    }
                }
            }
        }

        if (targetBlocks.isEmpty()) return;

        // 分散処理 (1ティックあたり20個ずつ更新)
        int batchSize = 20;
        new org.bukkit.scheduler.BukkitRunnable() {
            int index = 0;
            @Override
            public void run() {
                for (int i = 0; i < batchSize && index < targetBlocks.size(); i++) {
                    Block b = targetBlocks.get(index++);
                    removeLightAt(b);
                    if (multiplier != 1.0) {
                        applyLightAt(b, multiplier, false, null);
                    }
                }
                if (index >= targetBlocks.size()) {
                    player.sendMessage("§a周囲の " + targetBlocks.size() + " 個の " + material.name() + " の明るさを更新完了しました。");
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    @Override
    public void clearNearbyLight(org.bukkit.entity.Player player, org.bukkit.block.Block center, int radius) {
        Location loc = center.getLocation();
        List<Block> targets = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.getBlock().getRelative(x, y, z);
                    if (b.getType() == Material.LIGHT) {
                        targets.add(b);
                    }
                }
            }
        }
        
        if (targets.isEmpty()) {
            player.sendMessage("§e指定された範囲にライトブロックは見つかりませんでした。");
            return;
        }

        int batchSize = 100; // 少し多めに
        new org.bukkit.scheduler.BukkitRunnable() {
            int index = 0;
            @Override
            public void run() {
                for (int i = 0; i < batchSize && index < targets.size(); i++) {
                    targets.get(index++).setType(Material.AIR);
                }
                if (index >= targets.size()) {
                    player.sendMessage("§a起点座標 (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ") 周囲の " + targets.size() + " 個のライトブロックを削除完了しました。");
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    @Override
    public List<Material> getLuminousMaterials() {
        List<Material> list = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (mat.isBlock() && getBaseLight(mat) > 0) {
                if (mat == Material.LIGHT) continue; // ライトブロックは除外
                list.add(mat);
            }
        }
        return list;
    }

    @Override
    public int getBaseLight(Material mat) {
        if (mat == null || !mat.isBlock()) return 0;
        
        // LIGHTブロック自体は無限ループ防止のため除外
        if (mat == Material.LIGHT) return 0;

        // 1.21環境では、Bukkitの提供するプロパティから直接光度を取得することを試みる
        // (BlockDataを生成して実際のデフォルト状態の光度を確認)
        try {
            // デフォルトのBlockDataから光度を判定するのが最も確実
            int light = ReflectionUtils.getLightEmission(mat.createBlockData(), mat);
            if (light > 0) return light;
        } catch (Exception e) {}

        // フォールバック
        String name = mat.name();
        if (name.contains("TORCH") || name.contains("LANTERN") || name.contains("GLOWSTONE") || 
            name.contains("LAMP") || name.contains("SEA_LANTERN") || name.contains("FROGLIGHT") ||
            name.contains("SHROOMLIGHT") || name.contains("JACK_O_LANTERN")) return 15;
        if (name.contains("CANDLE") || name.contains("CAMPFIRE") || name.contains("SOUL")) return 10;
        if (name.contains("BEACON")) return 15;
        if (name.contains("CRYSTAL")) return 15;
        return 0;
    }

    @Override
    public Material getSafeMaterial(String materialName) {
        try {
            return Material.valueOf(materialName);
        } catch (Exception e) {
            return null;
        }
    }

    private String locToString(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
