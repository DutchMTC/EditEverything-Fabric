package com.dutchmtc.ee.internalcommand;

import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.lang.reflect.Method;

class AbstractInternalCommand {
    Class<?> module;
    String name;
    Method method;

    AbstractInternalCommand(Class<?> module, String name, Method method) {
        this.module = module;
        this.name = name;
        this.method = method;
    }

    String getCommandRealName() {
        return module.getName() + "#" + method.getName();
    }

    CommandNode<CommandSourceStack> buildNode() {
        var node = Commands.literal(name);

        // var params = module.getTypeParameters();

        // if (params.length == 0) {
        // return node;
        // }

        // for (var typeVariable : params) {
        // var argName = typeVariable.getName();
        // var argType = typeVariable.getBounds();

        // }

        return node.build();
    }
}
