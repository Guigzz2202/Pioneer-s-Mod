#!/usr/bin/env python3
"""Client RCON minimal (protocole Source) pour piloter le serveur dedie des GameTests locaux (tools/tests/gt_run.sh).

Usage : rcon.py <port> <mot de passe> <commande>...   -- envoie chaque commande, affiche la reponse du serveur.
Le serveur lance par ./gradlew runServer ne lit pas l'entree standard (JavaExec) : on passe par RCON.
"""
import socket
import struct
import sys


def packet(rid, kind, body):
    data = struct.pack('<ii', rid, kind) + body.encode('utf-8') + b'\x00\x00'
    return struct.pack('<i', len(data)) + data


def read(sock):
    head = b''
    while len(head) < 4:
        chunk = sock.recv(4 - len(head))
        if not chunk:
            raise ConnectionError('connexion fermee')
        head += chunk
    (n,) = struct.unpack('<i', head)
    data = b''
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise ConnectionError('connexion fermee')
        data += chunk
    rid, kind = struct.unpack('<ii', data[:8])
    return rid, kind, data[8:-2].decode('utf-8', 'replace')


def main():
    port, password, commands = int(sys.argv[1]), sys.argv[2], sys.argv[3:]
    with socket.create_connection(('127.0.0.1', port), timeout=30) as s:
        s.sendall(packet(1, 3, password))
        rid, _, _ = read(s)
        if rid == -1:
            print('RCON : mot de passe refuse')
            sys.exit(1)
        for i, c in enumerate(commands, 2):
            s.sendall(packet(i, 2, c))
            _, _, body = read(s)
            print(body)


main()
