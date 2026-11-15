package fr.pioneers.tech.forge.smithing;

import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.tech.forge.ForgeBlock;
import fr.pioneers.tech.forge.ForgeTile;
import fr.pioneers.core.registries.PiForge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 1.0.2-beta (lot 2) : porte-outils ({@link ToolRackBlock}) : trois emplacements d'UN outil chacun (objet non empilable), de gauche a droite
 * vus de face. Clic droit : avec un outil, il se range dans l'emplacement vise s'il est libre (sinon le premier libre) ; main vide, on
 * reprend l'outil vise (sinon le dernier range). Contenu lache a la casse, entonnoirs acceptes (cases {@code slots}, format des tuiles de la
 * forge). Client : les trois piles ({@code tools}), dessinees par {@code ToolRackRenderer}.
 */
public class ToolRackTile extends ForgeTile {

    public static final int SLOTS = 3;
    /** Abscisse (px, rack tourne vers le sud) du centre de chaque emplacement (1.0.3-beta lot 14 : 3 / 8 / 13, {@link ToolRackLayout}). */
    public static final float[] SLOT_X = ToolRackLayout.SLOT_X;
    private final List<ItemSlot> tools = new ArrayList<>();

    public ToolRackTile(BlockPos pos, BlockState state) {
        super(PiForge.TOOL_RACK_TILE.get(), pos, state);
        for (int i = 0; i < SLOTS; i++) {
            tools.add(slot(new ItemSlot(() -> 1, ToolRackTile::isTool, (s, a) -> true, (s, a) -> true, this::contentsChanged, 0, 0)));
        }
    }

    /** Un outil : un objet qui ne s'empile pas (outils, marteaux, pinces, limes, armes...). */
    public static boolean isTool(ItemStack stack) {
        return !stack.isEmpty() && stack.getMaxStackSize() == 1;
    }

    private void contentsChanged() {
        setChanged();
        markClient();
    }

    public ItemStack tool(int i) {
        return tools.get(i).getStack();
    }

    public int count() {
        int n = 0;
        for (ItemSlot slot : tools) {
            if (!slot.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** Emplacement vise (0..2) d'apres le point clique, rack tourne selon sa facade. */
    static int aimed(BlockState state, BlockPos pos, Vec3 hit) {
        Direction facing = state.getValue(ForgeBlock.FACING);
        double x = hit.x - pos.getX(), z = hit.z - pos.getZ();
        //abscisse locale (de gauche a droite vu de face) : facade au sud = x monde
        double u = switch (facing) {
            case NORTH -> 1 - x;
            case EAST -> 1 - z;
            case WEST -> z;
            default -> x;
        };
        return Math.max(0, Math.min(SLOTS - 1, (int) Math.floor(u * SLOTS)));
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, BlockHitResult hit) {
        if (level == null) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        boolean client = level.isClientSide;
        int at = aimed(getBlockState(), worldPosition, hit.getLocation());
        if (isTool(held)) {
            int free = tools.get(at).isEmpty() ? at : -1;
            for (int i = 0; i < SLOTS && free < 0; i++) {
                if (tools.get(i).isEmpty()) {
                    free = i;
                }
            }
            if (free < 0) {
                return InteractionResult.PASS;
            }
            if (!client) {
                tools.get(free).setStack(held.split(1));
                level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (!held.isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        int take = !tools.get(at).isEmpty() ? at : -1;
        for (int i = SLOTS - 1; i >= 0 && take < 0; i--) {
            if (!tools.get(i).isEmpty()) {
                take = i;
            }
        }
        if (take < 0) {
            return InteractionResult.PASS;
        }
        if (!client) {
            player.setItemInHand(hand, tools.get(take).getStack().copy());
            tools.get(take).setStack(ItemStack.EMPTY);
            level.playSound(null, worldPosition, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.6F, 1.1F);
        }
        return InteractionResult.sidedSuccess(client);
    }

    @Override
    public void serverTick(ServerLevel level) {
        flushClient(level, 2);
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.put("tools", ItemSlots.save(tools));
    }

    @Override
    protected void readClient(CompoundTag tag) {
        ItemSlots.load(tools, tag.getList("tools", 10));
    }
}
