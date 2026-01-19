package me.user.moc.ability;

import me.user.moc.MocPlugin;
import me.user.moc.ability.impl.Magnus;
import me.user.moc.ability.impl.Midas;
import me.user.moc.ability.impl.Olaf;
import me.user.moc.ability.impl.Ueki;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class AbilityManager {
    private final MocPlugin plugin;
    private final Map<String, Ability> abilities = new HashMap<>();
    private final Map<UUID, String> playerAbilities = new HashMap<>();
    private final Map<UUID, Integer> rerollCounts = new HashMap<>();

    public AbilityManager(MocPlugin plugin) {
        this.plugin = plugin;
        registerAbilities();
    }

    private void registerAbilities() {
        // 능력 등록
        addAbility(new Ueki(plugin));
        addAbility(new Olaf(plugin));
        addAbility(new Midas(plugin));
        addAbility(new Magnus(plugin));
    }

    private void addAbility(Ability ability) {
        abilities.put(ability.getName(), ability);
    }

    public void resetAbilities() {
        playerAbilities.clear();
        rerollCounts.clear();
    }

    public void distributeAbilities() {
        List<String> abilityNames = new ArrayList<>(abilities.keySet());
        Collections.shuffle(abilityNames);

        int i = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String initialAbility = abilityNames.get(i % abilityNames.size());
            playerAbilities.put(p.getUniqueId(), initialAbility);
            rerollCounts.put(p.getUniqueId(), 2);
            showAbilityInfo(p, initialAbility);
            i++;
        }
    }

    public void showAbilityInfo(Player p, String abilityName) {
        Ability ability = abilities.get(abilityName);
        if (ability == null) return;

        p.sendMessage("§f ");
        p.sendMessage("§e=== §l능력 정보 §e===");
        p.sendMessage("§b" + ability.getName());
        for (String line : ability.getDescription()) {
            p.sendMessage(line);
        }
        p.sendMessage("§f ");
        p.sendMessage("§e능력 수락 : §a/moc yes");
        p.sendMessage("§e리롤(2회) : §c/moc re §7(소고기 15개 소모)");
        p.sendMessage("§e==================");
    }

    public void rerollAbility(Player p) {
        int left = rerollCounts.getOrDefault(p.getUniqueId(), 0);
        if (left <= 0) {
            p.sendMessage("§c[MOC] 리롤 횟수를 모두 사용했습니다.");
            return;
        }

        if (!p.getInventory().contains(Material.COOKED_BEEF, 15)) {
            p.sendMessage("§c[MOC] 소고기가 부족하여 리롤할 수 없습니다.");
            return;
        }

        p.getInventory().removeItem(new ItemStack(Material.COOKED_BEEF, 15));
        
        List<String> pool = new ArrayList<>(abilities.keySet());
        String newAbility = pool.get(new Random().nextInt(pool.size()));

        playerAbilities.put(p.getUniqueId(), newAbility);
        rerollCounts.put(p.getUniqueId(), left - 1);
        p.sendMessage("§e[MOC] §f능력이 교체되었습니다! 남은 리롤: §c" + (left - 1));
        showAbilityInfo(p, newAbility);
    }

    public void giveAbilityItems(Player p) {
        String abilityName = playerAbilities.get(p.getUniqueId());
        if (abilityName != null) {
            Ability ability = abilities.get(abilityName);
            if (ability != null) {
                ability.giveItem(p);
            }
        }
    }
    
    // 능력 이름으로 플레이어가 해당 능력을 가졌는지 확인
    public boolean hasAbility(Player p, String abilityName) {
        return abilityName.equals(playerAbilities.get(p.getUniqueId()));
    }
}
