package fr.pioneers.ruins;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerCap;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : fonctions de butin des ruines. {@code pioneers:lore_entry} {palier} : page de lore (ou module) d'une entree non lue
 * de preference ; {@code pioneers:parchment_note} : une note de parchemin ; {@code pioneers:site_scrap} {tier} : scrap de la categorie
 * dominante du site (origine du butin) ; {@code pioneers:service_code} : Note de service du code de la console du site.
 */
public final class RuinLoot {

    public static final DeferredRegister<LootItemFunctionType> FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Pioneers.MODID);
    public static final RegistryObject<LootItemFunctionType> LORE_ENTRY = FUNCTIONS.register("lore_entry",
          () -> new LootItemFunctionType(new Ser<>(LoreEntry::new)));
    public static final RegistryObject<LootItemFunctionType> NOTE = FUNCTIONS.register("parchment_note",
          () -> new LootItemFunctionType(new Ser<>(Note::new)));
    public static final RegistryObject<LootItemFunctionType> SITE_SCRAP = FUNCTIONS.register("site_scrap",
          () -> new LootItemFunctionType(new Ser<>(SiteScrap::new)));
    public static final RegistryObject<LootItemFunctionType> SERVICE_CODE = FUNCTIONS.register("service_code",
          () -> new LootItemFunctionType(new Ser<>(ServiceCode::new)));

    private RuinLoot() {
    }

    interface Factory<F extends Base> {

        F make(LootItemCondition[] conditions, JsonObject json);
    }

    static final class Ser<F extends Base> extends LootItemConditionalFunction.Serializer<F> {

        private final Factory<F> factory;

        Ser(Factory<F> factory) {
            this.factory = factory;
        }

        @Override
        public void serialize(@NotNull JsonObject json, @NotNull F f, @NotNull JsonSerializationContext ctx) {
            super.serialize(json, f, ctx);
            f.write(json);
        }

        @Override
        public @NotNull F deserialize(@NotNull JsonObject json, @NotNull JsonDeserializationContext ctx, LootItemCondition @NotNull [] conditions) {
            return factory.make(conditions, json);
        }
    }

    abstract static class Base extends LootItemConditionalFunction {

        protected Base(LootItemCondition[] conditions) {
            super(conditions);
        }

        void write(JsonObject json) {
        }

        static Set<String> read(LootContext ctx, boolean notes) {
            Entity e = ctx.getParamOrNull(LootContextParams.THIS_ENTITY);
            if (e instanceof Player p) {
                return notes ? PioneerCap.get(p).notesRead : PioneerCap.get(p).loreRead;
            }
            return Set.of();
        }

        static RuinSites.Site site(LootContext ctx) {
            Vec3 o = ctx.getParamOrNull(LootContextParams.ORIGIN);
            if (o == null) {
                return null;
            }
            ServerLevel level = ctx.getLevel();
            return RuinSites.get(level).siteAt(BlockPos.containing(o), 8);
        }
    }

    static final class LoreEntry extends Base {

        private final String tier;

        LoreEntry(LootItemCondition[] conditions, JsonObject json) {
            super(conditions);
            tier = GsonHelper.getAsString(json, "tier", "mk1");
        }

        @Override
        void write(JsonObject json) {
            json.addProperty("tier", tier);
        }

        @Override
        protected @NotNull ItemStack run(@NotNull ItemStack stack, @NotNull LootContext ctx) {
            String id = LoreIndex.pick(tier, read(ctx, false), ctx.getRandom());
            if (id == null) {
                return ItemStack.EMPTY;
            }
            return LoreItem.page(id, "servitor".equals(tier) && stack.is(RuinItems.MEMORY_MODULE.get())).copyWithCount(1);
        }

        @Override
        public @NotNull LootItemFunctionType getType() {
            return LORE_ENTRY.get();
        }
    }

    static final class Note extends Base {

        Note(LootItemCondition[] conditions, JsonObject json) {
            super(conditions);
        }

        @Override
        protected @NotNull ItemStack run(@NotNull ItemStack stack, @NotNull LootContext ctx) {
            String id = LoreIndex.pickNote(read(ctx, true), ctx.getRandom());
            return id == null ? ItemStack.EMPTY : LoreItem.note(id);
        }

        @Override
        public @NotNull LootItemFunctionType getType() {
            return NOTE.get();
        }
    }

    static final class SiteScrap extends Base {

        private final int tier;

        SiteScrap(LootItemCondition[] conditions, JsonObject json) {
            super(conditions);
            tier = GsonHelper.getAsInt(json, "tier", 1);
        }

        @Override
        void write(JsonObject json) {
            json.addProperty("tier", tier);
        }

        @Override
        protected @NotNull ItemStack run(@NotNull ItemStack stack, @NotNull LootContext ctx) {
            RuinSites.Site s = site(ctx);
            ScrapCategory c = s == null ? ScrapCategory.STRUCTURE : RuinCatalog.category(s.machine);
            return new ItemStack(RuinItems.scrap(c, s == null ? tier : s.tier), stack.getCount());
        }

        @Override
        public @NotNull LootItemFunctionType getType() {
            return SITE_SCRAP.get();
        }
    }

    static final class ServiceCode extends Base {

        ServiceCode(LootItemCondition[] conditions, JsonObject json) {
            super(conditions);
        }

        @Override
        protected @NotNull ItemStack run(@NotNull ItemStack stack, @NotNull LootContext ctx) {
            RuinSites.Site s = site(ctx);
            if (s == null) {
                return ItemStack.EMPTY;
            }
            ItemStack page = LoreItem.page(LoreItem.SERVICE_CODE, false);
            page.getOrCreateTag().putInt("code", s.code);
            page.getOrCreateTag().putLong("site", s.id);
            return page;
        }

        @Override
        public @NotNull LootItemFunctionType getType() {
            return SERVICE_CODE.get();
        }
    }
}
