package net.justonedev.mc.plugins.bettergraves;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class BetterGraves extends JavaPlugin implements Listener {
	
	public static BetterGraves instance;
	static HashMap<Location, Grave> Graves;
	
	public static long GraveLifetimeMinutes = 15;	// X * 60000 = X minutes
	public static long GraveLifetimeMS;	// X * 60000 = X minutes
	public static long GravePurgeOlderThanMS;	// MS, config lists in hours
	
	public static long MaxRange = 900;	// For looking for good spots
	
	public static boolean GraveProtExpires = true;
	public static boolean DropItemsIfInventoryFull = true;
	public static boolean EnforceBindingCurse = true;
	
	@Override
	public void onEnable() {
		// Plugin startup logic
		instance = this;
		Graves = new HashMap<>();
		Bukkit.getPluginManager().registerEvents(this, this);
		Bukkit.getPluginManager().registerEvents(new Graves(), this);
		
		Config.Load();
		CarePackage.Init();
		
		getCommand("purgeoldgraves").setExecutor(this);
		getCommand("gravelist").setExecutor(new Gravelist());
		getCommand("recovergrave").setExecutor(new Cmd_recoverGrave());
		
		// Load all graves. Since they aren't changed and only created once somewhere else, there is no need to export them on shutdown
		File folder = new File(getDataFolder() + "/graves/");
		if(!folder.exists()) folder.mkdirs();
		File[] files = folder.listFiles();
		if(files == null) return;
		for (File f : files)
		{
			Grave grave = Grave.load(f.getName().substring(0, f.getName().length() - 4));
			if (grave == null) continue;
			Graves.put(grave.Location, grave);
		}
		GraveExpiration.startScheduler();
	}
	
	/**
	 * Gets a grave by its location.
	 * Returns null if there is currently no grave at the given location.
	 *
	 * @param location The grave location
	 * @return The grave object or null
	 */
	public static Grave getGrave(final Location location) {
		Location loc = floor(location);
		if (Graves.containsKey(loc)) {
			return Graves.get(loc);
		}
		return null;
	}
	
	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		if (!CarePackage.CarePackageFileNames.contains(e.getPlayer().getName())
		&& !CarePackage.CarePackageFileNames.contains(e.getPlayer().getUniqueId().toString())) return;
		
		if (CarePackage.CarePackageFileNames.contains(e.getPlayer().getName())) {
			CarePackage pkg = CarePackage.loadFromFile(e.getPlayer().getName());
			if (pkg != null) {
				e.getPlayer().sendMessage(Config.MSG_YOU_HAVE_MAIL);
				pkg.Collect(e.getPlayer());
			}
		}
		if (CarePackage.CarePackageFileNames.contains(e.getPlayer().getUniqueId().toString())) {
			CarePackage pkg = CarePackage.loadFromFile(e.getPlayer().getUniqueId().toString());
			if (pkg == null) return;
			e.getPlayer().sendMessage(Config.MSG_YOU_HAVE_MAIL);
			pkg.Collect(e.getPlayer());
		}
	}
	
	@EventHandler
	public void onGraveBreak(BlockBreakEvent e)
	{
		if (!Graves.containsKey(e.getBlock().getLocation())) return;
		
		Location loc = e.getBlock().getLocation();
		Grave grave = Graves.get(loc);
		// Allowed to break: Either own grave or grave expired
		boolean allowed = grave.PlayerUUID.equals(e.getPlayer().getUniqueId().toString()) || GraveExpiration.isExpired(loc) || e.getPlayer().hasPermission(Config.PERMISSION_BYPASS_GRAVE_PROT);
		
		if(!allowed)
		{
			e.getPlayer().sendMessage(Config.MSG_NOT_YOUR_GRAVE);
			e.setCancelled(true);
			return;
		}
		
		e.setDropItems(false);
		Graves.remove(loc);
		GraveExpiration.removeExpiration(loc);
		grave.give(e.getPlayer());
	}
	
	public static Location floor(Location loc)
	{
		return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
	}
	
	@EventHandler
	public void entityExplodeProtection(EntityExplodeEvent e)
	{
		coreProtection(e.isCancelled(), e.blockList());
	}
	
	@EventHandler
	public void bedExplodeProtection(BlockExplodeEvent e)
	{
		coreProtection(e.isCancelled(), e.blockList());
	}
	
	private void coreProtection(boolean cancelled, List<Block> blocks) {
		if(cancelled) return;
		
		for(int i = 0; i < blocks.size(); i++)
		{
			if(!Graves.containsKey(blocks.get(i).getLocation())) continue;
			blocks.remove(i);
			i--;	// re-run the index
		}
	}
	
	@EventHandler
	public void pistonEvent(BlockPistonExtendEvent e)
	{
		for (Block block  : e.getBlocks())
		{
			if(!Graves.containsKey(block.getLocation())) continue;
			e.setCancelled(true);
			return;
		}
	}
	
	public static String generateGraveUUID()
	{
		StringBuilder uuid = new StringBuilder();
		do
		{
			for(int i = 0; i < 16; )
			{
				// 26 + 26 + 10
				int rnd = (int) (Math.random() * 62) + 48;
				if(rnd > 57) rnd += 7; // instead of 58 we want 65
				if(rnd > 90) rnd += 6; // instead of 91 we want 97
				uuid.append((char) rnd);
				if((++i) % 4 == 0 && i < 15) uuid.append('-');
			}
		} while (isUUIDUsed(uuid.toString()));
		return uuid.toString();
	}
	
	public static boolean isUUIDUsed(String uuid)
	{
		for (Grave g : Graves.values())
		{
			if(g.GraveUID.equals(uuid)) return true;
		}
		return false;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(!sender.hasPermission(Config.PERMISSION_PURGE_OLD_GRAVES) && !(sender.equals(Bukkit.getConsoleSender())))
		{
			sender.sendMessage(Config.MSG_GRAVEPURGE_NO_PERMS);
			return false;
		}
		
		Set<Grave> graves = new HashSet<>();
		long minTime = System.currentTimeMillis() - GravePurgeOlderThanMS;
		for (Location loc : Graves.keySet())
		{
			Grave g = Graves.get(loc);
			if(g.CreationTime < minTime)
			{
				graves.add(g);
				g.sendMessage(Config.MSG_YOUR_GRAVE_WILL_BE_PURGED.replace("%location%", loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
			}
		}
		
		for (Grave g : graves)
		{
			g.deleteGraveFile();
			Graves.remove(g.Location);
			GraveExpiration.removeExpiration(g.Location);
			g.Location.getBlock().setType(Material.AIR);
		}
		
		sender.sendMessage(Config.MSG_GRAVEPURGE_WIPED_GRAVES.replace("%graves%", "" + graves.size()));
		return true;
	}
	/* Funny idea here
	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if(!sender.hasPermission(Config.PERMISSION_PURGE_OLD_GRAVES) && !(sender.equals(Bukkit.getConsoleSender())))
		{
			return new ArrayList<>();
		}
		switch(args.length)
		{
			case 0:
				return
		}
	}
	 */
}
