package dev.anyenchant;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

/**
 * ความสามารถพิเศษของ Enchant เมื่อใส่บนไอเทมที่ไม่ใช่ของตัวเอง (ไอเทมของตัวเองทำงานแบบ vanilla)
 *
 * Blast Protection (ของตัวเอง = เกราะ)   : ไอเทมอื่น Shift + คลิกขวา = ระเบิดรอบตัว
 * Channeling       (ของตัวเอง = Trident) : อาวุธ/ไอเทมอื่นตีโดน = ฟ้าผ่าเป้าหมาย (ไม่ต้องมีพายุ)
 *                                          เครื่องมือ Shift + คลิกขวา = ฟ้าผ่าจุดที่เล็ง
 *                                          เกราะ ถูกตี = ฟ้าผ่าผู้โจมตี
 * Efficiency       (ของตัวเอง = เครื่องมือ): อาวุธ ถือในมือ = ตีเร็วขึ้น (ลดคูลดาวน์การตี)
 */
public final class AbilityListener implements Listener {

    private static final String BLAST = "blast-protection";
    private static final String CHANNELING = "channeling";
    private static final String EFFICIENCY = "efficiency";

    private final AnyEnchantPlugin plugin;
    private final NamespacedKey attackSpeedKey;

    private final Map<String, Long> readyAt = new HashMap<>();
    private final Map<UUID, Long> lightningImmuneUntil = new HashMap<>();
    private final Set<UUID> exploding = new HashSet<>();

    public AbilityListener(AnyEnchantPlugin plugin) {
        this.plugin = plugin;
        this.attackSpeedKey = new NamespacedKey(plugin, "efficiency_attack_speed");
    }

    // ---------------------------------------------------------------- config / cooldown helpers

    private double num(String ability, String path, double def) {
        return plugin.getConfig().getDouble("abilities." + ability + "." + path, def);
    }

    private boolean enabled(String ability) {
        return plugin.getConfig().getBoolean("abilities." + ability + ".enabled", true);
    }

    /** ใช้กับความสามารถที่กดเอง: ถ้ายังติดคูลดาวน์จะแจ้งเวลาที่เหลือบน action bar */
    private boolean tryUse(Player player, String ability, String label, double cooldownSeconds) {
        long now = System.currentTimeMillis();
        String key = player.getUniqueId() + ":" + ability;
        long ready = readyAt.getOrDefault(key, 0L);
        if (now < ready) {
            String left = String.format(Locale.ROOT, "%.1f", (ready - now) / 1000.0);
            player.sendActionBar(plugin.messages().get("cooldown", "ability", label, "seconds", left));
            return false;
        }
        readyAt.put(key, now + (long) (cooldownSeconds * 1000.0));
        return true;
    }

    /** ใช้กับความสามารถอัตโนมัติ (ไม่แจ้งเตือน) */
    private boolean offCooldown(UUID id, String name, long millis) {
        String key = id + ":" + name;
        long now = System.currentTimeMillis();
        if (now < readyAt.getOrDefault(key, 0L)) {
            return false;
        }
        readyAt.put(key, now + millis);
        return true;
    }

    private boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    // ---------------------------------------------------------------- Shift + right click

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        ItemKind kind = ItemKind.of(item.getType());
        boolean fired = false;

        int blast = item.getEnchantmentLevel(Enchantment.BLAST_PROTECTION);
        if (blast > 0 && kind != ItemKind.ARMOR && enabled(BLAST)) {
            fired |= castBlast(player, blast);
        }

        int channeling = item.getEnchantmentLevel(Enchantment.CHANNELING);
        if (channeling > 0 && kind == ItemKind.TOOL && enabled(CHANNELING)) {
            fired |= castLightningAtTarget(player, channeling);
        }

