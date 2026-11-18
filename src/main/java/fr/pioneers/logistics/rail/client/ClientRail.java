package fr.pioneers.logistics.rail.client;

import fr.pioneers.logistics.robotics.client.RoboticsMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/** 0.20.0 : reception des fiches du chemin de fer (train, gare) cote client. */
public final class ClientRail {

    private ClientRail() {
    }

    public static void info(int kind, CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        if (kind == 0) {
            if (mc.screen instanceof TrainScreen ts && ts.trainId().equals(data.getUUID("id"))) {
                ts.update(data);
            } else if (data.getBoolean("open")) {
                //0.20.5 : ouverte depuis la carte, elle y revient a la fermeture
                mc.setScreen(new TrainScreen(data, mc.screen instanceof RoboticsMapScreen map ? map : null));
            }
        } else if (kind == 1) {
            if (mc.screen instanceof StationScreen ss && ss.pos() == data.getLong("pos")) {
                ss.update(data);
            } else if (data.getBoolean("open")) {
                mc.setScreen(new StationScreen(data));
            }
        }
    }
}
