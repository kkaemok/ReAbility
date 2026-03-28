package org.kkaemok.reAbility.utils;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class SkillParticles {
    private SkillParticles() {}

    private static final Particle.DustOptions GOLD = dust(255, 215, 80, 1.3f);
    private static final Particle.DustOptions WHITE = dust(245, 245, 255, 1.1f);
    private static final Particle.DustOptions PINK = dust(255, 120, 180, 1.1f);
    private static final Particle.DustOptions MINT = dust(170, 255, 200, 1.1f);
    private static final Particle.DustOptions ORANGE = dust(255, 140, 60, 1.2f);
    private static final Particle.DustOptions PURPLE = dust(170, 90, 255, 1.2f);
    private static final Particle.DustOptions DEEP_PURPLE = dust(90, 40, 160, 1.2f);
    private static final Particle.DustOptions RED = dust(255, 70, 70, 1.2f);
    private static final Particle.DustOptions DARK_RED = dust(130, 10, 10, 1.2f);
    private static final Particle.DustOptions BLUE = dust(90, 160, 255, 1.2f);
    private static final Particle.DustOptions TEAL = dust(90, 255, 220, 1.2f);
    private static final Particle.DustOptions STEEL = dust(180, 180, 200, 1.1f);
    private static final Particle.DustOptions BLACK_PURPLE = dust(50, 10, 70, 1.2f);
    private static final Particle.DustOptions GREEN = dust(120, 220, 120, 1.1f);
    private static final Particle.DustTransition PRISM = new Particle.DustTransition(
            Color.fromRGB(170, 80, 255),
            Color.fromRGB(80, 200, 255),
            1.2f
    );

    public static void archangelSpear(Player player, Location strikeLoc) {
        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Vector dir = player.getLocation().getDirection();
        line(world, start, dir, 10.0, 18, Particle.DUST, GOLD);
        ring(world, strikeLoc.clone().add(0, 0.1, 0), 1.6, 20, Particle.DUST, WHITE);
        burst(world, strikeLoc.clone().add(0, 0.6, 0), Particle.END_ROD, 20, 0.4);
        spawn(world, Particle.FLASH, strikeLoc, 1, 0, 0, 0);
    }

    public static void archangelWingbeat(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        ring(world, center, 2.2, 26, Particle.WHITE_ASH, null);
        burst(world, center, Particle.CLOUD, 20, 0.6);
        burst(world, center, Particle.END_ROD, 12, 0.4);
    }

    public static void beautyCharm(Player target) {
        World world = target.getWorld();
        Location center = target.getLocation().add(0, 1.0, 0);
        burst(world, center, Particle.HEART, 8, 0.5);
        ring(world, center, 1.2, 16, Particle.DUST, PINK);
    }

    public static void beautyScent(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        burst(world, center, Particle.SPORE_BLOSSOM_AIR, 40, 1.0);
        ring(world, center, 2.4, 28, Particle.DUST, MINT);
    }

    public static void chefOvereat(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 0.8, 0);
        burst(world, center, Particle.CAMPFIRE_COSY_SMOKE, 20, 0.5);
        spawn(world, Particle.DUST, center, 16, 0.4, 0.4, 0.4, ORANGE);
    }

    public static void chronosRoar(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        ring(world, center, 4.5, 40, Particle.DUST, PURPLE);
        burst(world, center, Particle.END_ROD, 25, 0.9);
        spawn(world, Particle.SONIC_BOOM, center, 1, 0, 0, 0);
    }

    public static void chronosWisdom(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        burst(world, center, Particle.ENCHANT, 25, 0.8);
        ring(world, center, 1.6, 20, Particle.DUST, GOLD);
    }

    public static void chronosSeveranceCharge(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 0.8, 0);
        ring(world, center, 2.4, 26, Particle.DUST, DEEP_PURPLE);
        burst(world, center, Particle.REVERSE_PORTAL, 40, 1.0);
    }

    public static void chronosSeveranceBurst(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        ring(world, center, 5.0, 46, Particle.DUST, BLUE);
        burst(world, center, Particle.END_ROD, 35, 1.2);
        spawn(world, Particle.FLASH, center, 1, 0, 0, 0);
    }

    public static void demonHell(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        Location center = loc.clone().add(0, 0.8, 0);
        ring(world, center.clone().add(0, -0.6, 0), 1.8, 20, Particle.DUST, DARK_RED);
        burst(world, center, Particle.LAVA, 12, 0.4);
        burst(world, center, Particle.LARGE_SMOKE, 15, 0.6);
    }

    public static void demonStaff(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        Location center = loc.clone().add(0, 1.0, 0);
        burst(world, center, Particle.ELECTRIC_SPARK, 30, 0.8);
        burst(world, center, Particle.SOUL_FIRE_FLAME, 12, 0.5);
        ring(world, center, 2.0, 24, Particle.DUST, PURPLE);
    }

    public static void domainExpand(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 0.1, 0);
        ring(world, center, 3.0, 32, Particle.DUST, TEAL);
        burst(world, center.clone().add(0, 1.0, 0), Particle.ENCHANT, 18, 0.6);
    }

    public static void domainMark(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 0.1, 0);
        ring(world, center, 4.0, 36, Particle.DUST, BLUE);
        burst(world, center.clone().add(0, 1.0, 0), Particle.END_ROD, 15, 0.4);
    }

    public static void elementerWater(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        burst(world, center, Particle.SPLASH, 20, 0.6);
        burst(world, center, Particle.BUBBLE_POP, 20, 0.5);
        ring(world, center, 1.8, 20, Particle.DUST, BLUE);
    }

    public static void elementerFire(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        burst(world, center, Particle.FLAME, 25, 0.6);
        burst(world, center, Particle.LARGE_SMOKE, 15, 0.6);
        ring(world, center, 2.0, 24, Particle.DUST, ORANGE);
    }

    public static void fighterEscape(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        burst(world, center, Particle.LARGE_SMOKE, 20, 0.6);
        line(world, center, player.getLocation().getDirection(), 3.0, 12, Particle.DUST, BLACK_PURPLE);
    }

    public static void ghostSkill(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        burst(world, center, Particle.SOUL, 18, 0.6);
        burst(world, center, Particle.CLOUD, 12, 0.5);
        ring(world, center, 1.6, 18, Particle.DUST, BLUE);
    }

    public static void gunslingerBarrageCharge(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        burst(world, eye, Particle.SMOKE, 8, 0.2);
        burst(world, eye, Particle.FIREWORK, 8, 0.2);
    }

    public static void gunslingerMuzzle(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        burst(world, eye, Particle.FIREWORK, 6, 0.05);
        burst(world, eye, Particle.FLAME, 4, 0.05);
    }

    public static void jokerSpy(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        ring(world, center, 1.5, 20, Particle.DUST, GREEN);
        burst(world, center, Particle.EFFECT, 15, 0.5);
    }

    public static void jokerIllusion(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        ring(world, center, 2.0, 24, Particle.DUST_COLOR_TRANSITION, PRISM);
        burst(world, center, Particle.WITCH, 20, 0.6);
    }

    public static void jokerIllusionTarget(Player target) {
        World world = target.getWorld();
        Location center = target.getLocation().add(0, 1.0, 0);
        burst(world, center, Particle.WITCH, 8, 0.4);
    }

    public static void jokerDeathTrick(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        ring(world, center, 1.8, 24, Particle.DUST, DARK_RED);
        burst(world, center, Particle.SMOKE, 20, 0.7);
        burst(world, center, Particle.FIREWORK, 10, 0.4);
    }

    public static void minerAlchemy(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        ring(world, center, 1.6, 20, Particle.DUST, GOLD);
        burst(world, center, Particle.ENCHANT, 25, 0.5);
        burst(world, center, Particle.FIREWORK, 8, 0.3);
    }

    public static void onlySwordField(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 0.1, 0);
        ring(world, center, 3.5, 36, Particle.DUST, STEEL);
        burst(world, center.clone().add(0, 1.0, 0), Particle.SWEEP_ATTACK, 6, 0.3);
    }

    public static void onlySwordWTap(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        line(world, eye, player.getLocation().getDirection(), 2.5, 10, Particle.SWEEP_ATTACK, null);
        burst(world, eye, Particle.CRIT, 8, 0.2);
    }

    public static void phoenixEvade(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        burst(world, center, Particle.FLAME, 25, 0.6);
        burst(world, center, Particle.TOTEM_OF_UNDYING, 10, 0.6);
        ring(world, center, 1.8, 20, Particle.DUST, ORANGE);
    }

    public static void phoenixIIEvade(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        ring(world, center, 2.5, 28, Particle.DUST, GOLD);
        burst(world, center, Particle.TOTEM_OF_UNDYING, 18, 0.8);
    }

    public static void phoenixIITarget(Player target) {
        World world = target.getWorld();
        Location center = target.getLocation().add(0, 1.0, 0);
        burst(world, center, Particle.END_ROD, 8, 0.3);
        ring(world, center, 1.5, 16, Particle.DUST, GOLD);
    }

    public static void shoddyCircle(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 0.1, 0);
        ring(world, center, 2.0, 20, Particle.DUST, DEEP_PURPLE);
        burst(world, center.clone().add(0, 1.0, 0), Particle.WITCH, 12, 0.5);
        burst(world, center.clone().add(0, 1.0, 0), Particle.SMOKE, 10, 0.4);
    }

    public static void spaceMoveSource(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        Location center = loc.clone().add(0, 0.2, 0);
        ring(world, center, 1.8, 20, Particle.REVERSE_PORTAL, null);
        burst(world, loc.clone().add(0, 1.0, 0), Particle.PORTAL, 25, 0.7);
    }

    public static void spaceMoveDest(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        Location center = loc.clone().add(0, 0.2, 0);
        ring(world, center, 1.8, 20, Particle.PORTAL, null);
        burst(world, loc.clone().add(0, 1.0, 0), Particle.END_ROD, 20, 0.6);
    }

    public static void spaceCut(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        line(world, eye, player.getLocation().getDirection(), 4.0, 16, Particle.DUST, PURPLE);
        burst(world, eye, Particle.SWEEP_ATTACK, 4, 0.2);
    }

    public static void valkyrieWings(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        burst(world, center, Particle.WHITE_ASH, 20, 0.6);
        ring(world, center, 2.0, 24, Particle.DUST, WHITE);
    }

    public static void valkyrieVortex(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 0.5, 0);
        ring(world, center, 2.5, 28, Particle.DUST, RED);
        burst(world, center.clone().add(0, 1.0, 0), Particle.CLOUD, 18, 0.6);
    }

    public static void werewolfNight(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        ring(world, center, 3.0, 32, Particle.DUST, BLUE);
        burst(world, center, Particle.ASH, 25, 0.8);
        burst(world, center, Particle.SMOKE, 12, 0.6);
    }

    public static void splusUnlock(Player player, int souls) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        switch (souls) {
            case 50 -> {
                burst(world, center, Particle.SOUL, 16, 0.6);
                ring(world, center, 1.6, 18, Particle.DUST, PURPLE);
            }
            case 100 -> {
                burst(world, center, Particle.SQUID_INK, 12, 0.6);
                ring(world, center, 1.8, 20, Particle.DUST, DEEP_PURPLE);
            }
            case 300 -> {
                burst(world, center, Particle.DAMAGE_INDICATOR, 12, 0.5);
                ring(world, center, 2.0, 22, Particle.DUST, RED);
            }
            case 1000 -> {
                burst(world, center, Particle.SCULK_SOUL, 16, 0.6);
                burst(world, center, Particle.SCULK_CHARGE, 10, 0.6);
            }
            case 2000 -> {
                burst(world, center, Particle.SPORE_BLOSSOM_AIR, 24, 0.8);
                ring(world, center, 2.2, 24, Particle.DUST, BLACK_PURPLE);
            }
            case 10000 -> {
                ring(world, center, 3.2, 36, Particle.DUST, DARK_RED);
                burst(world, center, Particle.ASH, 40, 1.0);
                burst(world, center, Particle.LARGE_SMOKE, 20, 0.8);
                spawn(world, Particle.FLASH, center, 1, 0, 0, 0);
            }
            default -> {}
        }
    }

    private static Particle.DustOptions dust(int r, int g, int b, float size) {
        return new Particle.DustOptions(Color.fromRGB(r, g, b), size);
    }

    private static void spawn(World world, Particle particle, Location loc, int count,
                              double ox, double oy, double oz) {
        Object defaultData = defaultDataFor(particle);
        try {
            if (defaultData != null) {
                world.spawnParticle(particle, loc, count, ox, oy, oz, 0.0, defaultData);
                return;
            }

            if (particle.getDataType() == Void.class) {
                world.spawnParticle(particle, loc, count, ox, oy, oz, 0.0);
            }
        } catch (IllegalArgumentException ignored) {
            // Do not break skill execution due to a particle data mismatch.
        }
    }

    private static void spawn(World world, Particle particle, Location loc, int count,
                              double ox, double oy, double oz, Object data) {
        if (data == null) {
            spawn(world, particle, loc, count, ox, oy, oz);
        } else {
            try {
                world.spawnParticle(particle, loc, count, ox, oy, oz, 0.0, data);
            } catch (IllegalArgumentException ignored) {
                // Do not break skill execution due to a particle data mismatch.
            }
        }
    }

    private static Object defaultDataFor(Particle particle) {
        Class<?> dataType = particle.getDataType();
        if (dataType == Color.class) {
            return Color.WHITE;
        }
        if (dataType == Particle.DustOptions.class) {
            return WHITE;
        }
        if (dataType == Particle.DustTransition.class) {
            return PRISM;
        }
        return null;
    }

    private static void burst(World world, Location center, Particle particle, int count, double spread) {
        spawn(world, particle, center, count, spread, spread, spread);
    }

    private static void ring(World world, Location center, double radius, int points,
                             Particle particle, Object data) {
        double step = (Math.PI * 2.0) / points;
        for (int i = 0; i < points; i++) {
            double angle = step * i;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location loc = center.clone().add(x, 0, z);
            spawn(world, particle, loc, 1, 0, 0, 0, data);
        }
    }

    private static void line(World world, Location start, Vector direction, double length,
                             int points, Particle particle, Object data) {
        Vector dir = direction.clone().normalize();
        double step = length / points;
        for (int i = 0; i <= points; i++) {
            Location loc = start.clone().add(dir.clone().multiply(step * i));
            spawn(world, particle, loc, 1, 0, 0, 0, data);
        }
    }
}
