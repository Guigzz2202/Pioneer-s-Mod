package fr.pioneers.tech.industry;

import fr.pioneers.Pioneers;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.industry.multi.RadiatorTile;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.buffer.BufferTile;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Kits d'amelioration : clic droit avec le kit du tier suivant sur une machine a tier ({@code <tier>_<machine>} : kit de machines, machines
 * d'atelier, radiateurs, buffers) la remplace par le tier superieur sans perdre son contenu (cases, reservoirs, energie, reglages des faces).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Upgrades {

    private Upgrades() {
    }

    /** Kit d'amelioration vers un tier (avance, elite, ultime). */
    public static class KitItem extends Item {

        public final CableTier tier;

        public KitItem(CableTier tier, Properties properties) {
            super(properties);
            this.tier = tier;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            Tooltips tips = Tooltips.of(tooltip);
            tips.desc("tooltip.pioneers.upgrade_kit", Component.translatable("industry.pioneers.tier." + tier.id()));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!(held.getItem() instanceof KitItem kit) || kit.tier.ordinal() == 0) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        CableTier from = CableTier.values()[kit.tier.ordinal() - 1];
        if (id == null || !id.getPath().startsWith(from.id() + "_")) {
            return;
        }
        Block next = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), kit.tier.id() + id.getPath().substring(from.id().length())));
        BlockEntity tile = level.getBlockEntity(pos);
        if (next == null || next == Blocks.AIR || !(tile instanceof MachineTile
                                                                                 || tile instanceof RadiatorTile || tile instanceof BufferTile)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) {
            return;
        }
        CompoundTag saved = tile.saveWithoutMetadata();
        //Les cases sont videes avant le remplacement : rien ne tombe au sol, tout revient dans le nouveau bloc
        List<ItemSlot> slots = tile instanceof MachineTile machine ? machine.guiSlots()
              : tile instanceof BufferTile buffer ? buffer.getIoSlots() : List.of();
        slots.forEach(slot -> slot.setStack(ItemStack.EMPTY));
        BlockState upgraded = copy(state, next.defaultBlockState());
        level.setBlock(pos, upgraded, Block.UPDATE_ALL);
        BlockEntity fresh = level.getBlockEntity(pos);
        if (fresh != null) {
            fresh.load(saved);
            fresh.setChanged();
        }
        Player player = event.getEntity();
        //1.9 (MAJ RP) : l'Ingenierie epargne parfois le kit (les pieces de l'ancien tier sont reemployees)
        double spare = fr.pioneers.core.profile.PioneerApi.modifier(player, "engineering.kit_saving");
        if (!player.getAbilities().instabuild && !(spare > 0 && level.random.nextDouble() < spare)) {
            held.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.1F);
        player.displayClientMessage(Component.translatable("industry.pioneers.upgraded", next.getName()).withStyle(ChatFormatting.GREEN), true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copy(BlockState from, BlockState to) {
        for (Property property : from.getProperties()) {
            if (to.hasProperty(property)) {
                to = to.setValue(property, from.getValue(property));
            }
        }
        return to;
    }
}
