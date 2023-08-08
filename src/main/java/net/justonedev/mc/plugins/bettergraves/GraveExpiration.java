package net.justonedev.mc.plugins.bettergraves;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class GraveExpiration {
	
	// If a grave is not contained in here, it means it does not expire.
	private static final HashMap<Location, Long> ExpirationTimes = new HashMap<>();
	
	private static int Scheduler;
	private static boolean running = false;
	
	public static void addExpiration(Location loc)
	{
		ExpirationTimes.put(loc, System.currentTimeMillis() + BetterGraves.GraveLifetimeMS);
		startScheduler();
	}
	
	public static void addExpiration(Location loc, long endTime)
	{
		if(endTime < System.currentTimeMillis()) return;	// Already expired
		ExpirationTimes.put(loc, endTime);
		startScheduler();
	}
	
	public static void removeExpiration(Location loc)
	{
		ExpirationTimes.remove(loc);
		if(ExpirationTimes.isEmpty())
		{
			Bukkit.getScheduler().cancelTask(Scheduler);
			running = false;
		}
	}
	
	public static boolean isExpired(Location loc)
	{
		// ExpirationTime is 0 if its not in the HashMap I think
		if (!ExpirationTimes.containsKey(loc)) return false;		// If the server crashes, all graves gain immortality status
		return ExpirationTimes.get(loc) < System.currentTimeMillis();
	}
	
	public static long getExpiration(Location loc)
	{
		if(!ExpirationTimes.containsKey(loc)) return 0;
		return ExpirationTimes.get(loc);
	}
	
	static void startScheduler()
	{
		if (Bukkit.getScheduler().isCurrentlyRunning(Scheduler)) return;
		if (running) return;
		if (ExpirationTimes.isEmpty()) return;
		running = true;
		Scheduler = Bukkit.getScheduler().scheduleSyncRepeatingTask(BetterGraves.instance, () ->
		{
			Set<Location> remove = new HashSet<>();
			for (Location loc : ExpirationTimes.keySet())
			{
				if(isExpired(loc)) {
					remove.add(loc);
					// Send expired message
					BetterGraves.Graves.get(loc).sendMessage(Config.MSG_GRAVE_EXPIRED.replace("%location%", loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
					continue;
				}
				long remainingProt = ExpirationTimes.get(loc) - System.currentTimeMillis();
				// if its between 5 and 6 minutes, we have not yet received the time
				// 5 minutes: 5 * 60000 = 300000 | -> 360000
				if(remainingProt < 75000 && remainingProt >= 15000)
				{
					// Send ~1 minute remaining message
					BetterGraves.Graves.get(loc).sendMessage(Config.MSG_GRAVE_EXPIRES_MINUTE.replace("%location%", loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
				}
				else if(remainingProt >= 300000 && remainingProt < 360000)
				{
					// Send 5 minutes remaining message
					BetterGraves.Graves.get(loc).sendMessage(Config.MSG_GRAVE_EXPIRES_MINUTES.replace("%location%", loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()).replace("%minutes%", "5"));
				}
				else if(remainingProt >= 600000 && remainingProt < 660000)
				{
					// Send 10 minutes remaining message
					BetterGraves.Graves.get(loc).sendMessage(Config.MSG_GRAVE_EXPIRES_MINUTES.replace("%location%", loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()).replace("%minutes%", "10"));
				}
			}
			
			for (Location loc : remove)
			{
				removeExpiration(loc);
			}
		}, 1200, 1200);		// 1 min: 20 ticks/sec * 60 secs = 20*60 = 2*6 * 10^2 = 12*100 = 1200
	}
}
