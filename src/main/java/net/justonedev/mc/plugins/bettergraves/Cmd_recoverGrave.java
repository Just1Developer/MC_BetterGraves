package net.justonedev.mc.plugins.bettergraves;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Cmd_recoverGrave implements CommandExecutor {
	
	@Override
	public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
	
		// Permission check: Needs to be OP / Console
		if ((commandSender.equals(Bukkit.getConsoleSender())
				&& (!(commandSender instanceof Player) || !commandSender.isOp()))
				&& !commandSender.hasPermission(Config.PERMISSION_RECOVERGRAVE)) {
			commandSender.sendMessage(Config.MSG_GRAVEPURGE_NO_PERMS);
			return true;
		}
		
		// Command Syntax: /recovergrave <grave location (3 args)> [Receiver] ["override"]
		// Alternate: /recovergrave <grave id from file> [Receiver] ["override": When player should receive the item]
		//   -> Coordinates can be checked by the grave owner via the list
		// Note to receiver:
		//   -> Receiver is the one who receives the items
		//   -> If no receiver is specified, the grave owner will be selected as receiver.
		//      If the grave owner is not online, the player will either receive the items upon joining, or
		//		an error message will be sent to the command sender.
		//		If the receiver is specified, the player must be online to receive the items.
		
		/*
		* recovergrave help | ?
		*
		* recovergrave 23 45 67
		* recovergrave 23 45 67 PercyJackson
		* recovergrave 23 45 67 PercyJackson override
		*
		* (/recovergrave) XXXX-XXXX-XXXX-XXXX
		* (/recovergrave) XXXX-XXXX-XXXX-XXXX PercyJackson
		* (/recovergrave) XXXX-XXXX-XXXX-XXXX PercyJackson override
		*
		* Regex: ^([a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4})(?:\s+([a-zA-Z0-9_]{3,16}))?(?:\s+(-override))?$
		* Regex Breakdown:
		* 	^: Begin of string
		* 	(: Begin of Group ID
		*     - [a-zA-Z0-9]: Upper- and lowercase characters and numbers
		* 	  - {4}: Exactly 4 of them
		*     - -: Then a minus
		*     - (repeat another 3 times but not end with minus for ID layout
		*   ): End of Group ID
		*   (: Start of group username
		*     "?:": Group is non-capturing. This is only for the whitespaces, so they don't get saved
		*     \s+: Any amount of whitespace characters (\s), but at least one (+)
		*     [a-zA-Z0-9_]: Upper- and lowercase characters, numbers and any others. I know MC names have '_', but I'll need to check and improve that list
		*     {3, 16}: Between 3 and 16 characters, those are the known boundaries for minecraft names
		*   )?: Group is optional. End of Group username
		*   (: Start of Group optional override
		*     "?:": Group is non-capturing. This is only for the whitespaces, so they don't get saved
		*     \s+: Any amount of whitespace characters (\s), but at least one (+)
		*     (-override): Checks of the exact string -override is added at the end
		*   )?: Group is optional. End of Group optional override
		*   $: End of string
		*
		*   Note that \s+ may be replaced with \s, as we always have only one space between arguments
		*
		* Coordinate version:
		* Regex: ^(-?[0-9]+)\s(-?[0-9]+)\s(-?[0-9]+)(?:\s([a-zA-Z0-9_]{3,16}))?(?:\s(-override))?$
		* Regex Breakdown (differences only):
		*   Instead of ID, 3 groups of (-?[0-9]+) separated by \s
		*   (-?[0-9]+):
		* 	  -?: A minus sign, which is optional (?), meaning it doesn't need to be there
		*     [0-9]+: A number with at least one digit
		*   This 3 times for 3 coordinates
		*   \s: Whitespace
		*
		* Example: /recovergrave CGBx-cIXS-5hzr-UINz xX_ShadowSpire
		*
		* Example:
		*
		* Regex for ID: (a-zA-z){4}-(a-zA-z){4}-(a-zA-z){4}-(a-zA-z){4}
		* Regex for command
		* */
		
		if (args.length == 1 && (args[0].equalsIgnoreCase("help") || args[0].equals("?"))) {
			sendHelp(commandSender);
			return true;
		}
		
		StringBuilder builder = new StringBuilder();
		for (String arg : args) builder.append(arg).append(' ');
		
		Pattern pattern = Pattern.compile("^([a-zA-Z]{4}-[a-zA-Z]{4}-[a-zA-Z]{4}-[a-zA-Z]{4})(?:\\s+([a-zA-Z0-9_]{3,16}))?(?:\\s+(-override))?$");
		Matcher matcher = pattern.matcher(builder.toString().trim());
		
		Grave grave = null;
		String targetPlayerName;
		UUID targetPlayerUUID;
		boolean overrideOffline;
		
		Player targetPlayer = null;
		
		if (!matcher.matches()) {
			// No match? Try for coordinates instead: We need to parse / handle this differently, that's why we're doing it separated
			pattern = Pattern.compile("^(-?[0-9]+)\\s+(-?[0-9]+)\\s+(-?[0-9]+)(?:\\s+([a-zA-Z0-9_]{3,16}))?(?:\\s+(-override))?$");
			matcher = pattern.matcher(builder.toString().trim());
			
			if (!matcher.matches()) {
				// Also not? Get some help my guy
				
				sendHelp(commandSender);
				return false;
			}
			
			// Coordinate version here
			
			targetPlayerName = matcher.group(4); // This will be null if no receiver is specified.
			overrideOffline = matcher.group(5) != null;
			if(targetPlayerName != null && targetPlayerName.equals("-override") && !overrideOffline) {
				overrideOffline = true;
				targetPlayerName = null;
			}
			
			if(targetPlayerName != null) targetPlayer = Bukkit.getPlayer(targetPlayerName);
			
			if (!(commandSender instanceof Player) && targetPlayer == null) {
				sendCommandSenderNeedsPlayerArgs(commandSender);
				return false;
			}
			
			World world = targetPlayer == null ? ((Player) commandSender).getWorld() : targetPlayer.getWorld();
			Location graveLoc = new Location(
					world,
					Integer.parseInt(matcher.group(1)),
					Integer.parseInt(matcher.group(2)),
					Integer.parseInt(matcher.group(3))
			);
			
			grave = BetterGraves.getGrave(graveLoc);
		}
		else
		{
			// ID version here
			String graveId = matcher.group(1);
			targetPlayerName = matcher.group(2); // This will be null if no receiver is specified.
			overrideOffline = matcher.group(3) != null;
			if(targetPlayerName != null && targetPlayerName.equals("-override") && !overrideOffline) {
				overrideOffline = true;
				targetPlayerName = null;
			}
			
			grave = Grave.load(graveId);
		}
		
		if (grave == null) {
			sendGraveNotFound(commandSender);
			return false;
		}
		
		// No player specified: Grave owner
		if (targetPlayerName == null) {
			targetPlayer = Bukkit.getPlayer(UUID.fromString(grave.PlayerUUID));
		} else {
			targetPlayer = Bukkit.getPlayer(targetPlayerName);
		}
		
		// Player is not online
		if (targetPlayer == null) {
			if (!overrideOffline) {
				sendPlayerOffline(commandSender);
				return true;
			}
			
			// Enqueue offline player
			if (targetPlayerName == null) {
				targetPlayerUUID = UUID.fromString(grave.PlayerUUID);
				new CarePackage(grave, targetPlayerUUID.toString(), CarePackage.CarePackageIDType.UUID);
			} else {
				new CarePackage(grave, targetPlayerName, CarePackage.CarePackageIDType.NAME);
			}
			sendPlayerOfflineSuccess(commandSender);
			
			return true;
		}
		
		// Player is online and grave is found
		BetterGraves.Graves.remove(grave.Location);
		GraveExpiration.removeExpiration(grave.Location);
		grave.deleteGraveFile();
		if(grave.Location.getWorld() != null) grave.Location.getWorld().setType(grave.Location, Material.AIR);
		
		CarePackage.redeemImmediately(grave, targetPlayer);
		
		sendSuccessSender(commandSender);
		sendSuccessTarget(targetPlayer);
		
		return true;
	}
	
	private static void sendSuccessSender(CommandSender sender) {
		sender.sendMessage(Config.MSG_RECOVER_RECOVER_SUCCESS_SENDER);
	}
	
	private static void sendSuccessTarget(Player player) {
		player.sendMessage(Config.MSG_RECOVER_RECOVER_SUCCESS_RECEIVER);
	}
	
	private static void sendHelp(CommandSender sender) {
		sender.sendMessage("§7————————————————————————————");
		sender.sendMessage("§e/recovergrave <grave id> [Receiver] [-override]");
		sender.sendMessage("§e/recovergrave <grave coordinates> [Receiver] [-override]");
		sender.sendMessage(Config.MSG_RECOVER_RECEIVER_EXPLANATION);
		sender.sendMessage(Config.MSG_RECOVER_OVERRIDE_EXPLANATION);
	}
	
	private static void sendGraveNotFound(CommandSender sender) {
		sender.sendMessage(Config.MSG_RECOVER_GRAVE_NOT_FOUND);
	}
	
	private static void sendPlayerOffline(CommandSender sender) {
		sender.sendMessage(Config.MSG_RECOVER_PLAYER_OFFLINE_NO_OVERRIDE);
	}
	
	private static void sendPlayerOfflineSuccess(CommandSender sender) {
		sender.sendMessage(Config.MSG_RECOVER_PLAYER_OFFLINE_YES_OVERRIDE);
	}
	
	private static void sendCommandSenderNeedsPlayerArgs(CommandSender sender) {
		sender.sendMessage(Config.MSG_RECOVER_CONSOLESENDER_NO_WORLD);
	}
	
}
