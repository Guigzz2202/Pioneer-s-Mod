package fr.pioneers.tech.forge;

import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.tech.forge.client.HeatedMetalItemRenderer;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.forge.PiForge;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Metal chauffe (objet generique, voir {@link HeatedMetal} pour le NBT et l'API) : nom selon l'etat (lingot, piece en forge, piece a
 * tremper), infobulle avec la temperature et sa barre de couleur, teinte selon la temperature (client, {@code ForgeClientSetup}).
 * <p>
 * Comportements : brule le joueur qui le tient en main principale au-dessus de 400 degres sans pince a creuset en main secondaire ; jete dans
 * l'eau, il est trempe (piece finie, ou lingot) ; refroidi a l'air, un lingot chauffe brut redevient lingot.
 */
public class HeatedMetalItem extends Item {

    private static final String BAR = "█";
    private static final int BAR_CELLS = 12;
    private static final int BAR_MAX_C = 1600;

    public HeatedMetalItem(Properties properties) {
        super(properties);
    }

    /** 1.0.5-beta (lot 15) : l'objet vise, teinte par la chaleur ({@link HeatedMetal#lookOf}, rendu client). */
    @Override
    public void initializeClient(@NotNull Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return HeatedMetalItemRenderer.instance();
            }
        });
    }

    @NotNull
    @Override
    public Component getName(@NotNull ItemStack stack) {
        String metal = HeatedMetal.metal(stack);
        Component name = Metals.nameOrMixed(metal);
        ItemStack part = HeatedMetal.shape(stack);
        if (!part.isEmpty()) {
            return Component.translatable("item.pioneers.heated_metal.part", part.getHoverName());
        }
        if (HeatedMetal.hasWork(stack)) {
            return Component.translatable("item.pioneers.heated_metal.work", name);
        }
        return Component.translatable("item.pioneers.heated_metal.ingot", name);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        String metal = HeatedMetal.metal(stack);
        int mb = HeatedMetal.amount(stack);
        float t = HeatedMetal.temperatureNow(stack, level);
        tips.spec("tooltip.pioneers.heated_metal.amount", mb, HeatedMetal.pixels(stack));
        tips.custom(Component.translatable("tooltip.pioneers.heated_metal.temperature", Math.round(t))
              .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(t > 450 ? ForgeMetals.glow(t) & 0xFFFFFF : 0xAAAAAA))));
        tips.custom(bar(t, ForgeMetals.workingC(metal)));
        if (!metal.isEmpty()) {
            boolean workable = t >= ForgeMetals.workingC(metal);
            tips.custom(Component.translatable(workable ? "tooltip.pioneers.heated_metal.workable" : "tooltip.pioneers.heated_metal.too_cold",
                  ForgeMetals.workingC(metal)).withStyle(workable ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        ItemStack part = HeatedMetal.shape(stack);
        if (!part.isEmpty()) {
            tips.state("tooltip.pioneers.heated_metal.quench", part.getHoverName());
        }
        if (t > ForgeMetals.BURN_C) {
            tips.warn("tooltip.pioneers.heated_metal.burns");
        }
    }

    /** Barre de 12 cases de 0 a 1 600 degres, colorees a leur temperature ; le seuil de travail est marque. */
    private static Component bar(float t, int workingC) {
        MutableComponent out = Component.literal("");
        int mark = Math.min(BAR_CELLS - 1, workingC * BAR_CELLS / BAR_MAX_C);
        for (int i = 0; i < BAR_CELLS; i++) {
            float cellC = (i + 0.5F) * BAR_MAX_C / BAR_CELLS;
            boolean filled = cellC <= t;
            int colour = filled ? (cellC > 450 ? ForgeMetals.glow(cellC) & 0xFFFFFF : 0x777777) : 0x333333;
            out.append(Component.literal(i == mark ? "┃" : BAR).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(i == mark ? 0xFFFFFF : colour))));
        }
        return out;
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 20 != Math.floorMod(entity.getId(), 20) || !(entity instanceof Player player)) {
            return;
        }
        //Brulure : en main principale, chaud, sans pince en main secondaire
        if (selected && player.getMainHandItem() == stack && HeatedMetal.burnsHand(stack, level) && !player.isCreative()
              && !player.getOffhandItem().is(PiForge.CRUCIBLE_TONGS.get())) {
            player.setSecondsOnFire(2);
        }
        //Refroidi a l'air : un lingot chauffe brut redevient lingot (on retrouve la pile par identite : l'indice de case depend du compartiment)
        if (!HeatedMetal.isHot(stack, level)) {
            ItemStack cold = HeatedMetal.coldForm(stack);
            if (cold != stack) {
                var inv = player.getInventory();
                for (var list : List.of(inv.items, inv.offhand)) {
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i) == stack) {
                            list.set(i, cold);
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        Level level = entity.level();
        if (level.isClientSide || entity.tickCount % 10 != 0) {
            return false;
        }
        //Chaude, ou piece finie refroidie sans trempe : l'eau donne la piece (rien ne reste bloque)
        if (entity.isInWater() && (HeatedMetal.isHot(stack, level) || !HeatedMetal.shape(stack).isEmpty())) {
            ItemStack result = HeatedMetal.quench(stack, level);
            result.setCount(result.getCount() * Math.max(1, stack.getCount()));
            entity.setItem(result);
            for (ItemStack extra : HeatedMetal.quenchExtras(stack)) {
                level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), extra.copy()));
            }
            hiss((ServerLevel) level, entity.getX(), entity.getY(), entity.getZ());
            return false;
        }
        if (!HeatedMetal.isHot(stack, level)) {
            ItemStack cold = HeatedMetal.coldForm(stack);
            if (cold != stack) {
                entity.setItem(cold.copyWithCount(cold.getCount() * Math.max(1, stack.getCount())));
            }
        }
        return false;
    }

    /** Pschitt et vapeur de la trempe (a appeler par tout ce qui trempe une piece). */
    public static void hiss(ServerLevel level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.4F + level.random.nextFloat() * 0.3F);
        level.sendParticles(ParticleTypes.CLOUD, x, y + 0.2, z, 6, 0.15, 0.1, 0.15, 0.02);
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return false;
    }
}
