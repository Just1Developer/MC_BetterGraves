package net.justonedev.mc.plugins.bettergraves;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import static net.justonedev.mc.plugins.bettergraves.BetterGraves.GraveLifetimeMinutes;
import static net.justonedev.mc.plugins.bettergraves.BetterGraves.MaxRange;
import static net.justonedev.mc.plugins.bettergraves.BetterGraves.floor;

public class Graves implements Listener {

	private static final int OFFSET_WORLDLOC_MAXHEIGHT = 1;	// Subtract 1 because world max height is 1 more than build height
	private static final int OFFSET_WORLDLOC_MINHEIGHT = 2;	// Add 2, +1 because we check the block underneath too and +1 because we want to leave the lowest bedrock layer (always obstructed) alone (not consider it)
	/**
	 * So graves don't spawn too low, graves must be at most this much deeper than the OG grave location.
	 */
	private static final int MINIMUM_HEIGHT_DELTA = 25;
	/**
	 * This effect should only happen in close proximity to the place of death.
	 */
	private static final int MAX_RADIUS_SQUARED_MINIMUM_HEIGHT_EFFECT = 75;
	
	@EventHandler
	public void onDeath(PlayerDeathEvent e)
	{
		Player p = e.getEntity();
		Block eyeLoc = p.getEyeLocation().getBlock();
		Block locBlock = p.getLocation().getBlock();
		p.sendMessage("§9DEBUG: Block your loc: " + locBlock.getType() + ", eye: " + eyeLoc.getType());
		p.sendMessage("§9DEBUG: More block info: " + locBlock.getState() + ", " + locBlock.getBlockData() + ", " + locBlock.getPistonMoveReaction());
		p.sendMessage("§9DEBUG: More eye info: " + eyeLoc.getState() + ", " + eyeLoc.getBlockData() + ", " + eyeLoc.getPistonMoveReaction());
		if (e.getKeepInventory())
		{
			e.getEntity().sendMessage(Config.MSG_KEEP_INVENTORY_ON);
			return;
		}
		if (e.getDrops().isEmpty() && e.getDroppedExp() == 0)
		{
			e.getEntity().sendMessage(Config.MSG_PLAYER_HAS_NOTHING);
			return;
		}
		
		Location grave = createGrave(e.getEntity(), e.getDroppedExp());
		if (grave == null)
		{
			e.getEntity().sendMessage(Config.MSG_NO_GRAVE_LOC_FOUND);
			return;
		}
		
		e.setDroppedExp(0);
		e.getDrops().clear();
		p.sendMessage("DEBUG 2: grave loc: " + grave + ", grave loc material: " + grave.getBlock().getType());
		e.getEntity().sendMessage(Config.MSG_GRAVE_CREATED_AT.replace("%location%", grave.getBlockX() + " " + grave.getBlockY() + " " + grave.getBlockZ()).replace("%minutes%", "" + GraveLifetimeMinutes));
	}
	
	public static Location createGrave(Player p, int droppedXP) { return createGrave(p, p.getInventory(), droppedXP); }
	public static Location createGrave(Player p, Inventory inventory, int droppedXP)
	{
		// Clone inv data
		Inventory inv = Bukkit.createInventory(null, 45);
		for (int i = 0; i < 45; i++)
		{
			inv.setItem(i, inventory.getItem(i));
		}
		
		String uuid = BetterGraves.generateGraveUUID();
		Location location = findGraveLocation(p.getLocation());
		if(location == null) return null;
		
		// Create the file with all contents and stuff
		for (int i = 0; i < inv.getSize(); i++)
		{
			ItemStack it = inv.getItem(i);
			if (it == null) continue;
			if (!it.getEnchantments().containsKey(Enchantment.VANISHING_CURSE)) continue;
			inv.setItem(i, new ItemStack(Material.AIR));
		}
		
		Grave grave = new Grave(uuid, p.getUniqueId().toString(), inv, location, droppedXP);
		
		p.sendMessage("§eDEBUG 1: grave loc: " + location + ", grave loc material: " + location.getBlock().getType());
		p.sendMessage("§eDEBUG 2: isSuitable: " + isSuitableGraveLoc(location));
		
		location.getBlock().setType(Material.PLAYER_HEAD);
		Skull skull = (Skull) location.getBlock().getState();
		skull.setOwnerProfile(p.getPlayerProfile());
		skull.update();
		
		if (BetterGraves.GraveProtExpires) GraveExpiration.addExpiration(location);
		BetterGraves.Graves.put(location, grave);
		saveToFile(grave, uuid, GraveExpiration.getExpiration(location));
		
		return location;
	}
	
