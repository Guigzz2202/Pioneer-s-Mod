package fr.pioneers.tech.industry.multi;

import fr.pioneers.api.multiblock.StructureMatcher;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.0.4-beta (lot 15, decision de Guillaume du 07/11/2026) : les neuf blocs de multibloc retires (brûleur, tuyère, cheminée, torchère, évent,
 * ventilateur, groupe froid, bobine de chauffe, bloc d'ingénierie) se chargent en carcasse MKI ({@code PiRemaps.p104Lot15Block} : un remap de
 * registre ne garde pas le tier peint sur la piece). Au premier controle d'un controleur sauve avant le lot 15, cette carcasse est remise au
 * tier de la structure, pour qu'un multibloc forme reste forme AU MEME TIER :
 * <ul>
 *     <li>case d'enveloppe (carcasse au sommet comprise) : une carcasse MKI dans un multibloc peint a un tier superieur ne peut etre qu'une
 *     ancienne piece (le tier d'un multibloc est celui de sa plus faible carcasse) : elle devient la carcasse du tier peint ;</li>
 *     <li>paroi d'un contenant (toles) : une carcasse n'y etait jamais acceptee (anciens blocs de paroi fonctionnels) : elle devient la tole du
 *     tier peint (MKI si le multibloc n'etait pas forme).</li>
 * </ul>
 * Une seule fois par controleur (drapeau {@code p104Parts} de sa sauvegarde) ; plate-forme offshore (forme libre) : rien (ses carcasses MKI
 * restent, le tier peut baisser).
 */
public final class PartMigration {

    public static final String TAG = "p104Parts";

    private PartMigration() {
    }

    /** Controleur sauve avant le lot : migration au premier controle (tier peint sur le controleur), puis drapeau leve (sauvegarde). */
    static boolean run(ControllerTile c) {
        Level level = c.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        BlockState state = c.getBlockState();
        int painted = state.hasProperty(IndustryStyle.TIER) ? state.getValue(IndustryStyle.TIER) : 0;
        boolean done = run(level, c.getBlockPos(), c.facing(), c.machine, painted);
        if (done) {
            c.setChanged();
        }
        return done;
    }

    /**
     * Remet les carcasses MKI issues des pieces retirees au tier peint sur le controleur ({@code paintedTier} : 0 = libre, 1..4 = tier + 1).
     * Rend false si une case du gabarit n'est pas chargee (a refaire plus tard), true sinon (fait, ou rien a faire).
     */
    public static boolean run(Level level, BlockPos controller, Direction front, IndustryMachine machine, int paintedTier) {
        if (machine.freeform || level.isClientSide) {
            return true;
        }
        List<ShapeTemplate> all = ShapeTemplate.of(machine);
        if (all.isEmpty()) {
            return true;
        }
        ShapeTemplate t = all.get(0);
        if (t.nbt) {
            //1.0.4-beta (lot 24) : structure de Guillaume (NBT) : une structure d'avant le lot 15 a l'ancienne forme ne s'y forme pas (« Non
            //forme ») : rien a remettre au tier (on ne touche pas aux blocs d'une autre forme)
            return true;
        }
        Direction forward = front.getOpposite(), right = forward.getClockWise();
        int tier = Math.max(0, Math.min(3, paintedTier - 1));
        Block basic = PiIndustry.CASINGS.get(CableTier.BASIC).getBlock();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cell : t.cells()) {
            StructureMatcher.set(pos, controller, forward, right, ShapeTemplate.x(cell), ShapeTemplate.y(cell), ShapeTemplate.z(cell));
            if (!level.isLoaded(pos)) {
                return false;
            }
        }
        for (int cell : t.cells()) {
            if (ShapeTemplate.x(cell) == 0 && ShapeTemplate.y(cell) == 0 && ShapeTemplate.z(cell) == 0 || ShapeTemplate.interior(cell)
                || ShapeTemplate.reactor(cell) != 0 || ShapeTemplate.frame(cell) || ShapeTemplate.isAttached(cell)) {
                continue;
            }
            StructureMatcher.set(pos, controller, forward, right, ShapeTemplate.x(cell), ShapeTemplate.y(cell), ShapeTemplate.z(cell));
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() != basic) {
                continue;
            }
            if (ShapeTemplate.sheet(cell)) {
                level.setBlock(pos, PiIndustry.SHEETS.get(CableTier.values()[tier]).getBlock().defaultBlockState(), Block.UPDATE_ALL);
            } else if (tier > 0) {
                level.setBlock(pos, PiIndustry.CASINGS.get(CableTier.values()[tier]).getBlock().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return true;
    }
}
