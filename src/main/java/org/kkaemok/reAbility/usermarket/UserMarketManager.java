package org.kkaemok.reAbility.usermarket;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class UserMarketManager implements Listener {
    private static final int SELL_MENU_SIZE = 27;
    private static final int SELL_ITEM_SLOT = 13;
    private static final int SELL_CONFIRM_SLOT = 15;
    private static final int SELL_CANCEL_SLOT = 11;
    private static final int SELL_GUIDE_SLOT = 4;
    private static final int SELL_SLOT_HINT_SLOT = 22;

    private static final int MARKET_MENU_SIZE = 54;
    private static final int MARKET_ITEMS_PER_PAGE = 45;
    private static final int MARKET_PREV_SLOT = 45;
    private static final int MARKET_INFO_SLOT = 49;
    private static final int MARKET_NEXT_SLOT = 53;

    private final ReAbility plugin;
    private final AbilityManager abilityManager;
    private final NamespacedKey actionKey;
    private final File shopFile;
    private final Map<UUID, ItemStack> pendingItems = new LinkedHashMap<>();
    private final Map<Long, UserListing> listings = new LinkedHashMap<>();

    private FileConfiguration shopConfig;
    private long nextListingId = 1L;

    public UserMarketManager(ReAbility plugin, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
        this.actionKey = new NamespacedKey(plugin, "user_market_action");
        this.shopFile = new File(plugin.getDataFolder(), "user-shop.yml");
        loadFromDisk();
    }

    public void openSellMenu(Player player) {
        SellMenuHolder holder = new SellMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, SELL_MENU_SIZE, "유저상점 판매 (가운데 슬롯)");
        holder.setInventory(inv);

        inv.setItem(SELL_CONFIRM_SLOT, createActionItem(Material.LIME_WOOL,
                "확인", List.of("가운데 슬롯(13번)에 아이템 배치 후 클릭"), "confirm"));
        inv.setItem(SELL_CANCEL_SLOT, createActionItem(Material.RED_WOOL,
                "취소", List.of("닫고 취소"), "cancel"));
        inv.setItem(SELL_GUIDE_SLOT, createInfoItem(Material.WRITABLE_BOOK, "판매 방법 안내", List.of(
                "1) 가운데 슬롯(13번)에 판매할 아이템 배치",
                "2) [확인] 버튼 클릭",
                "3) /가격설정 <가격> 입력으로 등록 완료"
        )));
        inv.setItem(SELL_SLOT_HINT_SLOT, createInfoItem(Material.YELLOW_STAINED_GLASS_PANE,
                "↑ 가운데 슬롯에 아이템을 올려주세요",
                List.of("가운데 칸이 판매 아이템 등록 칸입니다.")));

        player.openInventory(inv);
        player.sendMessage("[유저상점] 가운데 슬롯(13번)에 판매할 아이템을 넣고 [확인]을 눌러주세요.");
    }

    public boolean setPrice(Player player, long price) {
        if (price <= 0L) {
            player.sendMessage("[유저상점] 가격은 1 이상이어야 합니다.");
            return false;
        }

        UUID playerId = player.getUniqueId();
        ItemStack pending = pendingItems.remove(playerId);
        if (pending == null || pending.getType().isAir()) {
            player.sendMessage("[유저상점] 대기 중인 판매 아이템이 없습니다. /팔기 를 먼저 사용하세요.");
            return false;
        }

        long listingId = nextListingId++;
        listings.put(listingId, new UserListing(
                listingId,
                playerId,
                pending.clone(),
                price,
                System.currentTimeMillis()
        ));
        saveToDisk();

        player.sendMessage("[유저상점] " + price + " 코인에 등록했습니다.");
        return true;
    }

    public void openMarket(Player player, int requestedPage) {
        List<UserListing> all = getSortedListings();
        int total = all.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) MARKET_ITEMS_PER_PAGE));
        int page = Math.max(1, Math.min(totalPages, requestedPage));

        MarketMenuHolder holder = new MarketMenuHolder(page);
        Inventory inv = Bukkit.createInventory(holder, MARKET_MENU_SIZE, "유저상점 " + page + "/" + totalPages);
        holder.setInventory(inv);

        if (all.isEmpty()) {
            inv.setItem(22, createInfoItem(Material.BARRIER, "등록된 상품 없음", List.of("/팔기 로 아이템을 등록하세요")));
        } else {
            int start = (page - 1) * MARKET_ITEMS_PER_PAGE;
            int end = Math.min(start + MARKET_ITEMS_PER_PAGE, total);
            for (int index = start; index < end; index++) {
                UserListing listing = all.get(index);
                int slot = index - start;
                inv.setItem(slot, createListingIcon(listing));
            }
        }

        boolean hasPrev = page > 1;
        boolean hasNext = page < totalPages;
        inv.setItem(MARKET_PREV_SLOT, hasPrev
                ? createActionItem(Material.ARROW, "이전", List.of("이전 페이지 열기"), "prev")
                : createInfoItem(Material.GRAY_STAINED_GLASS_PANE, "이전", List.of("이전 페이지 없음")));
        inv.setItem(MARKET_NEXT_SLOT, hasNext
                ? createActionItem(Material.ARROW, "다음", List.of("다음 페이지 열기"), "next")
                : createInfoItem(Material.GRAY_STAINED_GLASS_PANE, "다음", List.of("다음 페이지 없음")));
        inv.setItem(MARKET_INFO_SLOT, createInfoItem(Material.BOOK,
                "등록 수: " + total,
                List.of("페이지 " + page + " / " + totalPages)));

        player.openInventory(inv);
    }

    public void shutdown() {
        saveToDisk();
        pendingItems.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder holder)) return;

        if (holder instanceof SellMenuHolder sellHolder) {
            handleSellClick(event, player, sellHolder);
            return;
        }
        if (holder instanceof MarketMenuHolder marketHolder) {
            handleMarketClick(event, player, marketHolder);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder)) return;

        if (event.getView().getTopInventory().getHolder() instanceof SellMenuHolder) {
            int topSize = event.getView().getTopInventory().getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= topSize) continue;
                if (rawSlot != SELL_ITEM_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof SellMenuHolder holder)) return;

        if (holder.isConfirmed()) return;

        ItemStack item = sanitizeItem(event.getInventory().getItem(SELL_ITEM_SLOT));
        if (item == null) return;

        giveItem(player, item);
        event.getInventory().setItem(SELL_ITEM_SLOT, null);
    }

    private void handleSellClick(InventoryClickEvent event, Player player, SellMenuHolder holder) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        boolean inTop = rawSlot >= 0 && rawSlot < top.getSize();

        if (inTop) {
            if (rawSlot == SELL_CONFIRM_SLOT) {
                event.setCancelled(true);
                confirmSell(player, holder, top);
                return;
            }
            if (rawSlot == SELL_CANCEL_SLOT) {
                event.setCancelled(true);
                player.closeInventory();
                player.sendMessage("[유저상점] 판매를 취소했습니다.");
                return;
            }
            event.setCancelled(rawSlot != SELL_ITEM_SLOT);
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    private void handleMarketClick(InventoryClickEvent event, Player player, MarketMenuHolder holder) {
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();
        if (rawSlot < 0 || rawSlot >= top.getSize()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.isBlank()) return;

        if (action.equals("prev")) {
            openMarket(player, holder.page() - 1);
            return;
        }
        if (action.equals("next")) {
            openMarket(player, holder.page() + 1);
            return;
        }
        if (!action.startsWith("buy:")) return;

        long listingId;
        try {
            listingId = Long.parseLong(action.substring("buy:".length()));
        } catch (NumberFormatException ex) {
            player.sendMessage("[유저상점] 잘못된 상품 번호입니다.");
            return;
        }

        purchaseListing(player, listingId, holder.page());
    }

    private void confirmSell(Player player, SellMenuHolder holder, Inventory top) {
        if (pendingItems.containsKey(player.getUniqueId())) {
            player.sendMessage("[유저상점] 이미 대기 중인 아이템이 있습니다. /가격설정 을 먼저 입력하세요.");
            return;
        }

        ItemStack item = sanitizeItem(top.getItem(SELL_ITEM_SLOT));
        if (item == null) {
            player.sendMessage("[유저상점] 판매 슬롯에 아이템을 먼저 넣어주세요.");
            return;
        }

        pendingItems.put(player.getUniqueId(), item.clone());
        saveToDisk();
        top.setItem(SELL_ITEM_SLOT, null);
        holder.markConfirmed();
        player.closeInventory();
        player.sendMessage("[유저상점] 이제 /가격설정 <가격> 을 입력하세요.");
    }

    private void purchaseListing(Player buyer, long listingId, int returnPage) {
        UserListing listing = listings.get(listingId);
        if (listing == null) {
            buyer.sendMessage("[유저상점] 이미 판매 완료된 상품입니다.");
            openMarket(buyer, returnPage);
            return;
        }

        UUID buyerId = buyer.getUniqueId();
        if (listing.sellerUuid().equals(buyerId)) {
            buyer.sendMessage("[유저상점] 본인 상품은 구매할 수 없습니다.");
            return;
        }

        long price = listing.price();
        PlayerData buyerData = abilityManager.getPlayerData(buyerId);
        if (buyerData.getCoins() < price) {
            buyer.sendMessage("[유저상점] 코인이 부족합니다.");
            return;
        }

        ItemStack purchased = sanitizeItem(listing.item());
        if (purchased == null) {
            listings.remove(listingId);
            saveToDisk();
            buyer.sendMessage("[유저상점] 상품 아이템이 비정상이라 목록에서 제거되었습니다.");
            openMarket(buyer, returnPage);
            return;
        }

        PlayerData sellerData = abilityManager.getPlayerData(listing.sellerUuid());
        buyerData.addCoins(-price);
        sellerData.addCoins(price);
        abilityManager.savePlayerData(buyerId);
        abilityManager.savePlayerData(listing.sellerUuid());

        listings.remove(listingId);
        saveToDisk();

        giveItem(buyer, purchased);
        buyer.sendMessage("[유저상점] " + price + " 코인으로 구매했습니다.");

        Player seller = Bukkit.getPlayer(listing.sellerUuid());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage("[유저상점] 등록한 상품이 " + price + " 코인에 판매되었습니다.");
        }

        openMarket(buyer, returnPage);
    }

    private ItemStack createListingIcon(UserListing listing) {
        ItemStack icon = listing.item().clone();
        if (icon.getAmount() <= 0) {
            icon.setAmount(1);
        }

        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        String sellerName = resolvePlayerName(listing.sellerUuid());
        List<Component> lore = new ArrayList<>();
        List<Component> existingLore = meta.lore();
        if (existingLore != null) {
            lore.addAll(existingLore);
        }
        lore.add(Component.empty());
        lore.add(Component.text("가격: " + listing.price() + " 코인"));
        lore.add(Component.text("판매자: " + sellerName));
        lore.add(Component.text("클릭하여 구매"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "buy:" + listing.id());
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createActionItem(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(name));
        if (!lore.isEmpty()) {
            meta.lore(toComponents(lore));
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(name));
        if (!lore.isEmpty()) {
            meta.lore(toComponents(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> toComponents(List<String> lines) {
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(Component.text(line));
        }
        return components;
    }

    private String resolvePlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        String offline = Bukkit.getOfflinePlayer(uuid).getName();
        return offline == null || offline.isBlank() ? uuid.toString() : offline;
    }

    private List<UserListing> getSortedListings() {
        List<UserListing> ordered = new ArrayList<>(listings.values());
        ordered.sort(Comparator.comparingLong(UserListing::id));
        return ordered;
    }

    private ItemStack sanitizeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemStack clone = item.clone();
        if (clone.getAmount() <= 0) {
            clone.setAmount(1);
        }
        return clone;
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (overflow.isEmpty()) return;
        for (ItemStack drop : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private void loadFromDisk() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }
        if (!shopFile.exists()) {
            try {
                if (!shopFile.createNewFile()) {
                    plugin.getLogger().warning("Could not create user-shop.yml");
                }
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create user-shop.yml", ex);
            }
        }

        this.shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        this.listings.clear();
        this.pendingItems.clear();
        this.nextListingId = Math.max(1L, shopConfig.getLong("next-id", 1L));

        ConfigurationSection listingsSection = shopConfig.getConfigurationSection("listings");
        if (listingsSection != null) {
            for (String key : listingsSection.getKeys(false)) {
                long id;
                try {
                    id = Long.parseLong(key);
                } catch (NumberFormatException ex) {
                    continue;
                }

                String path = "listings." + key + ".";
                String sellerRaw = shopConfig.getString(path + "seller");
                ItemStack item = shopConfig.getItemStack(path + "item");
                long price = shopConfig.getLong(path + "price", 0L);
                long createdAt = shopConfig.getLong(path + "created-at", System.currentTimeMillis());
                if (sellerRaw == null || item == null || item.getType().isAir() || price <= 0L) continue;

                UUID sellerUuid;
                try {
                    sellerUuid = UUID.fromString(sellerRaw);
                } catch (IllegalArgumentException ex) {
                    continue;
                }

                listings.put(id, new UserListing(id, sellerUuid, item.clone(), price, createdAt));
                nextListingId = Math.max(nextListingId, id + 1L);
            }
        }

        ConfigurationSection pendingSection = shopConfig.getConfigurationSection("pending");
        if (pendingSection == null) return;

        for (String key : pendingSection.getKeys(false)) {
            UUID playerUuid;
            try {
                playerUuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            ItemStack item = sanitizeItem(shopConfig.getItemStack("pending." + key + ".item"));
            if (item == null) continue;
            pendingItems.put(playerUuid, item);
        }
    }

    private void saveToDisk() {
        if (shopConfig == null) {
            shopConfig = new YamlConfiguration();
        }
        shopConfig.set("next-id", nextListingId);
        shopConfig.set("listings", null);
        shopConfig.set("pending", null);
        for (UserListing listing : listings.values()) {
            String path = "listings." + listing.id() + ".";
            shopConfig.set(path + "seller", listing.sellerUuid().toString());
            shopConfig.set(path + "price", listing.price());
            shopConfig.set(path + "item", listing.item());
            shopConfig.set(path + "created-at", listing.createdAt());
        }
        for (Map.Entry<UUID, ItemStack> entry : pendingItems.entrySet()) {
            String path = "pending." + entry.getKey() + ".";
            shopConfig.set(path + "item", entry.getValue());
        }

        try {
            shopConfig.save(shopFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save user-shop.yml", ex);
        }
    }

    private record UserListing(long id, UUID sellerUuid, ItemStack item, long price, long createdAt) {
    }

    private abstract static class ShopHolder implements InventoryHolder {
        private Inventory inventory;

        protected void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        @NotNull
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class SellMenuHolder extends ShopHolder {
        private boolean confirmed;

        private boolean isConfirmed() {
            return confirmed;
        }

        private void markConfirmed() {
            this.confirmed = true;
        }
    }

    private static final class MarketMenuHolder extends ShopHolder {
        private final int page;

        private MarketMenuHolder(int page) {
            this.page = page;
        }

        private int page() {
            return page;
        }
    }
}
