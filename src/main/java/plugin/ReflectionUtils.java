package plugin;

import org.bukkit.event.inventory.InventoryEvent;
import java.lang.reflect.Method;

public class ReflectionUtils {
    private static Method getViewMethod;
    private static Method getTitleMethod;

    private static Method getLightEmissionMethod;

    static {
        try {
            // InventoryEvent#getView() を取得
            getViewMethod = InventoryEvent.class.getMethod("getView");
            // InventoryView#getTitle() を取得
            getTitleMethod = getViewMethod.getReturnType().getMethod("getTitle");
            
            // BlockData#getLightEmission() は 1.17+ で順次追加されている
            try {
                getLightEmissionMethod = org.bukkit.block.data.BlockData.class.getMethod("getLightEmission");
            } catch (NoSuchMethodException e) {
                // 1.17.x など、メソッドが存在しない場合
                getLightEmissionMethod = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * BlockData から光度を安全に取得します。1.17.1 では存在しないためフォールバックします。
     */
    public static int getLightEmission(org.bukkit.block.data.BlockData data, org.bukkit.Material mat) {
        if (getLightEmissionMethod != null) {
            try {
                return (int) getLightEmissionMethod.invoke(data);
            } catch (Exception e) {}
        }
        
        // 1.17.1 では API から直接取得する標準的な方法がないため 0 を返す。
        // VersionHandler 内のフォールバックリストで補完されることを期待する。
        return 0;
    }

    /**
     * InventoryEvent からタイトルを安全に取得します。
     * 1.21 での InventoryView のインターフェース化によるバイナリ不整合を回避します。
     */
    public static String getInventoryTitle(InventoryEvent event) {
        try {
            Object view = getViewMethod.invoke(event);
            return (String) getTitleMethod.invoke(view);
        } catch (Exception e) {
            return "";
        }
    }
}
