package smp.jalebi.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BoardManager {
    private static final int SPACER_SCORE = 6;
    private static final int NAME_SCORE = 7;
    private static final String TITLE = "JALEBI SMP";
    private static final TextColor START = TextColor.fromHexString("#FF8C00"); // orange
    private static final TextColor END = TextColor.fromHexString("#FFE000");   // yellow

    private final JalebiScoreboard plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private Economy economy;

    public BoardManager(JalebiScoreboard plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        if (economy == null) plugin.getLogger().warning("Vault economy not found - money will show as $0.");
    }

    private static Component gradientTitle() {
        String text = SmallCaps.of(TITLE);
        Component out = Component.empty();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0 : (float) i / (n - 1);
            out = out.append(Component.text(text.charAt(i),
                    TextColor.lerp(t, START, END)).decorate(TextDecoration.BOLD));
        }
        return out;
    }

    private static String entry(int score) {
        return "§" + score + "§r";
    }

    private org.bukkit.scoreboard.Team team(Scoreboard sb, int score) {
        String name = "line" + score;
        org.bukkit.scoreboard.Team t = sb.getTeam(name);
        if (t == null) {
            t = sb.registerNewTeam(name);
            t.addEntry(entry(score));
        }
        return t;
    }

    private void setLine(Scoreboard sb, Objective obj, int score, Component text, boolean visible) {
        org.bukkit.scoreboard.Team t = team(sb, score);
        if (visible) {
            t.prefix(text);
            obj.getScore(entry(score)).setScore(score);
        } else {
            sb.resetScores(entry(score));
        }
    }


    // Approximate pixel widths in Minecraft's default font (used for centering the name).
    private static int px(String t) {
        int w = 0;
        for (char c : t.toCharArray()) {
            if (c == ' ') w += 4;
            else if ("ɪ.,:;'|!".indexOf(c) >= 0) w += 4;
            else if (c >= 0x2190) w += (c == '»' ? 6 : 8); // symbols (unifont)
            else w += 6;
        }
        return w;
    }

    private static String plain(Line l, String value) {
        return l.symbol + " " + SmallCaps.of(l.label) + " » " + value;
    }

    private Component line(Line l, Component value) {
        TextColor c = TextColor.fromHexString(l.hex);
        return Component.text(l.symbol + " ", c)
                .append(Component.text(SmallCaps.of(l.label) + " ", c))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(value);
    }

    public void update(Player p) {
        DataStore d = plugin.data();
        UUID id = p.getUniqueId();

        if (!d.isEnabled(id, Line.SCOREBOARD)) {
            remove(p);
            return;
        }

        Scoreboard sb = boards.computeIfAbsent(id, k -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective obj = sb.getObjective("jalebi");
        if (obj == null) {
            obj = sb.registerNewObjective("jalebi", Criteria.DUMMY, gradientTitle());
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank()); // hide red numbers
        }

        double money = economy != null ? economy.getBalance(p) : 0;
        String moneyS = NumberFormatter.shorten(money);
        String killsS = NumberFormatter.shorten(p.getStatistic(Statistic.PLAYER_KILLS));
        String deathsS = NumberFormatter.shorten(p.getStatistic(Statistic.DEATHS));
        long secs = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        String playS = SmallCaps.of(NumberFormatter.playtime(secs));
        smp.jalebi.scoreboard.Team team = plugin.teams().teamOf(id);
        String teamS = SmallCaps.of(team == null ? "N/A" : team.name);

        boolean nOn = d.isEnabled(id, Line.NAME);
        boolean mOn = d.isEnabled(id, Line.MONEY), kOn = d.isEnabled(id, Line.KILLS),
                dOn = d.isEnabled(id, Line.DEATHS), pOn = d.isEnabled(id, Line.PLAYTIME),
                tOn = d.isEnabled(id, Line.TEAM);

        // widest visible line decides the sidebar width
        String nameS = SmallCaps.of(p.getName());
        int max = px(SmallCaps.of(TITLE)) + 10; // bold title
        if (nOn) max = Math.max(max, px(nameS));
        if (mOn) max = Math.max(max, px(plain(Line.MONEY, "$" + moneyS)));
        if (kOn) max = Math.max(max, px(plain(Line.KILLS, killsS)));
        if (dOn) max = Math.max(max, px(plain(Line.DEATHS, deathsS)));
        if (pOn) max = Math.max(max, px(plain(Line.PLAYTIME, playS)));
        if (tOn) max = Math.max(max, px(plain(Line.TEAM, teamS)));
        int pad = Math.max(0, (max - px(nameS)) / 2 / 4); // a space is 4px wide

        // centred player name right under the title, then a blank gap before the stats
        setLine(sb, obj, SPACER_SCORE, Component.empty(), true); // always keep the gap above the stats
        setLine(sb, obj, NAME_SCORE,
                Component.text(" ".repeat(pad) + nameS, NamedTextColor.WHITE), nOn);

        setLine(sb, obj, Line.MONEY.score, line(Line.MONEY,
                Component.text("$", NamedTextColor.GREEN)
                        .append(Component.text(moneyS, NamedTextColor.WHITE))), mOn);
        setLine(sb, obj, Line.KILLS.score, line(Line.KILLS,
                Component.text(killsS, NamedTextColor.WHITE)), kOn);
        setLine(sb, obj, Line.DEATHS.score, line(Line.DEATHS,
                Component.text(deathsS, NamedTextColor.WHITE)), dOn);
        setLine(sb, obj, Line.PLAYTIME.score, line(Line.PLAYTIME,
                Component.text(playS, NamedTextColor.WHITE)), pOn);
        setLine(sb, obj, Line.TEAM.score, line(Line.TEAM,
                Component.text(teamS, NamedTextColor.WHITE)), tOn);

        if (p.getScoreboard() != sb) p.setScoreboard(sb);
    }

    public void remove(Player p) {
        Scoreboard sb = boards.remove(p.getUniqueId());
        if (sb != null && p.getScoreboard() == sb) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void forget(Player p) {
        boards.remove(p.getUniqueId());
    }
}
