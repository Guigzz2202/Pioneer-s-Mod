package fr.pioneers.gametest;

import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestReporter;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import org.slf4j.Logger;

/**
 * 1.0.6 : journal des GameTests lances a la commande {@code /test run} sur un serveur de developpement ({@code tools/tests/gt_run.sh}) :
 * le rapporteur vanilla ({@link LogTestReporter}) n'ecrit que les echecs ; celui-ci ecrit aussi « {@code <test> passed!} ». Rien ne change
 * sur le serveur de GameTests ({@code runGameTestServer}, journal lu par la CI) ni hors developpement (GameTests desactives).
 */
@Mod.EventBusSubscriber(modid = "pioneers")
public final class GtReporter implements TestReporter {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final LogTestReporter base = new LogTestReporter();

    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent event) {
        if (ForgeGameTestHooks.isGametestEnabled() && !(event.getServer() instanceof GameTestServer)) {
            GlobalTestReporter.replaceWith(new GtReporter());
        }
    }

    @Override
    public void onTestFailed(GameTestInfo info) {
        base.onTestFailed(info);
    }

    @Override
    public void onTestSuccess(GameTestInfo info) {
        LOGGER.info("{} passed!", info.getTestName());
    }
}
