package me.skymc.taboolib.string.language2;

import java.io.File;
import java.io.FileNotFoundException;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import me.skymc.taboolib.fileutils.ConfigUtils;

/**
 * @author sky
 * @since 2018年2月13日 下午2:37:07
 */
public class Language2 {
	
	@Getter
	private FileConfiguration configuration;
	@Getter
	private File languageFile;
	@Getter
	private File languageFolder;
	@Getter
	private Plugin plugin;
	@Getter
	private String languageName;
	
	public Language2(Plugin plugin) {
		this("zh_CN", plugin);
	}
	
	public Language2(String languageName, Plugin plugin) {
		this.languageName = languageName;
		this.plugin = plugin;
		reload(languageName);
	}
	
	public Language2Value get(String key) {
		return new Language2Value(this, key);
	}
	
	public Language2Value get(String key, String... placeholder) {
		Language2Value value = new Language2Value(this, key);
		for (int i = 0 ; i < placeholder.length ; i++) {
			value.addPlaceholder("$" + i, placeholder[i]);
		}
		return value;
	}
	
	public void reload() {
		reload(this.languageName);
	}
	
	public void reload(String languageName) {
		createFolder(plugin);
		languageName = formatName(languageName);
		languageFile = new File(languageFolder, languageName);
		if (!languageFile.exists()) {
			if (plugin.getResource("Language2/" + languageName) == null) {
				try {
					throw new FileNotFoundException("语言文件 " + languageName + " 不存在");
				} catch (Exception ignored) {
				}
			} else {
				plugin.saveResource("Language2/" + languageName, true);
			}
		}
		configuration = ConfigUtils.load(plugin, languageFile);
	}
	
	public String setPlaceholderAPI(Player player, String string) {
		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null && player != null) {
			return PlaceholderAPI.setPlaceholders(player, string);
		}
		return string;
	}
	
	private String formatName(String name) {
		return name.contains(".yml") ? name : name + ".yml";
	}
	
	private void createFolder(Plugin plugin) {
		languageFolder = new File(plugin.getDataFolder(), "Language2");
		if (!languageFolder.exists()) {
			languageFolder.mkdir();
		}
	}
}
