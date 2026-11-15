package fr.pioneers.gametest;

import fr.pioneers.bronze.BronzeBlock;
import fr.pioneers.bronze.foundry.CastingTableTile;
import fr.pioneers.bronze.foundry.CrucibleFurnaceTile;
import fr.pioneers.bronze.foundry.Metals;
import fr.pioneers.core.fluid.Buckets;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.registries.PiBronze;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.forge.automation.QuenchBasinTile;
import fr.pioneers.kinetics.rope.WinchTile;
import fr.pioneers.registries.PiForge;
import fr.pioneers.registries.PiKinetics;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.1-beta (correctifs des retours de Guillaume) :
 * <ul>
 * <li>la torche (grille de fabrication, vraie recherche du gestionnaire de recettes) accepte notre charbon et notre charbon de bois ;</li>
 * <li>le puits (treuil sur de l'eau) remplit un seau en argile vide, aussi pris dans une pile ;</li>
 * <li>un seau en argile plein d'eau remplit le bac de trempe de la forge et revient vide ;</li>
 * <li>le chaudron vanilla prend et rend l'eau du seau en argile ;</li>
 * <li>le fourneau a creuset coule seul dans la table meme avec des minerais dans les cases de droite (creuset plein : ils ne peuvent plus
 * fondre), les minerais restent dans les cases ; interrupteur « Auto » coupe, rien ne coule, et l'etat est sauve.</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer) ; aucune classe du jeu ne cite ce paquet.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchBetaTests {

    private PatchBetaTests() {
    }

    private static BlockHitResult hit(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        return new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
    }

    /** Vrai clic droit avec la pile tenue sur le bloc. */
    private static void use(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
        p.gameMode.useItemOn(p, h.getLevel(), held, InteractionHand.MAIN_HAND, hit(h, rel));
    }

    private static ItemStack waterClayBucket() {
        ItemStack full = Buckets.filled(new ItemStack(PiForge.CLAY_BUCKET.get()), new FluidStack(Fluids.WATER, Buckets.VOLUME), false);
        Gt.check(!full.isEmpty(), "le seau en argile vide prend 1 000 mB d'eau");
        return full;
    }

    /** Torche fabriquee dans une grille 3x3 avec ce combustible en haut et un baton dessous. */
    private static ItemStack torch(GameTestHelper h, Item fuel) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };
        TransientCraftingContainer grid = new TransientCraftingContainer(menu, 3, 3);
        grid.setItem(1, new ItemStack(fuel));
        grid.setItem(4, new ItemStack(Items.STICK));
        Optional<CraftingRecipe> recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel());
        return recipe.map(r -> r.assemble(grid, h.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_101_torche", timeoutTicks = 20)
    public static void torche_charbon_du_mod(GameTestHelper h) {
        Gt.begin(h);
        for (Item fuel : new Item[]{PiVanillaMaterials.COAL.get(), PiVanillaMaterials.CHARCOAL.get()}) {
            Gt.check(new ItemStack(fuel).is(ItemTags.COALS), fuel + " dans le tag minecraft:coals");
            ItemStack out = torch(h, fuel);
            Gt.check(out.is(Items.TORCH), "torche avec " + fuel + " : obtenu " + out);
            Gt.equal(4, out.getCount(), "4 torches avec " + fuel);
        }
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_101_puits", timeoutTicks = 40)
    public static void puits_seau_argile(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos rel = new BlockPos(4, 2, 4);
        h.setBlock(rel, PiKinetics.WINCH.getBlock());
        WinchTile winch = (WinchTile) h.getBlockEntity(rel);
        Gt.check(winch != null, "treuil pose");
        winch.water().fill(new FluidStack(Fluids.WATER, 2 * Buckets.VOLUME), IFluidHandler.FluidAction.EXECUTE);
        ServerPlayer p = s.player("gtpuits", "Puisatier", new BlockPos(4, 2, 2));
        //un seau seul : il devient plein en main
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PiForge.CLAY_BUCKET.get()));
        use(h, p, rel);
        Gt.check(Buckets.isWaterBucket(p.getItemInHand(InteractionHand.MAIN_HAND)), "seau en argile plein d'eau en main");
        Gt.equal(Buckets.VOLUME, winch.water().getFluidAmount(), "eau restee au puits");
        //une pile de 3 : 2 vides en main, le plein dans l'inventaire
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PiForge.CLAY_BUCKET.get(), 3));
        use(h, p, rel);
        Gt.equal(2, p.getItemInHand(InteractionHand.MAIN_HAND).getCount(), "seaux vides restes en main");
        boolean full = false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            full |= Buckets.isWaterBucket(p.getInventory().getItem(i));
        }
        Gt.check(full, "seau en argile plein range dans l'inventaire");
        Gt.equal(0, winch.water().getFluidAmount(), "puits vide");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_101_bac_forge", timeoutTicks = 40)
    public static void seau_argile_bac_forge(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos rel = new BlockPos(4, 2, 4);
        h.setBlock(rel, PiForge.QUENCH_BASIN.getBlock());
        QuenchBasinTile basin = (QuenchBasinTile) h.getBlockEntity(rel);
        Gt.check(basin != null, "bac de trempe pose");
        ServerPlayer p = s.player("gtbac", "Forgeron", new BlockPos(4, 2, 2));
        p.setItemInHand(InteractionHand.MAIN_HAND, waterClayBucket());
        use(h, p, rel);
        Gt.equal(Buckets.VOLUME, basin.water(), "eau versee dans le bac");
        ItemStack back = p.getItemInHand(InteractionHand.MAIN_HAND);
        Gt.check(back.is(PiForge.CLAY_BUCKET.get()) && Buckets.contents(back).isEmpty(), "seau en argile vide rendu : " + back);
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_101_chaudron", timeoutTicks = 40)
    public static void seau_argile_chaudron(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos rel = new BlockPos(4, 2, 4);
        h.setBlock(rel, Blocks.CAULDRON);
        ServerPlayer p = s.player("gtchaudron", "Potier", new BlockPos(4, 2, 2));
        p.setItemInHand(InteractionHand.MAIN_HAND, waterClayBucket());
        use(h, p, rel);
        Gt.check(h.getBlockState(rel).is(Blocks.WATER_CAULDRON) && h.getBlockState(rel).getValue(LayeredCauldronBlock.LEVEL) == 3,
              "chaudron plein d'eau");
        Gt.check(Buckets.isEmptyBucket(p.getItemInHand(InteractionHand.MAIN_HAND)), "seau en argile vide apres versement");
        use(h, p, rel);
        Gt.check(h.getBlockState(rel).is(Blocks.CAULDRON), "chaudron vide apres puisage");
        Gt.check(Buckets.isWaterBucket(p.getItemInHand(InteractionHand.MAIN_HAND)), "seau en argile plein apres puisage");
        s.done();
    }

    // ------------------------------------------------------------------ fourneau a creuset : coulee automatique avec des minerais dans les cases
    private static final BlockPos FURNACE = new BlockPos(3, 2, 4), TABLE = new BlockPos(4, 2, 4);

    /**
     * Fourneau tourne vers l'est, table de coulee devant avec un moule a lingot, creuset en place, 9 lingots d'etain fondus (plein) et deux
     * piles de 64 minerais d'etain dans les cases de droite.
     */
    private static CrucibleFurnaceTile furnaceWithOres(GameTestHelper h) {
        h.setBlock(FURNACE, PiBronze.CRUCIBLE_FURNACE.getBlock().defaultBlockState().setValue(BronzeBlock.FACING, Direction.EAST));
        h.setBlock(TABLE, PiBronze.CASTING_TABLE.getBlock());
        CrucibleFurnaceTile furnace = (CrucibleFurnaceTile) h.getBlockEntity(FURNACE);
        CastingTableTile table = (CastingTableTile) h.getBlockEntity(TABLE);
        Gt.check(furnace != null && table != null, "fourneau et table poses");
        Gt.check(furnace.table() == table, "le fourneau voit la table devant lui");
        table.moldSlot().setStack(new ItemStack(PiBronze.MOLDS.get("ingot").get()));
        Item rawTin = BuiltInRegistries.ITEM.get(new ResourceLocation("pioneers", "raw_tin"));
        Gt.check(CrucibleFurnaceTile.meltable(new ItemStack(rawTin)), "le minerais d'etain se fond au creuset");
        //cases : 0 creuset, 1 combustible, 2..4 metal (1.0.5-beta lot 39 : 3 cases)
        furnace.guiSlots().get(0).setStack(new ItemStack(PiBronze.CRUCIBLE.get()));
        furnace.guiSlots().get(2).setStack(new ItemStack(rawTin, 64));
        furnace.guiSlots().get(3).setStack(new ItemStack(rawTin, 64));
        Metals.addTo(furnace.mix(), "tin", CrucibleFurnaceTile.CAPACITY);
        furnace.setTemperature(600);
        Gt.check(furnace.molten(), "etain liquide a 600 degres");
        return furnace;
    }

    private static int ores(CrucibleFurnaceTile furnace) {
        int n = 0;
        for (int i = 2; i < 2 + CrucibleFurnaceTile.METAL_SLOTS; i++) {   // 1.0.5-beta (lot 39) : 3 cases de metal
            ItemSlot slot = furnace.guiSlots().get(i);
            n += slot.getStack().getCount();
        }
        return n;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_101_fourneau_auto", timeoutTicks = 100)
    public static void fourneau_creuset_coule_avec_minerais(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        CrucibleFurnaceTile furnace = furnaceWithOres(h);
        Gt.check(furnace.autoPour(), "coulee automatique active par defaut");
        //Le bug : creuset plein, les minerais ne peuvent plus fondre ; avant 1.0.1-beta ils bloquaient la coulee pour toujours
        Gt.check(!furnace.inputsMelting(), "un minerais qui ne tient plus dans le creuset ne retient pas la coulee");
        h.runAfterDelay(40, () -> {
            CastingTableTile table = (CastingTableTile) h.getBlockEntity(TABLE);
            Gt.check(table != null && table.busy(), "le metal est coule dans la table malgre les minerais des cases");
            Gt.equal("tin", table.metal(), "metal coule");
            Gt.check(ores(furnace) >= 126, "les minerais restent dans les cases (au plus un ou deux fondus) : " + ores(furnace));
            Gt.check(furnace.guiSlots().get(0).getStack().is(PiBronze.CRUCIBLE.get()), "le creuset reste en place");
            s.done();
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_101_fourneau_manuel", timeoutTicks = 100)
    public static void fourneau_creuset_interrupteur(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        CrucibleFurnaceTile furnace = furnaceWithOres(h);
        //bouton de l'interface (meme action que le paquet du client)
        furnace.handleCustom(null, CrucibleFurnaceTile.AUTO);
        Gt.check(!furnace.autoPour(), "interrupteur coupe");
        Gt.equal(0L, furnace.guiValue(furnace.uiValues().index(fr.pioneers.api.machine.UiValues.AUTO)), "etat envoye a l'interface");
        CompoundTag saved = furnace.saveWithoutMetadata();
        Gt.check(saved.contains("auto") && !saved.getBoolean("auto"), "etat sauve");
        h.runAfterDelay(40, () -> {
            CastingTableTile table = (CastingTableTile) h.getBlockEntity(TABLE);
            Gt.check(table != null && !table.busy(), "interrupteur coupe : rien n'est coule");
            Gt.equal(128, ores(furnace), "minerais intacts");
            furnace.handleCustom(null, CrucibleFurnaceTile.AUTO);
            Gt.check(furnace.autoPour(), "interrupteur rallume");
            h.runAfterDelay(20, () -> {
                Gt.check(table.busy(), "interrupteur rallume : la coulee repart");
                s.done();
            });
        });
    }
}
