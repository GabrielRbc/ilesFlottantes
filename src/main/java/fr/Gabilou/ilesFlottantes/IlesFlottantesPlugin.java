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

    @Override
    public void onEnable() {
        this.messageManager = new MessageManager(this);
        this.teamManager = new TeamManager();

        final EconomyManager economy;
        try {
            economy = new EconomyManager(new FileBalanceRepository(getDataFolder().toPath().resolve("balances.properties")));
        } catch (java.io.IOException e) {
            getLogger().log(java.util.logging.Level.SEVERE, messageManager.text("system.load-failed"), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        GameSession session = new GameSession();
        GameManager game = new GameManager(session, teamManager, economy);
        display = new GameDisplay(teamManager, economy, session, messageManager);
        getServer().getPluginManager().registerEvents(new GameListener(session, teamManager, display), this);
        EconomyCommand economyCommand = new EconomyCommand(teamManager, economy, messageManager, getLogger(), session);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(new GameCommand(game, messageManager, display::refresh, getLogger()).build(), messageManager.text("commands.game"));
            event.registrar().register(new TeamCommand(teamManager, economy, messageManager).build(), messageManager.text("commands.team"));
            event.registrar().register(economyCommand.payCommand(), messageManager.text("commands.pay"));
            event.registrar().register(economyCommand.setCommand(), messageManager.text("commands.set"));
            event.registrar().register(economyCommand.balanceCommand(), messageManager.text("commands.balance"));
        });
        display.start(this, () -> {
            if (session.finishIfExpired()) {
                getServer().broadcastMessage(messageManager.get("game.time-ended"));
            }
        });

        getLogger().info(messageManager.text("system.enabled"));
    }

    @Override
    public void onDisable() {
        if (display != null) display.stop();
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }
}
