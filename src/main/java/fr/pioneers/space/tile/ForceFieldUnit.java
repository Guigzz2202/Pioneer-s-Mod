package fr.pioneers.space.tile;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.space.event.SpaceEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * API Machine (lot 8c) : LOGIQUE DU GENERATEUR DE CHAMP DE FORCE (code d'avant de {@link TileEntityForceField}, deplace) : batterie (case
 * declaree par l'unite, seule case comme avant), entretien du rayon (double pendant une eruption), effondrement apres un impact non encaisse,
 * impacts des meteores, bulle visible ou masquee. Cles sauvees : {@code radius}, {@code impacts}, {@code visible} ; client : {@code shielding},
 * {@code visible}, {@code radius}, {@code impactTime}, {@code impactX/Y/Z}. Le registre des champs (meteores) reste sur la tile.
 * <p>
 * Valeurs nommees (apres les 4 communes, memes indices qu'avant) : rayon, bouclier actif, effondrement restant, impacts, bulle visible,
 * entretien du rayon (FE/t), eruption en cours.
 */
public final class ForceFieldUnit extends MachineUnit {

    public static final UiValues.Key RADIUS = UiValues.Key.of("radius"), SHIELDING = UiValues.Key.of("shielding"),
          COLLAPSE = UiValues.Key.of("collapse"), IMPACTS = UiValues.Key.of("impacts"), VISIBLE = UiValues.Key.of("visible"),
          UPKEEP = UiValues.Key.of("upkeep"), FLARE = UiValues.Key.of("flare");
    /** Duree de l'effondrement apres un impact non encaisse (ticks). */
    private static final int COLLAPSE_TICKS = 200;

    private final ItemSlot battery;
    int radius = 16;
    boolean shielding;
    private int collapse;
    private int impacts;
    /** Bulle affichee dans le monde (purement visuel : le champ protege meme masque). */
    boolean visible = true;
    private boolean flareCache;
    /** Rendu : rayon, etat et dernier impact (direction depuis le centre). */
    long lastImpactTime = -100;
    Vec3 lastImpactDir = new Vec3(0, 1, 0);
    boolean clientShielding;
    int clientRadius = 16;

    public ForceFieldUnit(MachineTile.Access machine) {
        super(machine);
        battery = machine.batterySlot(TileEntityForceField.BATTERY_X, TileEntityForceField.BATTERY_Y);
    }

    private static long getUpkeep(int radius, boolean flare) {
        return TileEntityForceField.getUpkeep(radius, flare);
    }

    /** Rayon le plus grand de ce tier. */
    int maxRadius() {
        return TileEntityForceField.TIER_RADIUS[machine.machineTier()];
    }

    boolean absorb(ServerLevel level, Vec3 point, float size) {
        long cost = (long) (PiCommonConfig.get(PiCommonConfig.FORCE_FIELD_IMPACT, 400_000L) * size);
        lastImpactDir = point.subtract(Vec3.atCenterOf(pos())).normalize();
        lastImpactTime = level.getGameTime();
        if (machine.use(cost)) {
            impacts++;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 40, 1.2, 1.2, 1.2, 0.4);
            level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y, point.z, 2, 0.3, 0.3, 0.3, 0);
            level.playSound(null, point.x, point.y, point.z, SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, 4.0F, 0.5F + level.random.nextFloat() * 0.2F);
            level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 3.0F, 1.3F);
            machine.changed();
            machine.markSync();
            return true;
        }
        //Surcharge : le champ tombe, le meteore passe
        machine.energy().setStored(0);
        collapse = COLLAPSE_TICKS;
        shielding = false;
        level.playSound(null, pos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0F, 0.6F);
        machine.markSync();
        return false;
    }

    @Override
    public void tick() {
        machine.discharge(battery, getUpkeep(maxRadius(), true) * 4);
        if (level() instanceof ServerLevel server && level().getGameTime() % 20 == 0) {
            flareCache = SpaceEvents.flareAt(server, pos().getX()) != null;
        }
        boolean wasShielding = shielding;
        shielding = false;
        if (collapse > 0) {
            collapse--;
        } else if (machine.canWork() && machine.use(getUpkeep(radius, flareCache))) {
            shielding = true;
        }
        if (shielding != wasShielding) {
            if (shielding && level() != null) {
                level().playSound(null, pos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 1.2F);
            }
            machine.markSync();
        }
        machine.markWorking(shielding);
    }

    /** Reglages du joueur : bulle visible, rayon. */
    void action(int action) {
        if (action == TileEntityForceField.TOGGLE_VISIBLE) {
            visible = !visible;
        } else if (action == TileEntityForceField.INCREASE || action == TileEntityForceField.DECREASE) {
            radius = Math.max(TileEntityForceField.MIN_RADIUS, Math.min(maxRadius(), radius
                  + (action == TileEntityForceField.INCREASE ? TileEntityForceField.STEP : -TileEntityForceField.STEP)));
        } else {
            return;
        }
        machine.changed();
        machine.markSync();
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(RADIUS, () -> radius).add(SHIELDING, () -> shielding ? 1 : 0).add(COLLAPSE, () -> collapse).add(IMPACTS, () -> impacts)
              .add(VISIBLE, () -> visible ? 1 : 0).add(UPKEEP, () -> getUpkeep(radius, flareCache)).add(FLARE, () -> flareCache ? 1 : 0);
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putBoolean("shielding", shielding);
        tag.putBoolean("visible", visible);
        tag.putInt("radius", radius);
        tag.putLong("impactTime", lastImpactTime);
        tag.putDouble("impactX", lastImpactDir.x);
        tag.putDouble("impactY", lastImpactDir.y);
        tag.putDouble("impactZ", lastImpactDir.z);
    }

    @Override
    public void readClient(CompoundTag tag) {
        clientShielding = tag.getBoolean("shielding");
        visible = !tag.contains("visible") || tag.getBoolean("visible");
        clientRadius = tag.contains("radius") ? tag.getInt("radius") : clientRadius;
        lastImpactTime = tag.getLong("impactTime");
        lastImpactDir = new Vec3(tag.getDouble("impactX"), tag.getDouble("impactY"), tag.getDouble("impactZ"));
    }

    @Override
    public void load(CompoundTag tag) {
        radius = tag.contains("radius") ? Math.max(TileEntityForceField.MIN_RADIUS, Math.min(maxRadius(), tag.getInt("radius"))) : 16;
        impacts = tag.getInt("impacts");
        visible = !tag.contains("visible") || tag.getBoolean("visible");
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("radius", radius);
        tag.putInt("impacts", impacts);
        tag.putBoolean("visible", visible);
    }
}
