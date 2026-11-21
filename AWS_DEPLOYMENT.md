# Deploying TakeMyBible to AWS (with a separate database server)

This guide is written for someone learning AWS. It uses the AWS Console (the website) so you can see each piece. Nothing in your project needs to change, but you will create one extra file on the server (Step 7).

## 1. What you will build

```
                 internet
                    |
              port 80 / 443
                    |
      +-------------v--------------+
      |  EC2 server (a Linux VM)   |   <- "Server 1": runs Docker
      |   nginx (web) container    |
      |   Spring Boot (backend)    |
      +-------------+--------------+
                    | port 3306, only from the EC2 server
      +-------------v--------------+
      |  RDS MySQL database        |   <- "Server 2": managed by AWS
      +----------------------------+
```

- **EC2** is a virtual machine you rent. It runs your `web` and `backend` containers from `docker-compose.yml`.
- **RDS** is a database server that AWS runs for you (backups, patches, storage). This is your "database on a different server".
- **VPC** is your private network inside AWS. Both servers live in it. A **security group** is a firewall attached to each server.

Why RDS and not a MySQL container on a second EC2? RDS handles backups and updates for you, which is a good thing to learn first.

The main change from your local Docker setup: the `db` container goes away, and the backend's `DB_URL` points at the RDS address instead of `db`.

## 2. Before you start

- An AWS account, with an IAM user you use day to day (do not use the root user for daily work). Turn on MFA for the root user.
- Pick one **region** (for example `us-east-1`) in the top-right of the console and use it for everything. Resources in other regions are invisible from the one you are viewing.
- Set a **billing alert**: Billing > Budgets > Create budget > Zero spend or monthly cost budget. This protects you from surprise charges.
- Your local MySQL data exported to `database/seed/01-bible-data.sql`. The steps are in `database/seed/README.md`. Without it, the site starts but shows no books.
- Your project in a Git repository you can clone from the server (GitHub, for example). Do **not** commit `.env`.

## 3. Create the security groups (firewalls)

Console: **EC2 > Network & Security > Security Groups > Create security group**. Use the default VPC for this first deployment.

Create them in this order, because the database group refers to the web group.

**a) `tmb-web-sg`** (for the EC2 server)

| Type | Port | Source | Why |
|---|---|---|---|
| HTTP | 80 | Anywhere (0.0.0.0/0) | Public website |
| HTTPS | 443 | Anywhere (0.0.0.0/0) | For when you add HTTPS |
| SSH | 22 | **My IP** | So only you can log in |

**b) `tmb-db-sg`** (for the database)

| Type | Port | Source | Why |
|---|---|---|---|
| MYSQL/Aurora | 3306 | Security group: `tmb-web-sg` | Only the EC2 server can reach the DB |

Key idea: the source is a *security group*, not an IP address. Anything wearing `tmb-web-sg` is allowed in. The database has no rule for the internet, so it cannot be reached from outside.

## 4. Create the RDS MySQL database

Console: **RDS > Databases > Create database**.

1. Creation method: **Standard create**.
2. Engine: **MySQL**, version **8.4.x** (matches `mysql:8.4` in your compose file).
3. Template: **Free tier** or **Dev/Test** (pick the cheapest while learning). Turn off Multi-AZ for now.
4. DB instance identifier: `takemybible-db`.
5. Master username: `admin`. Choose **Self managed** and set a strong master password. Save it in a password manager.
6. Instance class: `db.t4g.micro` or `db.t3.micro`.
7. Storage: 20 GB General Purpose SSD. Leave encryption on.
8. Connectivity:
   - Compute resource: **Don't connect to an EC2 compute resource** (you set the firewall yourself).
   - VPC: default.
   - **Public access: No.** The database should never have a public address.
   - VPC security group: choose existing, select `tmb-db-sg`, and remove `default`.
9. Additional configuration:
   - **Initial database name:** `takemybible`
   - Automated backups: keep on (7 days is fine).
   - Deletion protection: on (you can turn it off when you want to delete it).
10. Create the database. It takes about 5-10 minutes.

When its status is **Available**, open it and copy the **Endpoint**. It looks like:

```
takemybible-db.abc123xyz.us-east-1.rds.amazonaws.com
```

You will use it as the database host.

## 5. Create the EC2 server

Console: **EC2 > Instances > Launch instances**.

1. Name: `takemybible-web`.
2. AMI: **Amazon Linux 2023**.
3. Instance type: **t3.small** (2 GB RAM). A 1 GB `t3.micro` often runs out of memory while Maven builds the Java app. If you only have a micro, add swap space (see Troubleshooting).
4. Key pair: **Create new key pair**, type RSA, format `.pem`. Save the file safely. You cannot download it again.
5. Network settings: choose **Select existing security group** and pick `tmb-web-sg`. Same VPC as the database. Auto-assign public IP: **Enable**.
6. Storage: 20 GB gp3 (the Docker images need space).
7. Launch.

