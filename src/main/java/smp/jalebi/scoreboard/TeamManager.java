package smp.jalebi.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {
    public record Invite(UUID teamId, UUID inviter, long expires) {}

    private static final TextColor BLUE = TextColor.fromHexString("#00A2FF");

    private final JalebiScoreboard plugin;
    private final File file;
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, Team> byPlayer = new HashMap<>();
    private final Map<UUID, List<Invite>> invites = new HashMap<>();

    public TeamManager(JalebiScoreboard plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "teams.yml");
        load();
    }

    // ------------------------------------------------------------ storage

    private void load() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("teams");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) continue;
                Material icon = Material.matchMaterial(s.getString("icon", "GRASS_BLOCK"));
                if (icon == null) icon = Material.GRASS_BLOCK;
                Team t = new Team(UUID.fromString(key), s.getString("name", "Team"), icon,
                        UUID.fromString(s.getString("owner", "")));
                t.members.clear();
                for (String m : s.getStringList("members")) t.members.add(UUID.fromString(m));
                t.members.add(t.owner);
                ConfigurationSection hs = s.getConfigurationSection("homes");
                if (hs != null) {
                    for (String hk : hs.getKeys(false)) {
                        ConfigurationSection h = hs.getConfigurationSection(hk);
                        if (h == null) continue;
                        t.homes.put(UUID.fromString(hk), new Team.Home(
                                h.getString("name", "Home"), h.getString("world", "world"),
                                h.getDouble("x"), h.getDouble("y"), h.getDouble("z"),
                                (float) h.getDouble("yaw"), (float) h.getDouble("pitch")));
                    }
                }
                teams.put(t.id, t);
                for (UUID m : t.members) byPlayer.put(m, t);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not load team " + key + ": " + ex.getMessage());
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Team t : teams.values()) {
            String p = "teams." + t.id;
            cfg.set(p + ".name", t.name);
            cfg.set(p + ".icon", t.icon.name());
            cfg.set(p + ".owner", t.owner.toString());
            List<String> ms = new ArrayList<>();
            for (UUID m : t.members) ms.add(m.toString());
            cfg.set(p + ".members", ms);
            for (Map.Entry<UUID, Team.Home> e : t.homes.entrySet()) {
                String hp = p + ".homes." + e.getKey();
                Team.Home h = e.getValue();
                cfg.set(hp + ".name", h.name);
                cfg.set(hp + ".world", h.world);
                cfg.set(hp + ".x", h.x);
                cfg.set(hp + ".y", h.y);
                cfg.set(hp + ".z", h.z);
                cfg.set(hp + ".yaw", h.yaw);
                cfg.set(hp + ".pitch", h.pitch);
            }
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save teams.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ lookups

    public Team teamOf(UUID id) { return byPlayer.get(id); }
    public Team getTeam(UUID teamId) { return teams.get(teamId); }
    public boolean sameTeam(UUID a, UUID b) {
        Team t = byPlayer.get(a);
        return t != null && t == byPlayer.get(b);
    }
    public int maxMembers() { return plugin.getConfig().getInt("max-members", 10); }

    public String ownerName(Team t) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(t.owner);
        return op.getName() == null ? "Unknown" : op.getName();
    }

    // ------------------------------------------------------------ validation

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    /** @return an error message, or null if the name is fine. */
    public String validateName(String raw, Team ignore) {
        String n = normalize(raw);
        if (n.length() < 3 || n.length() > 16) return "Name must be 3-16 characters.";
        if (!n.matches("[A-Za-z0-9_ ]+")) return "Use only letters, numbers, spaces and underscores.";
        for (Team t : teams.values()) {
            if (t != ignore && t.name.equalsIgnoreCase(n)) return "That team name is already taken.";
        }
        return null;
    }

    public String validateHomeName(String raw) {
        String n = normalize(raw);
        if (n.isEmpty() || n.length() > 16) return "Home name must be 1-16 characters.";
        if (!n.matches("[A-Za-z0-9_ ]+")) return "Use only letters, numbers, spaces and underscores.";
        return null;
    }

    // ------------------------------------------------------------ team actions

    public Team create(Player owner, String rawName, Material icon) {
        Team t = new Team(UUID.randomUUID(), normalize(rawName), icon, owner.getUniqueId());
        teams.put(t.id, t);
        byPlayer.put(owner.getUniqueId(), t);
        save();
        refresh(owner.getUniqueId());
        return t;
    }

    public void rename(Team t, String rawName) {
        t.name = normalize(rawName);
        save();
        refreshAll(t);
    }

    public void setIcon(Team t, Material icon) {
        t.icon = icon;
        save();
    }

    public void disband(Team t) {
        List<UUID> members = new ArrayList<>(t.members);
        teams.remove(t.id);
        for (UUID m : members) {
            byPlayer.remove(m);
            Player p = Bukkit.getPlayer(m);
            if (p != null) {
                if (!m.equals(t.owner)) msg(p, "Your team " + t.name + " was disbanded.", NamedTextColor.RED);
                refresh(m);
            }
        }
        save();
    }

    /** Non-owner leaves. */
    public String leave(UUID id) {
        Team t = byPlayer.get(id);
        if (t == null) return "You are not in a team.";
        if (t.owner.equals(id)) return "Owners must disband the team instead.";
        t.members.remove(id);
        t.homes.remove(id);
        byPlayer.remove(id);
        save();
        refresh(id);
        Player p = Bukkit.getPlayer(id);
        String name = p != null ? p.getName() : "A member";
        notifyTeam(t, name + " left the team.", NamedTextColor.YELLOW);
        return null;
    }

    // ------------------------------------------------------------ invites

    public String invite(Player from, Player to) {
        Team t = teamOf(from.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (from.equals(to)) return "You can't invite yourself.";
        if (teamOf(to.getUniqueId()) != null) return to.getName() + " is already in a team.";
        if (t.members.size() >= maxMembers()) return "Your team is full.";

        long expires = System.currentTimeMillis()
                + plugin.getConfig().getLong("invite-expire-seconds", 600) * 1000L;
        List<Invite> list = invites.computeIfAbsent(to.getUniqueId(), k -> new ArrayList<>());
        list.removeIf(i -> i.teamId().equals(t.id));
        list.add(new Invite(t.id, from.getUniqueId(), expires));

        to.sendMessage(prefix()
                .append(Component.text(from.getName(), NamedTextColor.WHITE))
                .append(Component.text(" invited you to join ", NamedTextColor.GRAY))
                .append(Component.text(t.name, NamedTextColor.WHITE))
                .append(Component.text(". ", NamedTextColor.GRAY))
                .append(Component.text("[Open /team]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/teams"))));
        return null;
    }

    public List<Invite> invitesFor(UUID id) {
        List<Invite> list = invites.get(id);
        if (list == null) return List.of();
        long now = System.currentTimeMillis();
        list.removeIf(i -> i.expires() < now || !teams.containsKey(i.teamId()));
        return new ArrayList<>(list);
    }

    public String join(UUID teamId, Player p) {
        Team t = teams.get(teamId);
        boolean valid = invitesFor(p.getUniqueId()).stream().anyMatch(i -> i.teamId().equals(teamId));
        if (t == null || !valid) return "That invite is no longer valid.";
        if (teamOf(p.getUniqueId()) != null) return "You are already in a team.";
        if (t.members.size() >= maxMembers()) return "That team is full.";

        t.members.add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), t);
        invites.remove(p.getUniqueId());
        save();
        refresh(p.getUniqueId());
        notifyTeam(t, p.getName() + " joined the team!", NamedTextColor.GREEN);
        return null;
    }

    // ------------------------------------------------------------ homes (one per person)

    public Team.Home getHome(UUID id) {
        Team t = byPlayer.get(id);
        return t == null ? null : t.homes.get(id);
    }

    public String setHome(Player p) {
        Team t = teamOf(p.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (t.homes.containsKey(p.getUniqueId())) return "You already have a home. Delete it first.";
        Location l = p.getLocation();
        t.homes.put(p.getUniqueId(), new Team.Home("Home", l));
        save();
        return null;
    }

    public void deleteHome(UUID id) {
        Team t = byPlayer.get(id);
        if (t != null && t.homes.remove(id) != null) save();
    }

    public void renameHome(UUID id, String rawName) {
        Team.Home h = getHome(id);
        if (h != null) {
            h.name = normalize(rawName);
            save();
        }
    }

    public String teleportHome(Player p) {
        Team.Home h = getHome(p.getUniqueId());
        if (h == null) return "You haven't set a home yet.";
        Location l = h.toLocation();
        if (l == null) return "That home's world isn't loaded.";
        p.teleportAsync(l);
        return null;
    }

    // ------------------------------------------------------------ helpers

    private Component prefix() {
        return Component.text(SmallCaps.of("Team") + " » ", BLUE);
    }

    public void msg(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(prefix().append(Component.text(text, color)));
    }

    private void notifyTeam(Team t, String text, NamedTextColor color) {
        for (UUID m : t.members) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) msg(p, text, color);
        }
    }

    private void refresh(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) plugin.boards().update(p);
    }

    private void refreshAll(Team t) {
        for (UUID m : t.members) refresh(m);
    }
}
