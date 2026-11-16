package fr.pioneers.tech.bronze.foundry;

import fr.pioneers.api.machine.StandardLayout.Part;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.tech.bronze.AirBlastReceiver;
import fr.pioneers.tech.bronze.BronzeBlock;
import fr.pioneers.tech.bronze.BronzeTile;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.materials.GeneratedMaterials;
import fr.pioneers.tech.bronze.PiBronze;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Fourneau a creuset : un creuset, du combustible, jusqu'a trois objets de metal (1.0.5-beta lot 39 ; avant : quatre). Sans air il plafonne vers 950 degres ; un soufflet
 * mecanique tourne vers lui le pousse jusqu'a 1 600 (selon sa vitesse) : le fer fond (forge primaire 0.21). Chaque objet fond a la temperature
 * de son metal ; le creuset melange tout (9 lingots au plus) et le resultat se lit en direct : metal pur, alliage dans ses proportions, ou
 * melange impur. Les metaux qui ne se fondent pas au creuset (acier : haut fourneau) sont refuses.
 * <p>Coulee dans la table posee devant (la quantite du moule) : le bouton « Couler », ou toute seule (forge primaire 0.21) des que la table a
 * un moule et est vide, que le metal est liquide et que plus rien n'attend de fondre dans les cases (un alliage n'est pas coule a moitie
 * fait) ; un signal redstone (front montant) force la coulee sans attendre les cases. 1.0.1-beta : la coulee automatique n'attend que les cases
 * qui fondent ou vont fondre ; interrupteur « Coulee auto » ({@link #AUTO}) dans l'interface, sauve et synchronise, actif par defaut.
 * <p>API Machine (lot 8b) : la tile ne fait plus que PORTER son unite ({@link CrucibleUnit} : foyer, fusion, melange, coulee) et garde le
 * bloc, l'air des soufflets et les methodes du rendu, de la table et des GameTests ; type {@code crucible_furnace}, cles et ordre des cases
 * inchanges ; interface v2 (foyer a la place de l'energie motrice, creuset et combustible, carte du bain, 4 cases de metal, interrupteur et
 * bouton de coulee sous l'ecran).
 * <p>1.0.2-beta (lot 2) : selecteur de recette (alliage choisi, dosage : voir {@link CrucibleUnit}), plus de lingot impur ; la table de coulee
 * se raccorde sur N'IMPORTE QUEL cote horizontal ({@link #table} : devant d'abord, puis les autres faces ; la table s'oriente vers le
 * fourneau, {@link CastingTableBlock#LINK}).
 */
public class CrucibleFurnaceTile extends BronzeTile implements AirBlastReceiver, RecipeLockable {

    public static final int CAPACITY = 9 * Metals.INGOT, BASE_C = 950, AIR_C = 650;
    /**
     * 1.0.5-beta (lot 39, retour 51 ; specification age du bronze §4.2) : cases de metal (3 : « 3 entrees solides (+ gaz) = liquide +
     * sous-produit » ; avant : 4, la 4e d'un fourneau pose est rangee ailleurs ou posee au sol : {@link CrucibleUnit#placeLegacy}).
     */
    public static final int METAL_SLOTS = 3;
    /** Interface v2 : creuset et combustible a droite du foyer, carte du bain, 3 cases de metal (2 + 1 en triangle) contre le bord droit. */
    public static final int CRUCIBLE_X = 30, CRUCIBLE_Y = StandardLayout.TOP + 4, FUEL_X = 30, FUEL_Y = StandardLayout.TOP + 40, IN_X = 152,
          IN_Y = StandardLayout.TOP + 6;
    public static final Rect BATH = StandardLayout.ageCard(52, 94);
    /** 1.0.2-beta : case « Recette » du selecteur, sous la grille des metaux. */
    public static final int RECIPE_X = 161, RECIPE_Y = 73;
    /** Actions : couler, interrupteur « Auto », 1.0.3-beta (lot 7b) « Vider le bain ». */
    public static final int POUR = CUSTOM, AUTO = CUSTOM + 1, DUMP = CUSTOM + 2;
    private final CrucibleUnit unit;
    private final PowerSource.Fuel fire;

    public CrucibleFurnaceTile(BlockPos pos, BlockState state) {
        super(PiBronze.CRUCIBLE_FURNACE_TILE.get(), pos, state);
        unit = carry(new CrucibleUnit(host(), this));
        fire = unit.power();
    }

    /** Case de metal n (0..2) : deux cases cote a cote, la 3e centree dessous (1.0.5-beta lot 39), une barre de fusion sous chaque case. */
    public static int[] inPos(int i) {
        return i < 2 ? new int[]{IN_X + i * StandardLayout.SLOT, IN_Y} : new int[]{IN_X + StandardLayout.SLOT / 2, IN_Y + 24};
    }

    @Override
    protected PowerSource power() {
        return fire;
    }

    public CrucibleUnit furnace() {
        return unit;
    }

    /** Objet accepte dans une case de metal : objet de la table de fusion dont le metal se fond au creuset (1.0.2-beta : plus de lingot impur). */
    public static boolean meltable(ItemStack stack) {
        GeneratedMaterials.Melt melt = ForgeMetals.meltOf(stack);
        return melt != null && ForgeMetals.crucible(melt.metal());
    }

    /** Vrai si la case attend de fondre (voir {@link CrucibleUnit#waits}). */
    static boolean waits(double temperature, double reachable, int total, int metalC, int add) {
        return CrucibleUnit.waits(temperature, reachable, total, metalC, add);
    }

    // ------------------------------------------------------------------
    // Etat (rendu, table, GameTests)
    // ------------------------------------------------------------------

    public double temperature() {
        return unit.temperature();
    }

    public double reachable() {
        return unit.reachable();
    }

    public Map<String, Integer> mix() {
        return unit.mix();
    }

    public int total() {
        return unit.total();
    }

    public String result() {
        return unit.result();
    }

    /** 1.0.2-beta : ce qui coulerait maintenant (l'alliage choisi seulement, s'il y a un verrou). */
    public String pourable() {
        return unit.pourable();
    }

    public boolean molten() {
        return unit.molten();
    }

    /**
     * Table de coulee raccordee : devant le fourneau d'abord, sinon sur un des autres cotes horizontaux (1.0.2-beta : plus d'orientation
     * imposee ; la premiere trouvee dans l'ordre devant, droite, gauche, arriere).
     */
    @Nullable
    public CastingTableTile table() {
        if (level == null) {
            return null;
        }
        Direction facing = getBlockState().getValue(BronzeBlock.FACING);
        for (Direction d : new Direction[]{facing, facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite()}) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof CastingTableTile table) {
                return table;
            }
        }
        return null;
    }

    @Override
    public void blast(double strength) {
        unit.blast(strength);
    }

    public boolean inputsMelting() {
        return unit.inputsMelting();
    }

    public boolean autoPour() {
        return unit.autoPour();
    }

    public void setAutoPour(boolean on) {
        unit.setAutoPour(on);
    }

    /** Pour les GameTests : met le creuset a cette temperature (il retombe ensuite vers ce que le feu permet). */
    public void setTemperature(double c) {
        unit.setTemperature(c);
    }

    @Override
    public void serverTick(ServerLevel level) {
        unit.tick();
        flushClient(level, 10);
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action == POUR && level instanceof ServerLevel server) {
            Component message = unit.pour(server);
            player.displayClientMessage(message, true);
        } else if (action == AUTO) {
            unit.setAutoPour(!unit.autoPour());
        } else if (action == DUMP && !unit.mix().isEmpty()) {
            //1.0.3-beta (lot 7b) : sortie de secours : le bain en lingots, pepites et scorie, dans l'inventaire du joueur
            int mb = unit.total();
            for (ItemStack s : unit.dump()) {
                if (!player.getInventory().add(s)) {
                    player.drop(s, false);
                }
            }
            player.displayClientMessage(Component.translatable("foundry.pioneers.dumped", mb).withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** 1.0.3-beta (lot 7b) : le fourneau casse rend aussi son bain (lingots, pepites, scorie) ; il etait perdu. */
    @Override
    public void dropContents(Consumer<ItemStack> drop) {
        super.dropContents(drop);
        unit.spill().forEach(drop);
    }

    /** 1.0.3-beta (lot 7b) : plan de coulee (interface). */
    public Metals.Plan plan() {
        return unit.plan();
    }

    Component pour(ServerLevel level) {
        return unit.pour(level);
    }

    // ------------------------------------------------------------------
    // Interface standard v2
    // ------------------------------------------------------------------

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public String guiTier() {
        return "steel";
    }

    // ------------------------------------------------------------------
    // 1.0.2-beta : selecteur de recette (alliage)
    // ------------------------------------------------------------------

    @Override
    public String recipeSource() {
        return CrucibleUnit.SOURCE;
    }

    @Override
    public RecipeLock recipeLock() {
        return unit.recipeLock();
    }

    @Override
    public void recipeLockChanged() {
        unit.recipeLockChanged();
    }

    @Override
    public int[] recipeSlot() {
        return unit.recipeSlot();
    }

    @Override
    public int recipeTier() {
        return 0;
    }

    @Override
    public List<ItemStack> presentItems() {
        return unit.presentItems();
    }

    @Override
    public Level recipeLevel() {
        return level;
    }

    @Override
    public List<Part> guiParts() {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part("card", BATH));
        parts.add(new Part("recipe", StandardLayout.slotFrame(RECIPE_X, RECIPE_Y)));
        for (int i = 0; i < METAL_SLOTS; i++) {
            int[] p = inPos(i);
            parts.add(new Part("bar", new Rect(p[0], p[1] + 17, 16, 2)));
        }
        return StandardLayout.ageService(true, parts.toArray(new Part[0]));
    }
}
