package com.dutchmtc.ee.internalcommand;

import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;

import java.util.HashMap;
import java.util.Map;

public class InternalCommandExecutor {
    private final Map<String, AbstractInternalCommand> commands = new HashMap<>();

    public void registerModule(Class<?> module) {
        var mic = module.getAnnotation(InternalCommandModule.class);

        var prefix = mic != null ? (mic.useBaseName() ? module.getCanonicalName() : mic.name())
                : module.getCanonicalName();

        if (!prefix.isEmpty()) {
            prefix += ".";
        }

        for (var method : module.getDeclaredMethods()) {
            var ic = method.getAnnotation(InternalCommand.class);
            if (ic != null) {
                var name = ic.name();
                if (name.isEmpty()) {
                    name = method.getName();
                }

                name = prefix + name;

                var aic = new AbstractInternalCommand(module, name, method);

                var old = commands.get(name);

                if (old != null) {
                    throw new IllegalArgumentException("The method " + aic.getCommandRealName()
                            + " has the same @InternalCommand name as " + old.getCommandRealName());
                }

                commands.put(name, aic);
            }
        }
    }

    public void registerAll(CommandNode<CommandSourceStack> node) {
        commands.values().stream().map(AbstractInternalCommand::buildNode).forEach(node::addChild);
    }
}
