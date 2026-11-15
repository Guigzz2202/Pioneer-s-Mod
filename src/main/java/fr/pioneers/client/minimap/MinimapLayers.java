package fr.pioneers.client.minimap;

import static fr.pioneers.client.minimap.MinimapConfig.num;
import static fr.pioneers.client.minimap.MinimapConfig.on;

/**
 * 1.10.3 : calques et filtres lus UNE fois par image depuis {@link MinimapConfig} (la minimap et la carte plein ecran partagent les memes
 * reglages ; les interrupteurs de la carte plein ecran changent la config).
 */
public final class MinimapLayers {

    public boolean claims, rail, stations, sea, roads, robotics, points, waypoints, entities, players, mates, grid;
    public boolean hostile, passive, items, vehicles, robots, names, playerNames, waypointNames;
    public int radius, height;
    public int cHostile, cPassive, cItem, cPlayer, cSelf, cRail, cRoad, cText;

    public MinimapLayers read() {
        claims = on(MinimapConfig.L_CLAIMS, true);
        rail = on(MinimapConfig.L_RAIL, true);
        stations = on(MinimapConfig.L_STATIONS, true);
        sea = on(MinimapConfig.L_SEA, true);
        roads = on(MinimapConfig.L_ROADS, true);
        robotics = on(MinimapConfig.L_ROBOTICS, true);
        points = on(MinimapConfig.L_POINTS, true);
        waypoints = on(MinimapConfig.L_WAYPOINTS, true);
        entities = on(MinimapConfig.L_ENTITIES, true);
        players = on(MinimapConfig.L_PLAYERS, true);
        mates = on(MinimapConfig.L_MATES, true);
        grid = on(MinimapConfig.L_GRID, false);
        hostile = on(MinimapConfig.E_HOSTILE, true);
        passive = on(MinimapConfig.E_PASSIVE, true);
        items = on(MinimapConfig.E_ITEMS, false);
        vehicles = on(MinimapConfig.E_VEHICLES, true);
        robots = on(MinimapConfig.E_ROBOTS, true);
        names = on(MinimapConfig.E_NAMES, false);
        playerNames = on(MinimapConfig.E_PLAYER_NAMES, true);
        waypointNames = on(MinimapConfig.W_NAMES, false);
        radius = num(MinimapConfig.E_RADIUS, 64);
        height = num(MinimapConfig.E_HEIGHT, 32);
        cHostile = num(MinimapConfig.C_HOSTILE, 0xE5484D);
        cPassive = num(MinimapConfig.C_PASSIVE, 0x6CD46C);
        cItem = num(MinimapConfig.C_ITEM, 0xF2D45C);
        cPlayer = num(MinimapConfig.C_PLAYER, 0xFFFFFF);
        cSelf = num(MinimapConfig.C_SELF, 0x3FD0A0);
        cRail = num(MinimapConfig.C_RAIL, 0xC8CED6);
        cRoad = num(MinimapConfig.C_ROAD, 0xD8B070);
        cText = num(MinimapConfig.C_TEXT, 0xE0F5EA);
        return this;
    }
}
