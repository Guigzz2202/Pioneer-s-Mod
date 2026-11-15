# Chemin de fer : tableau des trains sur les ecrans lies, et la gare GARE fermee tant que son quai deborde
GARE = "Mine"
PLEIN = 2000      # objets sur le quai au-dela desquels on n'envoie plus de train
while True:
    screen.clear()
    for t in rail.trains():
        nom = t["name"]
        etat = t["status"]
        gare = t["station"]
        reserve = t["fuel"]
        screen.print(f"{nom:<12} {etat:<12} {gare:<10} {reserve:>3} %")
    g = rail.station(GARE)
    if g is not None:
        total = 0
        for cle, n in g["stock"].items():
            total += n
        rail.set_limit(GARE, 0 if total > PLEIN else -1)
        # signal lu par la condition d'horaire « Signal » : 1 quand le quai deborde
        rail.signal("quai_plein", 1 if total > PLEIN else 0)
    sleep(2)
