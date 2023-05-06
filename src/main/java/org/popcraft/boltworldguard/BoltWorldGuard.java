package org.popcraft.boltworldguard;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.bolt.BoltAPI;
import org.popcraft.bolt.protection.BlockProtection;
import org.popcraft.bolt.protection.Protection;
import org.popcraft.bolt.source.SourceTypes;

import java.util.Collections;
import java.util.List;

public final class BoltWorldGuard extends JavaPlugin implements Listener {
    private BoltAPI bolt;
    private WorldGuardPlugin worldGuardPlugin;

    @Override
    public void onEnable() {
        this.bolt = getServer().getServicesManager().load(BoltAPI.class);
        if (bolt == null) {
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
        final BlockVector3 minimum = protectedRegion.getMinimumPoint();
        final BlockVector3 maximum = protectedRegion.getMaximumPoint();
        final int minBlockX = minimum.getBlockX();
        final int minBlockY = minimum.getBlockY();
        final int minBlockZ = minimum.getBlockZ();
        final int maxBlockX = maximum.getBlockX();
        final int maxBlockY = maximum.getBlockY();
        final int maxBlockZ = maximum.getBlockZ();
        final boolean purge = "purgeregion".equals(command.getName());
        for (int x = minBlockX; x <= maxBlockX; x++) {
            for (int y = minBlockY; y <= maxBlockY; y++) {
                for (int z = minBlockZ; z <= maxBlockZ; z++) {
                    final Block block = bukkitWorld.getBlockAt(x, y, z);
                    if (purge) {
                        final Protection protection = bolt.findProtection(block);
                        if (protection != null) {
                            bolt.removeProtection(protection);
                        }
                    } else {
                        if (!bolt.isProtectable(block) || bolt.isProtected(block)) {
                            continue;
                        }
                        final BlockProtection blockProtection = bolt.createProtection(block, player.getUniqueId(), "private");
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
