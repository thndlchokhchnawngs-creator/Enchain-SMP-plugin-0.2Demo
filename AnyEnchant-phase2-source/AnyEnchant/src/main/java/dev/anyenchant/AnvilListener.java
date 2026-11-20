package dev.anyenchant;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;

/**
 * รวม Enchant ที่ Anvil แบบไม่จำกัดชนิดไอเทมและไม่มีข้อขัดแย้ง
 * - ช่องขวาเป็นหนังสือ Enchant หรือไอเทมชนิดเดียวกับช่องซ้าย
 * - เลเวลเท่ากันรวมกันได้ +1 (เช่น V + V = VI) จนถึง max-level
 */
public final class AnvilListener implements Listener {

    private final AnyEnchantPlugin plugin;

    public AnvilListener(AnyEnchantPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!plugin.getConfig().getBoolean("anvil.enabled", true)) {
            return;
        }
        AnvilInventory inventory = event.getInventory();
        ItemStack first = inventory.getFirstItem();
        ItemStack second = inventory.getSecondItem();
        if (first == null || second == null || first.getType().isAir() || second.getType().isAir()) {
            return;
        }
        if (first.getType() == Material.BOOK || first.getAmount() != 1) {
            return;
        }
        boolean secondIsBook = second.getType() == Material.ENCHANTED_BOOK;
        if (!secondIsBook && second.getType() != first.getType()) {
            return;
        }

        Map<Enchantment, Integer> incoming = EnchantUtil.getEnchants(second);
        if (incoming.isEmpty()) {
            return;
        }

        int max = plugin.maxLevel();
        Map<Enchantment, Integer> merged = EnchantUtil.getEnchants(first);
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : incoming.entrySet()) {
            int have = merged.getOrDefault(entry.getKey(), 0);
            int add = entry.getValue();
            int next = (have == add) ? have + 1 : Math.max(have, add);
            next = Math.max(have, Math.min(next, max));
            if (next != have) {
                merged.put(entry.getKey(), next);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }

        // ถ้า vanilla ให้ผลลัพธ์อยู่แล้ว (เช่น ซ่อมความทนทาน/เปลี่ยนชื่อ) ใช้เป็นฐาน ไม่งั้นใช้ไอเทมช่องซ้าย
        ItemStack vanilla = event.getResult();
        ItemStack result = (vanilla != null && !vanilla.getType().isAir()) ? vanilla.clone() : first.clone();
        result.setAmount(1);
        EnchantUtil.setEnchants(result, merged);

        ItemMeta meta = result.getItemMeta();
        if (meta instanceof Repairable repairable) {
            repairable.setRepairCost(0);
            result.setItemMeta(meta);
        }
        event.setResult(result);

        int cost = Math.max(1, plugin.getConfig().getInt("anvil.cost", 5));
        AnvilView view = event.getView();
        view.setRepairCost(cost);
        view.setMaximumRepairCost(Math.max(view.getMaximumRepairCost(), cost + 1));
    }
}
