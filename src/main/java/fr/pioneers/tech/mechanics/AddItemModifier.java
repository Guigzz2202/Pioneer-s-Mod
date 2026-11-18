package fr.pioneers.tech.mechanics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

/**
 * Butin ajoute sous conditions (donnees : {@code data/pioneers/loot_modifiers/*.json}) : fibres vegetales des herbes coupees au couteau...
 * <pre>{"type": "pioneers:add_item", "conditions": [...], "item": "pioneers:plant_fiber", "min": 1, "max": 2, "chance": 0.6}</pre>
 */
public class AddItemModifier extends LootModifier {

    public static final Codec<AddItemModifier> CODEC = RecordCodecBuilder.create(i -> codecStart(i).and(i.group(
          ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(m -> m.item),
          Codec.INT.optionalFieldOf("min", 1).forGetter(m -> m.min),
          Codec.INT.optionalFieldOf("max", 1).forGetter(m -> m.max),
          Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(m -> m.chance)
    )).apply(i, AddItemModifier::new));

    private final Item item;
    private final int min, max;
    private final float chance;

    public AddItemModifier(LootItemCondition[] conditions, Item item, int min, int max, float chance) {
        super(conditions);
        this.item = item;
        this.min = min;
        this.max = Math.max(min, max);
        this.chance = chance;
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(@NotNull ObjectArrayList<ItemStack> loot, @NotNull LootContext context) {
        if (context.getRandom().nextFloat() < chance) {
            loot.add(new ItemStack(item, min + context.getRandom().nextInt(max - min + 1)));
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
