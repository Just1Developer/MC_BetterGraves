package net.justonedev.mc.plugins.bettergraves;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

import static net.justonedev.mc.plugins.bettergraves.BetterGraves.GraveLifetimeMinutes;

public class Config {
	
	public static String PERMISSION_BYPASS_GRAVE_PROT, PERMISSION_PURGE_OLD_GRAVES, PERMISSION_VIEWLIST_OTHER;
	public static String MSG_NOT_YOUR_GRAVE;
	public static String MSG_ITEMS_NOT_RECOVERED_1_TO_1, MSG_SOME_ITEMS_DROPPED, MSG_ITEMS_LEFTOVER_IN_GRAVE;
	public static String MSG_KEEP_INVENTORY_ON, MSG_PLAYER_HAS_NOTHING, MSG_NO_GRAVE_LOC_FOUND, MSG_GRAVE_CREATED_AT;
	public static String MSG_GRAVE_EXPIRES_MINUTES, MSG_GRAVE_EXPIRES_MINUTE, MSG_GRAVE_EXPIRED;
	public static String MSG_GRAVEPURGE_NO_PERMS, MSG_GRAVEPURGE_WIPED_GRAVES, MSG_YOUR_GRAVE_WILL_BE_PURGED;
	public static String MSG_LIST_NOPLAYER, MSG_LIST_NOPERMS, MSG_LIST_PLAYERNOTONLINE, MSG_LIST_INVALID_UUID,
			MSG_LIST_NOGRAVES_SELF, MSG_LIST_NOGRAVES_OTHER, MSG_LIST_GRAVELIST_SELF, MSG_LIST_GRAVELIST_OTHER, MSG_LIST_FORMAT;
	
