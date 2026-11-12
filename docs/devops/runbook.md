# Operations Runbook: Single-VM Deployment

Step-by-step provisioning, deployment, backup, and recovery for the single-VM demo described in [CI/CD and DevOps](ci-cd.md). The scripts named here live in [`scripts/`](../../scripts/) and were exercised against a local Docker stack. Adapt the provider-specific VM and network steps to the host you provision.

Audit events introduced in V7 are retained indefinitely. There is no automatic purge job; normal database backups include `audit_event`. Set a retention policy before its storage growth becomes material.

## 1. Before you start

- **Cost and availability.** Check the selected VM plan's current pricing, limits, and availability. Configure a budget alert if the provider offers one, and do not assume promotional capacity is persistent.
- **Capacity.** Select a Linux ARM64 VM with enough CPU, memory, and persistent storage for the API, PostgreSQL, backups, and demo data.
- **Host name.** The proxy needs a DNS name that resolves to the VM's public IP. A free option is a dynamic DNS name (for example DuckDNS) or a wildcard-IP name such as `203-0-113-10.sslip.io`. Use the same value for `LIBRO_DOMAIN`.

## 2. Provision the VM

1. Create an ARM64 Linux VM with a stable public IP and an Ubuntu LTS image.
2. Attach persistent storage for database data and backups, mount it at `/mnt/libro` (for example, format it as ext4 and add it to `/etc/fstab` with `nofail`). Then create the data directory for PostgreSQL's Alpine user:

   ```bash
   sudo mkdir -p /mnt/libro/postgres /mnt/libro/backups
   sudo chown 70:70 /mnt/libro/postgres
   ```

3. **Network.** In the provider firewall or security group, allow ingress TCP **80** and **443** from `0.0.0.0/0` and TCP **22 from your own IP only**. Do not open 5432, 8080, or 9091. Configure and persist the host firewall to allow only the required ports.
4. Install Docker Engine with the Compose plugin (**Compose 2.24 or newer**), `git`, and `curl`, and add your deploy user to the `docker` group.

## 3. Configure secrets

Clone the repository (for example to `/opt/libro`) and create the private environment file. It must be readable only by the deploy user; `scripts/deploy.sh` refuses to run otherwise.

```bash
cd /opt/libro
cp .env.example .env
chmod 600 .env
```

| Variable | Value |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Your own database name, role, and a long random password |
| `LIBRO_JWT_SECRET_BASE64` | `openssl rand -base64 32` |
| `LIBRO_ADMIN_PASSWORD_HASH` | BCrypt hash of a **strong, unique** password for the seeded `0000-0001` account (see below) |
| `LIBRO_BOOTSTRAP_LIBRARIAN_*` | All five values, with a BCrypt hash of a strong, unique librarian password |
| `LIBRO_DOMAIN` | The public host name from step 1 |
| `LIBRO_DATA_DIR` | `/mnt/libro` |
| `LIBRO_API_DOCS_ENABLED` | `true` for the synthetic-data demo (Swagger UI), otherwise `false` |
| `LIBRO_BACKUP_BUCKET` | Optional bucket name for off-host backups; see section 5 before enabling |

Generate a BCrypt hash without storing the password. The command prompts for it, and the `sed` turns the `$2y$` prefix into the `$2a$` form Spring and the Flyway check accept. **Single-quote the result in `.env`**, because hashes contain `$`:

```bash
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 10 "" | tr -d ':\n' | sed 's/^\$2y/$2a/'
```

Choose unique staff passwords for the deployment; see [Demo Accounts](../guides/demo-accounts.md). Keep them in a password manager, not in the repository.

## 4. First deploy

```bash
scripts/deploy.sh
```

The script runs preflight checks, builds the image on the VM, starts the stack, waits for health, creates the demo student, and runs the smoke test. Then load the rest of the demo data (it prompts for the librarian password and never stores it):

```bash
scripts/seed-demo.sh
```

Browse `https://<LIBRO_DOMAIN>/readyz`, and `/swagger-ui.html` if docs are enabled.

## 5. Backups and restore

Schedule a nightly backup and keep the dump count small (the volume is shared with the database):

```bash
# crontab -e
0 3 * * * cd /opt/libro && scripts/backup.sh >> /mnt/libro/backups/backup.log 2>&1
```

The optional off-host upload path in `scripts/backup.sh` depends on a provider-specific command-line tool and instance identity. This runbook covers local backups only. Before setting `LIBRO_BACKUP_BUCKET`, confirm that the upload tool and identity are configured for the target host and set a lifecycle rule to control storage growth.

