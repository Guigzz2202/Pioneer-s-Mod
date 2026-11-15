package fr.pioneers.farm.device;

import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.GeneratedFarm;
import fr.pioneers.farm.crop.Climate;
import fr.pioneers.farm.crop.CropDef;
import fr.pioneers.farm.tree.FruitLeavesBlock;
import fr.pioneers.farm.tree.TreeDef;
import fr.pioneers.farm.ui.FarmLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Station meteo : temperature (°C = 20 x t + 4, t du biome abaissee en altitude), humidite, temps ; clic droit : cultures et arbres fruitiers
 * idéaux et tolérables ici (terre irriguee). Redstone : 8 sous la pluie, 15 sous l'orage (voisins prevenus au changement). Rendu : anemometre
 * (vitesse selon le temps et l'altitude), girouette, colonne du thermometre.
 */
public class WeatherStationTile extends FarmDeviceTile {

    private int signal = -1;
    /** Cote client : angle des coupelles, de la girouette. */
    public float cups, lastCups, vane, lastVane;

    public WeatherStationTile(BlockPos pos, BlockState state) {
        super(pos, state, 0, 0);
    }

    /** 1.0.5-beta (lot 41, retour 60) : un instrument : son interface est un grand ecran (releves, cultures et arbres adaptes ; avant : au chat). */
    @Override
    protected FarmLayout layout() {
        return FarmLayout.builder().tall().build();
    }

    /** Progres « station meteo » : a la lecture (ouverture de l'interface). */
    @Override
    public void opened(Player player) {
        sound(SoundEvents.SPYGLASS_USE, 0.6F, 1.2F);
        PiFarmDevices.award(level, worldPosition, "weather");
    }

    public static int celsius(float t) {
        return Math.round(t * 20 + 4);
    }

    @Override
    public void serverTick() {
        if (level == null || level.getGameTime() % 20 != 3) {
            return;
        }
        int s = getBlockState().getSignal(level, worldPosition, Direction.UP);
        if (s != signal) {
            signal = s;
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    /** Vent (client et serveur) : 0..~2,5 ; tourne avec l'heure. */
    public static float wind(Level level, BlockPos pos) {
        double t = level.getGameTime() / 1200.0;
        float base = 0.6F + 0.25F * (float) Math.sin(t * 1.7 + (pos.asLong() & 63) / 9.0) + 0.1F * (float) Math.sin(t * 7.3);
        float weather = level.isThundering() ? 2.2F : level.isRaining() ? 1.5F : 1F;
        float altitude = Mth.clamp((pos.getY() - level.getSeaLevel()) / 60F, 0F, 1F) * 0.6F + 0.7F;
        return base * weather * altitude;
    }

    /** Direction du vent (degres). */
    public static float windDirection(Level level) {
        double t = level.getGameTime() / 6000.0;
        return (float) (Math.sin(t) * 120 + Math.sin(t * 3.1) * 40) + 200;
    }

    @Override
    public void clientTick() {
        if (level == null) {
            return;
        }
        lastCups = cups;
        lastVane = vane;
        cups += wind(level, worldPosition) * 14F;
        float target = windDirection(level) + (float) Math.sin(level.getGameTime() * 0.11 + worldPosition.getX()) * 6F * wind(level, worldPosition);
        vane += Mth.wrapDegrees(target - vane) * 0.08F;
    }

    private static Component join(List<Component> parts) {
        if (parts.isEmpty()) {
            return Component.translatable("farm.pioneers.device.weather.none");
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(parts.get(i));
        }
        return out;
    }

    private static Component weather(Level level) {
        return Component.translatable("farm.pioneers.device.weather." + (level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear"));
    }

    @Override
    protected int accent() {
        return 0x5AD2E6;
    }

    @Override
    protected void describe(FarmCard card, boolean sneaking) {
        if (level == null) {
            return;
        }
        BlockPos at = worldPosition.above();
        float t = Climate.temperature(level, at), h = Climate.humidity(level, at);
        int c = celsius(t);
        //1.6 : valeurs alignees ; le detail des cultures reste au clic droit
        card.add(FarmCard.Line.gauge(FarmCard.label("temperature"), Mth.clamp((c + 10) / 60F, 0, 1), Component.translatable("card.pioneers.celsius", c),
              c < 5 ? 0x6EB4F0 : c < 25 ? 0x6EDC5A : c < 35 ? 0xF0B040 : 0xF05050));
        card.add(FarmCard.Line.gauge(FarmCard.label("humidity"), h, FarmCard.percent(h), 0x3C78DC));
        card.add(FarmCard.Line.pair(FarmCard.label("sky"), weather(level), 0xD0D0D0));
        card.add(FarmCard.Line.pair(FarmCard.label("wind"), Component.translatable("card.pioneers.kmh", Math.round(wind(level, worldPosition) * 12)), 0xD0D0D0));
        card.add(FarmCard.Line.hint(FarmCard.word("hint.weather")));
        //1.0.5-beta (lot 41) : les cultures et arbres adaptes ici (avant : messages au chat au clic droit), dans l'ecran de l'interface
        List<Component> ideal = new ArrayList<>(), ok = new ArrayList<>(), trees = new ArrayList<>();
        for (CropDef def : GeneratedFarm.CROPS) {
            Climate.Status st = Climate.evaluate(level, worldPosition, def.tMin(), def.tMax(), def.hMin(), def.hMax(), 0.09F, true);
            Component name = Component.translatable("farm.pioneers.crop." + def.id());
            if (st == Climate.Status.IDEAL || st == Climate.Status.GREENHOUSE) {
                ideal.add(name);
            } else if (st == Climate.Status.TOLERABLE) {
                ok.add(name);
            }
        }
        for (TreeDef def : GeneratedFarm.TREES) {
            Climate.Status st = Climate.evaluate(level, worldPosition, def.tMin(), def.tMax(), def.hMin(), def.hMax(), FruitLeavesBlock.TOLERANCE, false);
            if (st.grows()) {
                trees.add(Component.translatable("farm.pioneers.tree." + def.id()));
            }
        }
        card.add(FarmCard.Line.pair(Component.translatable("gui.pioneers.farm.weather.ideal", ideal.size()), join(ideal), FarmCard.OK));
        card.add(FarmCard.Line.pair(Component.translatable("gui.pioneers.farm.weather.tolerable", ok.size()), join(ok), FarmCard.WARN));
        card.add(FarmCard.Line.pair(Component.translatable("gui.pioneers.farm.weather.trees", trees.size()), join(trees), 0xF0C040));
    }
}
