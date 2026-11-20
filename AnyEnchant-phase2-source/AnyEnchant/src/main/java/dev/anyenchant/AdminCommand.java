package dev.anyenchant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "anyenchant.admin";

    private final AnyEnchantPlugin plugin;

    public AdminCommand(AnyEnchantPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!sender.hasPermission(PERMISSION)) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "enchant" -> enchant(sender, args);
            case "list" -> list(sender);
            case "reload" -> {
                plugin.reloadAll();
                plugin.messages().send(sender, "reloaded");
            }
            default -> messages.send(sender, "usage");
        }
        return true;
    }

    private void enchant(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (args.length < 3) {
            messages.send(sender, "usage");
            return;
        }
        Enchantment enchantment = EnchantUtil.find(args[1]);
        if (enchantment == null) {
            messages.send(sender, "unknown-enchant", "enchant", args[1]);
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            messages.send(sender, "invalid-level");
            return;
        }
        int max = plugin.maxLevel();
        if (level < 0 || level > max) {
            messages.send(sender, "level-out-of-range", "max", String.valueOf(max));
            return;
        }

        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                messages.send(sender, "player-not-found", "player", args[3]);
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            messages.send(sender, "console-needs-player");
            return;
        }

        ItemStack item = target.getInventory().getItemInMainHand().clone();
        if (item.getType().isAir()) {
            messages.send(sender, "no-item");
            return;
        }

        Map<Enchantment, Integer> enchants = EnchantUtil.getEnchants(item);
        if (level == 0) {
            enchants.remove(enchantment);
        } else {
            enchants.put(enchantment, level);
        }
        EnchantUtil.setEnchants(item, enchants);
        target.getInventory().setItemInMainHand(item);

        String name = enchantment.getKey().getKey();
        if (level == 0) {
            messages.send(sender, "removed", "enchant", name, "player", target.getName());
        } else {
            messages.send(sender, "enchanted", "enchant", name, "level", String.valueOf(level),
                    "player", target.getName());
        }
    }

    private void list(CommandSender sender) {
        List<String> keys = EnchantUtil.keys();
        plugin.messages().sendRaw(sender, "list-header", "count", String.valueOf(keys.size()));
        sender.sendMessage(String.join(", ", keys));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (!sender.hasPermission(PERMISSION)) {
            return options;
        }
        if (args.length == 1) {
            options.addAll(List.of("enchant", "list", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("enchant")) {
            options.addAll(EnchantUtil.keys());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("enchant")) {
            for (int i = 0; i <= plugin.maxLevel(); i++) {
                options.add(String.valueOf(i));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("enchant")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                options.add(online.getName());
            }
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(typed));
        return options;
    }
}
