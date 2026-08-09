package com.vyntriloop.minecraft.witherstorm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
import org.bukkit.entity.EntityType;
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
        getLogger().info("VyntriWitherStorm 1.21.4 loaded. Ritual detector is active.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("witherstorm")) {
            return false;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.DARK_PURPLE + "VyntriWitherStorm is loaded on Paper 1.21.4.");
            sender.sendMessage(ChatColor.GRAY + "Ritual: 3 Wither Skeleton Skulls, Soul Sand arms, Command Block center, Soul Sand base.");
            return true;
        }

        sender.sendMessage(ChatColor.GRAY + "Use /witherstorm status");
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Location placedLocation = event.getBlockPlaced().getLocation();
        String playerName = event.getPlayer().getName();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            RitualMatch match = findRitual(placedLocation);
            if (match == null) {
                return;
            }

            getLogger().info(
                    "Wither Storm ritual detected for " + playerName
                            + " at " + placedLocation.getBlockX()
                            + "," + placedLocation.getBlockY()
                            + "," + placedLocation.getBlockZ()
            );
            consumeRitualAndSpawn(match, playerName);
        }, 1L);
    }

    private RitualMatch findRitual(Location placedLocation) {
        World world = placedLocation.getWorld();
        int originX = placedLocation.getBlockX();
        int originY = placedLocation.getBlockY();
        int originZ = placedLocation.getBlockZ();

        for (int dy = -4; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block base = world.getBlockAt(originX + dx, originY + dy, originZ + dz);

                    RitualMatch alongX = matchAt(base, true);
                    if (alongX != null) {
                        return alongX;
                    }

                    RitualMatch alongZ = matchAt(base, false);
                    if (alongZ != null) {
                        return alongZ;
                    }
                }
            }
        }

        return null;
    }

    private RitualMatch matchAt(Block base, boolean alongX) {
        if (base.getType() != Material.SOUL_SAND) {
            return null;
        }

        Block center = base.getRelative(0, 1, 0);
        if (!isCommandBlock(center.getType())) {
            return null;
        }

        int sideX = alongX ? 1 : 0;
        int sideZ = alongX ? 0 : 1;

        Block leftSand = center.getRelative(-sideX, 0, -sideZ);
        Block rightSand = center.getRelative(sideX, 0, sideZ);
        if (leftSand.getType() != Material.SOUL_SAND || rightSand.getType() != Material.SOUL_SAND) {
            return null;
        }

        Block centerSkull = center.getRelative(0, 1, 0);
        Block leftSkull = centerSkull.getRelative(-sideX, 0, -sideZ);
        Block rightSkull = centerSkull.getRelative(sideX, 0, sideZ);

        if (!isWitherSkull(leftSkull.getType())
                || !isWitherSkull(centerSkull.getType())
                || !isWitherSkull(rightSkull.getType())) {
            return null;
        }

        List<Block> blocks = new ArrayList<>();
        blocks.add(base);
        blocks.add(center);
        blocks.add(leftSand);
        blocks.add(rightSand);
        blocks.add(leftSkull);
        blocks.add(centerSkull);
        blocks.add(rightSkull);

        return new RitualMatch(base, blocks);
    }

    private boolean isCommandBlock(Material material) {
        return material == Material.COMMAND_BLOCK
                || material == Material.CHAIN_COMMAND_BLOCK
                || material == Material.REPEATING_COMMAND_BLOCK;
    }

    private boolean isWitherSkull(Material material) {
        return material == Material.WITHER_SKELETON_SKULL
                || material == Material.WITHER_SKELETON_WALL_SKULL;
    }

    private void consumeRitualAndSpawn(RitualMatch match, String playerName) {
        Location spawn = match.base.getLocation().add(0.5, 3.2, 0.5);
        World world = spawn.getWorld();

        for (Block block : match.blocks) {
            block.setType(Material.AIR, false);
        }

        world.strikeLightningEffect(spawn);
        world.playSound(spawn, Sound.ENTITY_WITHER_SPAWN, 4.0f, 0.65f);
        world.spawnParticle(Particle.PORTAL, spawn, 180, 3.0, 2.5, 3.0, 0.14);

        Wither storm = (Wither) world.spawnEntity(spawn, EntityType.WITHER);
        storm.setCustomName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "WITHER STORM - PHASE 1");
        storm.setCustomNameVisible(true);
        storm.setRemoveWhenFarAway(false);
        storm.setPersistent(true);
        storm.setGlowing(true);

        Bukkit.broadcastMessage(
                ChatColor.DARK_PURPLE + "[VyntriLoop] "
                        + ChatColor.LIGHT_PURPLE + playerName
                        + ChatColor.GRAY + " awakened the Wither Storm."
        );

        startStormController(storm);
    }

    private void startStormController(Wither storm) {
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
                    storm.setCustomName(
                            ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "WITHER STORM - PHASE " + phase
                    );
                    worldEffect(storm.getLocation(), phase);
                }

                renderAura(storm, phase);
                pullEntities(storm, phase);

                if (phase >= 2 && ageTicks % 40 == 0) {
                    consumeNearbyBlocks(storm, phase == 3 ? 2 : 1);
                }
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    private void worldEffect(Location location, int phase) {
        World world = location.getWorld();
        world.playSound(location, Sound.ENTITY_WITHER_AMBIENT, 3.5f, 0.55f + phase * 0.08f);
        world.spawnParticle(Particle.PORTAL, location, 80 + phase * 30, 4.0, 3.0, 4.0, 0.12);
    }

    private void renderAura(Wither storm, int phase) {
        Location location = storm.getLocation().add(0.0, 1.0, 0.0);
        double spread = 2.5 + phase;
        location.getWorld().spawnParticle(
                Particle.PORTAL,
                location,
                22 + phase * 12,
                spread,
                1.8 + phase,
                spread,
                0.08
        );
    }

    private void pullEntities(Wither storm, int phase) {
        double radius = 9.0 + phase * 4.0;
        Location center = storm.getLocation();

        for (Entity entity : storm.getNearbyEntities(radius, radius, radius)) {
            if (entity.getUniqueId().equals(storm.getUniqueId())) {
                continue;
            }

            Vector direction = center.toVector().subtract(entity.getLocation().toVector());
            double distance = direction.length();
            if (distance < 0.01 || distance > radius) {
                continue;
            }

            double strength = 0.035 + phase * 0.012;
            Vector pull = direction.normalize().multiply(strength);
            pull.setY(Math.min(0.16, pull.getY() + 0.045));
            entity.setVelocity(entity.getVelocity().multiply(0.72).add(pull));
        }
    }

    private void consumeNearbyBlocks(Wither storm, int amount) {
        World world = storm.getWorld();
        Location center = storm.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < amount; i++) {
            int x = center.getBlockX() + random.nextInt(-8, 9);
            int y = center.getBlockY() + random.nextInt(-5, 4);
            int z = center.getBlockZ() + random.nextInt(-8, 9);
            Block block = world.getBlockAt(x, y, z);

            if (!canConsume(block.getType())) {
                continue;
            }

            block.setType(Material.AIR, false);
            world.spawnParticle(Particle.PORTAL, block.getLocation().add(0.5, 0.5, 0.5), 12, 0.35, 0.35, 0.35, 0.06);
        }
    }

    private boolean canConsume(Material material) {
        if (material.isAir() || !material.isSolid()) {
            return false;
        }

        String name = material.name();
        return material != Material.BEDROCK
                && !name.contains("COMMAND_BLOCK")
                && !name.contains("CHEST")
                && !name.contains("SHULKER_BOX")
                && !name.contains("SPAWNER")
                && !name.contains("PORTAL")
                && !name.contains("BARRIER");
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
