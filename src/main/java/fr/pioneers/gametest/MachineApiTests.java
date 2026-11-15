package fr.pioneers.gametest;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineMenu;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.machine.MachineKind;
import fr.pioneers.core.network.PacketGuard;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.core.registries.PiMachineKit;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * API Machine (lot 2, GameTests) : la tile generique des machines a procede ({@code AtelierTile} + unite de procede) sur de vraies machines :
 * aller-retour de sauvegarde d'un atelier et d'une machine du kit (memes cles qu'avant le lot 2, memes cases relues a leur place, verrou et
 * energie gardes), et choix d'une recette par le menu de l'interface standard v2 (menu ouvert par un joueur, verrou pose par le chemin du
 * paquet, cases et inventaire a la place de la maquette validee).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MachineApiTests {

    private MachineApiTests() {
    }

    private static AtelierTile place(GameTestHelper h, BlockPos rel, Block block) {
        h.setBlock(rel, block.defaultBlockState());
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile generique (AtelierTile) pour " + block);
        return (AtelierTile) h.getBlockEntity(rel);
    }

    /** Memes piles dans les memes cases (ordre de la sauvegarde). */
    private static void sameSlots(List<ItemSlot> a, List<ItemSlot> b, String what) {
        Gt.equal(a.size(), b.size(), what + " : nombre de cases");
        for (int i = 0; i < a.size(); i++) {
            Gt.check(ItemStack.matches(a.get(i).getStack(), b.get(i).getStack()), what + " : case " + i + " relue " + b.get(i).getStack()
                                                                                   + " au lieu de " + a.get(i).getStack());
        }
    }

    // ================================================================== sauvegarde d'un atelier
    /** Atelier : objets, energie et verrou relus a leur place ; cles de sauvegarde d'avant le lot 2 (drapeau pioneers, ioVersion...). */
    @GameTest(template = Gt.SMALL, batch = "pioneers_api2_atelier_nbt", timeoutTicks = 40)
    public static void api_atelier_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Block block = PiIndustry.ATELIERS.get(IndustryMachine.ASSEMBLER).get(CableTier.ADVANCED).getBlock();
        AtelierTile a = place(h, new BlockPos(2, 2, 2), block);
        RecipeChoice first = a.recipeChoices().isEmpty() ? null : a.recipeChoices().get(0);
        Gt.check(first != null, "l'assembleur n'a pas de recettes");
        Gt.check(a.setLockedRecipe(first.id()), "verrou refuse");
        a.itemInputs().get(0).setStack(new ItemStack(Items.IRON_INGOT, 7));
        a.itemInputs().get(2).setStack(new ItemStack(Items.COPPER_INGOT, 3));
        a.itemOutputs().get(0).setStack(new ItemStack(Items.REDSTONE, 5));
        a.getEnergy().setStored(12_345);
        CompoundTag tag = a.saveWithoutMetadata();
        for (String key : new String[]{"pioneers", "energy", "config", "items", "redstone", "gases", "fluids", "progress", "burn", "burnPower",
                                       "ioVersion"}) {
            Gt.check(tag.contains(key), "cle de sauvegarde manquante : " + key);
        }
        AtelierTile b = place(h, new BlockPos(5, 2, 2), block);
        b.load(tag.copy());
        sameSlots(a.guiSlots(), b.guiSlots(), "atelier relu");
        Gt.equal(12_345L, b.getEnergy().getStored(), "energie relue");
        Gt.equal(first.id(), b.lockedRecipe(), "verrou relu");
        Gt.check(b.saveWithoutMetadata().equals(tag), "atelier : sauver, relire, sauver n'est pas identique");
        s.done();
    }

    // ================================================================== sauvegarde d'une machine du kit
    /**
     * Kit (compacteur, fondu dans l'atelier) : format de sauvegarde du kit d'avant le lot 2 ({@code energy}, {@code config}, {@code items},
     * {@code progress}, verrou ; ni drapeau {@code pioneers} ni reservoirs ni redstone par defaut), cases dans l'ordre d'avant (entree,
     * sortie, batterie, LIANT en dernier).
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_api2_kit_nbt", timeoutTicks = 40)
    public static void api_kit_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Block block = PiMachineKit.MACHINES.get(MachineKind.COMPRESSOR).get(CableTier.BASIC).getBlock();
        AtelierTile a = place(h, new BlockPos(2, 2, 2), block);
        Gt.check(a.isKit(), "le compacteur n'est pas une machine du kit");
        Gt.equal("kit_machine", net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(a.getType()).getPath(),
              "type de tile du kit (alias garde)");
        Gt.equal(4, a.guiSlots().size(), "cases du compacteur");
        Gt.check(a.guiSlots().get(3) == a.itemInputs().get(1), "le liant n'est pas la derniere case");
        a.itemInputs().get(0).setStack(new ItemStack(Items.COBBLESTONE, 9));
        a.itemInputs().get(1).setStack(new ItemStack(Items.CLAY_BALL, 2));
        a.itemOutputs().get(0).setStack(new ItemStack(Items.GRAVEL, 4));
        a.getEnergy().setStored(5_000);
        RecipeChoice first = a.recipeChoices().isEmpty() ? null : a.recipeChoices().get(0);
        Gt.check(first != null && a.setLockedRecipe(first.id()), "verrou du kit refuse");
        CompoundTag tag = a.saveWithoutMetadata();
        for (String key : new String[]{"energy", "config", "items", "progress"}) {
            Gt.check(tag.contains(key), "cle de sauvegarde du kit manquante : " + key);
        }
        for (String key : new String[]{"pioneers", "redstone", "gases", "fluids", "burn", "ioVersion"}) {
            Gt.check(!tag.contains(key), "cle etrangere au format du kit : " + key);
        }
        AtelierTile b = place(h, new BlockPos(5, 2, 2), block);
        b.load(tag.copy());
        sameSlots(a.guiSlots(), b.guiSlots(), "kit relu");
        Gt.equal(5_000L, b.getEnergy().getStored(), "energie du kit relue");
        Gt.equal(first.id(), b.lockedRecipe(), "verrou du kit relu");
        Gt.check(b.saveWithoutMetadata().equals(tag), "kit : sauver, relire, sauver n'est pas identique");
        s.done();
    }

    // ================================================================== choix de recette par le menu
    /**
     * Un joueur ouvre l'interface standard v2 d'un broyeur du kit : menu de 250 de haut et 196 de large (lot 4 : zone 152 + inventaire
     * DANS le cadre, rangees a 168 et barre a 226, inventaire centre ; carre du selecteur ouvert entierement dans la zone), valeurs nommees ; la recette choisie passe par le chemin du paquet ({@code PacketGuard} : interface ouverte) et
     * se verrouille ; le menu montre la duree du tier.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_api2_menu", timeoutTicks = 40)
    public static void api_selection_par_le_menu(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos rel = new BlockPos(2, 2, 2);
        AtelierTile t = place(h, rel, PiMachineKit.MACHINES.get(MachineKind.CRUSHER).get(CableTier.ADVANCED).getBlock());
        UUID id = UUID.randomUUID();
        ServerPlayer p = Gt.connect(h.getLevel().getServer(), h.getLevel(), id, Gt.account("api2"));
        try {
            BlockPos abs = h.absolutePos(rel);
            p.teleportTo(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 2.5);
            AbstractContainerMenu opened = t.createMenu(1, p.getInventory(), p);
            Gt.check(opened instanceof MachineMenu, "pas de menu standard");
            MachineMenu menu = (MachineMenu) opened;
            p.containerMenu = menu;
            Gt.equal(StandardLayout.ZONE + StandardLayout.INVENTORY, menu.height(), "hauteur du panneau v2");
            Gt.equal(t.guiSlots().size() + 36, menu.slots.size(), "cases du menu");
            Slot firstRow = menu.slots.get(t.guiSlots().size()), hotbar = menu.slots.get(t.guiSlots().size() + 27);
            Gt.equal(StandardLayout.ZONE + StandardLayout.INV_ROWS, firstRow.y, "premiere rangee de l'inventaire dans le cadre");
            Gt.equal(StandardLayout.ZONE + StandardLayout.INV_HOTBAR, hotbar.y, "barre d'action dans le cadre");
            //Lot 4 : panneau plus large, inventaire centre, carre ouvert sous le titre et au-dessus du cadre de l'inventaire
            Gt.equal(StandardLayout.WIDTH, menu.width(), "largeur du panneau v2");
            Gt.equal(8 + (menu.width() - 176) / 2, firstRow.x, "inventaire centre dans le cadre");
            StandardLayout.Rect q = StandardLayout.squareOver(menu.width());
            Gt.check(q.y() > 15 && q.y() + q.h() + 1 < StandardLayout.inventoryFrame(menu.width(), StandardLayout.ZONE).y(), "carre hors de la zone machine");
            Gt.equal(MachineKind.CRUSHER.ticks(CableTier.ADVANCED), menu.value(UiValues.DURATION), "duree lue par son nom");
            List<RecipeChoice> choices = t.recipeChoices();
            Gt.check(!choices.isEmpty(), "le broyeur n'a pas de recettes");
            RecipeChoice pick = choices.get(choices.size() / 2);
            RecipeLockable target = PacketGuard.tile(p, abs, RecipeLockable.class);
            Gt.check(target == t, "le chemin du paquet ne trouve pas la machine ouverte");
            Gt.check(target.setLockedRecipe(pick.id()), "recette choisie refusee");
            Gt.equal(pick.id(), t.lockedRecipe(), "recette verrouillee par le menu");
            Gt.check(t.lockedChoice() != null && t.lockedChoice().id().equals(pick.id()), "recette verrouillee introuvable");
            Gt.check(target.setLockedRecipe(null) && t.lockedRecipe() == null, "deverrouillage par le menu");
        } finally {
            p.containerMenu = p.inventoryMenu;
            Gt.disconnect(h.getLevel().getServer(), id);
        }
        s.done();
    }
}
