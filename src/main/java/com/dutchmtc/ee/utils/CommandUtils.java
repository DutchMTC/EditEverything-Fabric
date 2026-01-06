package com.dutchmtc.ee.utils;

import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.internalcommand.InternalCommand;
import com.dutchmtc.ee.internalcommand.InternalCommandModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * A set of tools to help to create commands
 *
 * @author ATE47
 * @since 2.0
 */
@InternalCommandModule(name = "command")
public class CommandUtils {
    private static final Random RANDOM = EEMod.RANDOM;

    public static <T> T getRandomElement(T[] array) {
        return array[RANDOM.nextInt(array.length)];
    }

    /**
     * Get all visible players in tab
     *
     * @return players' names
     * @since 2.0
     */
    @InternalCommand(name = "playerlist")
    public static List<String> getPlayerList() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        List<PlayerInfo> networkPlayerInfos = new ArrayList<>(
                player.connection.getOnlinePlayers());
        networkPlayerInfos.sort((o1, o2) -> Objects.requireNonNull(o1.getGameMode()).getName().compareToIgnoreCase(Objects.requireNonNull(o2.getGameMode()).getName()));
        List<String> players = new ArrayList<>();
        for (PlayerInfo info : networkPlayerInfos) {
            players.add(Objects.requireNonNull(info.getGameMode()).getName());
        }
        return players;
    }

    /**
     * Get all sub completion argument
     *
     * @param options Possible sub arguments
     * @param args    command arguments
     * @return List of string who match sorted by name
     * @since 2.0
     * @deprecated
     */
    @Deprecated
    public static List<String> getTabCompletion(List<String> options, String[] args) {
        List<String> optionsEnd = new ArrayList<>();
        if (args.length == 0)
            return optionsEnd;
        String start = args[args.length - 1].toLowerCase();
        for (String option : options) {
            if (option.toLowerCase().startsWith(start.toLowerCase()))
                optionsEnd.add(option);
        }
        optionsEnd.sort(String::compareToIgnoreCase); // sort by name
        return optionsEnd;
    }

    /**
     * Send a message in chat like with the client (Client command will be executed)
     *
     * @param message chat message to send
     * @since 2.0
     */
    @InternalCommand(name = "send")
    public static void sendMessage(String message) {
        LocalPlayer p;
        if ((p = Minecraft.getInstance().player) != null) {
            if (message.startsWith("/")) {
                p.connection.sendCommand(message.substring(1));
            } else {
                p.connection.sendChat(message);
            }
        }
    }

}
