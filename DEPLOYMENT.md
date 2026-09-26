# Postavljanje u produkciju

> **Napomena:** aplikacija još nije postavljena u produkciju. Ovo je generički postupak za Linux server (VPS) sa Docker Compose-om, zasnovan na `Dockerfile` i `docker-compose.yml` iz ovog repozitorijuma. Build i pokretanje iz koraka 4 i 5 su provereni lokalno (Docker Desktop). Priprema servera, reverse proxy i HTTPS nisu testirani na pravom serveru.

Ceo sistem čine dva kontejnera: **PostgreSQL 16** i **backend** (Spring Boot, port 8080). Frontend ([kahoot-frontend](https://github.com/Dumanex/kahoot-frontend)) se postavlja posebno.

## Sadržaj

1. [Priprema servera](#1-priprema-servera)
2. [Preuzimanje koda](#2-preuzimanje-koda)
3. [Konfiguracija varijabli](#3-konfiguracija-varijabli)
4. [Build i pokretanje](#4-build-i-pokretanje)
5. [Provera da aplikacija radi](#5-provera-da-aplikacija-radi)
6. [HTTPS i domen (reverse proxy)](#6-https-i-domen-reverse-proxy)
7. [Ažuriranje na novu verziju](#7-ažuriranje-na-novu-verziju)
8. [Backup i vraćanje podataka](#8-backup-i-vraćanje-podataka)
9. [Rešavanje problema](#9-rešavanje-problema)

## 1. Priprema servera

Potrebno je:
- Linux server (primer koristi Ubuntu 22.04/24.04), najmanje 1 vCPU i 2 GB RAM-a. Maven build unutar Docker-a troši više memorije od same aplikacije.
- SSH pristup korisnikom koji ima `sudo`
- (opciono) domen čiji DNS `A` zapis pokazuje na IP adresu servera

Instalacija Docker Engine-a i Compose plugina (zvanična skripta):

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# odjaviti se i ponovo prijaviti da bi grupa "docker" važila
docker --version
docker compose version
```

Instalacija Git-a:

```bash
sudo apt update && sudo apt install -y git
```

Firewall (`ufw`): otvoriti samo SSH i web portove.

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
# samo ako se backend koristi direktno, bez reverse proxy-ja (korak 6):
# sudo ufw allow 8080/tcp
sudo ufw enable
```

PostgreSQL port `5432` se **ne otvara**. U `docker-compose.yml` je vezan za `127.0.0.1`, pa je baza dostupna samo sa samog servera. Ovo je bitno jer Docker zaobilazi `ufw` pravila za portove koje sam objavi.

JDK i Maven na serveru nisu potrebni, jer se JAR builduje unutar Docker-a (multi-stage `Dockerfile`).

## 2. Preuzimanje koda

```bash
git clone https://github.com/Dumanex/kahoot-backend.git
cd kahoot-backend
```

## 3. Konfiguracija varijabli

```bash
cp .env.example .env
nano .env
```

Vrednosti za produkciju:

| Varijabla | Šta upisati |
|---|---|
| `JWT_SECRET` | Nov, nasumičan ključ, **ne** isti kao lokalno. Generisati sa `openssl rand -base64 48`. Mora imati najmanje 32 karaktera |
| `JWT_EXPIRATION` | Trajanje tokena u ms (`86400000` = 24 h) |
| `DB_NAME`, `DB_USERNAME` | Mogu ostati `kahoot` / `postgres` |
| `DB_PASSWORD` | Jaka lozinka, npr. `openssl rand -base64 24` |
| `CORS_ALLOWED_ORIGINS` | Tačan origin frontenda, npr. `https://kviz.example.com`, bez `/` na kraju. Više vrednosti se razdvaja zarezom. Isti spisak važi i za WebSocket `/ws` |
| `UPLOAD_BASE_URL` | Javni URL backenda, npr. `https://api.kviz.example.com`. Od njega se prave linkovi ka slikama i audio fajlovima |
| `DB_HOST`, `DB_PORT`, `UPLOAD_DIR` | Ne menjati. Docker Compose ih za backend kontejner sam postavlja (`postgres`, `5432`, `/app/uploads`) |

Primer produkcionog `.env` (vrednosti su izmišljene):

```dotenv
DB_NAME=kahoot
DB_USERNAME=postgres
DB_PASSWORD=<generisana-lozinka>
JWT_SECRET=<generisan-kljuc-od-najmanje-32-karaktera>
JWT_EXPIRATION=86400000
CORS_ALLOWED_ORIGINS=https://kviz.example.com
UPLOAD_BASE_URL=https://api.kviz.example.com
```

Zaštita fajla, da ga čita samo vlasnik:

```bash
chmod 600 .env
```

> `DB_NAME`, `DB_USERNAME` i `DB_PASSWORD` PostgreSQL primenjuje **samo pri prvom kreiranju** volume-a `postgres-data`. Ako se lozinka promeni kasnije, treba je promeniti i u bazi (`ALTER USER postgres PASSWORD '...'`), inače backend neće moći da se poveže.

## 4. Build i pokretanje

```bash
docker compose --profile prod up -d --build
```

Šta se dešava:
1. Docker builduje backend image. Prvi build traje nekoliko minuta jer Maven preuzima zavisnosti.
2. Pokreće se `kahoot-db` (PostgreSQL 16) sa volume-om `postgres-data`.
3. Kada baza prođe healthcheck, pokreće se `kahoot-backend`.
4. Pri startu backenda **Flyway** sam primenjuje migracije (`V1`–`V5`) i pravi šemu. Početni podaci ne postoje, a prvi nalog se pravi registracijom.
5. Otpremljeni fajlovi se čuvaju na volume-u `uploads-data`, pa ostaju i posle restarta ili ponovnog build-a.

Backend ima `restart: unless-stopped`, pa se sam podiže posle pada ili restarta servera.

## 5. Provera da aplikacija radi

Status kontejnera (oba treba da budu `Up`, a baza `healthy`):

```bash
docker compose --profile prod ps
```

Logovi backenda:

```bash
docker compose logs -f backend
```

U logu treba da se pojave redovi:

```
Successfully validated 5 migrations
Tomcat started on port 8080 (http)
Started KahootBackendApplication in ... seconds
```

API sa servera:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/v3/api-docs   # očekivano: 200
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/quizzes   # očekivano: 401 (nema tokena)
```

Registracija i prijava probnog korisnika:

```bash
curl -s -H 'Content-Type: application/json' \
  -d '{"username":"proba","email":"proba@example.com","password":"proba123"}' \
  http://localhost:8080/api/auth/register

curl -s -H 'Content-Type: application/json' \
  -d '{"username":"proba","password":"proba123"}' \
  http://localhost:8080/api/auth/login
```

Oba poziva treba da vrate JSON sa poljem `token`.

CORS za frontend domen (očekivano `Access-Control-Allow-Origin` sa tim domenom):

```bash
curl -s -o /dev/null -D - -X OPTIONS \
  -H 'Origin: https://kviz.example.com' \
  -H 'Access-Control-Request-Method: POST' \
  http://localhost:8080/api/auth/login
```

Ako origin nije u `CORS_ALLOWED_ORIGINS`, odgovor je `403`.

Swagger UI: `http://<server>:8080/swagger-ui.html`, ili preko domena posle koraka 6.

## 6. HTTPS i domen (reverse proxy)

Reverse proxy **nije deo ovog repozitorijuma**, ali se preporučuje za produkciju: backend ostaje na HTTP portu 8080, a proxy dodaje HTTPS i domen. Primer sa **Nginx**-om i **Certbot**-om (Let's Encrypt):

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
```

`/etc/nginx/sites-available/kahoot-api`:

```nginx
server {
    listen 80;
    server_name api.kviz.example.com;

    # upload do 20 MB (isto kao spring.servlet.multipart.max-file-size)
    client_max_body_size 20M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket (STOMP preko SockJS) traži Upgrade header-e
    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/kahoot-api /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d api.kviz.example.com
```

Posle toga:
- `UPLOAD_BASE_URL` u `.env` postaviti na `https://api.kviz.example.com`, a zatim `docker compose --profile prod up -d` (bez `--build`) primenjuje nove vrednosti.
- Frontend treba da koristi `https://api.kviz.example.com` za REST i `/ws`.
- Port 8080 ne treba otvarati u firewall-u. U `docker-compose.yml` se može vezati samo za localhost: `"127.0.0.1:8080:8080"`.

## 7. Ažuriranje na novu verziju

```bash
cd kahoot-backend
git pull
docker compose --profile prod up -d --build
```

Nove Flyway migracije se primenjuju same pri startu. Podaci u volume-ima ostaju sačuvani.

Brisanje starih image-a posle više build-ova:

```bash
docker image prune -f
```

## 8. Backup i vraćanje podataka

Backup baze:

```bash
docker exec kahoot-db pg_dump -U postgres -d kahoot > backup_$(date +%F).sql
```

Vraćanje baze u praznu bazu, pre prvog starta backenda ili posle `docker compose down -v`:

```bash
docker compose up -d postgres
cat backup_YYYY-MM-DD.sql | docker exec -i kahoot-db psql -U postgres -d kahoot
docker compose --profile prod up -d
```

Backup otpremljenih fajlova (volume `uploads-data`, pun naziv je `<ime-foldera>_uploads-data`, npr. `kahoot-backend_uploads-data`):

```bash
docker run --rm -v kahoot-backend_uploads-data:/data -v "$PWD":/backup alpine \
  tar czf /backup/uploads_$(date +%F).tar.gz -C /data .
```

> **Pažnja:** `docker compose down -v` briše volume-e, odnosno celu bazu i sve otpremljene fajlove. Za običan restart koristiti `docker compose --profile prod restart` ili `down` bez `-v`.

## 9. Rešavanje problema

| Simptom | Uzrok i rešenje |
|---|---|
| Backend se gasi uz `jwt.secret must be at least 32 bytes long` | `JWT_SECRET` nije postavljen ili je kraći od 32 karaktera. Ispraviti `.env`, pa `docker compose --profile prod up -d` |
| `password authentication failed for user "postgres"` | `DB_PASSWORD` se ne poklapa sa lozinkom iz vremena kada je volume napravljen (vidi napomenu u koraku 3) |
| `Validate failed: Migrations have failed validation` | Neka već primenjena migracija je izmenjena. Postojeće `V*.sql` fajlove ne menjati, već dodati novu migraciju |
| Frontend dobija CORS grešku ili WebSocket `403` | Origin frontenda nije u `CORS_ALLOWED_ORIGINS` (mora se tačno poklapati: šema, domen, port, bez `/` na kraju) |
| WebSocket ne radi iza Nginx-a | Nedostaju `Upgrade`/`Connection` header-i u `location /ws` (korak 6) |
| Slike se ne prikazuju | `UPLOAD_BASE_URL` pokazuje na pogrešnu adresu (npr. ostao je `http://localhost:8080`) |
| Upload vraća `413` | Nginx `client_max_body_size` je manji od 20 MB |
