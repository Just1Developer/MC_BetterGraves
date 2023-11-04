package net.justonedev.mc.plugins.bettergraves;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

public class Gravelist implements CommandExecutor {
	@Override
	public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
		
		String targetUUID;
		String playerName;
		boolean self = false;
		if(args.length == 0)
		{
			if(!(commandSender instanceof Player))
			{
				commandSender.sendMessage(Config.MSG_LIST_NOPLAYER);
				return false;
			}
			self = true;
			targetUUID = ((Player) commandSender).getUniqueId().toString();
			playerName = commandSender.getName();
		}
		else if (args.length == 1)
		{
			if(!commandSender.equals(Bukkit.getConsoleSender()) && !commandSender.hasPermission(Config.PERMISSION_VIEWLIST_OTHER))
			{
				// Still allow if they want to check their own
				if(!args[0].equals(commandSender.getName()) && !(commandSender instanceof Player && args[0].toLowerCase().equals("uuid:" + ((Player) commandSender).getUniqueId())))
				{
					commandSender.sendMessage(Config.MSG_LIST_NOPERMS);
					return false;
				}
			}
			
			if(args[0].toLowerCase().startsWith("uuid:"))
			{
				targetUUID = args[0].substring(5);
				try {
					playerName = Bukkit.getOfflinePlayer(UUID.fromString(targetUUID)).getName();
				} catch (IllegalArgumentException e)
				{
					// Not a valid UUID
					commandSender.sendMessage(Config.MSG_LIST_INVALID_UUID.replaceAll("%uuid%", targetUUID));
					return false;
				}
			}
			else
			{
				Player p = Bukkit.getPlayer(args[0]);
				if(p == null)
				{
					// Player not found
					commandSender.sendMessage(replaceName(Config.MSG_LIST_PLAYERNOTONLINE, args[0]));
					return false;
				}
				targetUUID = p.getUniqueId().toString();
				playerName = p.getName();
			}
		}
		else
		{
			commandSender.sendMessage("§e/gravelist §c[Player]");
			return false;
		}
		
		ArrayList<String> graves = new ArrayList<>();
		// Get graves and list
		for (Grave grave : BetterGraves.Graves.values())
		{
			if(!grave.PlayerUUID.equals(targetUUID)) return false;
			
			// List grave
			graves.add(formatGrave(grave));
		}
		
		if(graves.isEmpty())
		{
			if(self) commandSender.sendMessage(Config.MSG_LIST_NOGRAVES_SELF);
			else commandSender.sendMessage(replaceName(Config.MSG_LIST_NOGRAVES_OTHER, playerName));
			return true;
		}
		
		// Target name and uuid are now known
		if(self) commandSender.sendMessage(Config.MSG_LIST_GRAVELIST_SELF);
		else commandSender.sendMessage(replaceName(Config.MSG_LIST_GRAVELIST_OTHER, playerName));
		
		for (String gravelist : graves)
		{
			commandSender.sendMessage(gravelist);
		}
		
		return true;
	}
	
	private static String replaceName(String msg, String name)
	{
		return msg.replaceAll("%name%", name).replaceAll("%suffix%", name.toLowerCase().endsWith("s") ? "'" : "'s");
	}
	
	private static String formatGrave(Grave grave)
	{
		String locString = "[" + Objects.requireNonNull(grave.Location.getWorld()).getName() + "] " + grave.Location.getBlockX() + ", " + grave.Location.getBlockY() + ", " + grave.Location.getBlockZ();
		int items = 0;
		for(ItemStack i : grave.Inventory.getContents()) {
			if(i == null) continue; if(i.getType().isAir()) continue;
			items++;
		}
		long secsLeft = (GraveExpiration.getExpiration(grave.Location) - System.currentTimeMillis()) / 1000;
		return Config.MSG_LIST_FORMAT.replaceAll("%location%", locString).replaceAll("%itemcount%", "" + items).replaceAll("%time%", (secsLeft <= 0 ? "expired" : secsLeft + "s"));
	}
}
