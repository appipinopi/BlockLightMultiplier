package plugin;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import java.util.List;

/**
 * バージョンごとの光度変更処理を抽象化するインターフェース。
 */
public interface VersionHandler {
    void setLightMultiplier(Material material, double multiplier);

    /**
     * 特定の座標のブロックに輝度倍率を適用します。
     * @param block 設置したブロック
     * @param multiplier 倍率
     * @param persistent データをファイルに保存するかどうか
     * @param placer 設置したプレイヤー（null の場合はシステム）
     */
    void applyLightAt(org.bukkit.block.Block block, double multiplier, boolean persistent, org.bukkit.entity.Player placer);

    /**
     * 特定の座標のブロックの輝度倍率適用を解除します。
     */
    void removeLightAt(Block block);

    /**
     * プレイヤーの周囲にある特定のブロックの明るさを更新します。
     */
    void refreshNearbyLight(Player player, Material material, double multiplier);

    /**
     * 光るブロック（補完対象）のマテリアルリストを返します。
     */
    List<Material> getLuminousMaterials();

    /**
     * マテリアルの基本光度を返します。
     */
    int getBaseLight(Material material);

    /**
     * 指定された座標の周囲に光源があるかチェックし、なければライトブロックを削除します。
     */
    void checkAndRemoveLightAt(Block block);

    /**
     * 指定された座標の周囲にあるライトブロックを一括削除します。
     */
    void clearNearbyLight(org.bukkit.entity.Player player, org.bukkit.block.Block center, int radius);

    /**
     * バージョンに応じて安全なマテリアルを取得します。
     */
    Material getSafeMaterial(String materialName);
}