	private static void saveToFile(Grave grave, String uuid, long expirationTime)
	{
		File f = new File(BetterGraves.instance.getDataFolder() + "/graves/", uuid + ".yml");
		YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
		cfg.set("UUID", grave.PlayerUUID);
		cfg.set("Time.Creation", System.currentTimeMillis());
		cfg.set("Time.Expiration", expirationTime);
		cfg.set("Location.WorldUUID", grave.Location.getWorld().getUID().toString());
		cfg.set("Location.X", grave.Location.getX());
		cfg.set("Location.Y", grave.Location.getY());
		cfg.set("Location.Z", grave.Location.getZ());
		cfg.set("DroppedExp", grave.Location.getZ());
		//cfg.set("Inventory.size", grave.Inventory.getSize());
		for(int i = 0; i < grave.Inventory.getSize(); i++) {
			cfg.set("Inventory.slot." + i, grave.Inventory.getContents()[i]);
		}
		try
		{
			cfg.save(f);
		} catch (IOException ignored) {}
	}
	
	public static Location findGraveLocation(Location DeathLoc)
	{
		DeathLoc = floor(DeathLoc);
		
		// Since we're always searching the block beneath too, DeathLoc cant be minHeight
		if(DeathLoc.getWorld() != null)
		{
			if(DeathLoc.getBlockY() <= DeathLoc.getWorld().getMinHeight()) DeathLoc.setY(DeathLoc.getWorld().getMinHeight() + OFFSET_WORLDLOC_MINHEIGHT);		// +1 for block beneath and also +1 for the bottom bedrock layer
			else if(DeathLoc.getBlockY() >= DeathLoc.getWorld().getMaxHeight()) DeathLoc.setY(DeathLoc.getWorld().getMaxHeight() - OFFSET_WORLDLOC_MAXHEIGHT);	// MaxHeight = 320 even though max building height is 319 lol
		}
		
		boolean isObstructedFeet = !DeathLoc.getBlock().getType().isAir();
		boolean isObstructedBeneath = !DeathLoc.clone().subtract(0, 1, 0).getBlock().getType().isAir();
		if (!isObstructedFeet && isObstructedBeneath && isSuitableGraveLoc(DeathLoc))
		{
			return DeathLoc;
		}
		// Undo subtraction
		boolean isObstructedHead = !DeathLoc.clone().add(0, 1, 1).getBlock().getType().isAir();
		// Feet are obstructed
		if (!isObstructedHead && isSuitableGraveLoc(DeathLoc))
		{
			return DeathLoc;
		}
		
		World w = DeathLoc.getWorld();
		if (w == null) return DeathLoc;
		if (w.hasCeiling())
		{
			Location loc = findGraveLocationCeiling(w, DeathLoc);
			if(loc != null) return loc;
			if (!isObstructedFeet)
			{
				// isObstructedBeneath == false, otherwise it would have triggered
				DeathLoc.clone().subtract(0, 1, 0).getBlock().setType(Material.STONE);	// Common Material, safe(r) spot to land
				return DeathLoc;
			}
			return null;
		}
		Location loc = findGraveLocationNoCeiling(w, DeathLoc);
		if(loc != null) return loc;
		if (!isObstructedFeet)
		{
			// isObstructedBeneath == false, otherwise it would have triggered
			DeathLoc.clone().subtract(0, 1, 0).getBlock().setType(Material.STONE);	// Common Material, safe(r) spot to land
			return DeathLoc;
		}
		return null;
	}
	
	private static Location findGraveLocationNoCeiling(World w, Location OGDeathLoc)
	{
		// This is not an error. For the pillar the player died in, we use the Ceiling method to start where the player died, not at max height. For all other searches in here, we start at the top.
		Location loc = findGraveLocationSearchPillarAboveOnly(w, OGDeathLoc, 0, 0);
		if(loc != null) return loc;
		
		int offsetX = 1, offsetZ = 1;
		
		while (offsetX < MaxRange)
		{
			loc = findGraveLocationNoCeilingSearchPillar(w, OGDeathLoc, offsetX, offsetZ);
			if(loc != null) return loc;
			loc = findGraveLocationNoCeilingSearchPillar(w, OGDeathLoc, offsetX, -offsetZ);
			if(loc != null) return loc;
			loc = findGraveLocationNoCeilingSearchPillar(w, OGDeathLoc, -offsetX, offsetZ);
			if(loc != null) return loc;
			loc = findGraveLocationNoCeilingSearchPillar(w, OGDeathLoc, -offsetX, -offsetZ);
			if(loc != null) return loc;
			
			offsetX += 3;	// Range
			offsetZ += 3;	// Range
		}
		
		// Could not find a location, sorry
		return null;
	}
	
	private static Location findGraveLocationNoCeilingSearchPillar(World w, Location OGDeathLoc, int offsetX, int offsetZ)
	{
		Location DeathLoc = OGDeathLoc.clone().add(offsetX, 0, offsetZ);
		DeathLoc.setY(w.getMaxHeight() - OFFSET_WORLDLOC_MAXHEIGHT);	// Adjust height accordingly. Not inline because that uses an 'add'
		while (DeathLoc.getBlockY() >= w.getMinHeight() + OFFSET_WORLDLOC_MINHEIGHT + getWorldMinimumHeightOffset(OGDeathLoc.getBlockY(), offsetX, offsetZ))	// Offset for min height
		{
			if (pillarCeilingObstructionCheck(DeathLoc) && isSuitableGraveLoc(DeathLoc)) return DeathLoc;
		}
		return null;
	}
	