**Rehearse a restore before relying on it:**

```bash
scripts/backup.sh                          # prints the dump path
scripts/restore.sh /mnt/libro/backups/libro-<timestamp>.dump
```

The restore asks you to type the database name, takes a safety backup, replaces the data in one transaction, restarts the app, and waits for health.

## 6. Updating and rolling back

- **Update.** `git pull`, then `scripts/deploy.sh`. It backs up first, records the previous image, and rolls back on a failed health check or smoke test.
- **Manual rollback.** The previous image tag is in `/mnt/libro/state/previous-image`. A script rollback restarts it; if the failed release applied a Flyway migration, restore the pre-deploy backup instead, because migrations are forward-only.
- **Never** run `docker compose down -v`; it deletes the database volume.

## 7. Enable automated deployment

[`cd.yml`](../../.github/workflows/cd.yml) publishes an ARM64 image after every successful CI run on `main`. It deploys only after you complete this one-time setup, so do it after the manual first deploy in section 4 works.

1. **Make the package pullable.** After the first publish, open the `libro-api` package on GitHub and set its visibility to **public**, because the VM pulls the image without credentials. The image contains no secrets.
2. **Create a deploy key.** On your machine run `ssh-keygen -t ed25519 -N "" -f libro_deploy`, append `libro_deploy.pub` to `~/.ssh/authorized_keys` of the deploy user on the VM, and keep the private key only for the next step. The user must be in the `docker` group, and the repository must be cloned at `/opt/libro` with its `.env` in place.
3. **Pin the host key.** Run `ssh-keyscan -t ed25519 <host>` and compare the fingerprint with `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` run on the VM before trusting it.
4. **Create the environment.** In the repository settings add a `production` environment with **required reviewers** (yourself), and configure the four SSH secrets the workflow reads: VM host, deploy user, private key, and verified host key. Delete the local key files afterwards.
5. **Turn it on.** Set the deployment-enable repository variable used by the workflow to `true`. Each successful `main` build then waits for your approval before deploying.
6. **Protect `main`.** Require the `unit-test`, `integration-test`, `security-scan`, and `ui-test` checks in the branch rule, so only validated API and UI commits can be released.

Keep manual approval until you have rehearsed a restore and a rollback on the VM. To pause deployments, disable the deployment-enable repository variable; image publishing continues.

## 8. Troubleshooting

### Recover a locked-out librarian

The API deliberately cannot reset a librarian account. On the VM, take a backup before changing its database row:

```bash
cd /opt/libro
scripts/backup.sh
```

Confirm the backup path printed by the script. Generate a temporary BCrypt hash in the terminal; the password is prompted for and is not written to the repository or shell history. The command captures the hash without printing it:

```bash
hash=$(docker run --rm -it httpd:2.4-alpine htpasswd -nBC 10 "" | tr -d ':\r\n' | sed 's/^\$2y/\$2a/')
read -r -p 'Librarian university ID: ' librarian_id
[[ "$librarian_id" =~ ^[0-9]{4}-[0-9]{4}$ ]] || { echo 'Invalid ID' >&2; exit 1; }
```

Load the private `.env` values needed by Compose, then update only that librarian row. Check that `UPDATE 1` is printed; `UPDATE 0` means the ID was wrong and no account changed. The SQL is sent through standard input, keeping the hash out of the command line and shell history:

```bash
set -a; source .env; set +a
printf "UPDATE users SET password_hash='%s', must_change_password=true WHERE university_id='%s' AND user_role='LIBRARIAN';\n" "$hash" "$librarian_id" \
  | docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1
unset hash
```

Have the librarian log in with the temporary password and change it through `PUT /app/users/{universityId}/password`. The login response's `mustChangePassword` flag becomes false after that change. This flag guides the client; the backend does not block other routes while it is true, and already issued JWTs remain usable until their normal expiry plus clock skew.

| Symptom | Check |
| --- | --- |
| Deploy stops at preflight | The message names the missing variable, file mode, or directory |
| `db` restarts with a permissions error | `/mnt/libro/postgres` must be owned by UID 70 |
| Caddy cannot get a certificate | DNS must point at the VM and ports 80/443 must be reachable (provider firewall and host firewall) |
| Rate limits hit every visitor | Confirm the app sees real client addresses; the override sets `native` forwarded headers and the app port must stay on loopback |
| Smoke test fails at login | Run `scripts/seed-demo.sh --patrons-only`, then check `docker compose logs app` |
