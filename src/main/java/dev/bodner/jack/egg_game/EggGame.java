package dev.bodner.jack.egg_game;

import com.google.gson.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EggGame extends JavaPlugin implements Listener {
    EggState eggState;
    Location eggPos;
    UUID playerHolder;
    int shootCooldown = 0;
    int combatCooldown = 0;
    int potionCooldown = 0;

    Location origin;

    ArrayList<PotionEffect> buffEffects = new ArrayList<>();

    @Override
    public void onEnable() {
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        File data = new File(getDataFolder().getAbsolutePath() + File.separator + "data.json");
        origin = new Location(Bukkit.getWorlds().get(2), 0, Bukkit.getWorlds().get(2).getHighestBlockYAt(0, 0) + 1, 0);

        buffEffects.add(new PotionEffect(PotionEffectType.REGENERATION, 600, 0, false, false, false));
        buffEffects.add(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 600, 0, false, false, false));

        if (!getDataFolder().exists()){
            getDataFolder().mkdirs();
        }

        if (!data.exists()){
            JsonObject base = new JsonObject();
            base.add("state", new JsonPrimitive(EggState.BLOCK.toString()));
            base.add("world", new JsonPrimitive(origin.getWorld().getName()));
            JsonArray array = new JsonArray();
            array.add(origin.getX());
            array.add(origin.getY());
            array.add(origin.getZ());
            base.add("coordinates", array);
            base.add("playerHolder",null);
            try {
                data.createNewFile();
                BufferedWriter writer = new BufferedWriter(new FileWriter(data));
                writer.write(gson.toJson(base));
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Reader reader = null;
        try {
            reader = Files.newBufferedReader(Paths.get(data.toURI()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        JsonObject jsonData = gson.fromJson(reader,JsonObject.class);

        eggState = EggState.valueOf(jsonData.get("state").getAsString());
        eggPos = new Location(Bukkit.getWorld(jsonData.get("world").getAsString()),jsonData.get("coordinates").getAsJsonArray().get(0).getAsDouble(), jsonData.get("coordinates").getAsJsonArray().get(1).getAsDouble(), jsonData.get("coordinates").getAsJsonArray().get(2).getAsDouble());
        try{
            playerHolder = UUID.fromString(jsonData.get("playerHolder").getAsString());
        }
        catch (Exception e){
            playerHolder = null;
        }

        getServer().getPluginManager().registerEvents(this,this);

        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(this, () -> {

            if (combatCooldown > 0){
                combatCooldown--;
                Player player = Bukkit.getPlayer(playerHolder);
                if (player != null){
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("The Dragon Egg is in combat for " + (combatCooldown/20) + " seconds"));
                }
            }

            if (shootCooldown > 0){
                shootCooldown--;
                if (shootCooldown <= 0){
                    Player player = Bukkit.getPlayer(playerHolder);
                    if (player != null) {
                        player.spigot().sendMessage(ChatMessageType.CHAT, new TextComponent("Dragon's Breath is ready"));
                    }
                }
            }

            switch (eggState){
                case INVENTORY:
                    Player player = Bukkit.getPlayer(playerHolder);
                    if (player != null){
                        eggPos = player.getLocation();
                        World world = player.getWorld();
                        world.spawnParticle(Particle.PORTAL, eggPos.getX(), eggPos.getY() + 0.5, eggPos.getZ(), 3, 0.3,0.6, 0.3, 0.03);
                        potionCooldown--;
                        if (potionCooldown <= 0){
                            player.addPotionEffects(buffEffects);
                            potionCooldown = 600;
                        }
                    }
                    break;
                case DROPPED_ITEM:
                    for (World world : Bukkit.getWorlds()){
                        for (Entity entity : world.getEntities()){
                            if (entity instanceof Item){
                                if (((Item)entity).getItemStack().getType().equals(Material.DRAGON_EGG)){
                                    if (entity.getLocation().getY() < -1){
                                        entity.remove();
                                        origin.getBlock().setType(Material.DRAGON_EGG);
                                        eggPos = origin;
                                        eggState = EggState.BLOCK;
                                        break;
                                    }
                                    eggPos = entity.getLocation();
                                    world.spawnParticle(Particle.REVERSE_PORTAL, eggPos.getX(), eggPos.getY() + 0.25, eggPos.getZ(), 2, 0.1, 0.1, 0.1, 0.1);
                                    break;
                                }
                            }
                        }
                    }
                    break;
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                for (ItemStack item : player.getInventory()){
                    if (item != null) {
                        if (item.getType().equals(Material.COMPASS) && item.getItemMeta().getDisplayName().contains("Dragon Egg Tracker")) {
                            CompassMeta meta = (CompassMeta) item.getItemMeta();
                            meta.setLodestone(eggPos);
                            meta.setLodestoneTracked(false);
                            item.setItemMeta(meta);
                        }
                    }
                }
            }

        }, 0, 1);
    }

    @Override
    public void onDisable() {
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        File data = new File(getDataFolder().getAbsolutePath() + File.separator + "data.json");

        JsonObject base = new JsonObject();
        base.add("state", new JsonPrimitive(eggState.toString()));
        base.add("world", new JsonPrimitive(eggPos.getWorld().getName()));
        JsonArray array = new JsonArray();
        array.add(eggPos.getX());
        array.add(eggPos.getY());
        array.add(eggPos.getZ());
        base.add("coordinates", array);
        if (playerHolder == null){
            base.add("playerHolder", null);
        }
        else {
            base.add("playerHolder", new JsonPrimitive(playerHolder.toString()));
        }
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(data));
            writer.write(gson.toJson(base));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event){
        Item item = event.getEntity();
        if (item.getItemStack().getType().equals(Material.DRAGON_EGG)){
            eggState = EggState.DROPPED_ITEM;
            item.setInvulnerable(true);
            playerHolder = null;
        }
    }

    @EventHandler
    public void onItemPickUp(EntityPickupItemEvent event){
        if (event.getItem().getItemStack().getType().equals(Material.DRAGON_EGG)){
            if (!(event.getEntity() instanceof Player)){
                event.setCancelled(true);
            }
            else {
                eggState = EggState.INVENTORY;
                playerHolder = ((Player)event.getEntity()).getUniqueId();
            }
        }
    }

    @EventHandler
    public void onHopperPickUp(InventoryPickupItemEvent event){
        if (event.getItem().getItemStack().getType().equals(Material.DRAGON_EGG)){
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event){

        if (event.getCurrentItem() != null) {
            if ((event.getCurrentItem().getType().equals(Material.DRAGON_EGG) || event.getCursor().getType().equals(Material.DRAGON_EGG)) && ((!event.getClickedInventory().getType().equals(InventoryType.PLAYER)) || event.getClick().isShiftClick())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event){
        if (event.getOldCursor().getType().equals(Material.DRAGON_EGG)){
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event){
        Block block = event.getBlock();
        if (block.getType().equals(Material.DRAGON_EGG)){
            playerHolder = null;
            eggPos = event.getBlockPlaced().getLocation();
            eggState = EggState.BLOCK;
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event){
        if (event.getEntity().getItemStack().getType().equals(Material.DRAGON_EGG)){
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAnvilPut(PrepareAnvilEvent event){
        ItemStack item = event.getResult();
        if (item != null) {
            if (item.getType().equals(Material.COMPASS) && item.getItemMeta().getDisplayName().equalsIgnoreCase("dragon egg tracker")) {
                CompassMeta meta = (CompassMeta) item.getItemMeta();
                meta.setDisplayName("§r§dDragon Egg Tracker");
                meta.setLodestone(eggPos);
                meta.setLodestoneTracked(false);
                item.setItemMeta(meta);
            }
        }
    }

    @EventHandler
    public void onEggTeleport(BlockFromToEvent event){
        if (event.getBlock().getType().equals(Material.DRAGON_EGG)){
            eggPos = event.getToBlock().getLocation();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event){
        Player player = event.getPlayer();
        if(event.getItem() != null) {
            if (!player.isSneaking() && event.getAction().equals(Action.RIGHT_CLICK_BLOCK) && event.getItem().getType().equals(Material.DRAGON_EGG)){
                event.setCancelled(true);
            }
            if ((event.getAction().equals(Action.LEFT_CLICK_AIR) || event.getAction().equals(Action.LEFT_CLICK_BLOCK)) && event.getItem().getType().equals(Material.DRAGON_EGG)) {
                if (shootCooldown <= 0) {
                    player.launchProjectile(DragonFireball.class, player.getLocation().getDirection());
                    shootCooldown = 1200;
                    event.setCancelled(true);
                } else {
                    player.spigot().sendMessage(ChatMessageType.CHAT, new TextComponent("You must wait " + (shootCooldown / 20) + " seconds to do that"));
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e){
        //I kind of "borrowed" this part from this post: https://bukkit.org/threads/stopping-players-from-damaging-other-players-in-same-array.125550/
        if(!(e.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) e.getEntity();
        Player attacker = null;

        if(e.getDamager() instanceof Player) {
            attacker = (Player) e.getDamager();
        } else if(e.getDamager() instanceof Arrow) {
            Arrow arrow = (Arrow) e.getDamager();
            if(!(arrow.getShooter() instanceof Player)) {
                return;
            }
            attacker = (Player) arrow.getShooter();
        } else if(e.getDamager() instanceof ThrownPotion) {
            return;
        } else if(e.getDamager() instanceof Trident){
            Trident trident = (Trident) e.getDamager();
            if (!(trident.getShooter() instanceof Player)){
                return;
            }
            attacker = (Player) trident.getShooter();
        }

        if(victim == attacker) {
            return;
        }
        if(attacker == null) {
            return;
        }

        if (victim.getUniqueId().equals(playerHolder) || attacker.getUniqueId().equals(playerHolder)){
            combatCooldown = 2400;
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event){
        if (event.getPlayer().getUniqueId().equals(playerHolder) && combatCooldown > 0){
            Player player = event.getPlayer();
            player.getInventory().remove(Material.DRAGON_EGG);
            eggPos.getWorld().dropItem(eggPos, new ItemStack(Material.DRAGON_EGG, 1));
            eggState = EggState.DROPPED_ITEM;
        }
    }

    @EventHandler
    public void onApplyEffectCloud(AreaEffectCloudApplyEvent event){
        if (event.getEntity().getParticle().equals(Particle.DRAGON_BREATH)){
            event.getAffectedEntities().remove(Bukkit.getPlayer(playerHolder));
            for (LivingEntity entity : event.getAffectedEntities()){
                entity.damage(1.5, (Entity) event.getEntity().getSource());
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event){
        if (event.getPlayer().getInventory().getItemInOffHand().getType().equals(Material.DRAGON_EGG) || event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.DRAGON_EGG)){
            if (event.getPlayer().getUniqueId().equals(playerHolder)){
                event.setCancelled(true);
            }
        }
    }
}

enum EggState {
    INVENTORY,
    DROPPED_ITEM,
    BLOCK,
}
