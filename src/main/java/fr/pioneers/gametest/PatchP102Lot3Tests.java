package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.machines.SteamBlowerTile;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamProcess;
import fr.pioneers.tech.bronze.machines.SteamProcessTile;
import fr.pioneers.tech.bronze.steam.FireboxTile;
import fr.pioneers.tech.bronze.steam.SteamEngineTile;
import fr.pioneers.tech.bronze.steam.SteamRules;
import fr.pioneers.tech.bronze.steam.SteamVolume;
import fr.pioneers.tech.bronze.PiBronze;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.2-beta, lot 3 (GameTests) : la vapeur sans pression.
 * <ul>
 *     <li>une machine de traitement prend 3 mB de vapeur par tick de travail, a duree fixe ; reserve vide = « pas de vapeur » ;</li>
 *     <li>une chaudiere chauffee sans eau explose (config par defaut ON) ; avec de l'eau, rien (plus de surpression) ;</li>
 *     <li>aller-retour de sauvegarde des machines a vapeur (memes cles {@code steam} / {@code wet}) ; une reserve d'avant plus pleine que la
 *     contenance est ramenee a la contenance.</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP102Lot3Tests {

    private PatchP102Lot3Tests() {
    }

    private static <T extends BlockEntity> T place(GameTestHelper h, BlockPos rel, Block block, Class<T> type) {
        h.setBlock(rel, block.defaultBlockState());
        BlockEntity be = h.getBlockEntity(rel);
        Gt.check(type.isInstance(be), "pas de tuile " + type.getSimpleName());
        return type.cast(be);
    }

    // ================================================================== consommation a debit fixe
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_steam_use", timeoutTicks = 120)
    public static void p102_vapeur_consommee(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Block macerator = PiBronze.STEAM_MACHINES.get(SteamMachine.MACERATOR).getBlock();
        SteamProcessTile a = place(h, new BlockPos(2, 2, 2), macerator, SteamProcessTile.class);
        SteamProcessTile dry = place(h, new BlockPos(6, 2, 6), macerator, SteamProcessTile.class);
        a.inputs().get(0).setStack(new ItemStack(Items.RAW_COPPER, 4));
        dry.inputs().get(0).setStack(new ItemStack(Items.RAW_COPPER, 4));
        SteamVolume v = a.steam(Direction.DOWN);
        Gt.equal((long) SteamRules.PROCESS_RESERVE, (long) v.capacity(), "reserve d'une machine de traitement");
        v.add(1_000, 0);
        h.runAtTickTime(41, () -> {
            double used = 1_000 - v.steam();
            Gt.check(used > SteamProcess.STEAM_PER_TICK * 25 && used <= SteamProcess.STEAM_PER_TICK * 41 + 1e-6,
                  "3 mB de vapeur par tick de travail : " + used + " mB en 41 ticks");
            Gt.equal(SteamProcess.WORKING, a.process().status(), "la machine travaille");
            Gt.check(a.process().work() > 0, "elle avance");
            Gt.equal(SteamProcess.NO_STEAM, dry.process().status(), "sans vapeur : « pas de vapeur »");
            Gt.check(dry.process().work() == 0, "sans vapeur, rien n'avance");
            s.done();
        });
    }

    // ================================================================== chaudiere : le seul danger est de chauffer sans eau
    private static FireboxTile boiler(GameTestHelper h, BlockPos at) {
        //1.0.6 : forme de Guillaume du lot 24 (l'ancienne colonne foyer / corps / dome n'est plus formee) ; at = coin de la forme
        FireboxTile f = MultiblockApi6Tests.boiler(h, at, 0, null);
        f.guiSlots().get(0).setStack(new ItemStack(Items.COAL, 16));
        return f;
    }

    @GameTest(template = Gt.LARGE, batch = "pioneers_p102_boiler_dry", timeoutTicks = 400)
    public static void p102_chaudiere_a_sec(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        //1.0.6 : gabarit de 32 : l'explosion de la chaudiere de Guillaume (11 corps, puissance 5,25) ne doit pas toucher sa voisine
        BlockPos wet = new BlockPos(3, 1, 3), dry = new BlockPos(26, 1, 26);
        FireboxTile w = boiler(h, wet);
        //positions ABSOLUES des foyers (GameTestHelper.relativePos de la 1.20.1 tourne de 180 degres : inutilisable)
        BlockPos dryAt = boiler(h, dry).getBlockPos().immutable(), wetAt = w.getBlockPos().immutable();
        net.minecraft.server.level.ServerLevel level = h.getLevel();
        int half = w.waterCapacity() / 2;
        Gt.equal(half, w.fillWater(half, false), "eau acceptee (la moitie de la contenance)");
        int shells = MultiblockApi6Tests.count(fr.pioneers.api.multiblock.HouseShapes.BOILER, 0,
              PiBronze.BOILER_PARTS.get(fr.pioneers.tech.bronze.steam.BoilerPart.COPPER_SHELL).getBlock());
        int domes = MultiblockApi6Tests.count(fr.pioneers.api.multiblock.HouseShapes.BOILER, 0,
              PiBronze.BOILER_PARTS.get(fr.pioneers.tech.bronze.steam.BoilerPart.STEAM_DOME).getBlock());
        Gt.equal((long) (shells * SteamRules.COPPER_SHELL_STEAM + domes * SteamRules.DOME_STEAM + 1), (long) w.steamCapacity(),
              "reserve de vapeur = corps + domes");
        //diagnostic : dernier etat vu de la chaudiere remplie (tick, formee, eau, temperature)
        String[] seen = {"jamais vue"};
        h.onEachTick(() -> {
            if (level.getBlockEntity(wetAt) instanceof FireboxTile f) {
                seen[0] = "tick " + h.getTick() + ", formee " + f.formed() + ", eau " + Math.round(f.water()) + " / " + f.waterCapacity()
                      + ", " + Math.round(f.temperature()) + " C";
            }
        });
        h.runAtTickTime(330, () -> {
            Gt.check(level.getBlockState(dryAt).is(Blocks.AIR) || !(level.getBlockEntity(dryAt) instanceof FireboxTile),
                  "la chaudiere chauffee sans eau n'a pas explose");
            Gt.check(level.getBlockEntity(wetAt) instanceof FireboxTile f && f.formed(), "la chaudiere remplie d'eau a saute : " + level.getBlockState(wetAt)
                  + " ; vue en dernier : " + seen[0]);
            Gt.check(w.temperature() <= SteamRules.BOIL_C + 1e-6, "avec de l'eau, jamais au-dessus de 100 C");
            s.done();
        });
    }

    // ================================================================== sauvegardes des machines a vapeur
    private static CompoundTag roundTrip(BlockEntity a, BlockEntity b) {
        CompoundTag tag = a.saveWithoutMetadata();
        Gt.check(tag.contains("steam") && tag.contains("wet"), "cles de vapeur d'avant (steam, wet) absentes");
        b.load(tag.copy());
        Gt.equal(tag, b.saveWithoutMetadata(), "sauvegarde identique apres relecture");
        return tag;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_steam_nbt", timeoutTicks = 20)
    public static void p102_vapeur_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Block furnace = PiBronze.STEAM_MACHINES.get(SteamMachine.FURNACE).getBlock();
        SteamProcessTile a = place(h, new BlockPos(1, 2, 1), furnace, SteamProcessTile.class), b = place(h, new BlockPos(1, 2, 4), furnace, SteamProcessTile.class);
        a.steam(Direction.DOWN).add(1_234, 0.25);
        roundTrip(a, b);
        Gt.check(Math.abs(b.steam(Direction.DOWN).steam() - 1_234) < 1e-6, "vapeur relue");
        Gt.check(Math.abs(b.steam(Direction.DOWN).wet() - 0.25) < 1e-6, "humidite relue");
        SteamEngineTile e = place(h, new BlockPos(4, 2, 1), PiBronze.STEAM_ENGINE.getBlock(), SteamEngineTile.class);
        SteamEngineTile e2 = place(h, new BlockPos(4, 2, 4), PiBronze.STEAM_ENGINE.getBlock(), SteamEngineTile.class);
        e.steam(Direction.DOWN).add(3_000, 0);
        roundTrip(e, e2);
        //1.0.5-beta (lot 38) : la presse a vapeur est une machine de traitement (SteamProcessTile)
        Block pressBlock = PiBronze.STEAM_MACHINES.get(SteamMachine.PRESS).getBlock();
        SteamProcessTile p = place(h, new BlockPos(7, 2, 1), pressBlock, SteamProcessTile.class);
        SteamProcessTile p2 = place(h, new BlockPos(7, 2, 4), pressBlock, SteamProcessTile.class);
        p.steam(Direction.DOWN).add(800, 0);
        roundTrip(p, p2);
        SteamBlowerTile bl = place(h, new BlockPos(1, 2, 7), PiBronze.STEAM_BLOWER.getBlock(), SteamBlowerTile.class);
        SteamBlowerTile bl2 = place(h, new BlockPos(4, 2, 7), PiBronze.STEAM_BLOWER.getBlock(), SteamBlowerTile.class);
        bl.steam(Direction.DOWN).add(500, 0);
        roundTrip(bl, bl2);
        //1.0.4-beta (lot 27) : injecteur retire (son cas de sauvegarde d'avant le lot 3 avec lui)
        s.done();
    }
}
