package fr.pioneers.space.research;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.data.ItemBiomeDataCell;
import fr.pioneers.data.SpaceBiomes;
import fr.pioneers.registries.PiItems;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.SolarSystem;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Scanner de surface : maintenu trois secondes, il enregistre le biome d'astre ou se tient le joueur sur une cellule de donnees vierge
 * (les biomes terrestres ne s'enregistrent pas). Placees dans un analyseur de biomes, les cellules produisent les donnees de leur biome ;
 * recues par la table de recherche, elles valident l'exploration (recherche de surface, qui ouvre la geologie).
 */
public class ItemSurfaceScanner extends Item {

    public static final int SCAN_TICKS = 60;

    public ItemSurfaceScanner() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return SCAN_TICKS;
    }

    @NotNull
    @Override
    public UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BRUSH;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4F, 1.8F);
        }
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity entity, @NotNull ItemStack stack, int remaining) {
        if (!level.isClientSide && remaining % 15 == 0) {
            level.playSound(null, entity.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.PLAYERS, 0.35F,
                  1.2F + (SCAN_TICKS - remaining) / (float) SCAN_TICKS);
        }
    }

    @NotNull
    @Override
    public ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity) {
        if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player) {
            scan(serverLevel, player);
            player.getCooldowns().addCooldown(this, 20);
        }
        return stack;
    }

    private static void scan(ServerLevel level, ServerPlayer player) {
        Body body = SolarSystem.fromDimension(level.dimension());
        if (body == null) {
            message(player, Component.translatable("item.pioneers.surface_scanner.nothing").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        Optional<ResourceKey<Biome>> key = level.getBiome(player.blockPosition()).unwrapKey();
        if (key.isEmpty()) {
            return;
        }
        String biome = key.get().location().toString();
        Component biomeName = ItemBiomeDataCell.biomeName(biome);
        //Seuls les biomes des astres donnent une cellule (pas les biomes terrestres)
        if (SpaceBiomes.bodyOf(biome) == null) {
            message(player, Component.translatable("item.pioneers.surface_scanner.not_space", biomeName).withStyle(ChatFormatting.GRAY), false);
            return;
        }
        //Astre de recherche : on evite d'enregistrer un biome deja archive (la Terre n'a pas de recherche de surface)
        ResearchNode node = BodyResearch.node(body, ResearchNode.Kind.SURFACE);
        if (node != null) {
            ResearchState state = ResearchState.get(level.getServer());
            if (state.isCompleted(node)) {
                message(player, Component.translatable("item.pioneers.surface_scanner.done", Component.translatable(body.getTranslationKey()))
                      .withStyle(ChatFormatting.GRAY), false);
                return;
            } else if (state.isScanned(node, biome)) {
                message(player, Component.translatable("item.pioneers.surface_scanner.already", biomeName, state.getSamples(node, 0), node.units)
                      .withStyle(ChatFormatting.YELLOW), false);
                return;
            }
        }
        //Il faut une cellule de donnees vierge dans l'inventaire
        Inventory inventory = player.getInventory();
        int blank = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (ItemBiomeDataCell.isBlank(inventory.getItem(i))) {
                blank = i;
                break;
            }
        }
        if (blank < 0 && !player.getAbilities().instabuild) {
            message(player, Component.translatable("item.pioneers.surface_scanner.no_cell").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (blank >= 0 && !player.getAbilities().instabuild) {
            inventory.getItem(blank).shrink(1);
        }
        ItemStack cell = ItemBiomeDataCell.recorded(PiItems.BIOME_DATA_CELL.get(), body, biome);
        if (!inventory.add(cell)) {
            player.drop(cell, false);
        }
        message(player, Component.translatable("item.pioneers.surface_scanner.recorded", biomeName).withStyle(ChatFormatting.AQUA), true);
    }

    private static void message(ServerPlayer player, Component text, boolean success) {
        player.displayClientMessage(text, true);
        player.playNotifySound(success ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.PLAYERS, 0.8F, success ? 1.4F : 0.7F);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers.surface_scanner.desc");
        tips.hint("item.pioneers.surface_scanner.desc2");
    }
}
