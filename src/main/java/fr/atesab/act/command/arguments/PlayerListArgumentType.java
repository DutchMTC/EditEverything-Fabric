package fr.atesab.act.command.arguments;

import com.mojang.brigadier.context.CommandContext;
import fr.atesab.act.ACTMod;
import fr.atesab.act.utils.ACTUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayList;
import java.util.List;

public class PlayerListArgumentType extends StringListArgumentType {

    public PlayerListArgumentType() {
        super(() -> {
            var l = new ArrayList<String>();
            ClientPacketListener co = Minecraft.getInstance().getConnection();
            if (co != null) {
                co.getOnlinePlayers().forEach(p -> l.add(p.getProfile().name()));
            }
            var n = Minecraft.getInstance().getUser().getName();
            if (!l.contains(n)) {
                l.add(n); // add our username if not here
            }
            return l;
        }, ACTUtils.applyAndGet(new ArrayList<>(), e -> {
            e.add(Minecraft.getInstance().getUser().getName());
            e.add("Notch");
            e.addAll(List.of(ACTMod.getModAuthorsArray()));
        }), true);
    }

    public static PlayerListArgumentType playerList() {
        return new PlayerListArgumentType();
    }

    public static String[] getPlayerList(final CommandContext<?> context, final String name) {
        return getStringList(context, name);
    }
}
