package smp.jalebi.scoreboard;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** All the /team dialog screens. */
public class TeamGui {
    private static final TextColor ORANGE = TextColor.fromHexString("#FFA500");
    private static final TextColor YELLOW = TextColor.fromHexString("#FFE000");
    private static final TextColor BLUE = TextColor.fromHexString("#00A2FF");
    private static final String[] NAME_KEY = {"name"};

    private static class Draft {
        String name = "";
        Material icon = Material.GRASS_BLOCK;
    }

    private final JalebiScoreboard plugin;
    private final TeamManager tm;
    private final IconChooser icons;
    private final Map<UUID, Draft> drafts = new HashMap<>();

    public TeamGui(JalebiScoreboard plugin, IconChooser icons) {
        this.plugin = plugin;
        this.tm = plugin.teams();
        this.icons = icons;
    }

    // ------------------------------------------------------------ small helpers

    private static Component sc(String s, TextColor c) {
        return Component.text(SmallCaps.of(s), c);
    }

    private static Component scb(String s, TextColor c) {
        return Component.text(SmallCaps.of(s), c, TextDecoration.BOLD);
    }

    private static Component raw(String s, TextColor c) {
        return Component.text(s, c);
    }

    private static Component gradientTitle(String text) {
        String t = SmallCaps.of(text);
        Component out = Component.empty();
        for (int i = 0; i < t.length(); i++) {
            float f = t.length() == 1 ? 0 : (float) i / (t.length() - 1);
            out = out.append(Component.text(t.charAt(i), TextColor.lerp(f, ORANGE, YELLOW)).decorate(TextDecoration.BOLD));
        }
        return out;
    }

