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
import fr.Gabilou.ilesFlottantes.modeles.territories.SpawnService;
import fr.Gabilou.ilesFlottantes.interfaces.ResultsController;

/** Commandes administratives du cycle de partie ; tous les retours viennent de messages.yml. */
public final class GameCommand {
    private final GameManager game;
    private final MessageManager messages;
    private final Runnable refresh;
    private final Logger logger;
    private final ResultsController results;

    public GameCommand(GameManager game, MessageManager messages, Runnable refresh, Logger logger, ResultsController results) {
        this.game = game;
        this.messages = messages;
        this.refresh = refresh;
        this.logger = logger;
        this.results = results;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        var root = Commands.literal("game")
                .requires(source -> source.getSender().hasPermission(CommandSupport.ADMIN))
                .executes(context -> { context.getSource().getSender().sendMessage(messages.get("game.usage")); return 1; });
        for (String action : new String[]{"start", "pause", "stop", "reset"}) {
            root.then(Commands.literal(action).executes(context -> execute(context.getSource().getSender(), action)));
        }
        root.then(Commands.literal("save").executes(ctx -> {
            boolean saved = game.save();
            ctx.getSource().getSender().sendMessage(messages.get(saved ? "session.saved" : "session.save-failed"));
            return saved ? 1 : 0;
        }));
        root.then(Commands.literal("vote").then(Commands.literal("end").executes(ctx -> {
            boolean closed = results.closeVoting();
            ctx.getSource().getSender().sendMessage(messages.get(closed ? "results.vote-ended" : "results.vote-not-open"));
            return closed ? 1 : 0;
        })));
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
        if (action.equals("start") && !game.storageHealthy()) {
            sender.sendMessage(messages.get("session.save-failed")); return 0;
        }
        GameSession session = game.getSession();
        boolean resuming = session.getState() == GameSession.State.PAUSED || results.suspended();
        boolean changed;
        try {
            changed = switch (action) {
                case "start" -> results.suspended() ? results.resume() : game.start();
                case "pause" -> session.getState() == GameSession.State.STOPPED ? results.pause() : session.pause();
                case "stop" -> session.stop();
                case "reset" -> { game.reset(); yield true; }
                default -> false;
            };
        } catch (SpawnService.StartFailure e) {
            sender.sendMessage(messages.get("territories.start-failed", String.join(messages.text("general.separator"), e.teams())));
            return 0;
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
