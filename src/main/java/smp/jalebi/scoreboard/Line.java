package smp.jalebi.scoreboard;

import org.bukkit.Material;

/** Every toggleable element. Symbols + colours can be edited here. */
public enum Line {
    SCOREBOARD("Scoreboard", "★", "#FFA500", Material.REDSTONE_TORCH, 0),
    NAME("Name", "➤", "#FFFFFF", Material.NAME_TAG, 7),
    MONEY("Money", "$", "#55FF55", Material.GOLD_INGOT, 5),
    KILLS("Kills", "⚔", "#AA0000", Material.DIAMOND_SWORD, 4),
    DEATHS("Deaths", "☠", "#FFFF55", Material.SKELETON_SKULL, 3),
    PLAYTIME("Playtime", "⌚", "#55FFFF", Material.CLOCK, 2),
    TEAM("Team", "⚑", "#00A2FF", Material.WHITE_BANNER, 1);

    public final String label, symbol, hex;
    public final Material icon;
    public final int score;

    Line(String label, String symbol, String hex, Material icon, int score) {
        this.label = label;
        this.symbol = symbol;
        this.hex = hex;
        this.icon = icon;
        this.score = score;
    }
}