    private static String pretty(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String w : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private ActionButton btn(Component label, Component tooltip, int width, String[] keys, DialogUtil.Click c) {
        return DialogUtil.btn(plugin, label, tooltip, width, keys, c);
    }

    private ActionButton btn(Component label, int width, DialogUtil.Click c) {
        return DialogUtil.btn(plugin, label, null, width, DialogUtil.NO_KEYS, c);
    }

    private DialogBody msg(Component c) {
        return DialogBody.plainMessage(c);
    }

    private DialogBody teamHeader(Team t) {
        Component desc = Component.text(t.name, NamedTextColor.WHITE, TextDecoration.BOLD)
                .append(Component.newline())
                .append(sc("Owner » ", NamedTextColor.GRAY)).append(raw(tm.ownerName(t), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(sc("Members » ", NamedTextColor.GRAY))
                .append(raw(t.members.size() + "/" + tm.maxMembers(), NamedTextColor.WHITE));
        return DialogBody.item(new ItemStack(t.icon)).description(DialogBody.plainMessage(desc)).build();
    }

    private Team ownedTeam(Player p) {
        Team t = tm.teamOf(p.getUniqueId());
        return t != null && t.owner.equals(p.getUniqueId()) ? t : null;
    }

    // ------------------------------------------------------------ main page

    public void openMain(Player p) {
        openMain(p, null);
    }

    public void openMain(Player p, Component notice) {
        Team team = tm.teamOf(p.getUniqueId());
        List<DialogBody> body = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();
        if (notice != null) body.add(msg(notice));

        if (team == null) {
            body.add(msg(sc("You are not in a team yet", NamedTextColor.GRAY)));
            buttons.add(btn(scb("Create Team", NamedTextColor.WHITE), 200, (pl, in) -> {
                drafts.put(pl.getUniqueId(), new Draft());
                openCreate(pl, null);
            }));
            int n = tm.invitesFor(p.getUniqueId()).size();
            buttons.add(btn(scb("Join Team" + (n > 0 ? " (" + n + ")" : ""), NamedTextColor.WHITE), 200,
                    (pl, in) -> openJoin(pl, null)));
        } else {
            body.add(teamHeader(team));
            buttons.add(btn(Component.text(team.name, NamedTextColor.WHITE, TextDecoration.BOLD),
                    sc("Click to open your team", NamedTextColor.GRAY), 220, DialogUtil.NO_KEYS,
                    (pl, in) -> openTeam(pl, null)));
        }

        p.showDialog(DialogUtil.multi(gradientTitle("Jalebi Teams"), body, List.of(), buttons,
                DialogUtil.close(sc("Close", NamedTextColor.WHITE), 120), 1));
    }

    // ------------------------------------------------------------ create team

    private void openCreate(Player p, Component error) {
        Draft d = drafts.computeIfAbsent(p.getUniqueId(), k -> new Draft());
        List<DialogBody> body = new ArrayList<>();
        if (error != null) body.add(msg(error));
        body.add(DialogBody.item(new ItemStack(d.icon))
                .description(DialogBody.plainMessage(sc("Icon » ", NamedTextColor.GRAY).append(raw(pretty(d.icon), NamedTextColor.WHITE))))
                .build());

        List<DialogInput> inputs = List.of(
                DialogInput.text("name", sc("Name your team", ORANGE)).initial(d.name).maxLength(16).width(250).build());

        ActionButton choose = btn(sc("Choose Icon", NamedTextColor.WHITE), null, 150, NAME_KEY, (pl, in) -> {
            d.name = in.getOrDefault("name", "");
            icons.open(pl, m -> {
                d.icon = m;
                openCreate(pl, null);
            }, () -> openCreate(pl, null));
        });

        ActionButton save = btn(scb("Save", NamedTextColor.GREEN), null, 150, NAME_KEY, (pl, in) -> {
            String name = in.getOrDefault("name", "");
            d.name = name;
            if (tm.teamOf(pl.getUniqueId()) != null) {
                drafts.remove(pl.getUniqueId());
                openMain(pl, null);
                return;
            }
            String err = tm.validateName(name, null);
            if (err != null) {
                openCreate(pl, raw(err, NamedTextColor.RED));
                return;
            }
            tm.create(pl, name, d.icon);
            drafts.remove(pl.getUniqueId());
            openMain(pl, sc("Team created!", NamedTextColor.GREEN));
        });

        ActionButton back = btn(sc("Back", NamedTextColor.WHITE), 120, (pl, in) -> {
            drafts.remove(pl.getUniqueId());
            openMain(pl, null);
        });

        p.showDialog(DialogUtil.multi(gradientTitle("Create Team"), body, inputs,
                List.of(choose, save), back, 2));
    }

    // ------------------------------------------------------------ team page

    private void openTeam(Player p, Component notice) {
        Team team = tm.teamOf(p.getUniqueId());
        if (team == null) {
            openMain(p, null);
            return;
        }
        boolean owner = team.owner.equals(p.getUniqueId());
        List<DialogBody> body = new ArrayList<>();
        if (notice != null) body.add(msg(notice));
        body.add(teamHeader(team));

        List<ActionButton> buttons = new ArrayList<>();
        if (owner) {
            buttons.add(btn(sc("Rename", NamedTextColor.WHITE), 220, (pl, in) -> openRename(pl, null)));
            buttons.add(btn(sc("Change Icon", NamedTextColor.WHITE), 220, (pl, in) -> {
                if (ownedTeam(pl) == null) { openTeam(pl, raw("Only the owner can do that.", NamedTextColor.RED)); return; }
                icons.open(pl, m -> {
                    Team t = ownedTeam(pl);
                    if (t != null) {
                        tm.setIcon(t, m);
                        openTeam(pl, sc("Icon changed!", NamedTextColor.GREEN));
                    } else {
                        openMain(pl, null);
                    }
                }, () -> openTeam(pl, null));
            }));
        }

        // HOME: gray if not set, white if set
        boolean hasHome = team.homes.containsKey(p.getUniqueId());
        buttons.add(btn(scb("Home", hasHome ? NamedTextColor.WHITE : NamedTextColor.GRAY),
                sc(hasHome ? "Click to manage your home" : "Click to set your home here", NamedTextColor.GRAY),
                220, DialogUtil.NO_KEYS, (pl, in) -> {
                    Team t = tm.teamOf(pl.getUniqueId());
                    if (t == null) { openMain(pl, null); return; }
                    if (t.homes.containsKey(pl.getUniqueId())) {
                        openHome(pl, null);
                    } else {
                        String err = tm.setHome(pl);
                        openTeam(pl, err == null ? sc("Home saved at your location!", NamedTextColor.GREEN)
                                : raw(err, NamedTextColor.RED));
                    }
                }));

        buttons.add(btn(sc(owner ? "Disband Team" : "Leave Team", NamedTextColor.RED), 220,
                (pl, in) -> openLeave(pl)));

        // Members header (just refreshes) followed by every online player
        buttons.add(btn(scb("Members:", ORANGE), sc("Click a player below to invite them", NamedTextColor.GRAY),
                220, DialogUtil.NO_KEYS, (pl, in) -> openTeam(pl, null)));

        List<Player> others = new ArrayList<>(Bukkit.getOnlinePlayers());
        others.remove(p);
        others.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        for (Player o : others) {
            UUID oid = o.getUniqueId();
            Team ot = tm.teamOf(oid);
            if (ot == team) {
                buttons.add(btn(raw(o.getName() + " (member)", NamedTextColor.GREEN), 220,
                        (pl, in) -> openTeam(pl, null)));
            } else if (ot != null) {
                buttons.add(btn(raw(o.getName() + " (in a team)", NamedTextColor.GRAY), 220,
                        (pl, in) -> openTeam(pl, null)));
            } else {
                buttons.add(btn(raw(o.getName(), NamedTextColor.WHITE),
                        sc("Click to send a team invite", NamedTextColor.GRAY), 220, DialogUtil.NO_KEYS, (pl, in) -> {
                            Player target = Bukkit.getPlayer(oid);
                            if (target == null) {
                                openTeam(pl, raw("That player is offline.", NamedTextColor.RED));
                                return;
                            }
                            String err = tm.invite(pl, target);
                            openTeam(pl, err == null
                                    ? sc("Invite sent to ", NamedTextColor.GREEN).append(raw(target.getName(), NamedTextColor.WHITE))
                                    : raw(err, NamedTextColor.RED));
                        }));
            }
        }

        p.showDialog(DialogUtil.multi(Component.text(team.name, ORANGE, TextDecoration.BOLD), body, List.of(),
                buttons, btn(sc("Back", NamedTextColor.WHITE), 120, (pl, in) -> openMain(pl, null)), 1));
    }

    private void openRename(Player p, Component error) {
        Team t = ownedTeam(p);
        if (t == null) {
            openTeam(p, raw("Only the owner can do that.", NamedTextColor.RED));
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        if (error != null) body.add(msg(error));
        body.add(msg(sc("Choose a new name for your team", NamedTextColor.GRAY)));

        ActionButton save = btn(scb("Save", NamedTextColor.GREEN), null, 120, NAME_KEY, (pl, in) -> {
            Team tt = ownedTeam(pl);
            if (tt == null) { openMain(pl, null); return; }
            String name = in.getOrDefault("name", "");
            String err = tm.validateName(name, tt);
            if (err != null) {
                openRename(pl, raw(err, NamedTextColor.RED));
                return;
            }
            tm.rename(tt, name);
            openTeam(pl, sc("Team renamed!", NamedTextColor.GREEN));
        });
        ActionButton back = btn(sc("Back", NamedTextColor.WHITE), 120, (pl, in) -> openTeam(pl, null));

        p.showDialog(DialogUtil.confirm(gradientTitle("Rename Team"), body,
                List.of(DialogInput.text("name", sc("New team name", ORANGE)).initial(t.name).maxLength(16).width(250).build()),
                save, back));
    }

    private void openLeave(Player p) {
        Team t = tm.teamOf(p.getUniqueId());
        if (t == null) { openMain(p, null); return; }
        boolean owner = t.owner.equals(p.getUniqueId());
        List<DialogBody> body = List.of(
                msg(sc(owner ? "Disband " : "Leave ", NamedTextColor.GRAY).append(raw(t.name, NamedTextColor.WHITE))
                        .append(sc("?", NamedTextColor.GRAY))),
                msg(sc(owner ? "This removes the team for everyone." : "You can be invited back later.", NamedTextColor.DARK_GRAY)));

        ActionButton yes = btn(scb("Yes", NamedTextColor.RED), 120, (pl, in) -> {
            Team tt = tm.teamOf(pl.getUniqueId());
            if (tt == null) { openMain(pl, null); return; }
            if (tt.owner.equals(pl.getUniqueId())) {
                tm.disband(tt);
                openMain(pl, sc("Team disbanded.", NamedTextColor.YELLOW));
            } else {
                String err = tm.leave(pl.getUniqueId());
                openMain(pl, err == null ? sc("You left the team.", NamedTextColor.YELLOW) : raw(err, NamedTextColor.RED));
            }
        });
        ActionButton no = btn(sc("No", NamedTextColor.WHITE), 120, (pl, in) -> openTeam(pl, null));
        p.showDialog(DialogUtil.confirm(gradientTitle(owner ? "Disband Team" : "Leave Team"), body, List.of(), yes, no));
    }

    // ------------------------------------------------------------ home pages

    private void openHome(Player p, Component notice) {
        Team t = tm.teamOf(p.getUniqueId());
        Team.Home h = tm.getHome(p.getUniqueId());
        if (t == null || h == null) { openTeam(p, null); return; }

        List<DialogBody> body = new ArrayList<>();
        if (notice != null) body.add(msg(notice));
        body.add(msg(raw(h.name, NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
        body.add(msg(raw(h.world + "  " + Math.round(h.x) + ", " + Math.round(h.y) + ", " + Math.round(h.z),
                NamedTextColor.GRAY)));

        ActionButton tp = btn(scb("Teleport", NamedTextColor.GREEN), 150, (pl, in) -> {
            String err = tm.teleportHome(pl);
            if (err != null) openHome(pl, raw(err, NamedTextColor.RED));
            else tm.msg(pl, "Teleporting to your home...", NamedTextColor.GREEN);
        });
        ActionButton del = btn(scb("Delete", NamedTextColor.RED), 150, (pl, in) -> {
            tm.deleteHome(pl.getUniqueId());
            openTeam(pl, sc("Home deleted.", NamedTextColor.YELLOW));
        });
        ActionButton ren = btn(sc("Rename", NamedTextColor.WHITE), 150, (pl, in) -> openHomeRename(pl, null));

        p.showDialog(DialogUtil.multi(Component.text(h.name, ORANGE, TextDecoration.BOLD), body, List.of(),
                List.of(tp, del, ren), DialogUtil.close(sc("Close", NamedTextColor.WHITE), 150), 2));
    }

    private void openHomeRename(Player p, Component error) {
        Team.Home h = tm.getHome(p.getUniqueId());
        if (h == null) { openTeam(p, null); return; }
        List<DialogBody> body = new ArrayList<>();
        if (error != null) body.add(msg(error));
        body.add(msg(sc("Type the new name of your home", NamedTextColor.GRAY)));

        ActionButton save = btn(scb("Save", NamedTextColor.GREEN), null, 120, NAME_KEY, (pl, in) -> {
            String name = in.getOrDefault("name", "");
            String err = tm.validateHomeName(name);
            if (err != null) {
                openHomeRename(pl, raw(err, NamedTextColor.RED));
                return;
            }
            tm.renameHome(pl.getUniqueId(), name);
            openHome(pl, sc("Home renamed!", NamedTextColor.GREEN));
        });
        ActionButton back = btn(sc("Back", NamedTextColor.WHITE), 120, (pl, in) -> openHome(pl, null));

        p.showDialog(DialogUtil.confirm(gradientTitle("Rename Home"), body,
                List.of(DialogInput.text("name", sc("Home name", ORANGE)).initial(h.name).maxLength(16).width(250).build()),
                save, back));
    }

    // ------------------------------------------------------------ join pages

    private void openJoin(Player p, Component notice) {
        List<TeamManager.Invite> invs = tm.invitesFor(p.getUniqueId());
        List<DialogBody> body = new ArrayList<>();
        if (notice != null) body.add(msg(notice));
        body.add(msg(sc(invs.isEmpty() ? "You have no invites right now" : "Choose an invite to view it", NamedTextColor.GRAY)));

        List<ActionButton> buttons = new ArrayList<>();
        for (TeamManager.Invite inv : invs) {
            Team t = tm.getTeam(inv.teamId());
            if (t == null) continue;
            UUID tid = t.id;
            buttons.add(btn(Component.text(t.name, NamedTextColor.WHITE, TextDecoration.BOLD),
                    sc("Click to view this invite", NamedTextColor.GRAY), 220, DialogUtil.NO_KEYS,
                    (pl, in) -> openInvite(pl, tid)));
        }
        p.showDialog(DialogUtil.multi(gradientTitle("Team Invites"), body, List.of(), buttons,
                btn(sc("Back", NamedTextColor.WHITE), 120, (pl, in) -> openMain(pl, null)), 1));
    }

    private void openInvite(Player p, UUID teamId) {
        Team t = tm.getTeam(teamId);
        boolean valid = t != null && tm.invitesFor(p.getUniqueId()).stream().anyMatch(i -> i.teamId().equals(teamId));
        if (!valid) {
            openJoin(p, raw("That invite is no longer valid.", NamedTextColor.RED));
            return;
        }
        ActionButton join = btn(scb("Join", NamedTextColor.GREEN), 120, (pl, in) -> {
            String err = tm.join(teamId, pl);
            if (err != null) {
                openJoin(pl, raw(err, NamedTextColor.RED));
            } else {
                openMain(pl, sc("You joined the team!", NamedTextColor.GREEN));
            }
        });
        ActionButton back = btn(sc("Back", NamedTextColor.WHITE), 120, (pl, in) -> openJoin(pl, null));
        p.showDialog(DialogUtil.confirm(gradientTitle("Team Invite"), List.of(teamHeader(t)), List.of(), join, back));
    }
}
