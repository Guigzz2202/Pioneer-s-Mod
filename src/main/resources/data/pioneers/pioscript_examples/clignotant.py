# Clignotant : allume et eteint la face nord toutes les demi-secondes
etat = False
while True:
    etat = not etat
    redstone.set("nord", 15 if etat else 0)
    sleep(0.5)