	private static Location findGraveLocationCeiling(World w, Location OGDeathLoc)
	{
		Location loc = findGraveLocationCeilingSearchPillar(w, OGDeathLoc, 0, 0);
		if(loc != null) return loc;
		
		int offsetX = 1, offsetZ = 1;
		
		while (offsetX < MaxRange)
		{
			loc = findGraveLocationCeilingSearchPillar(w, OGDeathLoc, offsetX, offsetZ);
			if(loc != null) return loc;
			loc = findGraveLocationCeilingSearchPillar(w, OGDeathLoc, offsetX, -offsetZ);
			if(loc != null) return loc;
			loc = findGraveLocationCeilingSearchPillar(w, OGDeathLoc, -offsetX, offsetZ);
			if(loc != null) return loc;
			loc = findGraveLocationCeilingSearchPillar(w, OGDeathLoc, -offsetX, -offsetZ);
			if(loc != null) return loc;
			
			offsetX += 3;	// Range
			offsetZ += 3;	// Range
		}
		
		// Could not find a location, sorry
		return null;
	}
	
	private static Location findGraveLocationSearchPillarAboveOnly(World w, Location OGDeathLoc, int offsetX, int offsetZ)
	{
		Location DeathLoc = OGDeathLoc.clone().add(offsetX, 0, offsetZ);
		// Here it's okay to still have this because otherwise offset is 0.
		if (getWorldMinimumHeightOffset(OGDeathLoc.getBlockY(), offsetX, offsetZ) != 0) DeathLoc.setY(Math.max(w.getMinHeight() + OFFSET_WORLDLOC_MINHEIGHT, OGDeathLoc.getBlockY() - MINIMUM_HEIGHT_DELTA));
		while(DeathLoc.getBlockY() <= w.getMaxHeight() - OFFSET_WORLDLOC_MAXHEIGHT)	// offset because world max height funny
		{
			if (pillarCeilingObstructionCheck(DeathLoc) && isSuitableGraveLoc(DeathLoc)) return DeathLoc;
			DeathLoc.add(0, 2, 0);	// Go up first
		}
		
		return null;
	}
	
	private static Location findGraveLocationCeilingSearchPillar(World w, Location OGDeathLoc, int offsetX, int offsetZ)
	{
		Location DeathLoc = findGraveLocationSearchPillarAboveOnly(w, OGDeathLoc, offsetX, offsetZ);
		if(DeathLoc != null) return DeathLoc;
		
		// Could not find above, now below
		
		DeathLoc = OGDeathLoc.clone().add(offsetX, -1, offsetZ);
		while (DeathLoc.getBlockY() >= w.getMinHeight() + OFFSET_WORLDLOC_MINHEIGHT + getWorldMinimumHeightOffset(OGDeathLoc.getBlockY(), offsetX, offsetZ))
		{
			if (pillarCeilingObstructionCheck(DeathLoc) && isSuitableGraveLoc(DeathLoc)) return DeathLoc;
		}
		
		return null;
	}
	
	private static int getWorldMinimumHeightOffset(int OG_LocY, int offsetX, int offsetZ) {
		// If it's close enough
		if (Math.pow(offsetX, 2) + Math.pow(offsetZ, 2) <= MAX_RADIUS_SQUARED_MINIMUM_HEIGHT_EFFECT) return (OG_LocY - MINIMUM_HEIGHT_DELTA);
		return 0;
	}
	
	private static boolean pillarCeilingObstructionCheck(Location deathLoc) {
		boolean isObstructedLoc = !deathLoc.getBlock().getType().isAir();
		boolean isObstructedBeneath = !deathLoc.subtract(0, 1, 0).getBlock().getType().isAir();
		if (!isObstructedLoc && isObstructedBeneath)
		{
			// Get Block on ground
			deathLoc.add(0, 1, 0);
			return true;
		}
		return false;
	}
	
	// Checks the loc type and the loc type beneath
	// Just an additional security measure
	private static boolean isSuitableGraveLoc(Location loc) {
		Location loc2 = loc.clone();
		if (loc2.getBlockY() >= Objects.requireNonNull(loc2.getWorld()).getMaxHeight()) return false;
		if (loc2.getBlockY() <= Objects.requireNonNull(loc2.getWorld()).getMinHeight()) return false;
		
		Block b = loc2.getBlock();
		if (b.isLiquid()) return false;
		if (b.getType() != Material.AIR) return false;
		// Check block beneath
		loc2.subtract(0, 1, 0);
		b = loc2.getBlock();
		// Perhaps add some stronger safeguards here
		return !b.getType().isAir();
	}
	
}