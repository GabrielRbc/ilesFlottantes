package fr.Gabilou.ilesFlottantes.commandes;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.Gabilou.ilesFlottantes.modeles.game.GameManager;
import fr.Gabilou.ilesFlottantes.modeles.game.GameSession;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Commandes administratives du cycle de partie ; tous les retours viennent de messages.yml. */
public final class GameCommand {
    private final GameManager game;
    private final MessageManager messages;
    private final Runnable refresh;
    private final Logger logger;

    public GameCommand(GameManager game, MessageManager messages, Runnable refresh, Logger logger) {
        this.game = game;
        this.messages = messages;
        this.refresh = refresh;
        this.logger = logger;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        var root = Commands.literal("game")
                .requires(source -> source.getSender().hasPermission(CommandSupport.ADMIN))
                .executes(context -> { context.getSource().getSender().sendMessage(messages.get("game.usage")); return 1; });
        for (String action : new String[]{"start", "pause", "stop", "reset"}) {
            root.then(Commands.literal(action).executes(context -> execute(context.getSource().getSender(), action)));
        }
        root.then(Commands.literal("time")
                .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 525600))
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();
                            int minutes = IntegerArgumentType.getInteger(context, "minutes");
                            if (!game.getSession().setTimeLimit(minutes)) {
                                sender.sendMessage(messages.get("game.time-locked"));
                                return 0;
                            }
                            sender.sendMessage(messages.get(minutes == 0 ? "game.time-unlimited" : "game.time-set", minutes));
                            refresh.run();
                            return 1;
                        })));
        return root.build();
    }

    private int execute(CommandSender sender, String action) {
        GameSession session = game.getSession();
        boolean resuming = session.getState() == GameSession.State.PAUSED;
        boolean changed;
        try {
            changed = switch (action) {
                case "start" -> game.start();
                case "pause" -> session.pause();
                case "stop" -> session.stop();
                case "reset" -> { game.reset(); yield true; }
                default -> false;
            };
        } catch (IOException e) {
            logger.log(Level.SEVERE, messages.text("system.reset-failed"), e);
            sender.sendMessage(messages.get("game.reset-failed"));
            return 0;
        }
        if (!changed) {
            sender.sendMessage(messages.get("game.invalid-transition", messages.text("game.states." + session.getState().name())));
            return 0;
        }
        String key = action.equals("start") && resuming ? "resume" : action;
        Bukkit.broadcastMessage(messages.get("game." + key + "-broadcast"));
        refresh.run();
        return 1;
    }
}