        if (fired) {
            // กันการใช้งานปกติของไอเทม เช่น ไถดิน/ถากเปลือกไม้ ตอนที่ความสามารถทำงาน
            event.setCancelled(true);
        }
    }

    private boolean castBlast(Player player, int level) {
        double cooldown = Math.max(1.0, num(BLAST, "cooldown-base-seconds", 10.0)
                - level * num(BLAST, "cooldown-reduction-per-level", 0.5));
        if (!tryUse(player, BLAST, "Blast Protection", cooldown)) {
            return false;
        }
        float power = (float) (num(BLAST, "power-base", 1.5) + level * num(BLAST, "power-per-level", 0.25));
        UUID id = player.getUniqueId();
        exploding.add(id);
        try {
            // ไม่ทำลายบล็อก ไม่ติดไฟ ทำร้ายเฉพาะเอนทิตี้รอบตัว (ผู้ใช้ไม่เจ็บ)
            player.getWorld().createExplosion(player.getLocation(), power, false, false, player);
        } finally {
            exploding.remove(id);
        }
        return true;
    }

    private boolean castLightningAtTarget(Player player, int level) {
        double range = num(CHANNELING, "tool.range", 30.0);
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTrace(eye, eye.getDirection(), range,
                FluidCollisionMode.NEVER, true, 0.2, entity -> !entity.equals(player));
        if (hit == null) {
            player.sendActionBar(plugin.messages().get("no-target"));
            return false;
        }
        double cooldown = Math.max(1.0, num(CHANNELING, "tool.cooldown-base-seconds", 8.0)
                - level * num(CHANNELING, "tool.cooldown-reduction-per-level", 0.5));
        if (!tryUse(player, CHANNELING, "Channeling", cooldown)) {
            return false;
        }
        strike(hit.getHitPosition().toLocation(player.getWorld()), player);
        return true;
    }

    /** ฟ้าผ่าจริง (ไม่ต้องมีพายุ) พร้อมกันไม่ให้เจ้าของโดนฟ้า/ไฟของตัวเอง */
    private void strike(Location location, Player owner) {
        lightningImmuneUntil.put(owner.getUniqueId(), System.currentTimeMillis() + 3000L);
        location.getWorld().strikeLightning(location);
    }

    // ---------------------------------------------------------------- Channeling on hit

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getCause() != DamageCause.ENTITY_ATTACK || !enabled(CHANNELING)) {
            return;
        }

        // อาวุธ/ไอเทมอื่นในมือผู้โจมตี: ฟ้าผ่าเป้าหมาย (Trident ใช้แบบ vanilla)
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof LivingEntity victim) {
            ItemStack held = attacker.getInventory().getItemInMainHand();
            if (!held.getType().isAir() && held.getType() != Material.TRIDENT) {
                ItemKind kind = ItemKind.of(held.getType());
                int level = held.getEnchantmentLevel(Enchantment.CHANNELING);
                if (level > 0 && (kind == ItemKind.WEAPON || kind == ItemKind.OTHER)) {
                    double chance = level * num(CHANNELING, "chance-per-level", 0.10);
                    long cooldown = (long) num(CHANNELING, "hit-cooldown-ms", 500.0);
                    if (roll(chance) && offCooldown(attacker.getUniqueId(), "channeling-hit", cooldown)) {
                        strike(victim.getLocation(), attacker);
                    }
                }
            }
        }

        // เกราะของผู้ถูกตี: ฟ้าผ่าผู้โจมตี (ใช้เลเวลสูงสุดของเกราะที่ใส่ ไม่รวมกัน)
        if (event.getEntity() instanceof Player wearer && event.getDamager() instanceof LivingEntity hitter
                && !hitter.equals(wearer)) {
            int level = 0;
            for (ItemStack piece : wearer.getInventory().getArmorContents()) {
                if (piece == null || piece.getType().isAir() || ItemKind.of(piece.getType()) != ItemKind.ARMOR) {
                    continue;
                }
                level = Math.max(level, piece.getEnchantmentLevel(Enchantment.CHANNELING));
            }
            if (level > 0) {
                double chance = level * num(CHANNELING, "armor.chance-per-level", 0.05);
                long cooldown = (long) num(CHANNELING, "armor.cooldown-ms", 3000.0);
                if (roll(chance) && offCooldown(wearer.getUniqueId(), "channeling-armor", cooldown)) {
                    strike(hitter.getLocation(), wearer);
                }
            }
        }
    }

    // ---------------------------------------------------------------- self-damage protection

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID id = player.getUniqueId();
        DamageCause cause = event.getCause();
        if (cause == DamageCause.LIGHTNING
                && System.currentTimeMillis() < lightningImmuneUntil.getOrDefault(id, 0L)) {
            event.setCancelled(true);
        } else if ((cause == DamageCause.ENTITY_EXPLOSION || cause == DamageCause.BLOCK_EXPLOSION)
                && exploding.contains(id)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (event.getEntity() instanceof Player player && event.getCombuster() instanceof LightningStrike
                && System.currentTimeMillis() < lightningImmuneUntil.getOrDefault(player.getUniqueId(), 0L)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lightningImmuneUntil.remove(id);
        String prefix = id + ":";
        readyAt.keySet().removeIf(key -> key.startsWith(prefix));
    }

    // ---------------------------------------------------------------- Efficiency: faster attacks

    public BukkitTask startEfficiencyTask() {
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateAttackSpeed(player);
            }
        }, 20L, 2L);
    }

    public void clearAttackSpeed() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
            AttributeModifier current = findModifier(attribute);
            if (attribute != null && current != null) {
                attribute.removeModifier(current);
            }
        }
    }

    private AttributeModifier findModifier(AttributeInstance attribute) {
        if (attribute == null) {
            return null;
        }
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (attackSpeedKey.equals(modifier.getKey())) {
                return modifier;
            }
        }
        return null;
    }

    private void updateAttackSpeed(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) {
            return;
        }
        int level = 0;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (enabled(EFFICIENCY) && !held.getType().isAir() && ItemKind.of(held.getType()) == ItemKind.WEAPON) {
            level = held.getEnchantmentLevel(Enchantment.EFFICIENCY);
        }
        double amount = level * num(EFFICIENCY, "attack-speed-per-level", 0.15);
        AttributeModifier current = findModifier(attribute);

        if (level <= 0) {
            if (current != null) {
                attribute.removeModifier(current);
            }
            return;
        }
        if (current != null) {
            if (Math.abs(current.getAmount() - amount) < 1.0e-9) {
                return;
            }
            attribute.removeModifier(current);
        }
        attribute.addTransientModifier(new AttributeModifier(attackSpeedKey, amount,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }
}
