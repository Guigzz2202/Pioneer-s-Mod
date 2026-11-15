# Programme les ordinateurs de vol ranges dans le lecteur A (toute la pile), puis attend la pile suivante
MISSION = "Sonde lunaire"
while True:
    etat = flight.check("a")
    if etat == "blank":
        n = flight.flash("a", MISSION)
        print(f"{n} ordinateur(s) de vol programme(s) : {MISSION}")
    elif etat == "programmed":
        screen.write(1, "Pret : retirez les ordinateurs de vol")
    else:
        screen.write(1, "Lecteur A : inserez des ordinateurs de vol")
    sleep(1)
