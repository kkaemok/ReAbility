package org.kkaemok.reAbility.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ShopMenu implements Listener {
    private static final int DEFAULT_CATEGORY_MENU_SIZE = 27;
    private static final int DEFAULT_ITEM_MENU_SIZE = 54;
    private static final int DEFAULT_BACK_SLOT = 49;

    private final ReAbility plugin;
    private final AbilityManager abilityManager;
    private final NamespacedKey shopActionKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ShopMenu(ReAbility plugin, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
        this.shopActionKey = new NamespacedKey(plugin, "shop_action");
    }

    public void open(Player player) {
        openCategoryMenu(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(shopActionKey, PersistentDataType.STRING);
        if (action == null || action.isBlank()) return;

        if (action.startsWith("category:")) {
            String categoryId = action.substring("category:".length());
            openItemMenu(player, categoryId);
            return;
        }

        if (action.startsWith("item:")) {
            String[] parts = action.split(":", 3);
            if (parts.length != 3) {
                player.sendMessage("[상점] 잘못된 동작입니다.");
                return;
            }
            purchaseItem(player, parts[1], parts[2]);
            return;
        }

        if (action.equals("back")) {
            openCategoryMenu(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    private void openCategoryMenu(Player player) {
        int size = normalizeSize(plugin.getConfig().getInt("shop.menu.category-size", DEFAULT_CATEGORY_MENU_SIZE));
        String title = plugin.getConfig().getString("shop.menu.title", "<gold>상점");

        CategoryMenuHolder holder = new CategoryMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, size, parseMiniMessage(title, "상점"));
        holder.setInventory(inv);

        List<ShopCategory> categories = loadCategories();
        if (categories.isEmpty()) {
            inv.setItem(size / 2, createBarrierInfoItem(
                    "<red>카테고리가 없습니다",
                    List.of("<gray>config.yml에서 카테고리를 설정하세요")));
            player.openInventory(inv);
            return;
        }

        for (ShopCategory category : categories) {
            int slot = findSlot(inv, category.slot(), Set.of());
            if (slot < 0) continue;
            inv.setItem(slot, createCategoryItem(category));
        }

        player.openInventory(inv);
    }

    private void openItemMenu(Player player, String categoryId) {
        ShopCategory category = loadCategory(categoryId);
        if (category == null) {
            player.sendMessage("[상점] 해당 카테고리를 찾을 수 없습니다.");
            openCategoryMenu(player);
            return;
        }

        int size = normalizeSize(category.menuSize());
        Component title = parseMiniMessage(category.title(), category.displayNameFallback());

        ItemMenuHolder holder = new ItemMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        int backSlot = getBackSlot(size);
        Set<Integer> reserved = backSlot >= 0 ? Set.of(backSlot) : Set.of();

        List<ShopItem> items = loadItems(categoryId);
        if (items.isEmpty()) {
            int emptySlot = findSlot(inv, size / 2, reserved);
            if (emptySlot >= 0) {
                inv.setItem(emptySlot, createBarrierInfoItem(
                        "<red>등록된 상품이 없습니다",
                        List.of("<gray>config.yml에서 상품을 추가하세요")));
            }
        } else {
            for (ShopItem item : items) {
                int slot = findSlot(inv, item.slot(), reserved);
                if (slot < 0) continue;
                inv.setItem(slot, createShopItemIcon(categoryId, item));
            }
        }

        if (backSlot >= 0) {
            inv.setItem(backSlot, createBackItem());
        }
        player.openInventory(inv);
    }

    private void purchaseItem(Player player, String categoryId, String itemId) {
        ShopItem item = loadItem(categoryId, itemId);
        if (item == null) {
            player.sendMessage("[상점] 해당 상품은 더 이상 존재하지 않습니다.");
            openItemMenu(player, categoryId);
            return;
        }

        int price = Math.max(0, item.price());
        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        if (data.getCoins() < price) {
            player.sendMessage("[상점] 코인이 부족합니다.");
            return;
        }

        data.addCoins(-price);
        abilityManager.savePlayerData(player.getUniqueId());
        giveItem(player, item.createGiveItem(this::parseMiniMessage));
        player.sendMessage("[상점] " + item.itemId() + " 상품을 " + price + " 코인에 구매했습니다.");
    }

    private List<ShopCategory> loadCategories() {
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("shop.categories");
        if (categories == null) return List.of();

        List<ShopCategory> loaded = new ArrayList<>();
        for (String categoryId : categories.getKeys(false)) {
            ShopCategory category = loadCategory(categoryId);
            if (category != null) {
                loaded.add(category);
            }
        }

        loaded.sort(Comparator
                .comparingInt((ShopCategory c) -> c.slot() < 0 ? Integer.MAX_VALUE : c.slot())
                .thenComparing(ShopCategory::categoryId));
        return loaded;
    }

    private ShopCategory loadCategory(String categoryId) {
        String path = "shop.categories." + categoryId;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) return null;
        if (!section.getBoolean("enabled", true)) return null;

        Material icon = parseMaterial(section.getString("icon"), Material.CHEST);
        int slot = section.getInt("slot", -1);
        int menuSize = section.getInt("size", plugin.getConfig().getInt("shop.menu.item-size", DEFAULT_ITEM_MENU_SIZE));

        String displayName = section.getString("display-name", categoryId);
        String title = section.getString("title", displayName);
        List<String> lore = section.getStringList("lore");

        return new ShopCategory(categoryId, displayName, title, lore, icon, slot, menuSize);
    }

    private List<ShopItem> loadItems(String categoryId) {
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("shop.categories." + categoryId + ".items");
        if (section == null) return List.of();

        List<ShopItem> loaded = new ArrayList<>();
        for (String itemId : section.getKeys(false)) {
            ShopItem item = loadItem(categoryId, itemId);
            if (item != null) {
                loaded.add(item);
            }
        }

        loaded.sort(Comparator
                .comparingInt((ShopItem i) -> i.slot() < 0 ? Integer.MAX_VALUE : i.slot())
                .thenComparing(ShopItem::itemId));
        return loaded;
    }

    private ShopItem loadItem(String categoryId, String itemId) {
        String path = "shop.categories." + categoryId + ".items." + itemId;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) return null;
        if (!section.getBoolean("enabled", true)) return null;

        Material material = parseMaterial(section.getString("material"), Material.STONE);
        int amount = Math.max(1, section.getInt("amount", 1));
        int maxStack = Math.max(1, material.getMaxStackSize());
        amount = Math.min(amount, maxStack);

        int price = Math.max(0, section.getInt("price", 0));
        int slot = section.getInt("slot", -1);

        String displayName = section.getString("display-name",
                toTitle(material.name()) + " x" + amount);
        List<String> lore = section.getStringList("lore");
        String giveDisplayName = section.getString("give-display-name");
        List<String> giveLore = section.getStringList("give-lore");

        return new ShopItem(itemId, material, amount, price, slot, displayName, lore, giveDisplayName, giveLore);
    }

    private ItemStack createCategoryItem(ShopCategory category) {
        ItemStack stack = new ItemStack(category.icon());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(parseMiniMessage(category.displayName(), category.categoryId()));
        List<Component> lore = new ArrayList<>();
        for (String line : category.lore()) {
            lore.add(parseMiniMessage(line, line));
        }
        if (lore.isEmpty()) {
            lore.add(parseMiniMessage("<gray>클릭하여 열기", "클릭하여 열기"));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(shopActionKey, PersistentDataType.STRING, "category:" + category.categoryId());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createShopItemIcon(String categoryId, ShopItem item) {
        ItemStack stack = new ItemStack(item.material(), item.amount());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(parseMiniMessage(item.displayName(), item.itemId()));
        List<Component> lore = new ArrayList<>();
        for (String line : item.lore()) {
            lore.add(parseMiniMessage(line, line));
        }
        lore.add(Component.text("가격: " + item.price() + " 코인", NamedTextColor.GOLD));
        lore.add(Component.text("클릭하여 구매", NamedTextColor.GRAY));
        meta.lore(lore);
        meta.getPersistentDataContainer()
                .set(shopActionKey, PersistentDataType.STRING, "item:" + categoryId + ":" + item.itemId());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createBackItem() {
        String path = "shop.menu.back-item";
        Material material = parseMaterial(plugin.getConfig().getString(path + ".material"), Material.ARROW);
        String name = plugin.getConfig().getString(path + ".name", "<yellow>뒤로가기");
        List<String> lore = plugin.getConfig().getStringList(path + ".lore");

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(parseMiniMessage(name, "뒤로가기"));
        List<Component> parsedLore = new ArrayList<>();
        for (String line : lore) {
            parsedLore.add(parseMiniMessage(line, line));
        }
        if (!parsedLore.isEmpty()) {
            meta.lore(parsedLore);
        }
        meta.getPersistentDataContainer().set(shopActionKey, PersistentDataType.STRING, "back");
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createBarrierInfoItem(String name, List<String> lore) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(parseMiniMessage(name, name));
        List<Component> parsedLore = new ArrayList<>();
        for (String line : lore) {
            parsedLore.add(parseMiniMessage(line, line));
        }
        if (!parsedLore.isEmpty()) {
            meta.lore(parsedLore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private int getBackSlot(int menuSize) {
        int configured = plugin.getConfig().getInt("shop.menu.back-item.slot", DEFAULT_BACK_SLOT);
        return configured >= 0 && configured < menuSize ? configured : -1;
    }

    private int findSlot(Inventory inv, int preferred, Set<Integer> reserved) {
        if (preferred >= 0 && preferred < inv.getSize()
                && inv.getItem(preferred) == null
                && !reserved.contains(preferred)) {
            return preferred;
        }

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null && !reserved.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    private int normalizeSize(int raw) {
        int clamped = Math.clamp(raw, 9, 54);
        int remainder = clamped % 9;
        if (remainder == 0) {
            return clamped;
        }
        return Math.clamp(clamped - remainder, 9, 54);
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) return fallback;

        Material material = Material.matchMaterial(raw);
        if (material != null) return material;

        material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        return material != null ? material : fallback;
    }

    private Component parseMiniMessage(String input, String fallback) {
        if (input == null || input.isBlank()) {
            return Component.text(fallback == null ? "" : fallback);
        }
        try {
            return miniMessage.deserialize(input);
        } catch (IllegalArgumentException ex) {
            return Component.text(fallback == null ? input : fallback);
        }
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (leftover.isEmpty()) return;

        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private String toTitle(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] words = lower.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                out.append(word.substring(1));
            }
        }
        return out.toString();
    }

    private record ShopCategory(
            String categoryId,
            String displayName,
            String title,
            List<String> lore,
            Material icon,
            int slot,
            int menuSize
    ) {
        private ShopCategory {
            lore = List.copyOf(lore == null ? List.of() : lore);
        }

        private String displayNameFallback() {
            return displayName == null || displayName.isBlank() ? categoryId : displayName;
        }
    }

    private record ShopItem(
            String itemId,
            Material material,
            int amount,
            int price,
            int slot,
            String displayName,
            List<String> lore,
            String giveDisplayName,
            List<String> giveLore
    ) {
        private ShopItem {
            lore = List.copyOf(lore == null ? List.of() : lore);
            giveLore = List.copyOf(giveLore == null ? List.of() : giveLore);
        }

        private ItemStack createGiveItem(MiniParser parser) {
            ItemStack stack = new ItemStack(material, amount);
            if ((giveDisplayName == null || giveDisplayName.isBlank()) && giveLore.isEmpty()) {
                return stack;
            }

            ItemMeta meta = stack.getItemMeta();
            if (meta == null) return stack;
            if (giveDisplayName != null && !giveDisplayName.isBlank()) {
                meta.displayName(parser.parse(giveDisplayName, giveDisplayName));
            }
            if (!giveLore.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : giveLore) {
                    lore.add(parser.parse(line, line));
                }
                meta.lore(lore);
            }
            stack.setItemMeta(meta);
            return stack;
        }
    }

    @FunctionalInterface
    private interface MiniParser {
        Component parse(String input, String fallback);
    }

    private abstract static class ShopHolder implements InventoryHolder {
        private Inventory inventory;

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        @NotNull
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class CategoryMenuHolder extends ShopHolder {
    }

    private static final class ItemMenuHolder extends ShopHolder {
    }
}
