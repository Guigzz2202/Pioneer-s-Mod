package fr.pioneers.bronze.bench;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * 1.12.0 (lot E4) : motifs de banniere a l'etabli du tanneur (il remplace le metier a tisser, qui n'est plus fabricable). Les motifs du
 * metier ne sont pas des recettes (banniere + teinture + motif choisi dans une liste, a appliquer sur une banniere deja ornee) : en faire
 * des recettes {@code pioneers:bench} demanderait des milliers de combinaisons et perdrait les motifs deja poses. L'etabli ouvre donc le
 * VRAI menu du metier ({@link LoomMenu} : meme type de menu, le client ouvre l'ecran vanilla) ; seule la condition « bloc encore la »
 * change : elle vise l'etabli du tanneur au lieu du metier.
 */
public class TannerLoomMenu extends LoomMenu {

    private final ContainerLevelAccess where;
    private final Block bench;

    public TannerLoomMenu(int id, Inventory inventory, ContainerLevelAccess where, Block bench) {
        super(id, inventory, where);
        this.where = where;
        this.bench = bench;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(where, player, bench);
    }

    public static void open(ServerPlayer player, Level level, BlockPos pos, Block bench) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TannerLoomMenu(id, inventory, ContainerLevelAccess.create(level, pos), bench),
              Component.translatable("gui.pioneers.bench.patterns_title")));
    }
}
