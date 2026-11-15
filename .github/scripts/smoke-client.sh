#!/bin/bash
# Démarrage du jeu sans écran (Xvfb + Mesa) : vérifie que le client arrive au menu, et en option dans un monde, sans plantage.
# Usage : .github/scripts/smoke-client.sh <dossier du mod> <dossier de sortie> [monde]
#   [monde] : nom d'une sauvegarde de <mod>/run/saves à ouvrir directement (propriété Gradle quickPlay).
# Sortie : <sortie>/client.log, <sortie>/menu.png, <sortie>/monde.png (si monde), et une ligne finale
#   « CLIENT : TOUT EST BON » ou « CLIENT : ECHEC (<raison>) ».
MOD=${1:?dossier du mod}; OUT=${2:?dossier de sortie}; WORLD=$3
mkdir -p "$OUT"; LOG="$OUT/client.log"; rm -f "$LOG" "$OUT"/*.png
DISPLAY_NUM=:97
Xvfb $DISPLAY_NUM -screen 0 1280x720x24 -nolisten tcp >/dev/null 2>&1 &
XPID=$!
sleep 2
export DISPLAY=$DISPLAY_NUM LIBGL_ALWAYS_SOFTWARE=1
cd "$MOD" || exit 2
EXTRA=()
[ -n "$WORLD" ] && EXTRA=(-PquickPlay="$WORLD")
# conteneur à 7 Go : démon Gradle léger, tas du client à 3 Go (CLIENT_HEAP pour changer)
./gradlew -Dorg.gradle.jvmargs=-Xmx512m runClient --console=plain -PclientHeap=${CLIENT_HEAP:-3G} "${EXTRA[@]}" > "$LOG" 2>&1 &
GPID=$!
verdict=""
for i in $(seq 1 360); do   # 30 min au plus
  sleep 5
  if grep -qE "Crash report saved|---- Minecraft Crash Report ----|Exception in thread \"Render thread\"" "$LOG"; then verdict="plantage"; break; fi
  if ! kill -0 $GPID 2>/dev/null; then verdict="arrêt inattendu"; break; fi
  if [ ! -f "$OUT/menu.png" ] && grep -qE "Created: .*textures/atlas/blocks.png-atlas" "$LOG" && grep -q "Sound engine started" "$LOG"; then
    sleep 20; import -display $DISPLAY_NUM -window root "$OUT/menu.png" 2>/dev/null
    [ -z "$WORLD" ] && { verdict="ok"; break; }
  fi
  # écran « Worlds using Experimental Settings » (dimensions du mod) : on valide « Proceed »
  if [ -n "$WORLD" ] && [ -z "$PROCEED" ] && grep -qE "Loaded [0-9]+ advancements" "$LOG"; then
    sleep 20; import -display $DISPLAY_NUM -window root "$OUT/avertissement.png" 2>/dev/null
    xdotool mousemove 478 437 click 1 2>/dev/null; sleep 2; xdotool key Tab Return 2>/dev/null
    PROCEED=1
  fi
  if [ -n "$WORLD" ] && grep -qE "joined the game" "$LOG"; then
    sleep 90; import -display $DISPLAY_NUM -window root "$OUT/monde.png" 2>/dev/null
    grep -qE "Crash report saved|---- Minecraft Crash Report ----" "$LOG" && verdict="plantage" || verdict="ok"
    break
  fi
done
[ -z "$verdict" ] && verdict="délai dépassé"
pkill -f "net.minecraftforge.bootstrap|forgeclientuserdev|GradleWrapperMain runClient" 2>/dev/null
kill $GPID 2>/dev/null; sleep 3; kill $XPID 2>/dev/null
if [ "$verdict" = "ok" ]; then echo "CLIENT : TOUT EST BON"; else echo "CLIENT : ECHEC ($verdict)"; fi
