package net.justonedev.mc.plugins.bettergraves;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.UUID;

public class Grave {

	public final String GraveUID;
	public String PlayerUUID;
	public Inventory Inventory;
	public Location Location;
	public int DroppedExp;
	public long CreationTime;
	
	public Grave(String graveUID, String PlayerUUID, Inventory Inv, Location graveLocation, int DroppedExp)
	{
		this(graveUID, PlayerUUID, Inv, graveLocation, DroppedExp, System.currentTimeMillis());
	}
	public Grave(String graveUID, String PlayerUUID, Inventory Inv, Location graveLocation, int DroppedExp, long CreationTime)
	{
		this.GraveUID = graveUID;
		this.PlayerUUID = PlayerUUID;
		// Inv is cloned already
		this.Inventory = Inv;
		this.Location = graveLocation;
		this.DroppedExp = DroppedExp;
		this.CreationTime = CreationTime == 0 ? System.currentTimeMillis() : CreationTime;
	}
	
	/**
	 * Loads a grave from its file.
	 * Returns null if the file does not exist.
	 *
	 * @param uuid The grave uuid
	 * @return The loaded grave or null
	 */
	public static Grave load(String uuid)
	{
		File f = new File(BetterGraves.instance.getDataFolder() + "/graves/", uuid + ".yml");
		if (!f.exists()) return null;
		YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
		
		String WorldUID = cfg.getString("Location.WorldUUID");
		Location loc = new Location(WorldUID == null ? Bukkit.getWorlds().get(0) : Bukkit.getWorld(UUID.fromString(WorldUID)),
				cfg.getInt("Location.X"), cfg.getInt("Location.Y"), cfg.getInt("Location.Z"));
		
		String PlayerUUID = cfg.getString("UUID");
		if(PlayerUUID == null) PlayerUUID = "";
		
		// The inventory size is 41. The inventory size must be a multiple of 9 though, so the next best is 45. Inventory size of a player does not vary, so we can and will hardcode this
		Inventory inv = Bukkit.createInventory(null, 45);//cfg.getInt("Inventory.size"));
		for (int i = 0; i < inv.getSize(); i++)
		{
			try {
				ItemStack itemStack = cfg.getItemStack("Inventory.slot." + i);
				if (itemStack == null) continue;
				if (itemStack.getType() == Material.FIREWORK_ROCKET && itemStack.hasItemMeta()) {
					// Fix the item manually
					FireworkMeta fireworkMeta = (FireworkMeta) itemStack.getItemMeta();
					if (fireworkMeta != null && !fireworkMeta.hasEffects()) {
						// Add and remove an effect to assert that the firework effects list is != null
						fireworkMeta.addEffect(FireworkEffect.builder().withColor(Color.BLACK).with(FireworkEffect.Type.CREEPER).build());
						fireworkMeta.removeEffect(0);
						itemStack.setItemMeta(fireworkMeta);
					}
				}
				inv.setItem(i, itemStack);
			} catch (NullPointerException e) {
				Bukkit.getLogger().warning("[JustOneDeveloper's BetterGraves] Could not load item " + i + " for grave " + uuid + ": NullPointerException occurred when applying attributes. This is an issue caused by Spigot / Minecraft.");
			}
		}
		
		int DroppedExp = cfg.getInt("DroppedExp");
		
		long time = cfg.getLong("Time.Expiration");
		GraveExpiration.addExpiration(loc, time);
		time = cfg.getLong("Time.Creation");
		
		return new Grave(uuid, PlayerUUID, inv, loc, DroppedExp, time);
	}
	
