package com.vyntriloop.minecraft.witherstorm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SkullType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class VyntriWitherStorm extends JavaPlugin implements Listener {
    private final Set<UUID> activeStorms = new HashSet<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Vyntri Wither Storm ritual enabled.");
    }

    @Override
    public void onDisable() {
        activeStorms.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!couldCompleteRitual(placed)) {
            return;
        }

        RitualMatch match = findRitual(placed);
        if (match == null) {
            return;
        }

        consumeRitualAndSpawn(match, event.getPlayer());
    }

    private boolean couldCompleteRitual(Block block) {
        String material = block.getType().name();
        return material.equals("SOUL_SAND")
                || material.equals("SKULL")
                || material.contains("COMMAND");
    }

    private RitualMatch findRitual(Block placed) {
        World world = placed.getWorld();
        int originX = placed.getX();
        int originY = placed.getY();
        int originZ = placed.getZ();

        for (int dy = -2; dy <= 0; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block base = world.getBlockAt(originX + dx, originY + dy, originZ + dz);

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
        if (!isWitherSkull(leftSkull) || !isWitherSkull(centerSkull) || !isWitherSkull(rightSkull)) {
            return null;
        }

        List<Block> ritualBlocks = new ArrayList<>();
        ritualBlocks.add(base);
        ritualBlocks.add(command);
        ritualBlocks.add(leftSand);
        ritualBlocks.add(rightSand);
        ritualBlocks.add(leftSkull);
        ritualBlocks.add(centerSkull);
        ritualBlocks.add(rightSkull);

        return new RitualMatch(base, ritualBlocks);
    }

    private boolean isSoulSand(Block block) {
        return block.getType().name().equals("SOUL_SAND");
    }

    private boolean isCommandBlock(Block block) {
        return block.getType().name().contains("COMMAND");
    }

    private boolean isWitherSkull(Block block) {
        if (!block.getType().name().equals("SKULL")) {
            return false;
        }

        BlockState state = block.getState();
        if (!(state instanceof Skull)) {
            return false;
        }

        return ((Skull) state).getSkullType() == SkullType.WITHER;
    }

    private void consumeRitualAndSpawn(RitualMatch match, Player summoner) {
        Location spawn = match.base.getLocation().add(0.5, 3.1, 0.5);
        World world = spawn.getWorld();

        for (Block block : match.blocks) {
            block.setType(Material.AIR);
        }

        world.strikeLightningEffect(spawn);
        world.playSound(spawn, Sound.ENTITY_WITHER_SPAWN, 4.0f, 0.65f);
        world.spawnParticle(Particle.EXPLOSION_LARGE, spawn, 10, 1.8, 1.0, 1.8, 0.02);
        world.spawnParticle(Particle.PORTAL, spawn, 140, 2.5, 2.0, 2.5, 0.12);

        Wither storm = (Wither) world.spawnEntity(spawn, EntityType.WITHER);
        storm.setCustomName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "WITHER STORM - PHASE 1");
        storm.setCustomNameVisible(true);
        storm.setRemoveWhenFarAway(false);
        storm.setGlowing(true);
        storm.setMaxHealth(600.0);
        storm.setHealth(600.0);

        activeStorms.add(storm.getUniqueId());

        Bukkit.broadcastMessage(
                ChatColor.DARK_PURPLE + "[VyntriLoop] "
                        + ChatColor.LIGHT_PURPLE + summoner.getName()
                        + ChatColor.GRAY + " awakened the Wither Storm."
        );

        startStormController(storm);
    }

    private void startStormController(final Wither storm) {
        new BukkitRunnable() {
            private int ageTicks = 0;
            private int lastPhase = 1;

            @Override
            public void run() {
                if (storm.isDead() || !storm.isValid()) {
                    activeStorms.remove(storm.getUniqueId());
                    finishStorm(storm.getLocation());
                    cancel();
                    return;
                }

                ageTicks += 10;
                int phase = calculatePhase(storm, ageTicks);
                if (phase != lastPhase) {
                    lastPhase = phase;
                    announcePhase(storm, phase);
                }

                renderStormAura(storm, phase);
                pullNearbyEntities(storm, phase);

                if (ageTicks % 40 == 0 && phase >= 2) {
                    consumeNearbyBlocks(storm, phase == 3 ? 2 : 1);
                }
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    private int calculatePhase(Wither storm, int ageTicks) {
        double healthRatio = storm.getHealth() / storm.getMaxHealth();
        int phaseFromHealth = healthRatio <= 0.34 ? 3 : (healthRatio <= 0.67 ? 2 : 1);
        int phaseFromTime = ageTicks >= 2400 ? 3 : (ageTicks >= 1200 ? 2 : 1);
        return Math.max(phaseFromHealth, phaseFromTime);
    }

    private void announcePhase(Wither storm, int phase) {
        storm.setCustomName(
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "WITHER STORM - PHASE " + phase
        );

        Location location = storm.getLocation();
        World world = location.getWorld();
        world.playSound(location, Sound.ENTITY_ENDERDRAGON_GROWL, 3.5f, 0.55f + (phase * 0.08f));
        world.spawnParticle(Particle.EXPLOSION_LARGE, location, 14 + (phase * 8), 3.5, 2.0, 3.5, 0.04);

        Bukkit.broadcastMessage(
                ChatColor.DARK_PURPLE + "[VyntriLoop] "
                        + ChatColor.LIGHT_PURPLE + "The Wither Storm reached phase " + phase + "."
        );
    }

    private void renderStormAura(Wither storm, int phase) {
        Location location = storm.getLocation().add(0.0, 1.0, 0.0);
        World world = location.getWorld();
        double spread = 2.4 + (phase * 1.2);

        world.spawnParticle(Particle.PORTAL, location, 24 + (phase * 14), spread, 1.8 + phase, spread, 0.08);
        world.spawnParticle(Particle.SMOKE_LARGE, location, 10 + (phase * 8), spread, 1.5, spread, 0.03);
        world.spawnParticle(Particle.SPELL_WITCH, location, 8 + (phase * 5), spread * 0.8, 1.4, spread * 0.8, 0.02);

        if (ThreadLocalRandom.current().nextInt(10) == 0) {
            world.playSound(location, Sound.ENTITY_WITHER_AMBIENT, 2.2f, 0.55f);
        }
    }

    private void pullNearbyEntities(Wither storm, int phase) {
        double radius = 9.0 + (phase * 4.0);
        Location center = storm.getLocation();

        for (Entity entity : storm.getNearbyEntities(radius, radius, radius)) {
            if (entity.getUniqueId().equals(storm.getUniqueId())) {
                continue;
            }
            if (activeStorms.contains(entity.getUniqueId())) {
                continue;
            }

            Vector towardStorm = center.toVector().subtract(entity.getLocation().toVector());
            double distance = Math.max(1.0, towardStorm.length());
            if (distance > radius) {
                continue;
            }

            double strength = (0.035 + (phase * 0.012)) * (1.0 - (distance / (radius + 1.0)));
            Vector pull = towardStorm.normalize().multiply(Math.max(0.018, strength));
            pull.setY(Math.min(0.16, pull.getY() + 0.045));

            Vector current = entity.getVelocity();
            entity.setVelocity(current.multiply(0.72).add(pull));
        }
    }

    private void consumeNearbyBlocks(Wither storm, int amount) {
        World world = storm.getWorld();
        Location center = storm.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < amount; i++) {
            int x = center.getBlockX() + random.nextInt(-10, 11);
            int z = center.getBlockZ() + random.nextInt(-10, 11);
            int startY = Math.min(254, center.getBlockY() + 4);
            int minimumY = Math.max(1, startY - 18);

            for (int y = startY; y >= minimumY; y--) {
                Block block = world.getBlockAt(x, y, z);
                if (!canConsume(block)) {
                    continue;
                }

                Material material = block.getType();
                byte data = block.getData();
                Location blockLocation = block.getLocation().add(0.5, 0.5, 0.5);
                block.setType(Material.AIR);

                FallingBlock falling = world.spawnFallingBlock(blockLocation, material, data);
                falling.setDropItem(false);
                falling.setHurtEntities(false);

                Vector velocity = center.toVector().subtract(blockLocation.toVector());
                if (velocity.lengthSquared() > 0.001) {
                    velocity.normalize().multiply(0.48);
                }
                velocity.setY(Math.max(0.18, velocity.getY() + 0.28));
                falling.setVelocity(velocity);

                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (falling.isValid()) {
                        falling.remove();
                    }
                }, 34L);
                break;
            }
        }
    }

    private boolean canConsume(Block block) {
        Material material = block.getType();
        if (material == Material.AIR || !material.isSolid()) {
            return false;
        }

        String name = material.name();
        return !name.equals("BEDROCK")
                && !name.contains("COMMAND")
                && !name.contains("CHEST")
                && !name.contains("FURNACE")
                && !name.contains("DISPENSER")
                && !name.contains("DROPPER")
                && !name.contains("HOPPER")
                && !name.contains("SPAWNER")
                && !name.contains("PORTAL")
                && !name.contains("BARRIER");
    }

    private void finishStorm(Location location) {
        World world = location.getWorld();
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.7f);
        world.spawnParticle(Particle.EXPLOSION_HUGE, location, 5, 2.0, 2.0, 2.0, 0.02);
        world.spawnParticle(Particle.PORTAL, location, 180, 4.0, 3.0, 4.0, 0.15);
        Bukkit.broadcastMessage(
                ChatColor.DARK_PURPLE + "[VyntriLoop] "
                        + ChatColor.GRAY + "The Wither Storm has been defeated."
        );
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
