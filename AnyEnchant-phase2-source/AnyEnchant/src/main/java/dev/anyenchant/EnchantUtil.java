package dev.anyenchant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

public final class EnchantUtil {

    private EnchantUtil() {}

    /** อ่าน Enchant ของไอเทม (หนังสือ Enchant ใช้ stored enchants) */
    public static Map<Enchantment, Integer> getEnchants(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return new LinkedHashMap<>();
        }
        if (meta instanceof EnchantmentStorageMeta storage) {
            return new LinkedHashMap<>(storage.getStoredEnchants());
        }
        return new LinkedHashMap<>(meta.getEnchants());
    }

    /** แทนที่ Enchant ทั้งหมดของไอเทมด้วยแผนที่ที่ให้มา โดยไม่เช็กชนิดไอเทม/ข้อขัดแย้ง/เลเวลสูงสุดของ vanilla */
    public static void setEnchants(ItemStack item, Map<Enchantment, Integer> enchants) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (meta instanceof EnchantmentStorageMeta storage) {
            for (Enchantment old : new ArrayList<>(storage.getStoredEnchants().keySet())) {
                storage.removeStoredEnchant(old);
            }
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                storage.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            }
        } else {
            for (Enchantment old : new ArrayList<>(meta.getEnchants().keySet())) {
                meta.removeEnchant(old);
            }
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }
        item.setItemMeta(meta);
    }

    /** หา Enchant จากชื่อ เช่น "sharpness" หรือ "minecraft:sharpness" */
    public static Enchantment find(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        NamespacedKey namespaced = NamespacedKey.minecraft(key);
        return Registry.ENCHANTMENT.get(namespaced);
    }

    /** ชื่อ Enchant ทั้งหมด (key ไม่มี namespace) เรียงตามตัวอักษร */
    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            keys.add(enchantment.getKey().getKey());
        }
        keys.sort(String::compareTo);
        return keys;
    }
}
