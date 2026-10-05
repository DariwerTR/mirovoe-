package com.wildlands.util;

import java.lang.reflect.Method;
import net.minecraft.world.level.block.Block;

/**
 * Запасной способ включить прозрачные вырезы листвы (cutout) на клиенте.
 * Основной способ это "render_type" в JSON-модели; этот вызов страхует на случай, если сборка Forge его не применяет.
 * Всё через рефлексию и в try/catch: на сервере и при другой сигнатуре метода ничего не происходит и ничего не ломается.
 */
public final class RenderLayerHook {
    private RenderLayerHook() {
    }

    public static void cutout(Block block) {
        try {
            Class<?> types = Class.forName("net.minecraft.client.renderer.ItemBlockRenderTypes");
            for (Method m : types.getDeclaredMethods()) {
                if (!m.getName().equals("setRenderLayer") || m.getParameterCount() != 2
                        || !m.getParameterTypes()[0].isAssignableFrom(Block.class)) {
                    continue;
                }
                Class<?> layer = m.getParameterTypes()[1];
                Object value = null;
                if (layer.isEnum()) {
                    for (Object c : layer.getEnumConstants()) {
                        String n = ((Enum<?>) c).name();
                        if (n.equals("CUTOUT_MIPPED")) {
                            value = c;
                        } else if (n.equals("CUTOUT") && value == null) {
                            value = c;
                        }
                    }
                } else if (layer.getName().endsWith("RenderType")) {
                    value = layer.getMethod("cutoutMipped").invoke(null);
                }
                if (value != null) {
                    m.setAccessible(true);
                    m.invoke(null, block, value);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // нет клиентских классов (сервер) или другая версия API
        }
    }
}
