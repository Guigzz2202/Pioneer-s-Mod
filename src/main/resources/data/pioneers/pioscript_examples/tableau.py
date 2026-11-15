# Tableau de bord : toutes les mesures de la machine liee 1, sur les ecrans de supervision lies
m = link(1)
while True:
    screen.clear()
    if not m.connected():
        screen.print("Liaison 1 libre")
    else:
        screen.print(f"== {m.name()} ==")
        for cle, valeur in m.values().items():
            screen.print(f"{cle:<18} {valeur:>10.1f}")
    sleep(2)
