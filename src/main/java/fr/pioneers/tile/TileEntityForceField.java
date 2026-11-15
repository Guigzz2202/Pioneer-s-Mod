package fr.pioneers.tile;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.registries.PiSpaceMachines;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Generateur de champ de force (socle) : une bulle de rayon 4 a 48 autour du bloc. Elle arrete les meteores (pic de consommation a chaque
 * impact), protege du rayonnement et des incendies des eruptions solaires. Entretien proportionnel au carre du rayon, double pendant une
 * eruption. Faute d'energie pour encaisser un impact, le champ s'effondre quelques secondes.
 * <p>
 * Interface : 4 rayon, 5 bouclier actif, 6 effondrement restant (ticks), 7 impacts encaisses, 8 bulle visible, 9 entretien du rayon
 * (FE/t), 10 eruption en cours. Actions : {@link #INCREASE}, {@link #DECREASE}, {@link #TOGGLE_VISIBLE}.
 * <p>
 * API Machine (lot 8c) : logique dans {@link ForceFieldUnit} (fiche {@code migrated}) ; cette tile la porte ({@link #carry}) et garde le
 * registre des champs (meteores, eruptions) et le rendu de la bulle.
 */
public class TileEntityForceField extends MachineTile {

    public static final int MIN_RADIUS = 4, MAX_RADIUS = 48, STEP = 4;
    /** 1.0 : rayon le plus grand par tier (MKI a MKIV). */
    public static final int[] TIER_RADIUS = {16, 24, 36, 48};
    public static final long ENERGY_STORAGE_FE = 16_000_000;
    public static final int INCREASE = CUSTOM, DECREASE = CUSTOM + 1, TOGGLE_VISIBLE = CUSTOM + 2;
    public static final int BATTERY_X = StandardLayout.BATTERY_X, BATTERY_Y = StandardLayout.BATTERY_Y;

    /** Champs charges cote serveur, par dimension. */
    private static final Map<ResourceKey<Level>, Set<TileEntityForceField>> FIELDS = new HashMap<>();

    /** API Machine (lot 8c) : la logique (entretien, impacts, effondrement, bulle) est dans l'unite portee. */
    private final ForceFieldUnit unit;

    public TileEntityForceField(BlockPos pos, BlockState state) {
        super(PiSpaceMachines.FORCE_FIELD_TILE.get(), pos, state, ENERGY_STORAGE_FE, (int) Math.min(Integer.MAX_VALUE,
              getUpkeep(TIER_RADIUS[TieredMachineBlock.tierOf(state)], true) * 4));
        unit = carry(new ForceFieldUnit(access()));
    }

    @Override
    protected Set<SideMode> allowedModes() {
        return EnumSet.of(SideMode.NONE, SideMode.INPUT);
    }

    // ------------------------------------------------------------------
    // Registre des champs (serveur)
    // ------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            FIELDS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(this);
        }
    }

    @Override
    public void setRemoved() {
        unregister();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        unregister();
        super.onChunkUnloaded();
    }

    private void unregister() {
        if (level != null && !level.isClientSide) {
            Set<TileEntityForceField> set = FIELDS.get(level.dimension());
            if (set != null) {
                set.remove(this);
            }
        }
    }

    /** Champs d'une dimension (ensemble concurrent : parcours sans copie, appele a chaque tick par les meteores). */
    private static Set<TileEntityForceField> fields(Level level) {
        Set<TileEntityForceField> set = FIELDS.get(level.dimension());
        return set == null ? Set.of() : set;
    }

    public static void clearRegistry() {
        FIELDS.clear();
    }

    private boolean contains(Vec3 point) {
        return unit.shielding && !isRemoved() && point.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= (double) unit.radius * unit.radius;
    }

    /** Un point est-il a l'abri d'un champ de force actif ? */
    public static boolean protects(Level level, Vec3 point) {
        for (TileEntityForceField field : fields(level)) {
            if (field.contains(point)) {
                return true;
            }
        }
        return false;
    }

    /** Un meteore entre dans un champ : le champ l'arrete s'il a l'energie de l'impact (renvoie true), sinon il s'effondre et laisse passer. */
    public static boolean intercept(ServerLevel level, Vec3 point, float size) {
        for (TileEntityForceField field : fields(level)) {
            if (field.contains(point)) {
                return field.unit.absorb(level, point, size);
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

    @Override
    protected void tick() {
    }

    public static long getUpkeep(int radius, boolean flare) {
        return PiCommonConfig.get(PiCommonConfig.FORCE_FIELD_UPKEEP, 8L) * radius * radius * (flare ? 2 : 1);
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        unit.action(action);
    }

    public int getRadius() {
        return unit.radius;
    }

    /** Rayon le plus grand de ce tier. */
    public int maxRadius() {
        return unit.maxRadius();
    }

    public boolean isVisible() {
        return unit.visible;
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return StandardLayout.serviceParts(true, StandardLayout.setting(StandardLayout.WIDTH, StandardLayout.STEP_A), StandardLayout.setting(StandardLayout.WIDTH, StandardLayout.STEP_B));
    }

    /** API Machine (lot 4) : valeurs NOMMEES (lot 8c : declarees par l'unite, memes cles et indices). */
    public static final UiValues.Key RADIUS = ForceFieldUnit.RADIUS, SHIELDING = ForceFieldUnit.SHIELDING, COLLAPSE = ForceFieldUnit.COLLAPSE,
          IMPACTS = ForceFieldUnit.IMPACTS, VISIBLE = ForceFieldUnit.VISIBLE, UPKEEP = ForceFieldUnit.UPKEEP, FLARE = ForceFieldUnit.FLARE;

    // ------------------------------------------------------------------
    // Client : rendu de la bulle
    // ------------------------------------------------------------------

    /** Le bloc est-il actif (etat {@code active}) ? */
    public boolean getActive() {
        BlockState state = getBlockState();
        return state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE);
    }

    public boolean isClientShielding() {
        return unit.clientShielding;
    }

    public int getClientRadius() {
        return unit.clientRadius;
    }

    public long getLastImpactTime() {
        return unit.lastImpactTime;
    }

    public Vec3 getLastImpactDir() {
        return unit.lastImpactDir;
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(Math.max(unit.radius, unit.clientRadius) + 1);
    }
}
