package plugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.20.4 (および 1.17+) 用のハンドラー。
 */
public class VersionHandler_Legacy implements VersionHandler {

    private final Main plugin;

    public VersionHandler_Legacy(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void setLightMultiplier(Material material, double multiplier) {}

    @Override
    public void applyLightAt(Block sourceBlock, double multiplier, boolean persistent, Player placer) {
        Location sourceLoc = sourceBlock.getLocation();
        World world = sourceLoc.getWorld();
        int baseLight = getBaseLight(sourceBlock.getType());
        if (baseLight <= 0) return;
        
        List<String> lightLocs = new ArrayList<>();
        int maxDist = (int) (baseLight * multiplier);
        if (maxDist > 15) maxDist = 15;
        
        for (int dx = -maxDist; dx <= maxDist; dx++) {
            for (int dy = -maxDist; dy <= maxDist; dy++) {
                for (int dz = -maxDist; dz <= maxDist; dz++) {
                    int dist = (int) Math.sqrt(dx*dx + dy*dy + dz*dz);
                    if (dist == 0 || dist >= maxDist) continue;

                    int targetLevel = (int) (baseLight - (dist / multiplier));
                    if (targetLevel <= 0 || targetLevel > 15) continue;

                    Block target = sourceBlock.getRelative(dx, dy, dz);
                    if (target.getType() == Material.AIR) {
                        target.setType(getSafeMaterial("LIGHT"));
                        Levelled data = (Levelled) target.getBlockData();
                        data.setLevel(targetLevel);
                        target.setBlockData(data);
                        lightLocs.add(target.getX() + "," + target.getY() + "," + target.getZ());
                        
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
            if (b.getType() == getSafeMaterial("LIGHT")) {
                b.setType(Material.AIR);
            }
        }
    }

    @Override
    public void refreshNearbyLight(Player player, Material material, double multiplier) {
        Location loc = player.getLocation();
        int radius = 16;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.getBlock().getRelative(x, y, z);
                    if (b.getType() == material) {
                        removeLightAt(b);
                        if (multiplier != 1.0) applyLightAt(b, multiplier, false, null);
                    }
                }
            }
        }
    }

    @Override
    public List<Material> getLuminousMaterials() {
        List<Material> list = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (mat.isBlock() && getBaseLight(mat) > 0) {
                if (mat == getSafeMaterial("LIGHT")) continue;
                list.add(mat);
            }
        }
        return list;
    }

    @Override
    public int getBaseLight(Material mat) {
        if (mat == null || !mat.isBlock()) return 0;
        
        Material lightMat = getSafeMaterial("LIGHT");
        if (mat == lightMat) return 0;

        try {
            // BlockData から光度を取得
            int light = ReflectionUtils.getLightEmission(mat.createBlockData(), mat);
            if (light > 0) return light;
        } catch (Exception e) {}

        // 1.17.1 以前の API 用フォールバック
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
    public void checkAndRemoveLightAt(Block block) {
        if (block.getType() != getSafeMaterial("LIGHT")) return;
        block.setType(Material.AIR);
    }

    @Override
    public void clearNearbyLight(org.bukkit.entity.Player player, org.bukkit.block.Block center, int radius) {
        Location loc = center.getLocation();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.getBlock().getRelative(x, y, z);
                    if (b.getType() == getSafeMaterial("LIGHT")) b.setType(Material.AIR);
                }
            }
        }
    }

    @Override
    public Material getSafeMaterial(String materialName) {
        try {
            return Material.valueOf(materialName);
        } catch (Exception e) {
            return null;
        }
    }
}
