package dev.anyenchant;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final AnyEnchantPlugin plugin;
    private YamlConfiguration active = new YamlConfiguration();      // ไฟล์ในโฟลเดอร์ปลั๊กอิน (แก้ได้)
    private YamlConfiguration bundledLang = new YamlConfiguration(); // ค่าเริ่มต้นในไฟล์ jar ของภาษาเดียวกัน
    private YamlConfiguration fallback = new YamlConfiguration();    // ค่าเริ่มต้นภาษาอังกฤษ

    public Messages(AnyEnchantPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String lang = plugin.getConfig().getString("language", "th").toLowerCase(Locale.ROOT);
        File file = new File(plugin.getDataFolder(), "messages_" + lang + ".yml");
        active = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        bundledLang = loadBundled("messages_" + lang + ".yml");
        fallback = loadBundled("messages_en.yml");
    }

    private YamlConfiguration loadBundled(String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                return new YamlConfiguration();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not read bundled " + name + ": " + ex.getMessage());
            return new YamlConfiguration();
        }
    }

    /** pairs = key1, value1, key2, value2 ... แทนที่ {key} ในข้อความ */
    public Component get(String key, String... pairs) {
        String raw = active.getString(key, bundledLang.getString(key, fallback.getString(key, key)));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return LEGACY.deserialize(raw);
    }

    public void send(CommandSender to, String key, String... pairs) {
        to.sendMessage(get("prefix").append(get(key, pairs)));
    }

    public void sendRaw(CommandSender to, String key, String... pairs) {
        to.sendMessage(get(key, pairs));
    }
}
