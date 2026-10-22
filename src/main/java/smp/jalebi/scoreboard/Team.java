package smp.jalebi.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.*;

public class Team {
    public final UUID id;
    public String name;
    public Material icon;
    public UUID owner;
    public final Set<UUID> members = new LinkedHashSet<>();
    /** One home per member. */
    public final Map<UUID, Home> homes = new HashMap<>();

    public Team(UUID id, String name, Material icon, UUID owner) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.owner = owner;
        this.members.add(owner);
    }

    public static class Home {
        public String name;
        public String world;
        public double x, y, z;
        public float yaw, pitch;

        public Home(String name, String world, double x, double y, double z, float yaw, float pitch) {
            this.name = name;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Home(String name, Location l) {
            this(name, l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        }

        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }
}
