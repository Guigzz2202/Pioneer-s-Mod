package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.hemp.LitHempJointItem;
import fr.pioneers.farm.hemp.PiHemp;
import fr.pioneers.farm.storage.CropStorageBlock;
import fr.pioneers.farm.storage.GeneratedCropStorage;
import fr.pioneers.farm.storage.PiCropStorage;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.4-beta, lot 6 (GameTests) : paille, recoltes, tableaux, chanvre. Chaque bloc de stockage des recoltes existe, se pose oriente,
 * se casse en lui-meme, se compose (9 recoltes a l'etabli agricole) et se defait (9 sur la grille), et se composte ; un plant de chanvre mur
 * donne ses tetes (pas les autres cultures) ; une bouffee de joint donne ses trois effets moderes et met le joint au repos ; les quatre
 * elements sont des tableaux posables comme les 26 autres.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot6Tests {

    private PatchP104Lot6Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104f_storage", timeoutTicks = 40)
    public static void p104_6_blocs_de_recoltes(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = h.getLevel();
        Gt.equal(GeneratedCropStorage.ENTRIES.size(), PiCropStorage.blocks().size(), "un bloc par entree de la table");
        int x = 0;
        for (GeneratedCropStorage.Entry e : List.of(GeneratedCropStorage.ENTRIES.get(0), GeneratedCropStorage.ENTRIES.get(16),
              GeneratedCropStorage.ENTRIES.get(GeneratedCropStorage.ENTRIES.size() - 1))) {
            CropStorageBlock block = PiCropStorage.blocks().get(e.id()).getBlock();
            Gt.equal(e.produce(), block.produce, "recolte du bloc " + e.id());
            BlockPos at = new BlockPos(1 + x, 1, 2);
            x += 3;
            h.setBlock(at, block.defaultBlockState().setValue(CropStorageBlock.FACING, Direction.EAST));
            BlockPos abs = h.absolutePos(at);
            BlockState st = level.getBlockState(abs);
            Gt.check(st.getBlock() == block && st.getValue(CropStorageBlock.FACING) == Direction.EAST, "pose orientee : " + e.id());
            Gt.check(ComposterBlock.COMPOSTABLES.getFloat(block.asItem()) > 0.8F, "compostable comme la botte de paille : " + e.id());
            Recipe<?> pack = level.getRecipeManager().byKey(Pioneers.rl("farm_storage/" + e.id())).orElse(null);
            Recipe<?> unpack = level.getRecipeManager().byKey(Pioneers.rl("farm_storage/" + e.id() + "_unpack")).orElse(null);
            Gt.check(pack != null && unpack != null, "recettes : 9 -> 1 et 1 -> 9 : " + e.id());
            ItemStack back = unpack.getResultItem(level.registryAccess());
            Gt.equal(9, back.getCount(), "retour en 9 : " + e.id());
            Gt.equal(e.produce(), BuiltInRegistries.ITEM.getKey(back.getItem()).toString(), "retour : la recolte " + e.id());
            level.destroyBlock(abs, true);
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(1.5));
            Gt.check(drops.stream().anyMatch(d -> d.getItem().is(block.asItem())), "casse : le bloc lui-meme : " + e.id());
            drops.forEach(d -> d.discard());
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104f_hemp", timeoutTicks = 20)
    public static void p104_6_tetes_de_chanvre(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        RandomSource random = RandomSource.create(6);
        int total = 0;
        for (int i = 0; i < 200; i++) {
            List<ItemStack> extra = PiHemp.harvestExtras("hemp", 1F, random);
            Gt.equal(1, extra.size(), "une pile de tetes par recolte");
            Gt.check(extra.get(0).is(PiHemp.HEMP_FLOWER.get()), "ce sont des tetes de chanvre");
            int n = extra.get(0).getCount();
            Gt.check(n >= 1 && n <= 2, "1 ou 2 tetes (rendement moyen)");
            total += n;
        }
        Gt.check(total > 260 && total < 340, "1,5 tete en moyenne : " + total + " pour 200");
        Gt.check(PiHemp.harvestExtras("wheat", 1F, random).isEmpty(), "les autres cultures ne donnent rien de plus");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104f_joint", timeoutTicks = 20)
    public static void p104_6_joint_effets_et_repos(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = h.getLevel();
        ServerPlayer p = Gt.connect(level.getServer(), level, UUID.randomUUID(), Gt.account("joint"));
        try {
            ItemStack lit = LitHempJointItem.make(level.getGameTime() + LitHempJointItem.BURN);
            Gt.equal(LitHempJointItem.BURN, LitHempJointItem.remaining(lit, level.getGameTime()), "joint neuf : 2 minutes");
            p.setItemInHand(InteractionHand.MAIN_HAND, lit);
            lit.releaseUsing(level, p, lit.getUseDuration() - 20);
            Gt.check(p.hasEffect(MobEffects.HUNGER) && p.getEffect(MobEffects.HUNGER).getAmplifier() == 0, "fringale : Faim I");
            Gt.check(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && p.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration() <= LitHempJointItem.SLOW_TICKS,
                  "pas lourds : Lenteur I courte");
            Gt.check(p.hasEffect(MobEffects.REGENERATION), "detente : Regeneration I");
            Gt.check(p.getCooldowns().isOnCooldown(PiHemp.LIT_HEMP_JOINT.get()), "repos entre deux bouffees");
            Gt.check(LitHempJointItem.remaining(lit, level.getGameTime()) <= LitHempJointItem.BURN - LitHempJointItem.PUFF_COST,
                  "la bouffee consume le joint");
        } finally {
            Gt.disconnect(level.getServer(), p.getUUID());
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104f_paintings", timeoutTicks = 20)
    public static void p104_6_tableaux_posables(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        var reg = h.getLevel().registryAccess().registryOrThrow(Registries.PAINTING_VARIANT);
        int placeable = 0;
        for (Holder<PaintingVariant> v : reg.getTagOrEmpty(PaintingVariantTags.PLACEABLE)) {
            placeable++;
        }
        for (String id : new String[]{"earth", "wind", "water", "fire", "kebab", "pointer", "fighters", "skeleton"}) {
            Holder<PaintingVariant> v = reg.getHolderOrThrow(ResourceKey.create(Registries.PAINTING_VARIANT, new ResourceLocation(id)));
            Gt.check(v.is(PaintingVariantTags.PLACEABLE), "tableau posable : " + id);
        }
        Gt.check(placeable >= 30, "30 oeuvres posables : " + placeable);
        s.done();
    }
}
