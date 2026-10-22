package smp.jalebi.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class JalebiScoreboard extends JavaPlugin implements Listener {
    private DataStore data;
    private TeamManager teams;
    private BoardManager boards;
    private ScoreboardGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new DataStore(this);
        teams = new TeamManager(this);
        boards = new BoardManager(this);
        gui = new ScoreboardGui(this);

        IconChooser icons = new IconChooser(this);
        TeamGui teamGui = new TeamGui(this, icons);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(icons, this);
        Bukkit.getPluginManager().registerEvents(new TeamListener(teams), this);

        TeamCommand teamCmd = new TeamCommand(this, teamGui);
        getCommand("team").setExecutor(teamCmd);
        getCommand("team").setTabCompleter(teamCmd);

        // refresh every second
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) boards.update(p);
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        data.save();
        teams.save();
    }

    public DataStore data() { return data; }
    public BoardManager boards() { return boards; }
    public TeamManager teams() { return teams; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> boards.update(e.getPlayer()), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.forget(e.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }
        gui.open(p);
        return true;
    }
}
