package fr.pioneers.tech.bronze.steam;

import fr.pioneers.api.machine.StandardLayout.Part;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.tech.kinetics.KineticMachineTile;
import fr.pioneers.tech.kinetics.KineticsConfig;
import fr.pioneers.core.registries.PiBronze;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Machine a vapeur : 1.0.2-beta (lot 3, plus de pression) : couple de demarrage {@link SteamRules#ENGINE_TORQUE} N.m tant que le coffre
 * fournit sa prise, 120 tr/min a vide ; prend {@link SteamRules#ENGINE_USE} mB de vapeur par tr/min et par tick (au moins a 10 tr/min). Coffre
 * a court : couple au prorata. La vapeur humide (primage) fait perdre jusqu'a moitie ; un condenseur devant l'echappement ajoute 25 %.
 * API Machine (lot 8b) : la tile ne fait plus que PORTER son unite ({@link SteamEngineUnit} : pression utile, vapeur prise, condenseur,
 * bouffees) par l'adaptateur cinetique {@link KineticMachineTile} ; elle reste la SOURCE du reseau de rotation et le noeud de vapeur (coffre
 * de {@link SteamRules#ENGINE_RESERVE} mB, faces arriere et dessous, echanges). Type de tile {@code steam_engine}, cles de sauvegarde
 * inchangees ; interface v2 (reserve de vapeur a la place de l'energie motrice, vitesse de sortie, carte, ecran).
 */
public class SteamEngineTile extends KineticMachineTile implements SteamNode {

    public static final double TORQUE = SteamRules.ENGINE_TORQUE, FREE = SteamRules.ENGINE_FREE, USE = SteamRules.ENGINE_USE;
    /** Interface v2 : vitesse de sortie a droite du manometre, carte des mesures. */
    public static final Rect SPEED = StandardLayout.ageGauge(0), CARD = StandardLayout.ageCard(StandardLayout.ageGauge(1).x() + 4, 0);
    private final SteamVolume chest = new SteamVolume(SteamRules.ENGINE_RESERVE);
    private final SteamEngineUnit unit;

    public SteamEngineTile(BlockPos pos, BlockState state) {
        super(PiBronze.STEAM_ENGINE_TILE.get(), pos, state);
        unit = carry(new SteamEngineUnit(host, this, new PowerSource.Steam(chest), power));
    }

    public SteamEngineUnit engine() {
        return unit;
    }

    public Direction facing() {
        return getBlockState().getValue(SteamEngineBlock.FACING);
    }

    /** Part du couple disponible (0..1,25) : vapeur fournie, humidite, condenseur. */
    public double effective() {
        return unit.effective();
    }

    // ------------------------------------------------------------------
    // Reseau de rotation
    // ------------------------------------------------------------------

    @Override
    public double shaftFactor(Direction face) {
        return face.getAxis() == facing().getClockWise().getAxis() ? 1 : 0;
    }

    @Override
    public double sourceTorque() {
        return TORQUE * unit.effective() * KineticsConfig.sourcePower();
    }

    @Override
    public double sourceFreeSpeed() {
        return FREE;
    }

    @Override
    public double inertia() {
        return 60;
    }

    @Override
    public double maxTorque() {
        return 16_000;
    }

    @Override
    public double maxSpeed() {
        return 200;
    }

    /** Une source sans donnees propres pour le client (comme avant). */
    @Override
    protected boolean ownClientData() {
        return false;
    }

    // ------------------------------------------------------------------
    // Vapeur
    // ------------------------------------------------------------------

    @Override
    public SteamVolume steam(Direction side) {
        Direction f = facing();
        return side == f.getOpposite() || side == Direction.DOWN ? chest : null;
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        Steam.exchangeAll(level, this);
        super.serverTick(level);
    }

    @Override
    public void describe(List<Component> lines) {
        super.describe(lines);
        lines.add(Component.translatable("steam.pioneers.engine_info", Math.round(chest.steam()), String.format(Locale.ROOT, "%.1f", unit.used())));
    }

    @Override
    public List<Part> guiParts() {
        return StandardLayout.ageService(false, StandardLayout.framed("gauge", SPEED), new Part("card", CARD));
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        chest.save(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        chest.load(tag);
    }
}
