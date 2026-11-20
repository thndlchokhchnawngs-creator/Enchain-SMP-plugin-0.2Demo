package dev.anyenchant;

import java.io.File;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class AnyEnchantPlugin extends JavaPlugin {

    private Messages messages;
    private AbilityListener abilities;
    private BukkitTask efficiencyTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveLangIfMissing("messages_th.yml");
        saveLangIfMissing("messages_en.yml");
        messages = new Messages(this);

        AdminCommand admin = new AdminCommand(this);
        PluginCommand command = getCommand("anyenchant");
        if (command != null) {
            command.setExecutor(admin);
            command.setTabCompleter(admin);
        }
        getServer().getPluginManager().registerEvents(new AnvilListener(this), this);

        abilities = new AbilityListener(this);
        getServer().getPluginManager().registerEvents(abilities, this);
        efficiencyTask = abilities.startEfficiencyTask();
    }

    @Override
    public void onDisable() {
        if (efficiencyTask != null) {
            efficiencyTask.cancel();
        }
        if (abilities != null) {
            abilities.clearAttackSpeed();
        }
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
    }

    public Messages messages() {
        return messages;
    }

    /** เลเวลสูงสุดจาก config (จำกัดช่วง 1-255 ตามที่เกมรองรับ) */
    public int maxLevel() {
        return Math.min(255, Math.max(1, getConfig().getInt("max-level", 10)));
    }

    private void saveLangIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }
}
