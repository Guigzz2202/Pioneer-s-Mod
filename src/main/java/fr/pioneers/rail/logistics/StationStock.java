package fr.pioneers.rail.logistics;

import fr.pioneers.rail.net.RailNetwork;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * 0.20.5 : ce que le moteur du rail sait des gares dans le monde : contenu des postes du quai, stocks du reseau robotique, commandes des
 * terminaux, poste -> gare, redstone du panneau. Le jeu fournit {@link WorldStationStock} ; le banc d'essai, une version factice.
 */
public interface StationStock {

    /** Contenu des postes du quai (relu au plus chaque seconde). */
    Map<Cargo, Long> stock(RailNetwork.Station station);

    /** Stock du reseau robotique qui couvre la gare ; -1 : pas de reseau. */
    long network(RailNetwork.Station station, Cargo cargo);

    /** Ce qui manque au reseau de la gare pour les commandes de ses terminaux. */
    Map<Cargo, Long> terminalMissing(RailNetwork.Station station);

    /** Gare (position du panneau) servie par ce poste ; 0 : aucune. */
    long stationOf(BlockPos post);

    /** Puissance de redstone recue par le panneau. */
    int redstone(RailNetwork.Station station);

    StationStock NONE = new StationStock() {
        @Override
        public Map<Cargo, Long> stock(RailNetwork.Station station) {
            return Map.of();
        }

        @Override
        public long network(RailNetwork.Station station, Cargo cargo) {
            return -1;
        }

        @Override
        public Map<Cargo, Long> terminalMissing(RailNetwork.Station station) {
            return Map.of();
        }

        @Override
        public long stationOf(BlockPos post) {
            return 0;
        }

        @Override
        public int redstone(RailNetwork.Station station) {
            return 0;
        }
    };
}
