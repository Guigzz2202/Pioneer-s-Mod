package fr.pioneers.core.vanilla;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/**
 * Tous les butins (coffres, blocs, creatures, peche, troc des piglins...) : objets vanilla remplaces par ceux du mod ; 1.0.5-beta (lot 40) :
 * objets retires des butins naturels ({@link VanillaReplacements#filterLoot} : redstone).
 */
public class ReplaceVanillaModifier extends LootModifier {

    public static final Codec<ReplaceVanillaModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, ReplaceVanillaModifier::new));

    public ReplaceVanillaModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        //1.0.5-beta (lot 40, retour 50) : la redstone des butins naturels est retiree (seulement par les foreuses), le reste converti
        VanillaReplacements.filterLoot(loot, context.getQueriedLootTableId());
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
