package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.bench.BenchTile;
import fr.pioneers.tech.bronze.bench.BenchType;
import fr.pioneers.equipment.cosmetic.CosmeticActions;
import fr.pioneers.equipment.cosmetic.CosmeticCap;
import fr.pioneers.equipment.cosmetic.CosmeticPiece;
import fr.pioneers.equipment.cosmetic.PiCosmetics;
import fr.pioneers.equipment.cosmetic.Wardrobe;
import fr.pioneers.tech.bronze.PiBronze;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.12.0 (lot E4) : la garde-robe cosmetique et l'etabli du tanneur.
 * <ul>
 *     <li>la garde-robe (pieces et interrupteurs) est sauvee avec le joueur et relue a la reconnexion ; elle passe la mort entiere ;</li>
 *     <li>l'interrupteur montre le cosmetique ou l'armure sans toucher a l'armure portee ; un geste du panneau n'accepte que la piece de
 *     l'emplacement ;</li>
 *     <li>l'etabli du tanneur fait les cosmetiques et les motifs de banniere ; le metier a tisser n'a plus de recette.</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class CosmeticTests {

    private static final BlockPos AT = new BlockPos(2, 2, 2);

    private CosmeticTests() {
    }

    private static ItemStack piece(String outfit, CosmeticPiece piece) {
        return new ItemStack(PiCosmetics.all().get("cosmetic_" + outfit + "_" + piece.key).get());
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_e4_garde_robe", timeoutTicks = 60)
    public static void garde_robe_sauvee_et_relue(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("gtcos", "Celeste", AT);
        UUID id = p.getUUID();
        String account = p.getGameProfile().getName();
        Gt.check(CosmeticActions.equip(p, piece("sailor", CosmeticPiece.HAT)).isEmpty(), "rien a rendre a la premiere piece");
        CosmeticActions.equip(p, piece("sailor", CosmeticPiece.SHOES));
        Gt.check(CosmeticActions.toggle(p, CosmeticPiece.SHOES.ordinal()), "interrupteur refuse");
        //Piece remplacee : l'ancienne est rendue
        ItemStack back = CosmeticActions.equip(p, piece("captain", CosmeticPiece.HAT));
        Gt.check(back.is(PiCosmetics.all().get("cosmetic_sailor_hat").get()), "la coiffe remplacee n'est pas rendue");
        Gt.disconnect(s.server, id);
        ServerPlayer again = s.reconnect(id, account, AT);
        Wardrobe w = CosmeticCap.get(again);
        Gt.check(w.get(0).is(PiCosmetics.all().get("cosmetic_captain_hat").get()), "coiffe perdue a la reconnexion");
        Gt.check(w.get(3).is(PiCosmetics.all().get("cosmetic_sailor_shoes").get()), "chaussures perdues a la reconnexion");
        Gt.check(w.get(1).isEmpty() && w.get(2).isEmpty(), "pieces fantomes apres relecture");
        Gt.check(w.armorShown(3) && !w.armorShown(0), "interrupteurs mal relus");
        //Relecture tolerante : une piece a la mauvaise place est ignoree (aucun plantage)
        Wardrobe bad = new Wardrobe();
        bad.set(0, piece("sailor", CosmeticPiece.TOP));
        Wardrobe read = new Wardrobe();
        read.load(bad.save());
        Gt.check(read.get(0).isEmpty(), "une piece a la mauvaise place a ete relue");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_e4_interrupteur", timeoutTicks = 40)
    public static void interrupteur_cosmetique_ou_armure(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("gtcot", "Tancrede", AT);
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        int top = CosmeticPiece.TOP.ordinal();
        //Geste du panneau : l'objet tenu par la souris va dans sa case, une piece d'un autre emplacement est refusee
        p.inventoryMenu.setCarried(piece("explorer", CosmeticPiece.HAT));
        Gt.check(!CosmeticActions.click(p, top), "une coiffe est entree dans la case du haut");
        p.inventoryMenu.setCarried(piece("explorer", CosmeticPiece.TOP));
        Gt.check(CosmeticActions.click(p, top), "le haut n'est pas entre dans sa case");
        Gt.check(p.inventoryMenu.getCarried().isEmpty(), "l'objet tenu n'a pas ete pose");
        Wardrobe w = CosmeticCap.get(p);
        Gt.check(w.shows(top), "le haut pose doit etre montre (interrupteur par defaut : cosmetique)");
        CosmeticActions.toggle(p, top);
        Gt.check(!w.shows(top) && w.armorShown(top), "l'interrupteur ne montre pas l'armure");
        Gt.check(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "l'armure a bouge");
        Gt.check(p.getArmorValue() > 0, "l'armure ne protege plus");
        CosmeticActions.toggle(p, top);
        Gt.check(w.shows(top), "l'interrupteur ne revient pas au cosmetique");
        Gt.check(p.getArmorValue() > 0, "l'armure ne protege plus quand le cosmetique est montre");
        //Maj + clic : rangee dans l'inventaire, puis enfilee depuis l'inventaire
        Gt.check(CosmeticActions.quickOut(p, top), "Maj + clic : la piece ne retourne pas dans l'inventaire");
        int slot = p.getInventory().findSlotMatchingItem(piece("explorer", CosmeticPiece.TOP));
        Gt.check(slot >= 0 && w.get(top).isEmpty(), "piece absente de l'inventaire");
        Gt.check(CosmeticActions.quickIn(p, slot), "Maj + clic depuis l'inventaire refuse");
        Gt.check(!w.get(top).isEmpty() && p.getInventory().getItem(slot).isEmpty(), "la piece n'a pas ete enfilee");
        s.done();
    }

    private static boolean has(List<BenchRecipe> recipes, String id) {
        for (BenchRecipe r : recipes) {
            if (r.getId().equals(ResourceLocation.fromNamespaceAndPath("pioneers", id))) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_e4_tanneur", timeoutTicks = 40)
    public static void etabli_du_tanneur(GameTestHelper h) {
        Gt.begin(h);
        h.setBlock(AT, PiBronze.BENCHES.get(BenchType.TANNER).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(AT) instanceof BenchTile, "pas de tuile d'etabli");
        Gt.equal(BenchType.TANNER, ((BenchTile) h.getBlockEntity(AT)).type(), "sorte d'etabli");
        List<BenchRecipe> tanner = BenchTile.recipes(h.getLevel(), BenchType.TANNER);
        Gt.check(tanner.size() >= 80, "moins de 80 recettes a l'etabli du tanneur : " + tanner.size());
        Gt.check(has(tanner, "cosmetics/cosmetic_peasant_hat"), "chapeau de paille absent de l'etabli du tanneur");
        Gt.check(has(tanner, "cosmetics/cosmetic_retro_astronaut_hat"), "casque-bulle absent de l'etabli du tanneur");
        Gt.check(has(tanner, "vanilla/flower_banner_pattern"), "motif de banniere absent de l'etabli du tanneur");
        Gt.check(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("minecraft", "loom")).isEmpty(),
              "le metier a tisser a encore une recette");
        h.succeed();
    }
}
