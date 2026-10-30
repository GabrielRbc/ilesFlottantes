package fr.Gabilou.ilesFlottantes;

import fr.Gabilou.ilesFlottantes.commandes.GameCommand;
import fr.Gabilou.ilesFlottantes.commandes.TeamCommand;
import fr.Gabilou.ilesFlottantes.commandes.EconomyCommand;
import fr.Gabilou.ilesFlottantes.modeles.economy.EconomyManager;
import fr.Gabilou.ilesFlottantes.persistence.FileBalanceRepository;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import org.bukkit.plugin.java.JavaPlugin;
import fr.Gabilou.ilesFlottantes.interfaces.GameDisplay;
import fr.Gabilou.ilesFlottantes.listeners.GameListener;
import fr.Gabilou.ilesFlottantes.modeles.game.GameManager;
import fr.Gabilou.ilesFlottantes.modeles.game.GameSession;
import fr.Gabilou.ilesFlottantes.modeles.territories.*;
import fr.Gabilou.ilesFlottantes.persistence.FileTerritoryRepository;
import fr.Gabilou.ilesFlottantes.commandes.ZoneCommand;
import fr.Gabilou.ilesFlottantes.commandes.SpawnCommand;
import fr.Gabilou.ilesFlottantes.interfaces.RaidDisplay;
import fr.Gabilou.ilesFlottantes.listeners.TerritoryProtectionListener;
import fr.Gabilou.ilesFlottantes.listeners.TeamSpawnListener;
import fr.Gabilou.ilesFlottantes.modeles.results.FinalResults;
import fr.Gabilou.ilesFlottantes.interfaces.ResultsController;
import fr.Gabilou.ilesFlottantes.interfaces.SpectatorTour;
import fr.Gabilou.ilesFlottantes.listeners.BossPointsListener;
import fr.Gabilou.ilesFlottantes.commandes.VoteCommand;
import io.papermc.paper.command.brigadier.Commands;
import fr.Gabilou.ilesFlottantes.persistence.SessionPersistence;
import fr.Gabilou.ilesFlottantes.persistence.FileSessionRepository;

/**
 * Point d’entrée du plugin Paper : initialise les équipes, les messages et les caisses,
 * puis enregistre les commandes via le cycle de vie du serveur.
 * <p>Les dépendances sont injectées dans les commandes. Une erreur de chargement
 * des caisses désactive le plugin afin de préserver les données existantes.</p>
 */
public class IlesFlottantesPlugin extends JavaPlugin {

    private TeamManager teamManager;
    private MessageManager messageManager;
    private GameDisplay display;
    private RaidDisplay raids;
    private ResultsController resultsController;
    private SessionPersistence sessionPersistence;

    @Override
    public void onEnable() {
        this.messageManager = new MessageManager(this);
        this.teamManager = new TeamManager(messageManager.text("territories.common-team"));

        final EconomyManager economy;
        try {
            economy = new EconomyManager(new FileBalanceRepository(getDataFolder().toPath().resolve("balances.properties")));
        } catch (java.io.IOException e) {
            getLogger().log(java.util.logging.Level.SEVERE, messageManager.text("system.load-failed"), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        final TerritoryManager territories;
        try {
            territories = new TerritoryManager(new FileTerritoryRepository(getDataFolder().toPath().resolve("territories.properties")),
                    teamManager.getTeams().stream().map(fr.Gabilou.ilesFlottantes.modeles.teams.TeamModel::getName).collect(java.util.stream.Collectors.toSet()));
        } catch (java.io.IOException e) {
            getLogger().log(java.util.logging.Level.SEVERE, messageManager.text("system.territories-load-failed"), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        SpawnService spawns = new SpawnService(teamManager, territories);
        GameSession session = new GameSession();
        FinalResults results = new FinalResults();
        SpectatorTour tour = new SpectatorTour();
        resultsController = new ResultsController(results, session, teamManager, economy, messageManager, tour);
        GameManager game = new GameManager(session, teamManager, economy, spawns, territories, resultsController::reset);
        sessionPersistence = new SessionPersistence(new FileSessionRepository(getDataFolder().toPath().resolve("session.json")),
                session, teamManager, results, resultsController, tour, spawns, messageManager, getLogger());
        try {
            if (sessionPersistence.load(game)) getLogger().info(messageManager.text("session.restored"));
        } catch (java.io.IOException e) {
            getLogger().log(java.util.logging.Level.SEVERE, messageManager.text("session.load-failed"), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        raids = new RaidDisplay(territories, messageManager);
        display = new GameDisplay(teamManager, economy, session, messageManager, results);
        getServer().getPluginManager().registerEvents(tour, this);
        getServer().getPluginManager().registerEvents(new BossPointsListener(session, teamManager, results, messageManager), this);
        getServer().getPluginManager().registerEvents(new GameListener(session, teamManager, display), this);
        getServer().getPluginManager().registerEvents(new TerritoryProtectionListener(territories, teamManager, messageManager), this);
        getServer().getPluginManager().registerEvents(new TeamSpawnListener(this, session, teamManager, territories, spawns, messageManager), this);
        ZoneCommand zoneCommand = new ZoneCommand(territories, teamManager, messageManager, getLogger());
        getServer().getPluginManager().registerEvents(zoneCommand, this);
        EconomyCommand economyCommand = new EconomyCommand(teamManager, economy, messageManager, getLogger(), session);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(zoneCommand.build(), messageManager.text("commands.zone"));
            event.registrar().register(new SpawnCommand(territories, spawns, teamManager, messageManager, getLogger()).build(), messageManager.text("commands.spawn"));
            event.registrar().register(new GameCommand(game, messageManager, display::refresh, getLogger(), resultsController).build(), messageManager.text("commands.game"));
            event.registrar().register(new TeamCommand(teamManager, economy, messageManager, session).build(), messageManager.text("commands.team"));
            event.registrar().register(new VoteCommand(results, teamManager, messageManager).build(), messageManager.text("commands.vote"));
            event.registrar().register(Commands.literal("points").executes(ctx -> {
                resultsController.showRanking(ctx.getSource().getSender()); return 1;
            }).build(), messageManager.text("commands.points"));
            event.registrar().register(economyCommand.payCommand(), messageManager.text("commands.pay"));
            event.registrar().register(economyCommand.setCommand(), messageManager.text("commands.set"));
            event.registrar().register(economyCommand.balanceCommand(), messageManager.text("commands.balance"));
        });
        display.start(this, () -> {
            raids.tick();
            if (session.finishIfExpired()) {
                getServer().broadcastMessage(messageManager.get("game.time-ended"));
            }
            resultsController.tick();
            sessionPersistence.checkpoint();
        });

        getLogger().info(messageManager.text("system.enabled"));
    }

    @Override
    public void onDisable() {
        if (sessionPersistence != null) sessionPersistence.shutdown();
        if (raids != null) raids.stop();
        if (display != null) display.stop();
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }
}
