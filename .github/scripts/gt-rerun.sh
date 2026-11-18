#!/bin/bash
# GameTests rejoues SEULS (CI, 1.0.6) : un serveur de jeu avec RCON, chaque test lance par « test run <nom> » apres « test clearall ».
# Usage : gt-rerun.sh <dossier du mod> <dossier de sortie> <test>... ; resultats dans <sortie>/resultats.txt (« OK t » / « ECHEC t : ... »).
# Sert a la CI (.github/workflows/build.yml) : un echec nouveau de la serie complete qui passe seul est un test sensible a l'ordre des
# series (heure du monde, entites laissees), pas une regression. Copie de tools/tests/gt_run.sh du depot des outils, sans le verrou Java.
MOD=${1:?dossier du mod}; OUT=${2:?dossier de sortie}; shift 2
[ $# -gt 0 ] || { echo "aucun test"; exit 2; }
mkdir -p "$OUT"; OUT=$(cd "$OUT" && pwd); LOG="$OUT/server.log"; RES="$OUT/resultats.txt"; rm -f "$LOG" "$RES"
cd "$MOD" || exit 2
mkdir -p run && echo "eula=true" > run/eula.txt
rm -rf run/world
cat > run/server.properties <<'EOF2'
level-type=minecraft\:flat
generate-structures=false
spawn-animals=true
spawn-monsters=true
spawn-npcs=true
online-mode=false
view-distance=6
simulation-distance=6
enable-rcon=true
rcon.port=25599
rcon.password=gt
EOF2
RCON="python3 -I $(cd "$(dirname "$0")" && pwd)/rcon.py 25599 gt"
( exec ./gradlew runServer --console=plain > "$LOG" 2>&1 ) &
GPID=$!
ok=0; n=0; ready=0
for i in $(seq 1 360); do   # 30 min au plus pour demarrer (attente du verrou comprise)
  sleep 5
  if grep -qE "RCON running on" "$LOG"; then ready=1; break; fi
  if grep -qE "Crash report saved|---- Minecraft Crash Report ----" "$LOG" || ! kill -0 $GPID 2>/dev/null; then break; fi
done
if [ $ready = 1 ]; then
  $RCON "gamerule doMobSpawning false" "gamerule doWeatherCycle false" "gamerule doDaylightCycle false" "weather clear 1000000" > /dev/null
  for t in "$@"; do
    n=$((n + 1))
    $RCON "test clearall" > /dev/null; sleep 2
    before=$(wc -l < "$LOG")
    reply=$($RCON "test run $t" | head -c 300)
    verdict=""
    for j in $(seq 1 600); do   # 10 min au plus par test
      line=$(tail -n +$((before + 1)) "$LOG" | grep -E "\]: $t (failed|passed)!" | head -n 1)
      if [ -n "$line" ]; then verdict="$line"; break; fi
      grep -qE "Crash report saved" "$LOG" && break
      sleep 1
    done
    if echo "$verdict" | grep -q " passed"; then
      ok=$((ok + 1)); echo "OK $t" | tee -a "$RES"
    elif [ -n "$verdict" ]; then
      echo "ECHEC $t : $(echo "$verdict" | sed -E 's/.*failed! //' | cut -c1-300)" | tee -a "$RES"
    else
      echo "ECHEC $t : pas de verdict (reponse : $reply)" | tee -a "$RES"
    fi
  done
  $RCON "stop" > /dev/null 2>&1
  for j in $(seq 1 60); do sleep 2; kill -0 $GPID 2>/dev/null || break; done
else
  echo "SERVEUR NON DEMARRE (voir $LOG)" | tee -a "$RES"
fi
kill $GPID 2>/dev/null
for p in $(pgrep -f forgeserveruserdev); do tr '\0' ' ' < /proc/$p/cmdline 2>/dev/null | grep -q "$(pwd)/" && kill $p; done
echo "GT : $ok/$n OK"
