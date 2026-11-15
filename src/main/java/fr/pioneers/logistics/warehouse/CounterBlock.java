package fr.pioneers.logistics.warehouse;

import fr.pioneers.tech.bronze.BronzeBlock;
import fr.pioneers.tech.bronze.BronzeTile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * 1.0.4-beta (lot 1, Guillaume : « un comptoir d'entrepot relie aux cordes et poulies ; le joueur pourra demander les objets depuis ce
 * comptoir ») : COMPTOIR D'ENTREPOT, bloc oriente (facade vers le joueur) ; la poulie d'entrepot posee DESSUS en fait la station du reseau.
 * Clic droit : l'interface (liste de l'entrepot, demandes, cases de sortie). Casse : sortie, envois et colis en route (leur contenu) tombent.
 */
public class CounterBlock extends BronzeBlock {

    /** 1.0.5-beta (lot 17) : comptoir, interface d'entree ou de sortie (meme bloc, meme tile, role different). */
    public final CounterTile.Role role;

    public CounterBlock(Properties properties) {
        this(properties, CounterTile.Role.COUNTER);
    }

    public CounterBlock(Properties properties, CounterTile.Role role) {
        super(properties, role == CounterTile.Role.INLET ? CounterTile::inlet : role == CounterTile.Role.OUTLET ? CounterTile::outlet : CounterTile::new,
              Block.box(0, 0, 0, 16, 16, 16));
        this.role = role;
    }

    /**
     * 1.0.5-beta (lot 17) : l'interface de sortie posee CONTRE un inventaire (machine, coffre, tuyau : clic sur sa face) le regarde : sa
     * goulotte y pousse les objets livres. Sinon (et pour le comptoir et l'entree), facade vers le joueur.
     */
    @Override
    public net.minecraft.world.level.block.state.BlockState getStateForPlacement(@org.jetbrains.annotations.NotNull net.minecraft.world.item.context.BlockPlaceContext context) {
        net.minecraft.world.level.block.state.BlockState state = super.getStateForPlacement(context);
        net.minecraft.core.Direction face = context.getClickedFace();
        if (role == CounterTile.Role.OUTLET && state != null && face.getAxis().isHorizontal() && !context.replacingClickedOnBlock()) {
            //Le bloc clique est juste derriere la face cliquee
            net.minecraft.world.level.block.entity.BlockEntity be = context.getLevel().getBlockEntity(context.getClickedPos().relative(face.getOpposite()));
            if (be != null && be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, face).isPresent()) {
                return state.setValue(FACING, face.getOpposite());
            }
        }
        return state;
    }

    @Override
    protected void onBroken(Level level, BlockPos pos, BronzeTile tile) {
        WarehouseNetwork.changed();
    }

    /**
     * 1.0.4-beta (lot 34, Guillaume : « mon comptoir ne se connecte pas au reseau ») : a la pose, le comptoir dit tout de suite ce qui lui
     * manque s'il n'a pas de petite poulie au-dessus (barre d'action) ; le detail est dans son onglet Infos.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
          @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        WarehouseNetwork.changed();
        if (!level.isClientSide && placer instanceof net.minecraft.world.entity.player.Player player && WarehouseLines.pulleyAbove(level, pos) == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(role == CounterTile.Role.COUNTER ? "warehouse.pioneers.counter.place_hint"
                  : "warehouse.pioneers.io.place_hint", WarehouseLines.MAX_DROP)
                  .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        }
    }
}