Optional but recommended: **EC2 > Elastic IPs > Allocate**, then associate it with the instance. A normal public IP changes when the instance stops. An Elastic IP stays fixed (needed for a domain name).

## 6. Connect and install Docker

From PowerShell on your PC (replace the key file and IP):

```powershell
ssh -i "C:\path\to\your-key.pem" ec2-user@YOUR_EC2_PUBLIC_IP
```

If Windows complains the key file permissions are too open, right-click the `.pem` > Properties > Security > Advanced, remove all users except yourself.

On the server:

```bash
sudo dnf update -y
sudo dnf install -y docker git mariadb105
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

# Docker Compose plugin (not included in the Amazon Linux package)
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
```

Log out (`exit`) and SSH in again so the `docker` group applies, then check:

```bash
docker --version
docker compose version
mysql --version
```

`mariadb105` provides the `mysql` command-line client, which you need to talk to RDS.

## 7. Get the code and prepare the files

```bash
git clone https://github.com/YOUR_USER/YOUR_REPO.git takemybible
cd takemybible
```

### 7a. Create `docker-compose.aws.yml`

Your normal `docker-compose.yml` starts a MySQL container and makes the backend wait for it. In AWS you want neither. Create a second file, next to it, with only the `backend` and `web` services:

```bash
nano docker-compose.aws.yml
```

Paste:

```yaml
name: takemybible

services:
  backend:
    build:
      context: ./backend/backend
    image: takemybible-backend
    restart: unless-stopped
    environment:
      SERVER_PORT: "8080"
      DB_URL: ${DB_URL:?Set DB_URL in .env}
      DB_USERNAME: ${MYSQL_USER:-takemybible}
      DB_PASSWORD: ${MYSQL_PASSWORD:?Set MYSQL_PASSWORD in .env}
      JWT_SECRET: ${JWT_SECRET:?Set JWT_SECRET in .env}
      JWT_EXPIRATION: ${JWT_EXPIRATION:-86400000}
      CORS_ORIGINS: ${SITE_URL:-http://localhost}
    mem_limit: ${BACKEND_MEM_LIMIT:-768m}

  web:
    build:
      context: ./frontend
    image: takemybible-web
    restart: unless-stopped
    depends_on:
      - backend
    ports:
      - "${HTTP_PORT:-80}:80"
```

Differences from `docker-compose.yml`:

- No `db` service, no `db-data` volume, no `db-net` network.
- `DB_URL` is read from `.env` instead of being built with the host `db`.
- No `depends_on: db`. The database is already running on its own.
- Compose gives these two services a default network, so nginx can still reach `backend` by name (this is what `frontend/nginx.conf` expects).

If you prefer not to keep a second file, an alternative is to keep one file and remove the `db` parts by hand. A separate file is safer because your local setup keeps working.

### 7b. Create `.env`

```bash
cp .env.example .env
nano .env
```

Set these values:

```ini
MYSQL_DATABASE=takemybible
MYSQL_USER=takemybible
MYSQL_PASSWORD=<strong password for the app user, you choose it in Step 8>
JWT_SECRET=<long random string>
JWT_EXPIRATION=86400000
SITE_URL=http://YOUR_EC2_PUBLIC_IP
HTTP_PORT=80

DB_URL=jdbc:mysql://takemybible-db.abc123xyz.us-east-1.rds.amazonaws.com:3306/takemybible?sslMode=REQUIRED&serverTimezone=UTC
```

Notes:

- `MYSQL_ROOT_PASSWORD` is not needed here. RDS has its own admin user.
- Generate `JWT_SECRET` on the server with `openssl rand -base64 48`.
- `sslMode=REQUIRED` encrypts the connection between the backend and RDS. Your local file uses `useSSL=false`, which you should not use across a network.
- Do not put quotes around values. If a password contains `$`, write `$$`.
- Keep `.env` private: `chmod 600 .env`.

## 8. Prepare the database

First test that the server can reach RDS. If this hangs, the security groups are wrong (see Troubleshooting).

```bash
mysql -h takemybible-db.abc123xyz.us-east-1.rds.amazonaws.com -u admin -p
```

Enter the master password. At the `mysql>` prompt, create a limited user for the app. Use the same password you put in `.env`:

```sql
CREATE USER 'takemybible'@'%' IDENTIFIED BY 'THE_APP_PASSWORD';
GRANT ALL PRIVILEGES ON takemybible.* TO 'takemybible'@'%';
FLUSH PRIVILEGES;
EXIT;
```

Why not let the app use `admin`? If the app is ever compromised, the attacker only gets one database, not the whole server. The app needs `ALL` on its own database because `spring.jpa.hibernate.ddl-auto=update` creates and alters tables.

### Load the Bible data

Your seed file is on your PC. Copy it to the server from PowerShell in your project folder:

