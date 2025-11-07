package de.deepsz.combatretention;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatInventoryRetentionPlugin extends JavaPlugin implements Listener {
    private static final long COMBAT_TAG_DURATION_MILLIS = 15_000L;

    private final Map<UUID, Long> combatTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> retainedItems = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        combatTimestamps.clear();
        retainedItems.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player damager = resolvePlayer(event.getDamager());
        Player victim = event.getEntity() instanceof Player ? (Player) event.getEntity() : null;
        if (damager == null && victim == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (damager != null) {
            combatTimestamps.put(damager.getUniqueId(), now);
        }
        if (victim != null) {
            combatTimestamps.put(victim.getUniqueId(), now);
        }
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            Object shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();
        if (!isInCombat(playerId)) {
            combatTimestamps.remove(playerId);
            retainedItems.remove(playerId);
            return;
        }

        List<ItemStack> drops = event.getDrops();
        if (drops.isEmpty()) {
            return;
        }

        List<ItemStack> shuffled = new ArrayList<>(drops);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());

        int keepCount = shuffled.size() / 2;
        if (keepCount <= 0) {
            return;
        }

        Set<ItemStack> toKeep = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < keepCount; i++) {
            toKeep.add(shuffled.get(i));
        }

        List<ItemStack> keptCopies = new ArrayList<>(keepCount);
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext() && !toKeep.isEmpty()) {
            ItemStack stack = iterator.next();
            if (toKeep.remove(stack)) {
                keptCopies.add(stack.clone());
                iterator.remove();
            }
        }

        if (!keptCopies.isEmpty()) {
            retainedItems.put(playerId, keptCopies);
            player.sendMessage(ChatColor.GREEN + "Du behältst " + keptCopies.size() + " Items dank Kampf-Bonus.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        List<ItemStack> kept = retainedItems.remove(playerId);
        combatTimestamps.remove(playerId);
        if (kept == null || kept.isEmpty()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : kept) {
            Map<Integer, ItemStack> leftover = inventory.addItem(stack);
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }

        player.sendMessage(ChatColor.YELLOW + "Deine Kampf-Items wurden wiederhergestellt.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        combatTimestamps.remove(playerId);
        retainedItems.remove(playerId);
    }

    private boolean isInCombat(UUID playerId) {
        Long timestamp = combatTimestamps.get(playerId);
        if (timestamp == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - timestamp > COMBAT_TAG_DURATION_MILLIS) {
            combatTimestamps.remove(playerId);
            return false;
        }
        return true;
    }
}
