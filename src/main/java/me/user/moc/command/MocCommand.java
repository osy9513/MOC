package me.user.moc.command;

import me.user.moc.ability.AbilityManager;
import me.user.moc.game.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MocCommand implements CommandExecutor {
    private final GameManager gameManager;
    private final AbilityManager abilityManager;

    public MocCommand(GameManager gameManager, AbilityManager abilityManager) {
        this.gameManager = gameManager;
        this.abilityManager = abilityManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "start":
                    if (player.isOp()) gameManager.startNewRound(player.getLocation());
                    break;
                case "stop":
                    if (player.isOp()) gameManager.stopGame();
                    break;
                case "yes":
                    gameManager.acceptAbility(player);
                    break;
                case "re":
                    abilityManager.rerollAbility(player);
                    break;
            }
            return true;
        }
        return false;
    }
}
