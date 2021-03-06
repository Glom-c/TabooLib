package io.izzel.taboolib.module.config;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.izzel.taboolib.TabooLib;
import io.izzel.taboolib.TabooLibAPI;
import io.izzel.taboolib.module.locale.TLocale;
import io.izzel.taboolib.module.locale.logger.TLogger;
import io.izzel.taboolib.util.Files;
import io.izzel.taboolib.util.Pair;
import io.izzel.taboolib.util.Ref;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TabooLib YAML配置文件操作类
 *
 * @author sky
 * @since 2018-09-08 15:00
 */
public class TConfig extends YamlConfiguration {

    private static final Map<String, List<File>> files = Maps.newHashMap();
    private final Plugin plugin;
    private final File file;
    private final List<Runnable> runnable = Lists.newArrayList();
    private final List<Pair<String, String[]>> migrate = Lists.newArrayList();
    private String path;

    private TConfig(File file, Plugin plugin) {
        files.computeIfAbsent(plugin.getName(), name -> new ArrayList<>()).add(file);
        this.plugin = plugin;
        this.file = file;
        reload();
        TConfigWatcher.getInst().addSimpleListener(this.file, this::reload);
        TabooLibAPI.debug("Loaded TConfiguration \"" + file.getName() + "\" from Plugin \"" + plugin.getName() + "\"");
    }

    @NotNull
    public static Map<String, List<File>> getFiles() {
        return files;
    }

    /**
     * 创建一个 TConfig 配置文件实例
     *
     * @param file 配置文件
     * @return TConfig 实例
     */
    @NotNull
    public static TConfig create(File file) {
        return new TConfig(file, Ref.getCallerPlugin(Ref.getCallerClass(3).orElse(TabooLib.class)));
    }

    /**
     * 创建一个 TConfig 配置文件实例
     *
     * @param file   配置文件
     * @param plugin 插件主类对象
     * @return TConfig 实例
     */
    @NotNull
    public static TConfig create(File file, Plugin plugin) {
        return new TConfig(file, plugin);
    }

    /**
     * 创建一个 TConfig 配置文件实例
     *
     * @param plugin 插件主类实例
     * @param path   配置文件路径
     * @return {@link TConfig}
     */
    @NotNull
    public static TConfig create(Plugin plugin, String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            Files.releaseResource(plugin, path, false);
        }
        TConfig conf = create(file, plugin);
        conf.path = path;
        return conf;
    }

    /**
     * 获取一个上色过的字符串
     * 使用'&amp;'作为样式代码
     *
     * @param path YAML路径
     * @return 上过色的字符串
     */
    @NotNull
    public String getStringColored(@NotNull String path) {
        return TLocale.Translate.setColored(getString(path, ""));
    }

    /**
     * 获取一个上色过的字符串
     * 使用'&amp;'作为样式代码
     *
     * @param path YAML路径
     * @param def  路径不存在时返回的默认值
     * @return 上过色的字符串
     */
    @NotNull
    public String getStringColored(@NotNull String path, @NotNull String def) {
        return TLocale.Translate.setColored(getString(path, def));
    }

    /**
     * 获取一个上色过的字符串列表
     * 使用'&amp;'作为样式代码
     *
     * @param path YAML路径
     * @return 上过色的字符串列表
     */
    @NotNull
    public List<String> getStringListColored(String path) {
        return TLocale.Translate.setColored(getStringList(path));
    }

    /**
     * 释放配置文件监听
     */
    public void release() {
        TConfigWatcher.getInst().removeListener(file);
    }

    /**
     * 重载配置文件
     */
    public void reload() {
        try {
            load(file);
            runListener();
        } catch (IOException | InvalidConfigurationException e) {
            TLogger.getGlobalLogger().warn("Cannot load configuration from stream: " + e.toString());
        }
    }

    /**
     * 保存配置到文件
     */
    public void saveToFile() {
        try {
            save(file);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public TConfig migrate() {
        Preconditions.checkNotNull(path, "path not exists");
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            List<String> migrate = TConfigMigrate.migrate(fileInputStream, Files.getResourceChecked(plugin, path));
            if (migrate != null) {
                Files.write(file, w -> {
                    for (String line : migrate) {
                        w.write(line);
                        w.newLine();
                    }
                });
                load(file);
            }
        } catch (NullPointerException ignored) {
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return this;
    }

    public File getFile() {
        return file;
    }

    public Runnable getListener() {
        return runnable.get(0);
    }

    public List<Runnable> getListeners() {
        return runnable;
    }

    public TConfig listener(Runnable runnable) {
        this.runnable.add(runnable);
        return this;
    }

    public void runListener() {
        for (Runnable listener : runnable) {
            try {
                listener.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
