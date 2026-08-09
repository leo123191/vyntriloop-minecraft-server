package com.vyntriloop.minecraft.witherstorm;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class VyntriWitherStormPlugin extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("VyntriWitherStorm loaded. Ritual detector is active.");

        // Eagler/ViaVersion setups can occasionally make the final skull placement event
        // unreliable. This fallback checks for completed rituals near online players every
        // two seconds, so a correctly built ritual still activates.
        new BukkitRunnable() {
            @Override
            public void run() {
                scanForCompletedRituals();
            }
        }.runTaskTimer(this, 40L, 40L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("witherstorm")) {
            return false;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.DARK_PURPLE + "VyntriWitherStorm is loaded and the ritual detector is active.");
            return true;
        }

        if (args[0].equalsIgnoreCase("scan")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only a player can scan for a ritual.");
                return true;
            }

            Player player = (Player) sender;
            RitualMatch match = findRitualNear(player.getLocation(), 8, 5);
            if (match == null) {
                player.sendMessage(ChatColor.RED + "No completed Wither Storm ritual was found nearby.");
                return true;
            }

            player.sendMessage(ChatColor.LIGHT_PURPLE + "Completed Wither Storm ritual found. Activating it now.");
            consumeRitualAndSpawn(match, player.getName());
            return true;
        }

        sender.sendMessage(ChatColor.GRAY + "Use /witherstorm status or /witherstorm scan");
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        final Location placedLocation = event.getBlockPlaced().getLocation();
        final String playerName = event.getPlayer().getName();

        // Wait two ticks so Bukkit/ViaVersion has finished updating the placed skull block.
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                Block placed = placedLocation.getBlock();
                if (!couldCompleteRitual(placed)) {
                    return;
                }

                RitualMatch match = findRitualNear(placedLocation, 4, 4);
                if (match == null) {
                    return;
                }

                getLogger().info("Wither Storm ritual detected for " + playerName + " at "
                        + placedLocation.getBlockX() + ","
                        + placedLocation.getBlockY() + ","
                        + placedLocation.getBlockZ());
                consumeRitualAndSpawn(match, playerName);
            }
        }, 2L);
    }

    private void scanForCompletedRituals() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            RitualMatch match = findRitualNear(player.getLocation(), 8, 5);
            if (match != null) {
                getLogger().info("Wither Storm fallback scanner found a completed ritual near " + player.getName());
                consumeRitualAndSpawn(match, player.getName());
            }
        }
    }

    private boolean couldCompleteRitual(Block block) {
        String material = block.getType().name();
        return material.equals("SOUL_SAND")
                || material.equals("SKULL")
                || material.contains("COMMAND");
    }

    private RitualMatch findRitualNear(Location center, int horizontalRadius, int verticalRadius) {
        World world = center.getWorld();
        int originX = center.getBlockX();
        int originY = center.getBlockY();
        int originZ = center.getBlockZ();

        int minY = Math.max(1, originY - verticalRadius);
        int maxY = Math.min(world.getMaxHeight() - 3, originY + verticalRadius);

        for (int y = minY; y <= maxY; y++) {
            for (int x = originX - horizontalRadius; x <= originX + horizontalRadius; x++) {
                for (int z = originZ - horizontalRadius; z <= originZ + horizontalRadius; z++) {
                    Block base = world.getBlockAt(x, y, z);
                    if (!isSoulSand(base)) {
                        continue;
                    }

                    RitualMatch xAxis = matchAt(base, true);
                    if (xAxis != null) {
                        return xAxis;
                    }

                    RitualMatch zAxis = matchAt(base, false);
                    if (zAxis != null) {
                        return zAxis;
                    }
                }
            }
        }

        return null;
    }

    private RitualMatch matchAt(Block base, boolean alongX) {
        if (!isSoulSand(base)) {
            return null;
        }

        Block command = base.getRelative(0, 1, 0);
        if (!isCommandBlock(command)) {
            return null;
        }

        int sideX = alongX ? 1 : 0;
        int sideZ = alongX ? 0 : 1;

        Block leftSand = command.getRelative(-sideX, 0, -sideZ);
        Block rightSand = command.getRelative(sideX, 0, sideZ);
        if (!isSoulSand(leftSand) || !isSoulSand(rightSand)) {
            return null;
        }

        Block centerSkull = command.getRelative(0, 1, 0);
        Block leftSkull = centerSkull.getRelative(-sideX, 0, -sideZ);
        Block rightSkull = centerSkull.getRelative(sideX, 0, sideZ);

        // A 1.8 Eagler client is translated by ViaVersion to the 1.12 server.
        // Checking the block type is more reliable than checking legacy skull metadata.
        if (!isSkull(leftSkull) || !isSkull(centerSkull) || !isSkull(rightSkull)) {
            return null;
        }

        List<Block> blocks = new ArrayList<Block>();
        blocks.add(base);
        blocks.add(command);
        blocks.add(leftSand);
        blocks.add(rightSand);
        blocks.add(leftSkull);
        blocks.add(centerSkull);
        blocks.add(rightSkull);

        return new RitualMatch(base, blocks);
    }

    private boolean isSoulSand(Block block) {
        return block.getType() == Material.SOUL_SAND;
    }

    private boolean isCommandBlock(Block block) {
        return block.getType().name().contains("COMMAND");
    }

    private boolean isSkull(Block block) {
        return block.getType().name().equals("SKULL");
    }

    private void consumeRitualAndSpawn(RitualMatch match, String playerName) {
        Location spawn = match.base.getLocation().add(0.5, 3.2, 0.5);
        World world = spawn.getWorld();

        final Wither storm;
        try {
            storm = world.spawn(spawn, Wither.class);
        } catch (Throwable error) {
            getLogger().severe("Could not spawn the Wither Storm: " + error.getMessage());
            return;
        }

        // Only remove the ritual after the entity has successfully spawned.
        for (Block block : match.blocks) {
            block.setType(Material.AIR);
        }

        storm.setCustomName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "WITHER STORM - PHASE 1");
        storm.setCustomNameVisible(true);
        storm.setRemoveWhenFarAway(false);
        storm.setGlowing(true);
        storm.setMaxHealth(600.0);
        storm.setHealth(600.0);

        world.strikeLightningEffect(spawn);
        world.playSound(spawn, Sound.ENTITY_WITHER_SPAWN, 4.0f, 0.65f);
        world.spawnParticle(Particle.EXPLOSION_LARGE, spawn, 12, 2.0, 1.4, 2.0, 0.03);
        world.spawnParticle(Particle.PORTAL, spawn, 180, 3.0, 2.5, 3.0, 0.14);

        Bukkit.broadcastMessage(
                ChatColor.DARK_PURPLE + "[VyntriLoop] "
                        + ChatColor.LIGHT_PURPLE + playerName
                        + ChatColor.GRAY + " awakened the Wither Storm."
        );

        startStormController(storm);
    }

    private void startStormController(final Wither storm) {
        new BukkitRunnable() {
            private int ageTicks = 0;
            private int phase = 1;

            @Override
            public void run() {
                if (!storm.isValid() || storm.isDead()) {
                    cancel();
                    return;
                }

                ageTicks += 10;
                int newPhase = ageTicks >= 2400 ? 3 : (ageTicks >= 1200 ? 2 : 1);
                if (newPhase != phase) {
                    phase = newPhase;
                    storm.setCustomName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "WITHER STORM - PHASE " + phase);
                    worldEffect(storm.getLocation(), phase);
                }

                renderAura(storm, phase);
                pullEntities(storm, phase);
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    private void worldEffect(Location location, int phase) {
        World world = location.getWorld();
        world.playSound(location, Sound.ENTITY_ENDERDRAGON_GROWL, 3.5f, 0.6f);
        world.spawnParticle(Particle.EXPLOSION_LARGE, location, 12 + phase * 6, 3.0, 2.0, 3.0, 0.03);
    }

    private void renderAura(Wither storm, int phase) {
        Location location = storm.getLocation().add(0.0, 1.0, 0.0);
        World world = location.getWorld();
        double spread = 2.5 + phase;
        world.spawnParticle(Particle.PORTAL, location, 22 + phase * 12, spread, 1.8 + phase, spread, 0.08);
        world.spawnParticle(Particle.SMOKE_LARGE, location, 8 + phase * 6, spread, 1.5, spread, 0.03);
    }

    private void pullEntities(Wither storm, int phase) {
        double radius = 9.0 + phase * 4.0;
        Location center = storm.getLocation();

        for (Entity entity : storm.getNearbyEntities(radius, radius, radius)) {
            if (entity.getUniqueId().equals(storm.getUniqueId())) {
                continue;
            }

            Vector towardStorm = center.toVector().subtract(entity.getLocation().toVector());
            double distance = towardStorm.length();
            if (distance < 0.01 || distance > radius) {
                continue;
            }

            double strength = 0.035 + phase * 0.012;
            Vector pull = towardStorm.normalize().multiply(strength);
            pull.setY(Math.min(0.16, pull.getY() + 0.045));
            entity.setVelocity(entity.getVelocity().multiply(0.72).add(pull));
        }
    }

    private static final class RitualMatch {
        private final Block base;
        private final List<Block> blocks;

        private RitualMatch(Block base, List<Block> blocks) {
            this.base = base;
            this.blocks = blocks;
        }
    }
}
