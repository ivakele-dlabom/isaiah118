# Seed data for the Docker database

Put `.sql` files in this folder to load them into MySQL automatically.

## How it works

`docker-compose.yml` mounts this folder at `/docker-entrypoint-initdb.d` inside the MySQL container. On the **first start only** (when the `db-data` volume is empty), MySQL runs every `*.sql` and `*.sql.gz` file here in alphabetical order. Other files (like this README) are ignored.

Later starts skip them. To load them again you must delete the data volume first, which erases the database:

```powershell
docker compose down -v
docker compose up -d
```

## Why you need to do this

A new database is empty. Hibernate creates the tables when the backend starts, but it cannot create the content. The Bible text (`kjv_books`, `kjv_verses`) and the cross references (`cross_references`, `cross_reference_extras`) are not stored anywhere in this repository. They only exist in your local MySQL. **Until you export them, the Docker site starts but shows no books.**

## Export from your local MySQL

Run this from the project root, with your local MySQL running. It asks for your local MySQL password.

```powershell
mysqldump -u root -p --single-transaction --default-character-set=utf8mb4 --no-tablespaces --set-gtid-purged=OFF --result-file=database/seed/01-bible-data.sql takemybible kjv_books kjv_verses cross_references cross_reference_extras
```

Notes:

- Use `--result-file`, as shown. Redirecting with `>` in Windows PowerShell 5.1 writes UTF-16, which MySQL cannot read.
- If `mysqldump` is not found, it is in the `bin` folder of your MySQL install (for example `C:\Program Files\MySQL\MySQL Server 8.0\bin`). Or use MySQL Workbench: Server > Data Export > choose those four tables > "Export to Self-Contained File".
- Do not include `users`, `comments` or `cross_reference_votes`. That is test data from your machine, and real users will create their own.
- `cross_references.votes` is a stored vote total, so it contains any votes you cast while testing. To start from zero, add `UPDATE cross_references SET votes = 0;` to a second file such as `02-reset-votes.sql`.
- The file can be large (the cross references table is the big one). Decide deliberately whether to commit it to git. Adding `database/seed/*.sql` to `.gitignore` keeps it out.

## Check it loaded

```powershell
docker compose exec db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot takemybible -e "SELECT COUNT(*) FROM kjv_books; SELECT COUNT(*) FROM kjv_verses;"'
```

The KJV has 66 books and 31,102 verses.
