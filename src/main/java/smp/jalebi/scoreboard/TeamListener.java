package smp.jalebi.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Teammates cannot hurt each other. */
public class TeamListener implements Listener {
    private final TeamManager tm;

    public TeamListener(TeamManager tm) {
        this.tm = tm;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (e.getDamager() instanceof Player p) {
            attacker = p;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null || attacker.equals(victim)) return;

        if (tm.sameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            e.setCancelled(true);
            attacker.sendActionBar(Component.text(SmallCaps.of("You can't hurt your teammates"), NamedTextColor.RED));
        }
    }
}
