# Compte les impulsions redstone recues par la face sud et les note dans un fichier
n = 0
avant = 0
while True:
    s = redstone.get("sud")
    if s > 0 and avant == 0:
        n += 1
        print(f"Impulsion {n}")
        fs.write("compteur.txt", str(n))
    avant = s
    sleep(0.05)
