package me.user.moc.ability.impl;

import me.user.moc.MocPlugin;
import me.user.moc.ability.Ability;
import me.user.moc.ability.AbilityManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class Ddumbi extends Ability {

    private final Map<UUID, Long> cooldownPink = new HashMap<>(); // 이렇게 좋은 날
    private final Map<UUID, Long> cooldownBlue = new HashMap<>(); // 그 겨울, 우리
    private final Map<UUID, Long> cooldownBlack = new HashMap<>(); // 널 처음 본 순간
    // Coffee consumption is limited by item count (15), no specific cooldown
    // mentioned but usually instant or item usage speed.
    // "Cooltime: CD per 10s". Coffee doesn't mention cooldown, just consume.

    public Ddumbi(MocPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCode() {
        return "H06";
    }

    @Override
    public String getName() {
        return "뚜비";
    }

    @Override
    public void giveItem(Player p) {
        // super.giveItem(p); // Abstract method

        // Pink CD - Like a Good Day
        ItemStack pinkCd = new ItemStack(Material.IRON_NUGGET); // Using IRON_NUGGET as per registry
        ItemMeta pinkMeta = pinkCd.getItemMeta();
        pinkMeta.setDisplayName("§d§l[ 이렇게 좋은 날 ]");
        pinkMeta.setCustomModelData(1);
        pinkMeta.setLore(Arrays.asList(
                "§f§l우클릭 시:",
                "§f전방에 분홍색 음표를 발사하여",
                "§f자신과 맞은 아군에게 §d재생 II§f를 5초간 부여합니다.",
                "§7(쿨타임: 10초)"));
        pinkCd.setItemMeta(pinkMeta);

        // Blue CD - That Winter, Us
        ItemStack blueCd = new ItemStack(Material.IRON_NUGGET);
        ItemMeta blueMeta = blueCd.getItemMeta();
        blueMeta.setDisplayName("§b§l[ 그 겨울, 우리 ]");
        blueMeta.setCustomModelData(2);
        blueMeta.setLore(Arrays.asList(
                "§f§l우클릭 시:",
                "§f전방에 하늘색 음표를 발사하여",
                "§f맞은 적에게 §b동상 및 구속 II§f를 5초간 부여합니다.",
                "§7(쿨타임: 10초)"));
        blueCd.setItemMeta(blueMeta);

        // Black CD - The Moment I Saw You
        ItemStack blackCd = new ItemStack(Material.IRON_NUGGET);
        ItemMeta blackMeta = blackCd.getItemMeta();
        blackMeta.setDisplayName("§8§l[ 널 처음 본 순간 ]");
        blackMeta.setCustomModelData(3);
        blackMeta.setLore(Arrays.asList(
                "§f§l우클릭 시:",
                "§f거대한 검은 음표를 발사합니다.",
                "§f적중 시 §c15 데미지§f와 함께 폭발합니다.",
                "§7(쿨타임: 10초)"));
        blackCd.setItemMeta(blackMeta);

        // Coffee
        ItemStack coffee = new ItemStack(Material.POTION, 15);
        PotionMeta coffeeMeta = (PotionMeta) coffee.getItemMeta();
        coffeeMeta.setDisplayName("§6§l[ 커피 ]");
        coffeeMeta.setColor(Color.ORANGE); // Coffee color approximation
        coffeeMeta.setCustomModelData(1); // Mapped to coffee model
        coffeeMeta.setLore(Arrays.asList(
                "§f§l마실 경우:",
                "§f배고픔을 5칸 회복하고",
                "§f§e성급함 III, 신속 III§f을 10초간 얻습니다."));
        coffee.setItemMeta(coffeeMeta);

        // Remove basics
        p.getInventory().remove(Material.IRON_SWORD);
        p.getInventory().remove(Material.COOKED_BEEF);

        // Give items
        p.getInventory().addItem(pinkCd, blueCd, blackCd, coffee);

        detailCheck(p);
    }

    @Override
    public void detailCheck(Player p) {
        p.sendMessage("§e복합 ● 뚜비(가족)");
        p.sendMessage("§f가수가 되.");
        p.sendMessage(" ");
        p.sendMessage("§d[이렇게 좋은 날] §f재생 음표 발사 (본인/아군 재생 II 5초)");
        p.sendMessage("§b[그 겨울, 우리] §f빙결 음표 발사 (적 구속 II/동상 5초)");
        p.sendMessage("§8[널 처음 본 순간] §f폭발 음표 발사 (15 데미지 + 폭발)");
        p.sendMessage("§6[커피] §f섭취 시 배고픔 회복 + 버프 (성급함/신속 III 10초)");
        p.sendMessage(" ");
        p.sendMessage("§f쿨타임 : 각 CD 10초");
        p.sendMessage("§f---");
        p.sendMessage("§f추가 장비 : CD 3종, 커피 15개");
        p.sendMessage("§f장비 제거 : 철검, 스테이크");
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "§e복합 ● 뚜비(가족)",
                "§fCD를 사용하여 다양한 음표 공격/지원을 하고,",
                "§f커피를 마셔 버프를 얻습니다.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;
        if (e.getItem() == null)
            return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = e.getItem();
        if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData())
            return;

        int cmd = item.getItemMeta().getCustomModelData();
        Material mat = item.getType();

        // CD Logic (Iron Nugget)
        if (mat == Material.IRON_NUGGET) {
            e.setCancelled(true); // Prevent consumption or weird interaction

            if (cmd == 1) { // Pink CD
                usePinkCd(p);
            } else if (cmd == 2) { // Blue CD
                useBlueCd(p);
            } else if (cmd == 3) { // Black CD
                useBlackCd(p);
            }
        }
        // Coffee Logic handled in Consume event usually, but if it's a potion, drinking
        // takes time.
        // If user wants instant click, we use Interact.
        // Prompt says "Masil myeon" (When drunk/consumed). Potion drinking animation is
        // standard.
        // So we will let PlayerItemConsumeEvent handle usage to allow animation.
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!AbilityManager.getInstance().hasAbility(p, getCode()))
            return;
        ItemStack item = e.getItem();

        if (item.getType() == Material.POTION && item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
            if (item.getItemMeta().getCustomModelData() == 1) { // Coffee
                // Apply effects AFTER consumption
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        p.setFoodLevel(Math.min(20, p.getFoodLevel() + 10)); // 5 drumsticks = 10 food points
                        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 200, 2)); // Level 3 = Amp 2
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 2)); // Level 3 = Amp 2
                        p.sendMessage("§6뚜비 : §f커피를 마셔 힘이 넘칩니다!");
                    }
                }.runTaskLater(plugin, 1L);
            }
        }
    }

    private void usePinkCd(Player p) {
        if (checkCooldown(p, cooldownPink, 10))
            return;
        setCooldown(p, cooldownPink);

        broadcast(p, "§d뚜비 : §f이렇게 좋은 날");

        // Self buff
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1)); // Regen 2 = Amp 1

        // The prompt says "Fires notes for 5 seconds".
        // It's a duration skill.
        long duration = 100L; // 5 seconds
        registerTask(p, new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!AbilityManager.getInstance().hasAbility(p, getCode()) || ticks >= duration) {
                    this.cancel();
                    return;
                }

                // Fire notes in direction
                Location loc = p.getEyeLocation();
                Vector dir = loc.getDirection().normalize();

                // Random spread cone
                for (int i = 0; i < 3; i++) { // 3 notes per tick
                    Vector spread = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).multiply(0.5);
                    Vector v = dir.clone().add(spread).normalize().multiply(0.5); // Speed

                    // Spawn particle projectile entity or just raytrace?
                    // "Gravity affects notes". Particles don't have gravity unless we simulate it.
                    // "Bumps into blocks/entities -> vanish".
                    // Need to simulate projectiles.
                    spawnNoteProjectile(p, loc.clone(), v, Color.FUCHSIA, true);
                }

                // Sound
                if (ticks % 5 == 0) {
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 2.0f);
                }

                ticks += 2; // Run every 2 ticks? Or 1?
                // Let's run this runnable every 2 ticks (0.1s)
            }
        }.runTaskTimer(plugin, 0L, 2L));
    }

    private void useBlueCd(Player p) {
        if (checkCooldown(p, cooldownBlue, 10))
            return;
        setCooldown(p, cooldownBlue);

        broadcast(p, "§b뚜비 : §f그 겨울, 우리");

        // Duration 5s
        long duration = 100L;
        registerTask(p, new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!AbilityManager.getInstance().hasAbility(p, getCode()) || ticks >= duration) {
                    this.cancel();
                    return;
                }

                Location loc = p.getEyeLocation();
                Vector dir = loc.getDirection().normalize();

                for (int i = 0; i < 3; i++) {
                    Vector spread = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).multiply(0.3); // Tighter
                                                                                                          // spread for
                                                                                                          // longer
                                                                                                          // range?
                                                                                                          // Range 12
                    Vector v = dir.clone().add(spread).normalize().multiply(0.8); // Faster

                    spawnNoteProjectile(p, loc.clone(), v, Color.AQUA, false);
                }

                if (ticks % 5 == 0) {
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);
                }

                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L));
    }

    private void useBlackCd(Player p) {
        if (checkCooldown(p, cooldownBlack, 10))
            return;
        setCooldown(p, cooldownBlack);

        broadcast(p, "§8뚜비 : §f널 처음 본 순간");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.5f);

        // Giant Note Projectile (Parabolic)
        // Use ItemDisplay riding an ArmorStand (or just invisible armorstand with item
        // on head)
        // ArmorStand is affected by gravity automatically.
        Location spawnLoc = p.getEyeLocation().add(p.getEyeLocation().getDirection());
        ArmorStand as = p.getWorld().spawn(spawnLoc, ArmorStand.class, entity -> {
            entity.setInvisible(true);
            entity.setMarker(false); // Gravity needed
            entity.setGravity(true);
            entity.setSmall(true);
            entity.setVelocity(p.getEyeLocation().getDirection().multiply(1.5)); // Launch force 30 blocks range implied
            entity.getEquipment().setHelmet(new ItemStack(Material.NOTE_BLOCK)); // Fallback visual
        });

        // ItemDisplay for visual scale
        ItemDisplay display = p.getWorld().spawn(spawnLoc, ItemDisplay.class, entity -> {
            ItemStack stack = new ItemStack(Material.IRON_NUGGET);
            ItemMeta meta = stack.getItemMeta();
            meta.setCustomModelData(3); // Black CD
            stack.setItemMeta(meta);
            entity.setItemStack(stack);
            entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(3f, 3f, 3f),
                    new AxisAngle4f(0, 0, 0, 1)));
            entity.setBillboard(Display.Billboard.CENTER); // Always face player? Or FIXED?
            // Notes usually are 2D, but ItemDisplay is 3D.
            // Let's use Billboard.CENTER to make it look like a floating sprite.
        });
        as.addPassenger(display);

        registerSummon(p, as);
        registerSummon(p, display);

        // Tracking Task
        registerTask(p, new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (as.isDead() || as.isOnGround() || ticks > 60) { // Max 3s or hit ground
                    explode(as.getLocation());
                    as.remove();
                    display.remove();
                    this.cancel();
                    return;
                }

                // Correlation: Check collision
                for (Entity e : as.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (e != p && e instanceof LivingEntity) {
                        explode(as.getLocation());
                        as.remove();
                        display.remove();
                        this.cancel();
                        return;
                    }
                }

                // Sound
                as.getWorld().playSound(as.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 2.0f, 0.5f);
                as.getWorld().spawnParticle(Particle.NOTE, as.getLocation().add(0, 0.5, 0), 5, 0.5, 0.5, 0.5, 1); // Black
                                                                                                                  // notes?
                                                                                                                  // Note
                                                                                                                  // particle
                                                                                                                  // color
                                                                                                                  // is
                                                                                                                  // random
                                                                                                                  // unless
                                                                                                                  // Color.BLACK?
                // Particle.NOTE color is controlled by offsetX (0-24)

                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L));
    }

    private void explode(Location loc) {
        loc.getWorld().createExplosion(loc, 2.0f, false, false); // Visual explosion
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        for (Entity e : loc.getWorld().getNearbyEntities(loc, 4, 4, 4)) {
            if (e instanceof LivingEntity le) {
                le.damage(15);
            }
        }
    }

    private void spawnNoteProjectile(Player owner, Location start, Vector velocity, Color color, boolean isPink) {
        // Simulated projectile
        new BukkitRunnable() {
            Location current = start.clone();
            Vector v = velocity.clone();
            int life = 0;
            int maxLife = isPink ? 10 : 30; // 4 blocks vs 12 blocks approx (Velocity dependent)
            // Range 4 for Pink: if speed ~0.5 -> 8 ticks?
            // Pink falls "immediately" after 4 blocks.
            // Blue flies 12 blocks.

            @Override
            public void run() {
                if (life >= maxLife || !owner.isOnline()) {
                    this.cancel();
                    return;
                }

                // Gravity for Pink after range? Prompt: "4 blocks range, then quickly falls".
                // We simulate gravity per tick.
                if (isPink && life > 8) { // After ~4 blocks
                    v.setY(v.getY() - 0.2); // Heavy gravity
                } else {
                    v.setY(v.getY() - 0.03); // Slight gravity default
                }

                current.add(v);

                // Collision
                if (current.getBlock().getType().isSolid()) {
                    this.cancel();
                    return;
                }

                // Entity Hit
                for (Entity e : current.getWorld().getNearbyEntities(current, 0.5, 0.5, 0.5)) {
                    if (e != owner && e instanceof LivingEntity) {
                        LivingEntity le = (LivingEntity) e;
                        applyEffect(le, isPink);
                        this.cancel();
                        return;
                    }
                }

                // Particle
                Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f); // Use dust for color?
                // Prompt says "Pink Note", "Sky Blue Note".
                // Particle.NOTE has limited colors (0-24 index).
                // Dust is better for specific colors, but shape is dot.
                // Let's use Particle.NOTE and try to control color, OR Particle.DUST for trail
                // and spawn a NOTE occasionally.
                // Actually, Packet or special parameter sets Note color.
                // In Spigot `spawnParticle(Particle.NOTE, loc, 0, R, G, B, 1)` uses RGB as
                // offset for Note color index?
                // Note color is offset X (div 24).
                // Let's just use Dust to ensure color match, or Spell Mob which is swirly.
                // Prompt: "Pink note appears above head".
                // I'll use Dust for the projectile trail and trigger the effect on hit.

                current.getWorld().spawnParticle(Particle.DUST, current, 1, 0, 0, 0, 0, dust);
                if (life % 2 == 0) {
                    // Try to spawn a note particle
                    double noteColor = isPink ? 6.0 / 24.0 : 14.0 / 24.0; // Approx indices
                    current.getWorld().spawnParticle(Particle.NOTE, current, 0, noteColor, 0, 0, 1);
                }

                life++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void applyEffect(LivingEntity target, boolean isPink) {
        if (isPink) {
            // Regen 2 (5s)
            target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
            // Visual Note above head
            target.getWorld().spawnParticle(Particle.NOTE, target.getEyeLocation().add(0, 0.5, 0), 5, 0.5, 0.5, 0.5);
            target.sendMessage("§d♪ 이렇게 좋은 날 ♪");
        } else {
            // Frozen + Slow 2 (5s)
            target.setFreezeTicks(100); // 5 sec freeze
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            target.getWorld().spawnParticle(Particle.NOTE, target.getEyeLocation().add(0, 0.5, 0), 5, 0.5, 0.5, 0.5);
            target.sendMessage("§b♪ 그 겨울, 우리 ♪");
        }
    }

    private boolean checkCooldown(Player p, Map<UUID, Long> cooldownMap, int seconds) {
        if (cooldownMap.containsKey(p.getUniqueId())) {
            long now = System.currentTimeMillis();
            long endTime = cooldownMap.get(p.getUniqueId()) + (seconds * 1000L);
            if (now < endTime) {
                double left = (endTime - now) / 1000.0;
                p.sendActionBar(
                        net.kyori.adventure.text.Component.text("§c쿨타임이 " + String.format("%.1f", left) + "초 남았습니다."));
                return true;
            }
        }
        return false;
    }

    private void setCooldown(Player p, Map<UUID, Long> cooldownMap) {
        cooldownMap.put(p.getUniqueId(), System.currentTimeMillis());
    }

    // Broadcast message helper since it's used multiple times
    private void broadcast(Player p, String msg) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(msg);
        }
    }

    @Override
    public void cleanup(Player p) {
        if (activeTasks != null) {
            // Basic Ability cleanup clears this, but specific logic check
        }
        cooldownPink.remove(p.getUniqueId());
        cooldownBlue.remove(p.getUniqueId());
        cooldownBlack.remove(p.getUniqueId());
        super.cleanup(p); // Clears activeTasks and activeEntities
    }
}
