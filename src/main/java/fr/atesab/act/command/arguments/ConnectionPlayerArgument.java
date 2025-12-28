package fr.atesab.act.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fr.atesab.act.ACTMod;
import fr.atesab.act.utils.ACTUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ConnectionPlayerArgument implements ArgumentType<PlayerInfo[]> {
    public static final DynamicCommandExceptionType ERROR_UNKNOW_PLAYER = new DynamicCommandExceptionType(
            name -> Component.translatable("cmd.act.argument.player.unknown", name));
    public static final SimpleCommandExceptionType ERROR_NO_CONN = new SimpleCommandExceptionType(
            Component.translatable("cmd.act.argument.player.noconnection"));

    public static ConnectionPlayerArgument player() {
        return new ConnectionPlayerArgument(false);
    }

    public static ConnectionPlayerArgument players() {
        return new ConnectionPlayerArgument(true);
    }

    public static PlayerInfo getPlayer(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, PlayerInfo[].class)[0];
    }

    public static PlayerInfo[] getPlayers(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, PlayerInfo[].class);
    }

    private final boolean multiple;

    private ConnectionPlayerArgument(boolean multiple) {
        this.multiple = multiple;
    }

    @Override
    public PlayerInfo[] parse(StringReader reader) throws CommandSyntaxException {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) {
            throw ERROR_NO_CONN.createWithContext(reader);
        }

        if (multiple) {
            String end = reader.getRemaining();
            reader.setCursor(reader.getTotalLength());
            var playerNames = end.split(" ");
            var players = new PlayerInfo[playerNames.length];
            for (int i = 0; i < playerNames.length; i++) {
                var name = playerNames[i];
                players[i] = conn.getPlayerInfo(name);

                if (players[i] == null) {
                    throw ERROR_UNKNOW_PLAYER.createWithContext(reader, name);
                }
            }
            return players;

        } else {
            var playerName = reader.readString();
            var info = conn.getPlayerInfo(playerName);

            if (info == null) {
                throw ERROR_UNKNOW_PLAYER.createWithContext(reader, playerName);
            }

            return new PlayerInfo[]{info};
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            for (var info : conn.getOnlinePlayers()) {
                builder.suggest(info.getProfile().name());
            }
        }
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return ACTUtils.applyAndGet(new ArrayList<>(), e -> {
            e.add(Minecraft.getInstance().getUser().getName());
            e.add("Notch");
            e.addAll(List.of(ACTMod.getModAuthorsArray()));
        });
    }
}