	public static void Load()
	{
		File f = new File(BetterGraves.instance.getDataFolder(), "config.yml");
		YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
		if(!f.exists())
		{
			cfg.options().copyDefaults(true);
			cfg.addDefault("Drop Items if the Inventory is full", true);
			cfg.addDefault("Enforce (Re-equip) Curse of Binding instead of currently worn armor", true);
			cfg.addDefault("Grave protection expires", true);
			cfg.addDefault("Grave protection time (minutes)", 15);
			cfg.addDefault("Maximum Search Range", 700);
			cfg.addDefault("Purge Command wipes graves older than x hours", 31);
			
			cfg.addDefault("Permissions.Bypass grave protection", "graves.breakOther");
			cfg.addDefault("Permissions.Purge old graves", "graves.purge");
			cfg.addDefault("Permissions.View other's graves", "graves.listother");
			cfg.addDefault("Messages.GraveRecovery.Not your grave", "&cThis grave is not yours! You need to wait until it expires.");
			cfg.addDefault("Messages.GraveRecovery.Items not recovered 1 to 1", "&7Some items might not be where they were previously stored.");
			cfg.addDefault("Messages.GraveRecovery.Some items were dropped", "&cWarning: &7Some items have been dropped.");
			cfg.addDefault("Messages.GraveRecovery.Some items left in grave", "&cWarning: &7Not all items fit into your inventory. The rest of the items remains in the grave, the timer has been reset.");
			cfg.addDefault("Messages.Death.KeepInventory is on", "&eSince KeepInventory is on, no grave was created for you.");
			cfg.addDefault("Messages.Death.Nothing of value was lost", "&eSince you have no stuff or xp, no grave will be spawned.");
			cfg.addDefault("Messages.Death.No grave found", "&cAlert: Could not find a location to set your grave, your items have been dropped like normal!");
			cfg.addDefault("Messages.Death.Grave created at", "&7Your grave was created at &c%location%&7. The access protection expires in %minutes% minutes.");
			cfg.addDefault("Messages.Expiration.Grave expires in minutes", "&cWarning: &7Your grave at &c%location%&7 will lose protection in around &e%minutes% &7minutes.");
			cfg.addDefault("Messages.Expiration.Grave expires in one minute", "&cWarning: &7Your grave at &c%location%&7 will lose protection in around &e1 &7minute.");
			cfg.addDefault("Messages.Expiration.Grave expired", "&cWarning: &7The protection of your grave at &c%location%&7 has expired.");
			cfg.addDefault("Messages.Purge.No permissions", "&cSorry, your don't have permission for that!");
			cfg.addDefault("Messages.Purge.Wiped graves", "&ePurged &c%graves% &eold graves.");
			cfg.addDefault("Messages.Purge.Your grave will be purged", "&cWarning: &7Your grave at &c%location%&7 will be purged momentarily.");
			
			cfg.addDefault("Messages.Gravelist.Not a Player", "&cSorry, this command is for players only.");
			cfg.addDefault("Messages.Gravelist.No Permission", "&cSorry, your don't have permission for that!");
			cfg.addDefault("Messages.Gravelist.Player not found", "&cCan't find %name% right now. For offline players, you can try the uuid instead: &e/gravelist uuid:<uuid>");
			cfg.addDefault("Messages.Gravelist.UUID invalid", "&cSorry, but '%uuid%' is not a valid uuid");
			cfg.addDefault("Messages.Gravelist.No Graves - Self", "&cYou don't seem to have any graves lying around..");
			cfg.addDefault("Messages.Gravelist.No Graves - Other", "&c%name% doesn't seem to have any graves lying around..");
			cfg.addDefault("Messages.Gravelist.GraveList - Self", "&eAll your current graves:");
			cfg.addDefault("Messages.Gravelist.GraveList - Other", "&eAll of %name%%suffix% current graves:");
			cfg.addDefault("Messages.Gravelist.GraveListing Format", "&7 >> &e%location%&7: &c%itemcount% &7Items | Time left: &a%time%");
			
			try {
				cfg.save(f);
			} catch (IOException ignored) { }
		}
		
		GraveLifetimeMinutes = cfg.getInt("Grave protection time (minutes)");
		BetterGraves.GraveLifetimeMS = GraveLifetimeMinutes * 60000;
		BetterGraves.GraveProtExpires = cfg.getBoolean("Grave protection expires");
		BetterGraves.DropItemsIfInventoryFull = cfg.getBoolean("Drop Items if the Inventory is full");
		BetterGraves.EnforceBindingCurse = cfg.getBoolean("Enforce (Re-equip) Curse of Binding instead of currently worn armor");
		BetterGraves.MaxRange = cfg.getInt("Maximum Search Range");
		int hours = cfg.getInt("Purge Command wipes graves older than x hours");
		BetterGraves.GravePurgeOlderThanMS = (hours == 0 ? 31 : hours) * 3600000L;
		
		PERMISSION_BYPASS_GRAVE_PROT = getString(f, cfg, "Permissions.Bypass grave protection", "graves.breakOther");
		PERMISSION_PURGE_OLD_GRAVES = getString(f, cfg, "Permissions.Purge old graves", "graves.purge");
		PERMISSION_VIEWLIST_OTHER = getString(f, cfg, "Permissions.View other's graves", "graves.listother");
		MSG_NOT_YOUR_GRAVE = getString(f, cfg, "Messages.GraveRecovery.Not your grave", "&cThis grave is not yours! You need to wait until it expires.");
		MSG_ITEMS_NOT_RECOVERED_1_TO_1 = getString(f, cfg, "Messages.GraveRecovery.Items not recovered 1 to 1", "&7Some items might not be where they were previously stored.");
		MSG_SOME_ITEMS_DROPPED = getString(f, cfg, "Messages.GraveRecovery.Some items were dropped", "&cWarning: &7Some items have been dropped.");
		MSG_ITEMS_LEFTOVER_IN_GRAVE = getString(f, cfg, "Messages.GraveRecovery.Some items left in grave", "&cWarning: &7Not all items fit into your inventory. The rest of the items remains in the grave, the timer has been reset.");
		MSG_KEEP_INVENTORY_ON = getString(f, cfg, "Messages.Death.KeepInventory is on", "&eSince KeepInventory is on, no grave was created for you.");
		MSG_PLAYER_HAS_NOTHING = getString(f, cfg, "Messages.Death.Nothing of value was lost", "&eSince you have no stuff or xp, no grave will be spawned.");
		MSG_NO_GRAVE_LOC_FOUND = getString(f, cfg, "Messages.Death.No grave found", "&cAlert: Could not find a location to set your grave, your items have been dropped like normal!");
		MSG_GRAVE_CREATED_AT = getString(f, cfg, "Messages.Death.Grave created at", "&7Your grave was created at &c%location%&7. The access protection expires in %minutes% minutes.");
		MSG_GRAVE_EXPIRES_MINUTES = getString(f, cfg, "Messages.Expiration.Grave expires in minutes", "&cWarning: &7Your grave at &c%location%&7 will lose protection in around &e%minutes% &7minutes.");
		MSG_GRAVE_EXPIRES_MINUTE = getString(f, cfg, "Messages.Expiration.Grave expires in one minute", "&cWarning: &7Your grave at &c%location%&7 will lose protection in around &e1 &7minute.");
		MSG_GRAVE_EXPIRED = getString(f, cfg, "Messages.Expiration.Grave expired", "&cWarning: &7The protection of your grave at &c%location%&7 has expired.");
		MSG_GRAVEPURGE_NO_PERMS = getString(f, cfg,"Messages.Purge.No permissions", "&cSorry, your don't have permission for that!");
		MSG_GRAVEPURGE_WIPED_GRAVES = getString(f, cfg, "Messages.Purge.Wiped graves", "&ePurged &c%graves% &eold graves.");
		MSG_YOUR_GRAVE_WILL_BE_PURGED = getString(f, cfg, "Messages.Purge.Your grave will be purged", "&cWarning: &7Your grave at &c%location%&7 will be purged momentarily.");
		
		MSG_LIST_NOPLAYER = getString(f, cfg, "Messages.Gravelist.Not a Player", "&cSorry, this command is for players only.");
		MSG_LIST_NOPERMS = getString(f, cfg, "Messages.Gravelist.No Permission", "&cSorry, your don't have permission for that!");
		MSG_LIST_PLAYERNOTONLINE = getString(f, cfg, "Messages.Gravelist.Player not found", "&cCan't find %name% right now. For offline players, you can try the uuid instead: &e/gravelist uuid:<uuid>");
		MSG_LIST_INVALID_UUID = getString(f, cfg, "Messages.Gravelist.UUID invalid", "&cSorry, but '%uuid%' is not a valid uuid");
		MSG_LIST_NOGRAVES_SELF = getString(f, cfg, "Messages.Gravelist.No Graves - Self", "&cYou don't seem to have any graves lying around..");
		MSG_LIST_NOGRAVES_OTHER = getString(f, cfg, "Messages.Gravelist.No Graves - Other", "&c%name% doesn't seem to have any graves lying around..");
		MSG_LIST_GRAVELIST_SELF = getString(f, cfg, "Messages.Gravelist.GraveList - Self", "&eAll your current graves:");
		MSG_LIST_GRAVELIST_OTHER = getString(f, cfg, "Messages.Gravelist.GraveList - Other", "&eAll of %name%%suffix% current graves:");
		MSG_LIST_FORMAT = getString(f, cfg, "Messages.Gravelist.GraveListing Format", "&7 >> &e%location%&7: &c%itemcount% &7Items | Time left: &a%time%");
		
	}
	
	public static String getString(File f, YamlConfiguration cfg, String path, String fallback)
	{
		String s = cfg.getString(path);
		if(s == null)
		{
			cfg.set(path, fallback);
			try { cfg.save(f); } catch (IOException ignored) { }
			s = fallback;
		}
		return ChatColor.translateAlternateColorCodes('&', s);
	}
	
}
