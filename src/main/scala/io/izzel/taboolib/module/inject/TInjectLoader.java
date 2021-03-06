package io.izzel.taboolib.module.inject;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.izzel.taboolib.TabooLibAPI;
import io.izzel.taboolib.TabooLibLoader;
import io.izzel.taboolib.module.command.lite.CommandBuilder;
import io.izzel.taboolib.module.config.TConfig;
import io.izzel.taboolib.module.locale.TLocaleLoader;
import io.izzel.taboolib.module.locale.logger.TLogger;
import io.izzel.taboolib.module.packet.TPacketHandler;
import io.izzel.taboolib.module.packet.TPacketListener;
import io.izzel.taboolib.util.Ref;
import io.izzel.taboolib.util.Strings;
import io.izzel.taboolib.util.lite.cooldown.Cooldown;
import io.izzel.taboolib.util.lite.cooldown.Cooldowns;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author sky
 * @since 2018-10-05 13:40
 */
public class TInjectLoader implements TabooLibLoader.Loader {

    private static final Map<Class<?>, TInjectTask> injectTypes = Maps.newLinkedHashMap();

    static {
        // Instance Inject
        injectTypes.put(Plugin.class, (plugin, field, args, pluginClass, instance) -> {
            try {
                Ref.putField(instance, field, plugin);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        // TLogger Inject
        injectTypes.put(TLogger.class, (plugin, field, args, pluginClass, instance) -> {
            try {
                Ref.putField(instance, field, args.value().length == 0 ? TLogger.getUnformatted(plugin) : TLogger.getUnformatted(args.value()[0]));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        // TPacketListener Inject
        injectTypes.put(TPacketListener.class, (plugin, field, args, pluginClass, instance) -> {
            try {
                TPacketHandler.addListener(plugin, ((TPacketListener) Ref.getField(instance, field)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        // TConfiguration Inject
        injectTypes.put(TConfig.class, (plugin, field, args, pluginClass, instance) -> {
            try {
                TConfig config = TConfig.create(plugin, args.value().length == 0 ? "config.yml" : args.value()[0]);
                Ref.putField(instance, field, config);
                if (Strings.nonEmpty(args.locale())) {
                    config.listener(() -> {
                        List<String> localePriority = Lists.newArrayList();
                        if (config.isList(args.locale())) {
                            localePriority.addAll(config.getStringList(args.locale()));
                        } else {
                            localePriority.add(String.valueOf(config.get(args.locale())));
                        }
                        TLocaleLoader.setLocalePriority(plugin, localePriority);
                        TLocaleLoader.load(plugin, true, true);
                    }).runListener();
                }
                if (Strings.nonEmpty(args.reload())) {
                    try {
                        Ref.getDeclaredMethods(pluginClass).forEach(method -> {
                            if (method.getName().equals(args.reload())) {
                                method.setAccessible(true);
                                config.listener(() -> {
                                    try {
                                        method.invoke(instance);
                                    } catch (NullPointerException ignored) {
                                    } catch (Throwable t) {
                                        t.printStackTrace();
                                    }
                                });
                            }
                        });
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
                if (args.migrate()) {
                    config.migrate();
                }
                TabooLibLoader.runTask(config::runListener);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        // CommandBuilder Inject
        injectTypes.put(CommandBuilder.class, (plugin, field, args, pluginClass, instance) -> {
            try {
                CommandBuilder builder = Ref.getField(instance, field, CommandBuilder.class);
                if (builder != null && !builder.isBuild()) {
                    if (builder.isSimpleMode()) {
                        builder.command(field.getName());
                    }
                    if (builder.getPlugin() == null) {
                        builder.plugin(plugin);
                    }
                    builder.build();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        // CooldownPack Inject
        injectTypes.put(Cooldown.class, (plugin, field, args, pluginClass, instance) -> {
            try {
                Cooldowns.register((Cooldown) Objects.requireNonNull(Ref.getField(instance, field)), plugin);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
        // PluginExists Inject
        injectTypes.put(Boolean.TYPE, (plugin, field, args, pluginClass, instance) -> {
            try {
                if (args.value().length > 0) {
                    Ref.putField(instance, field, Bukkit.getPluginManager().getPlugin(args.value()[0]) != null);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
        // PluginHook Inject
        injectTypes.put(JavaPlugin.class, (plugin, field, args, pluginClass, instance) -> {
            try {
                if (args.value().length > 0) {
                    Ref.putField(instance, field, Bukkit.getPluginManager().getPlugin(args.value()[0]));
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    @Override
    public int priority() {
        return -999;
    }

    @Override
    public void preLoad(Plugin plugin, Class<?> pluginClass) {
        for (Field declaredField : pluginClass.getDeclaredFields()) {
            TInject annotation = declaredField.getAnnotation(TInject.class);
            if (annotation == null || !declaredField.getType().equals(plugin.getClass())) {
                continue;
            }
            Ref.forcedAccess(declaredField);
            TInjectHelper.getInstance(declaredField, pluginClass, plugin).forEach(instance -> inject(plugin, declaredField, instance, annotation, injectTypes.get(Plugin.class), pluginClass));
        }
    }

    @Override
    public void postLoad(Plugin plugin, Class<?> pluginClass) {
        for (Field declaredField : pluginClass.getDeclaredFields()) {
            TInject annotation = declaredField.getAnnotation(TInject.class);
            if (annotation == null || declaredField.getType().equals(plugin.getClass())) {
                continue;
            }
            Ref.forcedAccess(declaredField);
            TInjectTask tInjectTask = injectTypes.get(declaredField.getType());
            if (tInjectTask != null) {
                TInjectHelper.getInstance(declaredField, pluginClass, plugin).forEach(instance -> inject(plugin, declaredField, instance, annotation, tInjectTask, pluginClass));
            }
        }
    }

    public void inject(Plugin plugin, Field field, Object instance, TInject annotation, TInjectTask injectTask, Class pluginClass) {
        try {
            injectTask.run(plugin, field, annotation, pluginClass, instance);
            TabooLibAPI.debug(field.getName() + " injected. (" + field.getType().getName() + ")");
        } catch (Throwable e) {
            TLogger.getGlobalLogger().error(field.getName() + " inject failed: " + e.getMessage() + " (" + field.getName() + ")");
            if (e.getMessage() == null) {
                e.printStackTrace();
            }
        }
    }
}