```powershell
scp -i "C:\path\to\your-key.pem" database/seed/01-bible-data.sql ec2-user@YOUR_EC2_PUBLIC_IP:~/takemybible/database/seed/
```

If you also have `02-reset-votes.sql`, copy it too. On the server, import in alphabetical order:

```bash
cd ~/takemybible
mysql -h YOUR_RDS_ENDPOINT -u admin -p takemybible < database/seed/01-bible-data.sql
```

The Docker `initdb.d` mechanism only works for the MySQL container, so on RDS you load the files yourself. Check it worked:

```bash
mysql -h YOUR_RDS_ENDPOINT -u admin -p takemybible -e "SELECT COUNT(*) FROM kjv_books; SELECT COUNT(*) FROM kjv_verses;"
```

Expect 66 books and 31,102 verses.

## 9. Start the app

```bash
cd ~/takemybible
docker compose -f docker-compose.aws.yml up -d --build
```

The first build takes several minutes (Maven and npm download dependencies). Then:

```bash
docker compose -f docker-compose.aws.yml ps
docker compose -f docker-compose.aws.yml logs -f backend
```

In the backend logs, look for `Started BackendApplication`. Press `Ctrl+C` to stop following logs (the app keeps running).

Open `http://YOUR_EC2_PUBLIC_IP` in your browser. You should see the site with the books listed. Register a test user to confirm writes to the database work.

## 10. Updating the app later

```bash
cd ~/takemybible
git pull
docker compose -f docker-compose.aws.yml up -d --build
```

The database is not touched, so data is kept.

## 11. Add HTTPS (do this before real users)

Without HTTPS, passwords and login tokens travel as readable text. You need a domain name first (Route 53 or any registrar) pointing at your Elastic IP.

Two common ways:

1. **Application Load Balancer + ACM certificate** (the AWS-native way). Request a free certificate in AWS Certificate Manager, create an ALB with an HTTPS listener (443) forwarding to your EC2 on port 80. Then set `SITE_URL=https://your-domain` in `.env` and restart. Costs a few dollars per month for the ALB.
2. **Let's Encrypt on the server** (cheaper). Put a reverse proxy such as Caddy in front, or add certbot to nginx. See the HTTPS section in `DOCKER.md`.

## 12. Habits worth building

- **Backups:** RDS automated backups are on. Before risky changes, take a manual snapshot: RDS > your DB > Actions > Take snapshot.
- **Passwords:** never commit `.env`. For a later step, look at AWS Secrets Manager or SSM Parameter Store to hold secrets instead of a file.
- **SSH:** keep port 22 limited to your IP. If your home IP changes, update the rule. Alternatively use **Session Manager** (no open SSH port at all).
- **Monitoring:** RDS > Monitoring shows CPU, connections and storage. Set a CloudWatch alarm on low free storage.
- **Cost:** a stopped EC2 instance still costs for its disk and Elastic IP. RDS keeps charging while stopped (it auto-restarts after 7 days).

## 13. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `mysql` command hangs, then times out | `tmb-db-sg` does not allow 3306 from `tmb-web-sg`, or the EC2 and RDS are in different VPCs. |
| `Access denied for user` | Wrong password in `.env`, or the app user was not created (Step 8). Passwords with `$` need `$$` in `.env`. |
| `Unknown database 'takemybible'` | The initial database name was left empty in Step 4. Create it: `CREATE DATABASE takemybible CHARACTER SET utf8mb4;` |
| Backend restarts in a loop | Run `docker compose -f docker-compose.aws.yml logs backend`. Usually a wrong `DB_URL`, password, or a blocked connection. |
| Site loads but no books | The seed data was not imported (Step 8). |
| Browser cannot open the site | `tmb-web-sg` is missing the HTTP 80 rule, or the instance has no public IP. |
| Build killed or `OOM` / "Killed" | Not enough RAM. Use `t3.small`, or add swap (below). |
| `permission denied` on `docker` | You did not log out and back in after `usermod -aG docker`. |

Add 2 GB of swap on a small instance:

```bash
sudo dd if=/dev/zero of=/swapfile bs=128M count=16
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
```

## 14. Clean up (to stop paying)

When you are done experimenting, delete in this order:

1. EC2 > terminate the instance, release the Elastic IP.
2. RDS > turn off deletion protection, delete the database (decline the final snapshot if you do not need it).
3. Delete the security groups: `tmb-db-sg` first, then `tmb-web-sg`. The DB group has a rule that refers to the web group, so the web group cannot be deleted while it exists.
4. Delete any load balancer, certificate or Route 53 records you created.

## Where to go next

- Put the database in **private subnets** in a custom VPC instead of the default VPC.
- Push images to **ECR** and run on **ECS Fargate** instead of managing an EC2 server.
- Automate the steps with **Terraform** or **CloudFormation**.
