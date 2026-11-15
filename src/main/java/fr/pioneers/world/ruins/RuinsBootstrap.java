package fr.pioneers.world.ruins;

import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerApi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 1) : chargement des ruines (registres, donnees, Sentinelles, generation) et regles du monde : casser un bloc delabre
 * eveille le site dans 12 blocs (sauf en Veille) et entame la ruine ; une ruine en chantier de restauration protege ses blocs (pas ses
 * gravats, qui ne sont pas la machine).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class RuinsBootstrap {

    private RuinsBootstrap() {
    }

    public static void init(IEventBus modBus) {
        RuinBlocks.init();
        RuinItems.init();
        RuinLoot.FUNCTIONS.register(modBus);
        Console.init();
        Restoration.init();
        fr.pioneers.world.ruins.sentinel.Sentinels.init(modBus);
        fr.pioneers.world.ruins.gen.RuinGen.init(modBus);
    }

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent event) {
        event.addListener(LoreIndex.INSTANCE);
        event.addListener(RuinCatalog.INSTANCE);
        event.addListener(Restoration.Catalog.INSTANCE);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getState().getBlock() instanceof DerelictBlock)) {
            return;
        }
        RuinSites sites = RuinSites.get(level);
        RuinSites.Site site = sites.siteAt(event.getPos(), 2);
        if (site == null) {
            return;
        }
        //1.9 (MAJ RP, lot 19) : les gravats ne sont pas la machine. Ils se retirent pendant le chantier (etape Degagement, qui exige qu'il
        //n'en reste aucun) et ne comptent pas comme un demontage ; avant, le chantier les protegeait et la restauration restait bloquee
        boolean rubble = event.getState().getBlock() instanceof TechRubbleBlock;
        if (!rubble && (site.state == RuinSites.State.RESTORING || site.state == RuinSites.State.RESTORED)) {
            event.setCanceled(true);
            return;
        }
        if (!site.standby(level.getGameTime())) {
            fr.pioneers.world.ruins.sentinel.Sentinels.wakeAround(level, event.getPos(), 12);
        }
        if (event.getPlayer() instanceof ServerPlayer player && !player.isCreative()) {
            if (site.state == RuinSites.State.INTACT && !rubble) {
                site.state = RuinSites.State.DISMANTLED;
                MinecraftForge.EVENT_BUS.post(new RuinEvents.Dismantled(player, site, event.getPos(), level));
            }
            site.broken++;
            sites.changed();
            PioneerApi.gain(player, "salvage.derelict", 1, event.getPos());
        }
    }
}
