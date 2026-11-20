package com.maidclinic.command;

import com.maidclinic.ClinicConfig;
import com.maidclinic.health.ClinicData;
import com.maidclinic.health.DeathStyle;
import com.maidclinic.util.Msg;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 玩家个人开关（按 UUID 保存在世界存档里）：
 * <ul>
 *     <li>{@code /maidclinic hardcore on|off|default} —— 疾病是否可能致死；</li>
 *     <li>{@code /maidclinic efficiency on|off|default} —— 疾病是否影响工作效率；</li>
 *     <li>{@code /maidclinic sequela on|off|default} —— 死亡复活是否保留后遗症；</li>
 *     <li>{@code /maidclinic deathmode virulence|sleep|default} —— 危重期的掉血方式；</li>
 *     <li>{@code /maidclinic status} —— 查看当前生效值与个人设置。</li>
 * </ul>
 * 不需要管理员权限：设置只影响自己的女仆。配置文件里的值作为“默认值”。
 */
public final class ClinicCommand {
    private ClinicCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("maidclinic")
                .then(Commands.literal("hardcore")
                        .then(Commands.literal("on").executes(context -> setHardcore(context, Boolean.TRUE)))
                        .then(Commands.literal("off").executes(context -> setHardcore(context, Boolean.FALSE)))
                        .then(Commands.literal("default").executes(context -> setHardcore(context, null)))
                        .executes(ClinicCommand::status))
                .then(Commands.literal("chronic")
                        .then(Commands.literal("on").executes(context -> setChronic(context, Boolean.TRUE)))
                        .then(Commands.literal("off").executes(context -> setChronic(context, Boolean.FALSE)))
                        .then(Commands.literal("default").executes(context -> setChronic(context, null)))
                        .executes(ClinicCommand::status))
                .then(Commands.literal("efficiency")
                        .then(Commands.literal("on").executes(context -> setEfficiency(context, Boolean.TRUE)))
                        .then(Commands.literal("off").executes(context -> setEfficiency(context, Boolean.FALSE)))
                        .then(Commands.literal("default").executes(context -> setEfficiency(context, null)))
                        .executes(ClinicCommand::status))
                .then(Commands.literal("sequela")
                        .then(Commands.literal("on").executes(context -> setSequela(context, Boolean.TRUE)))
                        .then(Commands.literal("off").executes(context -> setSequela(context, Boolean.FALSE)))
                        .then(Commands.literal("default").executes(context -> setSequela(context, null)))
                        .executes(ClinicCommand::status))
                .then(Commands.literal("deathmode")
                        .then(Commands.literal("virulence").executes(context -> setDeathStyle(context, DeathStyle.VIRULENCE)))
                        .then(Commands.literal("sleep").executes(context -> setDeathStyle(context, DeathStyle.SLEEP)))
                        .then(Commands.literal("default").executes(context -> setDeathStyle(context, null)))
                        .executes(ClinicCommand::status))
                .then(Commands.literal("status").executes(ClinicCommand::status)));
    }

    private static int setHardcore(CommandContext<CommandSourceStack> context, Boolean value) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClinicData data = ClinicData.get(player.serverLevel());
        data.setHardcoreOverride(player.getUUID(), value);

        Component result = value == null
                ? Msg.tr("command.maidclinic.hardcore.default", valueText(ClinicConfig.ILLNESS_CAN_KILL.get()))
                : Msg.tr(value ? "command.maidclinic.hardcore.on" : "command.maidclinic.hardcore.off");
        context.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int setChronic(CommandContext<CommandSourceStack> context, Boolean value) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClinicData data = ClinicData.get(player.serverLevel());
        data.setChronicOverride(player.getUUID(), value);

        Component result = value == null
                ? Msg.tr("command.maidclinic.chronic.default", valueText(ClinicConfig.ENABLE_CHRONIC.get()))
                : Msg.tr(value ? "command.maidclinic.chronic.on" : "command.maidclinic.chronic.off");
        context.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int setEfficiency(CommandContext<CommandSourceStack> context, Boolean value) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClinicData data = ClinicData.get(player.serverLevel());
        data.setEfficiencyOverride(player.getUUID(), value);

        Component result = value == null
                ? Msg.tr("command.maidclinic.efficiency.default", valueText(ClinicConfig.ENABLE_WORK_EFFICIENCY.get()))
                : Msg.tr(value ? "command.maidclinic.efficiency.on" : "command.maidclinic.efficiency.off");
        context.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int setSequela(CommandContext<CommandSourceStack> context, Boolean value) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClinicData data = ClinicData.get(player.serverLevel());
        data.setSequelaOverride(player.getUUID(), value);

        Component result = value == null
                ? Msg.tr("command.maidclinic.sequela.default", valueText(ClinicConfig.KEEP_SEQUELA_ON_DEATH.get()))
                : Msg.tr(value ? "command.maidclinic.sequela.on" : "command.maidclinic.sequela.off");
        context.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int setDeathStyle(CommandContext<CommandSourceStack> context, DeathStyle value) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClinicData data = ClinicData.get(player.serverLevel());
        data.setDeathStyleOverride(player.getUUID(), value);

        Component result = value == null
                ? Msg.tr("command.maidclinic.deathmode.default",
                        Msg.tr(DeathStyle.byId(ClinicConfig.DEATH_STYLE.get()).translationKey()))
                : Msg.tr("command.maidclinic.deathmode.set", Msg.tr(value.translationKey()));
        context.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClinicData data = ClinicData.get(player.serverLevel());

        Boolean hardcore = data.hardcoreOverride(player.getUUID());
        boolean hardcoreEffective = hardcore != null ? hardcore : ClinicConfig.ILLNESS_CAN_KILL.get();
        Boolean chronic = data.chronicOverride(player.getUUID());
        boolean chronicEffective = chronic != null ? chronic : ClinicConfig.ENABLE_CHRONIC.get();
        Boolean efficiency = data.efficiencyOverride(player.getUUID());
        boolean efficiencyEffective = efficiency != null ? efficiency : ClinicConfig.ENABLE_WORK_EFFICIENCY.get();
        Boolean sequela = data.sequelaOverride(player.getUUID());
        boolean sequelaEffective = sequela != null ? sequela : ClinicConfig.KEEP_SEQUELA_ON_DEATH.get();
        DeathStyle deathStyle = data.deathStyleOverride(player.getUUID());
        DeathStyle deathStyleEffective = deathStyle != null
                ? deathStyle : DeathStyle.byId(ClinicConfig.DEATH_STYLE.get());

        Component hardcoreLine = Msg.tr("command.maidclinic.status.hardcore",
                valueText(hardcoreEffective), personalText(hardcore),
                valueText(ClinicConfig.ILLNESS_CAN_KILL.get()));
        Component chronicLine = Msg.tr("command.maidclinic.status.chronic",
                valueText(chronicEffective), personalText(chronic),
                valueText(ClinicConfig.ENABLE_CHRONIC.get()));
        Component efficiencyLine = Msg.tr("command.maidclinic.status.efficiency",
                valueText(efficiencyEffective), personalText(efficiency),
                valueText(ClinicConfig.ENABLE_WORK_EFFICIENCY.get()));
        Component sequelaLine = Msg.tr("command.maidclinic.status.sequela",
                valueText(sequelaEffective), personalText(sequela),
                valueText(ClinicConfig.KEEP_SEQUELA_ON_DEATH.get()));
        Component deathStyleLine = Msg.tr("command.maidclinic.status.deathmode",
                Msg.tr(deathStyleEffective.translationKey()),
                deathStyle == null
                        ? Msg.tr("command.maidclinic.value.follow_config")
                        : Msg.tr(deathStyle.translationKey()),
                Msg.tr(DeathStyle.byId(ClinicConfig.DEATH_STYLE.get()).translationKey()));
        context.getSource().sendSuccess(() -> hardcoreLine, false);
        context.getSource().sendSuccess(() -> chronicLine, false);
        context.getSource().sendSuccess(() -> efficiencyLine, false);
        context.getSource().sendSuccess(() -> sequelaLine, false);
        context.getSource().sendSuccess(() -> deathStyleLine, false);
        return 1;
    }

    private static Component personalText(Boolean value) {
        return value == null
                ? Msg.tr("command.maidclinic.value.follow_config")
                : valueText(value);
    }

    private static Component valueText(boolean value) {
        return Msg.tr(value ? "command.maidclinic.value.on" : "command.maidclinic.value.off");
    }
}
