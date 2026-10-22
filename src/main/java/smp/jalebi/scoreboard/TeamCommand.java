package smp.jalebi.scoreboard;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class TeamCommand implements CommandExecutor, TabCompleter {
    private final JalebiScoreboard plugin;
    private final TeamGui gui;

    public TeamCommand(JalebiScoreboard plugin, TeamGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }
        TeamManager tm = plugin.teams();

        if (a.length == 0) {
            gui.openMain(p);
            return true;
        }

        switch (a[0].toLowerCase()) {
            case "home" -> {
                String err = tm.teleportHome(p);
                if (err != null) tm.msg(p, err, NamedTextColor.RED);
                else tm.msg(p, "Teleporting to your home...", NamedTextColor.GREEN);
            }
            case "sethome" -> {
                String err = tm.setHome(p);
                if (err != null) tm.msg(p, err, NamedTextColor.RED);
                else tm.msg(p, "Home saved at your location!", NamedTextColor.GREEN);
            }
            case "invite" -> {
                if (a.length < 2) {
                    tm.msg(p, "Usage: /team invite <player>", NamedTextColor.RED);
                    break;
                }
                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null) {
                    tm.msg(p, "That player is not online.", NamedTextColor.RED);
                    break;
                }
                String err = tm.invite(p, target);
                if (err != null) tm.msg(p, err, NamedTextColor.RED);
                else tm.msg(p, "Invite sent to " + target.getName() + ".", NamedTextColor.GREEN);
            }
            case "leave" -> {
                String err = tm.leave(p.getUniqueId());
                if (err != null) tm.msg(p, err, NamedTextColor.RED);
                else tm.msg(p, "You left the team.", NamedTextColor.YELLOW);
            }
            default -> tm.msg(p, "Usage: /team [home|sethome|invite <player>|leave]", NamedTextColor.RED);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String o : List.of("home", "sethome", "invite", "leave")) {
                if (o.startsWith(a[0].toLowerCase())) out.add(o);
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("invite")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(a[1].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
