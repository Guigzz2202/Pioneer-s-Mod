package fr.pioneers.equipment.gear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.15 : butin des blocs mines avec un outil modulaire (donnees : {@code data/pioneers/loot_modifiers/modular_tool.json}, dernier de la
 * liste, apres le remplacement des butins vanilla) : fusion instantanee (chaque objet qui se fond au four sort fondu) puis teleporteur de
 * butin (droit dans l'inventaire ; ce qui n'y entre pas tombe au sol).
 */
public class GearLootModifier extends LootModifier {

    public static final Codec<GearLootModifier> CODEC = RecordCodecBuilder.create(i -> codecStart(i).apply(i, GearLootModifier::new));

    public GearLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(@NotNull ObjectArrayList<ItemStack> loot, @NotNull LootContext context) {
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (tool == null || !(tool.getItem() instanceof ModularToolItem) || !context.hasParam(LootContextParams.BLOCK_STATE)) {
            return loot;
        }
        if (Modules.level(tool, GearModule.SMELTING) > 0) {
            ObjectArrayList<ItemStack> out = new ObjectArrayList<>();
            for (ItemStack stack : loot) {
                Optional<SmeltingRecipe> recipe = context.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack),
                      context.getLevel());
                if (recipe.isPresent()) {
                    ItemStack result = recipe.get().getResultItem(context.getLevel().registryAccess()).copy();
                    if (!result.isEmpty()) {
                        result.setCount(Math.min(result.getMaxStackSize(), result.getCount() * stack.getCount()));
                        out.add(result);
                        continue;
                    }
                }
                out.add(stack);
            }
            loot = out;
        }
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (entity instanceof Player player && Modules.level(tool, GearModule.TELEPORT) > 0) {
            ObjectArrayList<ItemStack> left = new ObjectArrayList<>();
            for (ItemStack stack : loot) {
                if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                    left.add(stack);
                }
            }
            loot = left;
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
