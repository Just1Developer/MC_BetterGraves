package net.justonedev.mc.plugins.bettergraves;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CarePackage {

	public static Set<String> CarePackageFileNames;
	
	private static final String path = BetterGraves.instance.getDataFolder() + "/carepackages/";
	
	public static void Init() {
		CarePackageFileNames = new HashSet<>();
		File pathFile = new File(path);
		if (!pathFile.exists()) {
			pathFile.mkdirs();
			return;
		}
		
		for (File f : pathFile.listFiles((dir, name) -> name.endsWith(".yml"))) {
			CarePackageFileNames.add(f.getName().substring(0, f.getName().length() - 4));
		}
	}
	
	public static CarePackage loadFromFile(String file) {
		File f = new File(String.format("%s%s.yml", path, file));
		if (!f.exists()) return null;
		return new CarePackage(f);
	}
	
	public static void redeemImmediately(Grave grave, Player player) {
		List<ItemStack> items = new ArrayList<>();
		for (ItemStack stack : grave.Inventory) {
			if (stack != null) items.add(stack);
		}
		
		player.giveExp(grave.DroppedExp);
		for (int i = 0; i < items.size(); ++i) {
			ItemStack item = items.get(i);
			HashMap<Integer, ItemStack> remainder = player.getInventory().addItem(item);
			if (remainder.isEmpty()) continue;
			
			// Create new Care package
			List<ItemStack> newItems = new ArrayList<>(remainder.values());
			for (int i2 = i + 1; i2 < items.size(); ++i2) {
				newItems.add(items.get(i));
			}
			new CarePackage(newItems, player.getUniqueId().toString(), CarePackageIDType.UUID);
			player.sendMessage(Config.MSG_YOU_HAVE_FURTHER_MAIL);
		}
	}
	
	// ----------------------------------------------------------
	
	private final String fileName;
	private final CarePackageIDType PackageType;
	private final List<ItemStack> items;
	private final int xp;
	
	public CarePackage(Grave grave, String filename, CarePackageIDType type) {
		
		if (new File(String.format("%s%s.yml", path, filename)).exists()) {
			// Add items to already existing care package
			File f = new File(String.format("%s%s.yml", path, filename));
			YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
			
			// Find out how many items there are
			int count = 0;
			while (cfg.getItemStack("Package.Item-" + (count)) != null) count++;
			
			// Get contents has a lot of null entries and is slot accurate
			ItemStack[] temp_allItems = grave.Inventory.getContents();
			List<ItemStack> allItems = new ArrayList<>();
			for (ItemStack it : temp_allItems) if (it != null) allItems.add(it);
			
			// On top of those, add our new ones
			for (int i = 0; i < allItems.size(); ++i) {
				cfg.set("Package.Item-" + (count + i), allItems.get(i));
			}
			
			try {
				cfg.save(f);
			} catch (IOException e) {
				System.err.println("Failed to save CarePackage file " + f.getName());
			}
			
			// Empty package
			fileName = "";
			PackageType = CarePackageIDType.UUID;
			items = new ArrayList<>();
			xp = 0;
			return;
		}
		
		items = new ArrayList<>();
		for (ItemStack stack : grave.Inventory) {
			if (stack != null) items.add(stack);
		}
		this.xp = grave.DroppedExp;
		
		this.PackageType = type;
		this.fileName = filename;
		
		saveToFile();
		
		// Remove grave
		BetterGraves.Graves.remove(grave.Location);
		GraveExpiration.removeExpiration(grave.Location);
		if (grave.Location.getWorld() != null) grave.Location.getWorld().setType(grave.Location, Material.AIR);
		grave.deleteGraveFile();
	}
	
	private CarePackage(List<ItemStack> items, String filename, CarePackageIDType type) {
		this.items = items;
		this.xp = 0;
		this.PackageType = type;
		this.fileName = filename;
		saveToFile();
	}
	
	// Only for existing ones!!!
	private CarePackage(File f) {
		
		YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
		String typeValue = cfg.getString("IDType");
		if (typeValue == null) this.PackageType = f.getName().length() <= 20 ? CarePackageIDType.NAME : CarePackageIDType.UUID;	// 20 = 16 (name) + 4 (".yml")
		else this.PackageType = CarePackageIDType.valueOf(typeValue);
		
		this.items = new ArrayList<>();
		
		ItemStack next;
		int i = 0;
		while ((next = cfg.getItemStack("Package.Item-" + (i++))) != null) {
			items.add(next);
		}
		
		this.xp = cfg.getInt("Package.XP");
		this.fileName = f.getName().substring(0, f.getName().length() - 4);	// remove ".yml"
	}
	
	private File getFile() {
		return new File(String.format("%s%s.yml", path, this.fileName));
	}
	
	private void saveToFile() {
		File f = getFile();
		YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
		cfg.set("IDType", this.PackageType.toString());
		cfg.set("Package.XP", xp);
		for (int i = 0; i < items.size(); ++i) {
			cfg.set("Package.Item-" + i, items.get(i));
		}
		try {
			cfg.save(f);
			CarePackageFileNames.add(fileName);
		} catch (IOException e) {
			System.err.println("Failed to save CarePackage file " + f.getName());
		}
	}
	
	public CarePackageIDType getPackageType() {
		return this.PackageType;
	}
	
	public String getFileName() {
		return this.fileName;
	}
	
	/**
	 * Adds the contents of the care package to the specified player.
	 * Care package is deleted after collection. If not all items could
	 * be collected, a new care package is created and the player is notified.
	 * <p></p>
	 * If all items were collected, returns true.
	 * If a new care package was created, returns false.
	 *
	 * @param p The player
	 * @return True if all items were collected, false if not.
	 *
	 */
	public boolean Collect(Player p) {
		
		CarePackageFileNames.remove(fileName);
		p.giveExp(this.xp);
		for (int i = 0; i < items.size(); ++i) {
			ItemStack item = items.get(i);
			HashMap<Integer, ItemStack> remainder = p.getInventory().addItem(item);
			if (remainder.isEmpty()) continue;
			
			// Create new Care package
			List<ItemStack> newItems = new ArrayList<>(remainder.values());
			for (int i2 = i + 1; i2 < items.size(); ++i2) {
				newItems.add(items.get(i));
			}
			new CarePackage(newItems, p.getUniqueId().toString(), CarePackageIDType.UUID);
			p.sendMessage(Config.MSG_YOU_HAVE_FURTHER_MAIL);
			return false;
		}
		
		File f = getFile();
		f.delete();
		return true;
	}
	
	public enum CarePackageIDType {
		NAME, UUID
	}

}
