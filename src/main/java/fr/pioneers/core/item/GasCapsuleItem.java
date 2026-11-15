package fr.pioneers.core.item;

import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.core.Action;
import fr.pioneers.core.gas.GasAccess;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Capsule de gaz : l'equivalent du seau pour les gaz. Vide, ou pleine d'exactement {@link #CAPACITY} mB (1 B) d'un gaz ; piles de 16.
 * Clic droit sur un bloc a reservoir de gaz (Pioneer's ou Mekanism) : une capsule vide preleve 1 B, une capsule pleine le verse (tout ou rien).
 * Le gaz est dans le NBT ({@code Gas} = identifiant) : deux capsules du meme gaz s'empilent. Apparence : predicat {@code pioneers:filled}
 * (modele gas_capsule_filled) et couche 1 teintee par la couleur du gaz (PiClientRegistration).
 */
public class GasCapsuleItem extends Item {

    public static final long CAPACITY = 1000;
    /**
     * Gaz dont la capsule a sa propre texture animee (non teintee) : rang + 1 = valeur du predicat {@code pioneers:gas_kind}
     * (modeles gas_capsule_filled_<gaz> et overrides ecrits par tools/gc/core/gas_capsule.py).
     */
    public static final List<ResourceLocation> ANIMATED = List.of(Pioneers.rl("void_singularity"),
          Pioneers.rl("stellar_plasma"));
    private static final String TAG = "Gas";

    public GasCapsuleItem() {
        super(new Item.Properties().stacksTo(16));
    }

    /** Gaz contenu, ou null si vide. */
    @Nullable
    public static Gas getGas(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG));
        return id == null ? null : PioneersAPI.gas(id);
    }

    /** Valeur du predicat {@code gas_kind} : 0 = gaz ordinaire (couche teintee), n = n-ieme gaz anime de {@link #ANIMATED}. */
    public static int gasKind(ItemStack stack) {
        Gas gas = getGas(stack);
        return gas == null ? 0 : ANIMATED.indexOf(gas.getRegistryName()) + 1;
    }

    /** Une capsule pleine de ce gaz. */
    public static ItemStack filled(Item capsule, Gas gas) {
        ItemStack stack = new ItemStack(capsule);
        stack.getOrCreateTag().putString(TAG, gas.getRegistryName().toString());
        return stack;
    }

    /** Avant le clic du bloc (sinon un reservoir ouvrirait son interface) : transfert si possible, sinon le bloc reagit normalement. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return transfer(context);
    }

    private InteractionResult transfer(@NotNull UseOnContext context) {
        Level level = context.getLevel();
        BlockEntity tile = level.getBlockEntity(context.getClickedPos());
        IGasHandler handler = GasAccess.find(tile, context.getClickedFace());
        Player player = context.getPlayer();
        if (handler == null || player == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        Gas gas = getGas(stack);
        ItemStack result;
        if (gas == null) {
            GasStack taken = handler.extractGas(CAPACITY, Action.SIMULATE);
            if (taken.isEmpty() || taken.getAmount() < CAPACITY) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                handler.extractGas(taken.copyWithAmount(CAPACITY), Action.EXECUTE);
            }
            result = filled(this, taken.getGas());
        } else {
            GasStack rest = handler.insertGas(GasStack.of(gas, CAPACITY), Action.SIMULATE);
            if (!rest.isEmpty()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                handler.insertGas(GasStack.of(gas, CAPACITY), Action.EXECUTE);
            }
            result = new ItemStack(this);
        }
        if (!level.isClientSide) {
            level.playSound(null, context.getClickedPos(), gas == null ? SoundEvents.BOTTLE_FILL : SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8F, 1.2F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @NotNull
    @Override
    public Component getName(@NotNull ItemStack stack) {
        Gas gas = getGas(stack);
        return gas == null ? super.getName(stack) : Component.translatable("item.pioneers.gas_capsule_filled", gas.getName());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        Gas gas = getGas(stack);
        if (gas == null) {
            tips.desc("tooltip.pioneers.gas_capsule.empty");
        } else {
            tips.spec("tooltip.pioneers.gas_capsule.content", gas.getName(), CAPACITY);
        }
    }
}
