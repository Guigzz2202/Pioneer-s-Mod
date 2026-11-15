package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.BronzeBlock;
import fr.pioneers.tech.bronze.autofoundry.AutoFoundryTile;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldBlock;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldTile;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldUnit;
import fr.pioneers.tech.bronze.autofoundry.CastingChannelBlock;
import fr.pioneers.tech.bronze.autofoundry.FoundryLayout;
import fr.pioneers.tech.bronze.autofoundry.FoundryPartBlock;
import fr.pioneers.tech.bronze.autofoundry.FoundryUnit;
import fr.pioneers.tech.bronze.foundry.CastingRecipe;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.core.registries.PiBronze;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * Patch 1.0.2-beta, lot 4 (GameTests) : fonderie automatique formee aux 3 tailles (plan {@link FoundryLayout}, la plus grande complete ;
 * rupture et erreur commune ; peinture de la cuve), coulee EN CHAINE par les conduits vers 3 moules automatiques (le premier libre d'abord,
 * puis le suivant), refroidissement selon le metal et piece sortie par le dessous, aller-retour de sauvegarde de la fonderie et du moule.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP102Lot4Tests {

    private PatchP102Lot4Tests() {
    }

    /** Construit une fonderie de cette taille (plan par defaut), controleur en {@code rel} tourne vers {@code facing} ; renvoie la tile. */
    private static AutoFoundryTile build(GameTestHelper h, BlockPos rel, Direction facing, FoundryLayout.Size size) {
        BlockPos c = h.absolutePos(rel);
        for (FoundryLayout.Cell cell : FoundryLayout.cells(size, FoundryLayout.defaultOffset(size))) {
            BlockPos p = FoundryLayout.at(c, facing, cell.x(), cell.y(), cell.z());
            var state = FoundryLayout.block(cell.kind()).defaultBlockState();
            if (cell.kind() == FoundryLayout.Kind.CONTROLLER) {
                state = state.setValue(BronzeBlock.FACING, facing);
            }
            h.getLevel().setBlock(p, state, 3);
        }
        Gt.check(h.getLevel().getBlockEntity(c) instanceof AutoFoundryTile, "pas de fonderie en " + rel);
        return (AutoFoundryTile) h.getLevel().getBlockEntity(c);
    }

    // ================================================================== formation aux 3 tailles
    @GameTest(template = Gt.TALL, batch = "pioneers_p102_foundry_form", timeoutTicks = 40)
    public static void p102_fonderie_trois_tailles(GameTestHelper h) {
        FoundryLayout.Size[] sizes = FoundryLayout.Size.values();
        for (int k = 0; k < sizes.length; k++) {
            FoundryLayout.Size size = sizes[k];
            BlockPos rel = new BlockPos(3 + 9 * k, 2, 8);
            AutoFoundryTile f = build(h, rel, Direction.SOUTH, size);
            Gt.check(f.house().checkNow(), "fonderie " + size.key() + " non formee : " + f.house().error());
            Gt.equal(size, f.size(), "taille formee");
            Gt.equal(size.vats(), f.vats().size(), "cases de cuve");
            Gt.equal(size.capacity(), f.foundry().capacity(), "contenance");
            int[] dims = f.house().size();
            Gt.equal(size.side, dims[0], "largeur de la structure");
            Gt.equal(size.height, dims[1], "hauteur de la structure");
            //La cuve du dessus est peinte (bain sous la grille)
            BlockPos topVat = FoundryLayout.at(h.absolutePos(rel), Direction.SOUTH, 0, size.height - 1, 0);
            Gt.check(h.getLevel().getBlockState(topVat).getValue(FoundryPartBlock.TOP), "cuve du dessus non peinte (" + size.key() + ")");
        }
        //Grande fonderie sans le foyer de derriere la porte : ni grande ni moyenne ; la plus grande taille complete = petite (une petite
        //fonderie tient dans une grande en chantier)
        BlockPos large = h.absolutePos(new BlockPos(21, 2, 8));
        BlockPos firebox = FoundryLayout.at(large, Direction.SOUTH, 0, 0, -1);
        h.getLevel().setBlock(firebox, Blocks.AIR.defaultBlockState(), 3);
        AutoFoundryTile f = (AutoFoundryTile) h.getLevel().getBlockEntity(large);
        Gt.check(f.house().checkNow(), "la petite fonderie aurait du rester formee");
        Gt.equal(FoundryLayout.Size.SMALL, f.size(), "taille retombee");
        //Petite sans sa cuve : rien de forme, erreur commune (quoi, ou, attendu : la cuve)
        BlockPos small = h.absolutePos(new BlockPos(3, 2, 8));
        h.getLevel().setBlock(small.above(), Blocks.AIR.defaultBlockState(), 3);
        AutoFoundryTile g = (AutoFoundryTile) h.getLevel().getBlockEntity(small);
        Gt.check(!g.house().checkNow(), "petite fonderie formee sans cuve");
        Gt.equal(AutoFoundryTile.ERROR, g.house().error(), "erreur commune de la fonderie");
        Gt.equal(small.above(), g.house().at(), "case fautive : la cuve");
        Gt.equal(PiBronze.FOUNDRY_VAT.getBlock().getDescriptionId(), g.house().expected(), "attendu : la cuve de fonderie");
        h.succeed();
    }

    // ================================================================== coulee en chaine vers 3 moules
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_foundry_chain", timeoutTicks = 260)
    public static void p102_fonderie_coulee_en_chaine(GameTestHelper h) {
        //Petite fonderie (porte en (1, 1, 4), cuve au-dessus), 3 conduits vers l'est au niveau de la cuve, un moule sous chacun
        BlockPos door = new BlockPos(1, 1, 4);
        AutoFoundryTile f = build(h, door, Direction.SOUTH, FoundryLayout.Size.SMALL);
        AutoMoldTile[] molds = new AutoMoldTile[3];
        for (int i = 0; i < 3; i++) {
            h.setBlock(new BlockPos(2 + i, 1, 4), PiBronze.AUTO_MOLD.getBlock().defaultBlockState());
            molds[i] = (AutoMoldTile) h.getBlockEntity(new BlockPos(2 + i, 1, 4));
        }
        for (int i = 0; i < 3; i++) {
            BlockPos at = new BlockPos(2 + i, 2, 4);
            h.setBlock(at, PiBronze.CASTING_CHANNEL.getBlock().defaultBlockState());
            //Raccord recalcule (les voisins sont poses)
            var s = h.getBlockState(at);
            h.setBlock(at, ((CastingChannelBlock) s.getBlock()).connected(s, h.getLevel(), h.absolutePos(at)));
        }
        Gt.check(h.getBlockState(new BlockPos(2, 2, 4)).getValue(CastingChannelBlock.WEST), "conduit raccorde a la cuve");
        Gt.check(h.getBlockState(new BlockPos(3, 2, 4)).getValue(CastingChannelBlock.DOWN), "bec au-dessus du moule");
        Gt.check(f.house().checkNow(), "fonderie non formee");
        //Etain en fusion : 3 lingots (il reste liquide longtemps sans feu)
        FoundryUnit u = f.foundry();
        u.mix().put("tin", 432);
        u.setTemperature(600);
        Gt.equal("tin", u.pourable(), "bain d'etain coulable");
        boolean[] order = {false};
        h.runAfterDelay(30, () -> {
            //Le premier moule (le plus proche par les conduits) recoit avant le troisieme
            order[0] = molds[0].mold().amount() > 0 && molds[2].mold().amount() == 0;
        });
        h.succeedWhen(() -> {
            u.setTemperature(600);
            Gt.check(order[0], "le premier moule libre n'a pas ete servi d'abord");
            for (int i = 0; i < 3; i++) {
                Gt.equal(144, molds[i].mold().amount(), "etain dans le moule " + (i + 1));
            }
            Gt.equal(0, u.total(), "cuve videe");
        });
    }

    // ================================================================== refroidissement selon le metal, piece sortie par le dessous
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_foundry_cool", timeoutTicks = 200)
    public static void p102_moule_refroidit(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at, PiBronze.AUTO_MOLD.getBlock().defaultBlockState());
        AutoMoldTile m = (AutoMoldTile) h.getBlockEntity(at);
        AutoMoldUnit u = m.mold();
        CastingRecipe ingot = u.recipeFor("tin");
        Gt.check(ingot != null, "recette de coulee : lingot d'etain");
        Gt.check(AutoMoldUnit.coolTicks(ingot, "tin") < AutoMoldUnit.coolTicks(u.recipeFor("copper"), "copper"), "l'etain fige plus vite que le cuivre");
        Gt.check(AutoMoldUnit.coolTicks(u.recipeFor("copper"), "copper") < AutoMoldUnit.coolTicks(u.recipeFor("iron"), "iron"), "le cuivre plus vite que le fer");
        //Changer d'empreinte (moule vide) : etat du bloc
        Gt.check(u.setForm(AutoMoldBlock.Form.PLATE), "empreinte plaque");
        Gt.equal(AutoMoldBlock.Form.PLATE, h.getBlockState(at).getValue(AutoMoldBlock.FORM), "empreinte posee sur le bloc");
        Gt.check(u.setForm(AutoMoldBlock.Form.INGOT), "retour au lingot");
        Gt.equal(144, u.wants("tin"), "moule libre : il demande un lingot");
        Gt.equal(100, u.receive("tin", 100), "premiere part");
        Gt.equal(44, u.wants("tin"), "il demande le reste");
        Gt.check(!u.setForm(AutoMoldBlock.Form.GEAR), "pas de changement d'empreinte moule entame");
        Gt.equal(44, u.receive("tin", 500), "complete (pas plus que la recette)");
        Gt.check(u.cooling(), "le moule fige");
        Gt.equal(0, u.wants("tin"), "occupe pendant le figeage");
        h.succeedWhen(() -> {
            Gt.check(!m.outputSlot().isEmpty(), "lingot d'etain pas encore sorti");
            IItemHandler down = m.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElse(null);
            IItemHandler up = m.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
            Gt.check(down != null && up == null, "la piece sort par le dessous, pas par le bec");
            ItemStack got = down.extractItem(0, 64, true);
            Gt.check(ItemStack.isSameItem(got, ForgeMetals.ingot("tin", 1)), "un lingot d'etain par le dessous");
            Gt.equal(0, u.amount(), "empreinte videe");
        });
    }

    // ================================================================== sauvegarde
    @GameTest(template = Gt.TALL, batch = "pioneers_p102_foundry_save", timeoutTicks = 20)
    public static void p102_fonderie_sauvegarde(GameTestHelper h) {
        AutoFoundryTile f = build(h, new BlockPos(4, 2, 8), Direction.SOUTH, FoundryLayout.Size.MEDIUM);
        Gt.check(f.house().checkNow(), "fonderie moyenne non formee");
        FoundryUnit u = f.foundry();
        u.mix().put("copper", 432);
        u.mix().put("tin", 144);
        u.setTemperature(1180);
        u.setAutoPour(false);
        Gt.check(f.setLockedRecipe(fr.pioneers.tech.bronze.foundry.CrucibleUnit.choiceId("bronze")), "verrou sur le bronze");
        f.guiSlots().get(0).setStack(new ItemStack(Items.CHARCOAL, 9));
        CompoundTag tag = f.saveWithoutMetadata();
        BlockPos other = h.absolutePos(new BlockPos(20, 2, 8));
        h.getLevel().setBlock(other, PiBronze.AUTO_FOUNDRY.getBlock().defaultBlockState().setValue(BronzeBlock.FACING, Direction.SOUTH), 3);
        AutoFoundryTile g = (AutoFoundryTile) h.getLevel().getBlockEntity(other);
        g.load(tag);
        Gt.equal(576, g.foundry().total(), "bain relu");
        Gt.equal("bronze", g.foundry().result(), "bronze relu");
        Gt.equal("bronze", g.foundry().lockedMetal(), "alliage choisi relu");
        Gt.check(!g.foundry().autoPour(), "interrupteur relu");
        Gt.equal(9, g.guiSlots().get(0).getCount(), "combustible relu");
        Gt.equal(tag, g.saveWithoutMetadata(), "aller-retour identique");
        //Moule entame
        BlockPos mp = new BlockPos(12, 2, 12);
        h.setBlock(mp, PiBronze.AUTO_MOLD.getBlock().defaultBlockState());
        AutoMoldTile m = (AutoMoldTile) h.getBlockEntity(mp);
        m.mold().receive("copper", 50);
        CompoundTag mt = m.saveWithoutMetadata();
        BlockPos mp2 = new BlockPos(14, 2, 12);
        h.setBlock(mp2, PiBronze.AUTO_MOLD.getBlock().defaultBlockState());
        AutoMoldTile m2 = (AutoMoldTile) h.getBlockEntity(mp2);
        m2.load(mt);
        Gt.equal(50, m2.mold().amount(), "metal du moule relu");
        Gt.equal(94, m2.mold().wants("copper"), "le moule relu demande le reste");
        Gt.equal(mt, m2.saveWithoutMetadata(), "aller-retour du moule identique");
        Gt.equal(List.of("copper"), List.of(m2.mold().metal()), "metal relu");
        h.succeed();
    }
}
