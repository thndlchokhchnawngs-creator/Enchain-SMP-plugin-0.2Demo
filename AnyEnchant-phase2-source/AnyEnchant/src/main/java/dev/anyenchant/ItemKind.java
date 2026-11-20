package dev.anyenchant;

import org.bukkit.Material;

/** แบ่งประเภทไอเทมเพื่อกำหนดความสามารถพิเศษของ Enchant ที่ไม่ได้อยู่บนไอเทม "ของตัวเอง" */
public enum ItemKind {
    WEAPON, TOOL, ARMOR, OTHER;

    public static ItemKind of(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS") || material == Material.ELYTRA) {
            return ARMOR;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_SPEAR")
                || material == Material.MACE || material == Material.TRIDENT) {
            return WEAPON;
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
                || material == Material.SHEARS) {
            return TOOL;
        }
        return OTHER;
    }
}
