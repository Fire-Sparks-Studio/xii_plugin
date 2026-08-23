package com.mceteams.xii.manager;

import com.mceteams.xii.enums.Lang;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerDataManager {
    private final NamespacedKey langKey;
    private final JavaPlugin plugin;

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.langKey = new NamespacedKey(plugin, "player_lang");
    }

    public Lang getLang(Player player) {
        Lang lang = loadLang(player.getUniqueId().toString());
        if (lang != null) return lang;

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String value = pdc.get(langKey, PersistentDataType.STRING);
        if ("EN".equals(value)) return Lang.EN;
        return Lang.FR;
    }

    public void setLang(Player player, Lang lang) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(langKey, PersistentDataType.STRING, lang.name());

        FileConfiguration config = plugin.getConfig();
        config.set("player_langs." + player.getUniqueId().toString(), lang.name());
        plugin.saveConfig();
    }

    public void loadAllLangs() {
        // Preload from config on startup if needed
        plugin.getConfig().getConfigurationSection("player_langs");
    }

    private Lang loadLang(String uuid) {
        FileConfiguration config = plugin.getConfig();
        String value = config.getString("player_langs." + uuid);
        if ("EN".equals(value)) return Lang.EN;
        if ("FR".equals(value)) return Lang.FR;
        return null;
    }
}
