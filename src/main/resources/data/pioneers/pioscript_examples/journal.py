# Journal : chaque minute, note l'heure du jeu, la meteo et une mesure de la machine liee 1 dans un fichier JSON,
# puis affiche un resume (moyenne, mediane, derniere valeur en barre) sur les ecrans de supervision lies
FICHIER = "journal.json"
MESURE = "energy"          # cle de la mesure (m.keys() donne la liste)
journal = json.loads(fs.read(FICHIER)) if fs.exists(FICHIER) else []
m = link(1)
while True:
    v = m.value(MESURE) if m.connected() else None
    journal.append({"heure": world.clock(), "jour": world.day(), "meteo": world.weather(), "mesure": v})
    journal = journal[-100:]
    fs.write(FICHIER, json.dumps(journal))
    mesures = []
    for e in journal:
        if e["mesure"] is not None:
            mesures.append(e["mesure"])
    screen.clear()
    screen.center(1, f"Journal - jour {world.day()} a {world.clock()}")
    screen.write(2, f"Meteo : {world.weather()}   Biome : {world.biome()}")
    if mesures:
        screen.write(4, f"Moyenne : {math.mean(mesures):.1f}   Mediane : {math.median(mesures):.1f}")
        screen.bar(5, "Derniere", mesures[-1], max(mesures))
    computer.beep(1.5)
    sleep(60)