	public void give(Player p)
	{
		// Give the grave to the given player and delete grave file
		
		/*
		* Slot Designations:
		*
		* e.getSlot() in InventoryClickEvent:
		* Hotbar: 0-8
		* Inventory: 9 (top left) - 35 (bottom right)
		* Boots: 36
		* Leggings: 37
		* Chestplate: 38
		* Helmet: 39
		* Off-hand: 40
		*
		* Survival Inventory: GetRawSlot:
		* 0: Small Crafting: Result
		* 1-4: Small Crafting: TopLeft, TopRight, BottomLeft, BottomRight
		* 5-8: Armor, 5 = Helmet, 8 = Boots
		* 9-35: Inventory, TopLeft to BottomRight
		* 36-44: Hotbar, L->R
		* */
		
		// Small crafting is not saved anyhow in the death event inventory and this RawSlots should not be used just because they also include this
		
		//Alternative to swapping like below: pre-define LeftOvers and LeftOvers.put(slot + 20, p.getInventory().getItem(slot));	// An index that will not occur in addItem return value because the index does not exist as slot
		
		if(BetterGraves.EnforceBindingCurse)
		{
			// First, Handle worn curse of binding armor, so swap them with current armor before adding
			int slot = 36;
			if (hasCurseOfBinding(Inventory.getItem(slot))) {
				// Swap
				ItemStack binding = Inventory.getItem(slot);
				Inventory.setItem(slot, p.getInventory().getItem(slot));
				p.getInventory().setItem(slot, binding);
			}
			++slot;
			if (hasCurseOfBinding(Inventory.getItem(slot))) {
				// Swap
				ItemStack binding = Inventory.getItem(slot);
				Inventory.setItem(slot, p.getInventory().getItem(slot));
				p.getInventory().setItem(slot, binding);
			}
			++slot;
			if (hasCurseOfBinding(Inventory.getItem(slot))) {
				// Swap
				ItemStack binding = Inventory.getItem(slot);
				Inventory.setItem(slot, p.getInventory().getItem(slot));
				p.getInventory().setItem(slot, binding);
			}
			++slot;
			if (hasCurseOfBinding(Inventory.getItem(slot))) {
				// Swap
				ItemStack binding = Inventory.getItem(slot);
				Inventory.setItem(slot, p.getInventory().getItem(slot));
				p.getInventory().setItem(slot, binding);
			}
		}
		
		// First, loop through the inventory and set all items to their respective slots, then handle the leftovers
		for (int i = 0; i < p.getInventory().getSize(); ++i)
		{
			if(p.getInventory().getItem(i) != null) continue;
			p.getInventory().setItem(i, Inventory.getItem(i));
			Inventory.setItem(i, null);
		}
		
		if(!Inventory.isEmpty())
		{
			p.sendMessage(Config.MSG_ITEMS_NOT_RECOVERED_1_TO_1);
			
			ArrayList<ItemStack> LeftOvers = new ArrayList<>();
			for(ItemStack stack : Inventory.getContents())
			{
				if(stack == null) continue;	// Because of this case, the null case, this cannot be done with p.getInventory().addItem(Inventory.getStorageContents())
				LeftOvers.addAll(p.getInventory().addItem(stack).values());
			}
			
			if (!LeftOvers.isEmpty()) {
				if(BetterGraves.DropItemsIfInventoryFull)
				{
					for (ItemStack drop : LeftOvers) {
						p.getWorld().dropItemNaturally(Location, drop);
					}
					p.sendMessage(Config.MSG_SOME_ITEMS_DROPPED);
				}
				else
				{
					Inventory inventory = Bukkit.createInventory(null, 45);
					for (ItemStack drop : LeftOvers) {
						inventory.addItem(drop);
					}
					Location.getBlock().setType(Material.AIR);
					Graves.createGrave(p, inventory, 0);
					p.sendMessage(Config.MSG_ITEMS_LEFTOVER_IN_GRAVE);
				}
			}
		}
		p.giveExp(DroppedExp);
		
		deleteGraveFile();
	}
	
	public void deleteGraveFile()
	{
		File f = new File(BetterGraves.instance.getDataFolder() + "/graves/", GraveUID + ".yml");
		if(!f.delete()) f.deleteOnExit();
		f = null;
		System.gc();
	}
	
	private static boolean hasCurseOfBinding(ItemStack stack)
	{
		if(stack == null) return false;
		return stack.getEnchantments().containsKey(Enchantment.BINDING_CURSE);
	}
	
	public void sendMessage(String msg)
	{
		Player p = Bukkit.getPlayer(UUID.fromString(PlayerUUID));
		if(p == null) return;
		p.sendMessage(msg);
	}
	
}
