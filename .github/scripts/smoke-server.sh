#!/bin/bash
# Démarrage d'un serveur dédié : vérifie qu'il arrive à « Done » sans plantage, puis l'arrête proprement.
# Usage : .github/scripts/smoke-server.sh <dossier du mod> <dossier de sortie> [copie]
#   [copie] : si donné, le monde créé est copié dans <mod>/run/saves/<copie> pour .github/scripts/smoke-client.sh.
# Ligne finale : « SERVEUR : TOUT EST BON » ou « SERVEUR : ECHEC (<raison>) ».
MOD=${1:?dossier du mod}; OUT=${2:?dossier de sortie}; COPY=$3
mkdir -p "$OUT"; LOG="$OUT/server.log"; rm -f "$LOG"
cd "$MOD" || exit 2
mkdir -p run && echo "eula=true" > run/eula.txt
rm -rf run/world
FIFO=$(mktemp -u); mkfifo "$FIFO"
( ./gradlew runServer --console=plain < "$FIFO" > "$LOG" 2>&1 ) &
GPID=$!
exec 3>"$FIFO"
verdict=""
for i in $(seq 1 240); do   # 20 min au plus
  sleep 5
  if grep -qE "Crash report saved|---- Minecraft Crash Report ----|Exception in server tick loop" "$LOG"; then verdict="plantage"; break; fi
  if ! kill -0 $GPID 2>/dev/null; then verdict="arrêt inattendu"; break; fi
  if grep -qE "\]: Done \([0-9.]+s\)!" "$LOG"; then
    sleep 20; echo "stop" >&3
    for j in $(seq 1 60); do sleep 2; kill -0 $GPID 2>/dev/null || break; done
    grep -qE "Crash report saved|---- Minecraft Crash Report ----" "$LOG" && verdict="plantage à l'arrêt" || verdict="ok"
    break
  fi
done
exec 3>&-; rm -f "$FIFO"
[ -z "$verdict" ] && verdict="délai dépassé"
kill $GPID 2>/dev/null; pkill -f "forgeserveruserdev|GradleWrapperMain runServer" 2>/dev/null
if [ "$verdict" = "ok" ] && [ -n "$COPY" ] && [ -d run/world ]; then
  mkdir -p run/saves && rm -rf "run/saves/$COPY" && cp -a run/world "run/saves/$COPY"
fi
if [ "$verdict" = "ok" ]; then echo "SERVEUR : TOUT EST BON"; else echo "SERVEUR : ECHEC ($verdict)"; fi
