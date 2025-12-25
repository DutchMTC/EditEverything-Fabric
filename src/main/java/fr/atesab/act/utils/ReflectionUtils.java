package fr.atesab.act.utils;

import fr.atesab.act.internalcommand.InternalCommandModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * A set of tools to help with reflection
 *
 * @author ATE47
 * @since 2.4.0
 */
@InternalCommandModule(name = "reflection")
public class ReflectionUtils {

    /**
     * fetch all the superclasses without Object.class
     *
     * @param current the current class
     * @return the list of the superclasses
     */
    public static List<Class<?>> superClassTo(Class<?> current) {
        return superClassTo(current, Object.class);
    }

    /**
     * fetch all the superclasses and stop at the end or Object.class if current
     * isn't a parent of end without end or Object.class
     *
     * @param current the current class
     * @param end     the end class
     * @return the list of the superclasses
     */
    public static List<Class<?>> superClassTo(Class<?> current, Class<?> end) {
        var stack = new ArrayList<Class<?>>();

        if (end == null || end == Object.class)
            return stack;

        var c = current;

        do {
            stack.add(c);
            c = c.getSuperclass();
        } while (c != Object.class && c != end);

        return stack;
    }

    public static void setRightClickDelay(Minecraft mc, int delay) {
        try {
            Field f;
            try {
                f = Minecraft.class.getDeclaredField("field_1728");
            } catch (NoSuchFieldException e) {
                f = Minecraft.class.getDeclaredField("rightClickDelay");
            }
            f.setAccessible(true);
            f.setInt(mc, delay);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setDestroyDelay(MultiPlayerGameMode gameMode, int delay) {
        try {
            Field f;
            try {
                f = MultiPlayerGameMode.class.getDeclaredField("field_3719");
            } catch (NoSuchFieldException e) {
                f = MultiPlayerGameMode.class.getDeclaredField("destroyDelay");
            }
            f.setAccessible(true);
            f.setInt(gameMode, delay);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ReflectionUtils() {
    }
}
