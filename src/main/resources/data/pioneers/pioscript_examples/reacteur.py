# Regulation d'un reacteur a fission : liaison 1 = controleur du reacteur, liaison 2 = un ecran de supervision
CIBLE = 1100      # temperature visee (K)
MAXI = 1300       # au-dela : arret d'urgence
r = link(1)
if not r.connected():
    print("Liez d'abord le controleur du reacteur (configurateur de liaison).")
else:
    barres = r.value("rods")
    if barres is None:
        barres = 100
    while True:
        t = r.value("temperature")
        if t is None:
            print("Plus de reacteur sur la liaison 1.")
            break
        if t > MAXI:
            r.command("scram")
            print(f"ARRET D'URGENCE a {t:.0f} K")
            break
        # correction proportionnelle : 1 % de barres pour 10 K d'ecart
        barres = max(0, min(100, barres + (t - CIBLE) / 10))
        r.command("rods", barres)
        p = r.value("power") or 0
        screen.write(1, f"Reacteur : {p:.0f} %")
        screen.write(2, f"Temperature : {t:.0f} K (cible {CIBLE})")
        screen.write(3, f"Barres : {barres:.0f} %")
        sleep(1)
