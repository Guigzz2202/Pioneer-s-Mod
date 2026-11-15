package fr.pioneers.world.ruins;

import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * 1.9 (MAJ RP, bloc 1) : Console de perimetre. Le bon code (4 chiffres, tire de la graine et du site) met le site en Veille un jour de jeu ;
 * trois codes faux declenchent l'alarme (tout le site s'eveille) et verrouillent la console 5 minutes. Le serveur decide de tout.
 */
public final class Console {

    public static final long LOCK_TICKS = 6000;
    public static final int MAX_WRONG = 3;

    private Console() {
    }

    static {
        RpNet.onServer(RpActions.CONSOLE_CODE, Console::submit);
    }

    static void init() {
    }

    static void open(ServerPlayer player, ServerLevel level, BlockPos pos, SiteTile tile) {
        RuinSites.Site site = RuinSites.get(level).site(tile.site());
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos.asLong());
        t.putInt("tier", tile.getBlockState().getBlock() instanceof DerelictBlock d ? d.tier : 1);
        state(level, site, t);
        RpNet.send(player, RpActions.CONSOLE_OPEN, t);
    }

    private static void state(ServerLevel level, RuinSites.Site site, CompoundTag t) {
        long now = level.getGameTime();
        t.putBoolean("dead", site == null);
        if (site != null) {
            t.putLong("standby", Math.max(0, site.standbyUntil - now));
            t.putLong("locked", Math.max(0, site.lockedUntil - now));
            t.putInt("wrong", site.wrong);
        }
    }

    private static void submit(ServerPlayer player, CompoundTag t) {
        BlockPos pos = BlockPos.of(t.getLong("pos"));
        ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(pos.getCenter()) > 36 || !(level.getBlockEntity(pos) instanceof SiteTile tile)) {
            return;
        }
        RuinSites.Site site = RuinSites.get(level).site(tile.site());
        if (site == null) {
            return;
        }
        long now = level.getGameTime();
        String typed = t.getString("code");
        CompoundTag r = new CompoundTag();
        r.putLong("pos", pos.asLong());
        if (now < site.lockedUntil) {
            r.putString("result", "locked");
        } else if (typed.equals(String.format("%04d", site.code))) {
            RuinApi.setStandby(level, site, player);
            level.playSound(null, pos, RpSounds.SHUTDOWN.get(), SoundSource.BLOCKS, 0.8F, 1.2F);
            player.displayClientMessage(Component.translatable("ruins.pioneers.console.standby").withStyle(ChatFormatting.AQUA), true);
            PioneerApi.gain(player, "salvage.derelict", 2, pos);
            r.putString("result", "ok");
        } else {
            site.wrong++;
            level.playSound(null, pos, RpSounds.KEYPAD.get(), SoundSource.BLOCKS, 0.8F, 0.6F);
            if (site.wrong >= MAX_WRONG) {
                site.wrong = 0;
                site.lockedUntil = now + LOCK_TICKS;
                site.standbyUntil = -1;
                level.playSound(null, pos, RpSounds.ALARM.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
                fr.pioneers.world.ruins.sentinel.Sentinels.wakeAround(level, site.controller(), 64);
                player.displayClientMessage(Component.translatable("ruins.pioneers.console.alarm").withStyle(ChatFormatting.RED), true);
                r.putString("result", "alarm");
            } else {
                r.putString("result", "wrong");
            }
            RuinSites.get(level).changed();
        }
        state(level, site, r);
        RpNet.send(player, RpActions.CONSOLE_STATE, r);
    }
}
