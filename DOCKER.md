# Running TakeMyBible with Docker

The whole app runs on one server as three containers. Read the comments in each file to learn how it works.

| Container | Built from | What it does |
|---|---|---|
| `web` | `frontend/Dockerfile`, `frontend/nginx.conf` | nginx serves the React site and forwards `/api/...` to the backend |
| `backend` | `backend/backend/Dockerfile` | Spring Boot API |
| `db` | `mysql:8.4` image | MySQL, data kept in the `db-data` volume |

Only `web` is reachable from outside (port 80). The backend and database sit on private Docker networks. `docker-compose.yml` has a diagram.

## Prerequisites

Docker Desktop (Windows/Mac) or Docker Engine with the Compose plugin (Linux server). Check with `docker compose version`.

## First-time setup

1. **Create your settings file.**
   ```powershell
   Copy-Item .env.example .env
   ```
   Open `.env` and set `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` and `JWT_SECRET`. The file explains how to generate the secret. `.env` is ignored by git, so keep a copy in a password manager.
2. **Add the Bible data.** Follow `database/seed/README.md`. Without it the site starts with no books.
3. **Build and start.**
   ```powershell
   docker compose up -d --build
   ```
   The first build downloads base images and dependencies and takes several minutes.
4. **Open** `http://localhost` (or your `HTTP_PORT`).

## Everyday commands

| Command | What it does |
|---|---|
| `docker compose ps` | Show containers and health |
| `docker compose logs -f backend` | Follow the backend's logs (`web`, `db` work too) |
| `docker compose up -d --build` | Rebuild changed images and restart them (use after code changes) |
| `docker compose restart backend` | Restart one service |
| `docker compose down` | Stop and remove containers. **Data is kept** |
| `docker compose down -v` | Also delete the database volume. **All data is lost** |
| `docker compose exec db mysql -uroot -p takemybible` | SQL shell |

## Deploying to a server

1. Install Docker on the server and copy the project there (git clone, or copy the folder).
2. Create `.env` on the server with strong, unique values. Set `SITE_URL` to your domain.
3. Load the seed data as above, then `docker compose up -d --build`.
4. Open port 80 (and 443 once you add HTTPS) in the server's firewall. Do not open 3306 or 8080. Nothing outside needs them.
5. Set up HTTPS before real users log in.

## HTTPS

The stack serves plain HTTP. On a public server, passwords and login tokens would travel in clear text, so add HTTPS. **This part is not set up or tested here.** Two common routes:

- **Caddy in front of nginx** (simplest). Caddy gets and renews free Let's Encrypt certificates by itself. Add a service like this to `docker-compose.yml`, remove `ports:` from `web`, and give Caddy a `Caddyfile` containing `bible.example.com { reverse_proxy web:80 }`:
  ```yaml
  caddy:
    image: caddy:2
    restart: unless-stopped
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
    networks: [web-net]
  ```
  (also declare `caddy-data:` under `volumes:`). Your domain's DNS must already point at the server.
- **nginx with certbot**. Get a certificate with Let's Encrypt's `certbot`, mount it into the `web` container, and add a `listen 443 ssl;` server block to `frontend/nginx.conf`. More manual work, and you must set up renewal.

## Backups

Back up the database regularly, and before upgrades. This writes the dump inside the container and then copies it out. It avoids shell redirection, which can corrupt text on Windows.

```powershell
docker compose exec db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces --result-file=/tmp/backup.sql takemybible'
docker compose cp db:/tmp/backup.sql ./backup.sql
```

Restore into a running database:

```powershell
docker compose cp ./backup.sql db:/tmp/backup.sql
docker compose exec db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot takemybible < /tmp/backup.sql'
```

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `docker compose` says `Set MYSQL_PASSWORD in .env` | A required value is missing in `.env` |
| Site loads but shows no books | The seed data was not loaded. See `database/seed/README.md`. Seeds only run on a fresh volume |
| `backend` keeps restarting | `docker compose logs backend`. Common causes: wrong DB password (it was set when the volume was first created, so changing `.env` later does not change it), or a missing variable |
| Changed `MYSQL_PASSWORD` but login to MySQL still uses the old one | The user is created once. Either change it inside MySQL or `docker compose down -v` (erases data) |
| `502 Bad Gateway` on `/api/...` | The backend is not up yet or crashed. Check `docker compose ps` and its logs |
| Port 80 already in use | Set `HTTP_PORT=8080` in `.env` and open `http://localhost:8080` |
| Backend fails to compile in the image | The Java version in `backend/backend/pom.xml` (25) must match the images in `backend/backend/Dockerfile` |

## Local development without Docker

Unchanged: run the backend with Maven and `npm run dev` in `frontend/`. The Vite dev server forwards `/api` to `http://localhost:9090` (see `frontend/vite.config.ts`). Change that port if your local `SERVER_PORT` differs.
