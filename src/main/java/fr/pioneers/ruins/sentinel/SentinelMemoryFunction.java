package fr.pioneers.ruins.sentinel;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.ruins.LoreIndex;
import fr.pioneers.ruins.LoreItem;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : fonction de butin {@code pioneers:sentinel_memory} : le Module de memoire lache par une Sentinelle porte une
 * entree « servitor » du Codex, de preference non lue par le tueur (dernier joueur qui l'a frappee). Sans entree disponible, rien ne
 * tombe (un module vide ne se lit pas).
 */
public class SentinelMemoryFunction extends LootItemConditionalFunction {

    protected SentinelMemoryFunction(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ItemStack run(@NotNull ItemStack stack, @NotNull LootContext ctx) {
        Set<String> read = Set.of();
        Entity killer = ctx.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
        if (killer == null) {
            killer = ctx.getParamOrNull(LootContextParams.KILLER_ENTITY);
        }
        if (killer instanceof Player p) {
            read = PioneerCap.get(p).loreRead;
        }
        String id = LoreIndex.pick("servitor", read, ctx.getRandom());
        return id == null ? ItemStack.EMPTY : LoreItem.page(id, true);
    }

    @Override
    public @NotNull LootItemFunctionType getType() {
        return SentinelTypes.MEMORY.get();
    }

    public static final class Serializer extends LootItemConditionalFunction.Serializer<SentinelMemoryFunction> {

        @Override
        public void serialize(@NotNull JsonObject json, @NotNull SentinelMemoryFunction f, @NotNull JsonSerializationContext ctx) {
            super.serialize(json, f, ctx);
        }

        @Override
        public @NotNull SentinelMemoryFunction deserialize(@NotNull JsonObject json, @NotNull JsonDeserializationContext ctx,
              LootItemCondition @NotNull [] conditions) {
            return new SentinelMemoryFunction(conditions);
        }
    }
}
