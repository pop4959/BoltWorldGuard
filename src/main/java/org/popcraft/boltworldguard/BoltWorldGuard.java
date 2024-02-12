package org.popcraft.boltworldguard;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.popcraft.bolt.BoltAPI;
import org.popcraft.bolt.event.LockBlockEvent;
import org.popcraft.bolt.event.LockEntityEvent;
import org.popcraft.bolt.protection.BlockProtection;
import org.popcraft.bolt.protection.EntityProtection;
import org.popcraft.bolt.protection.Protection;
import org.popcraft.bolt.source.SourceTypes;

import java.lang.module.ModuleDescriptor.Version;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class BoltWorldGuard extends JavaPlugin implements Listener {
    private static final Version EXPECTED_BOLT_VERSION = Version.parse("1.0.575");
    private BoltAPI bolt;
    private WorldGuardPlugin worldGuardPlugin;

    @Override
    public void onEnable() {
        this.bolt = getServer().getServicesManager().load(BoltAPI.class);
        if (bolt == null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        final Version boltVersion = Version.parse(getServer().getPluginManager().getPlugin("Bolt").getDescription().getVersion());
        if (boltVersion.compareTo(EXPECTED_BOLT_VERSION) < 0) {
            getLogger().severe("Your version of Bolt is too old to integrate with this version of BoltWorldGuard");
            getLogger().severe("Expected at least version " + EXPECTED_BOLT_VERSION + ", but you have version " + boltVersion);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.worldGuardPlugin = (WorldGuardPlugin) getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuardPlugin == null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        bolt.registerPlayerSourceResolver((source, uuid) -> {
            if (!SourceTypes.REGION.equals(source.getType())) {
                return false;
            }
            final Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                return false;
            }
            final LocalPlayer localPlayer = worldGuardPlugin.wrapPlayer(player);
            if (localPlayer == null) {
                return false;
            }
            final World world = localPlayer.getWorld();
            if (world == null) {
                return false;
            }
            final String region = source.getIdentifier();
            final RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(world);
            if (regionManager != null && regionManager.hasRegion(region)) {
                final ProtectedRegion protectedRegion = regionManager.getRegion(region);
                return protectedRegion != null && protectedRegion.isMember(localPlayer);
            }
            return false;
        });
        bolt.registerListener(LockBlockEvent.class, event -> {
            final boolean cancel = !worldGuardPlugin.createProtectionQuery().testBlockPlace(
                event.getPlayer(),
                event.getBlock().getLocation(),
                event.getBlock().getType()
            );
            if (cancel) {
                event.setCancelled(true);
            }
        });
        bolt.registerListener(LockEntityEvent.class, event -> {
            final boolean cancel = !worldGuardPlugin.createProtectionQuery().testEntityPlace(
                event.getPlayer(),
                event.getEntity().getLocation(),
                event.getEntity().getType()
            );
            if (cancel) {
                event.setCancelled(true);
            }
        });
    }

    @Override
    public void onDisable() {
        this.bolt = null;
        this.worldGuardPlugin = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (worldGuardPlugin == null) {
            sender.sendMessage("WorldGuard plugin not enabled!");
            return false;
        }
        if (!(sender instanceof final Player player) || args.length < 1) {
            sender.sendMessage("Not enough arguments!");
            return false;
        }
        final LocalPlayer localPlayer = worldGuardPlugin.wrapPlayer(player);
        if (localPlayer == null) {
            sender.sendMessage("Can't identify player!");
            return false;
        }
        final World world = localPlayer.getWorld();
        final org.bukkit.World bukkitWorld = player.getWorld();
        if (world == null) {
            sender.sendMessage("Can't identify world!");
            return false;
        }
        final String region = args[0];
        final RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(world);
        if (regionManager == null || !regionManager.hasRegion(region)) {
            sender.sendMessage("Region doesn't exist!");
            return false;
        }
        final ProtectedRegion protectedRegion = regionManager.getRegion(region);
        if (protectedRegion == null) {
            sender.sendMessage("Region doesn't exist!");
            return false;
        }
        Material material = null;
        UUID owner = null;
        String type = null;
        for (final String arg : args) {
            final String[] split = arg.split(":");
            if (split.length < 2) {
                continue;
            }
            switch (split[0]) {
                case "block" -> material = Material.matchMaterial(split[1].toUpperCase());
                case "owner" -> //noinspection deprecation
                        owner = Bukkit.getOfflinePlayer(split[1]).getUniqueId();
                case "type" -> type = split[1];
            }
        }
        final UUID protectOwner = owner == null ? player.getUniqueId() : owner;
        final String protectType = type == null ? "private" : type;
        final BlockVector3 minimum = protectedRegion.getMinimumPoint();
        final BlockVector3 maximum = protectedRegion.getMaximumPoint();
        final int minBlockX = minimum.getBlockX();
        final int minBlockY = minimum.getBlockY();
        final int minBlockZ = minimum.getBlockZ();
        final int maxBlockX = maximum.getBlockX();
        final int maxBlockY = maximum.getBlockY();
        final int maxBlockZ = maximum.getBlockZ();
        final Vector min = new Vector(minBlockX, minBlockY, minBlockZ);
        final Vector max = new Vector(maxBlockX, maxBlockY, maxBlockZ);
        final BoundingBox boundingBox = BoundingBox.of(min, max);
        final boolean purge = "purgeregion".equals(command.getName());
        if (purge) {
            final Collection<Protection> protections = bolt.findProtections(bukkitWorld, boundingBox);
            for (final Protection protection : protections) {
                if (protection instanceof EntityProtection) {
                    continue;
                }
                if (material != null && protection instanceof final BlockProtection blockProtection && !blockProtection.getBlock().equalsIgnoreCase(material.name())) {
                    continue;
                }
                if (owner != null && !protection.getOwner().equals(owner)) {
                    continue;
                }
                if (type != null && !protection.getType().equalsIgnoreCase(type)) {
                    continue;
                }
                bolt.removeProtection(protection);
            }
        } else {
            for (int x = minBlockX; x <= maxBlockX; x++) {
                for (int y = minBlockY; y <= maxBlockY; y++) {
                    for (int z = minBlockZ; z <= maxBlockZ; z++) {
                        final Block block = bukkitWorld.getBlockAt(x, y, z);
                        if (!bolt.isProtectable(block) || bolt.isProtected(block)) {
                            continue;
                        }
                        if (material != null && !material.equals(block.getType())) {
                            continue;
                        }
                        final BlockProtection blockProtection = bolt.createProtection(block, protectOwner, protectType);
                        bolt.saveProtection(blockProtection);
                    }
                }
            }
        }
        if (purge) {
            sender.sendMessage("Purged region %s".formatted(region));
        } else {
            sender.sendMessage("Protected region %s".formatted(region));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (worldGuardPlugin == null || args.length != 1 || !(sender instanceof final Player player)) {
            return Collections.emptyList();
        }
        final LocalPlayer localPlayer = worldGuardPlugin.wrapPlayer(player);
        if (localPlayer == null) {
            return Collections.emptyList();

        }
        final World world = localPlayer.getWorld();
        if (world == null) {
            return Collections.emptyList();
        }
        final RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(world);
        if (regionManager == null) {
            return Collections.emptyList();
        }
        return regionManager.getRegions().keySet().stream().toList();
    }
}
