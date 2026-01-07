package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import com.dutchmtc.ee.utils.PermissionCompat;
import com.dutchmtc.ee.network.EENetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class ModdedCommandArmorStand extends ModdedCommand {
    private static final double REACH = 6.0D;

    public ModdedCommandArmorStand() {
        super("armorstand", "cmd.ee.armorstand", CommandClickOption.doCommand);
        addAlias("as");
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(LiteralArgumentBuilder<CommandSourceStack> command,
            CommandBuildContext context) {
        return command.requires(PermissionCompat::hasGamemasterPermissions);
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var source = c.getSource();
            ServerPlayer player;
            try {
                player = source.getPlayerOrException();
            } catch (Exception e) {
                source.sendFailure(Component.translatable("cmd.ee.error.playeronly").withStyle(ChatFormatting.RED));
                return 0;
            }

            ArmorStand stand = findLookedAtArmorStand(player, REACH);
            if (stand == null) {
                source.sendFailure(Component.translatable("cmd.ee.armorstand.no_target").withStyle(ChatFormatting.RED));
                return 0;
            }

            EENetworking.sendOpenArmorStandEditor(player, stand.getId());
            return 1;
        };
    }

    private static ArmorStand findLookedAtArmorStand(ServerPlayer player, double reach) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(reach));

        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, start, end, box,
                e -> e instanceof ArmorStand, (float) reach);
        if (hit == null) {
            return null;
        }
        return hit.getEntity() instanceof ArmorStand a ? a : null;
    }
}
