# Operations Runbook: OCI Always Free VM

Step-by-step provisioning, deployment, backup, and recovery for the single-VM demo described in [CI/CD and DevOps](ci-cd.md). The scripts named here live in [`scripts/`](../../scripts/) and were exercised against a local Docker stack; the first run on a real OCI host is the final check, so verify each step against Oracle's current console and documentation.

## 1. Before you start

- **Account.** Confirm Always Free eligibility and your home region. To avoid idle reclaim of the A1 VM, upgrade the tenancy to **Pay As You Go**, stay inside the Always Free limits, and create a **budget alert** (for example at US$1) so an accidental paid resource is noticed. Oracle's reclaim rules and free allowances change, so check [OCI Always Free resources](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm).
- **Capacity.** A1 instances sometimes report "out of host capacity". Retry later or in another availability domain of the same region.
- **Host name.** The proxy needs a DNS name that resolves to the VM's public IP. A free option is a dynamic DNS name (for example DuckDNS) or a wildcard-IP name such as `203-0-113-10.sslip.io`. Use the same value for `LIBRO_DOMAIN`.

## 2. Provision the VM

1. Create a **VM.Standard.A1.Flex** instance (2 OCPUs, 12 GB RAM) with an ARM64 Ubuntu LTS image and a reserved public IP.
2. Attach a **block volume** (50 GB is plenty) and mount it at `/mnt/libro` (format ext4, add it to `/etc/fstab` with `nofail`). Then create the data directory for PostgreSQL's Alpine user:

   ```bash
   sudo mkdir -p /mnt/libro/postgres /mnt/libro/backups
   sudo chown 70:70 /mnt/libro/postgres
   ```

3. **Network.** In the subnet's security list allow ingress TCP **80** and **443** from `0.0.0.0/0` and TCP **22 from your own IP only**. Do not open 5432, 8080, or 9091. Ubuntu images on OCI also ship host firewall rules that reject other ports; allow 80 and 443 there too and persist the rules.
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
| `LIBRO_BACKUP_BUCKET` | Optional Object Storage bucket name for off-host backups |

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

To keep copies off the host, create an Object Storage bucket, a dynamic group for the VM, and a policy that lets that group manage objects in the bucket, then set `LIBRO_BACKUP_BUCKET` and install the OCI CLI. Objects are encrypted at rest; add a lifecycle rule to delete old objects, because the Always Free allowance is small.

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
4. **Create the environment.** In the repository settings add a `production` environment with **required reviewers** (yourself), and add the environment secrets `OCI_HOST`, `OCI_USER`, `OCI_SSH_KEY` (the private key), and `OCI_SSH_KNOWN_HOSTS` (the verified line from step 3). Delete the local key files afterwards.
5. **Turn it on.** Add the repository variable `OCI_DEPLOY_ENABLED` with the value `true`. Each successful `main` build then waits for your approval before deploying.
6. **Protect `main`.** Add a branch ruleset that requires the `unit-test`, `integration-test`, and `security-scan` checks, so only validated commits can be released.

Keep manual approval until you have rehearsed a restore and a rollback on the VM. To pause deployments, set `OCI_DEPLOY_ENABLED` to anything other than `true`; image publishing continues.

## 8. Troubleshooting

| Symptom | Check |
| --- | --- |
| Deploy stops at preflight | The message names the missing variable, file mode, or directory |
| `db` restarts with a permissions error | `/mnt/libro/postgres` must be owned by UID 70 |
| Caddy cannot get a certificate | DNS must point at the VM and ports 80/443 must be reachable (security list and host firewall) |
| Rate limits hit every visitor | Confirm the app sees real client addresses; the override sets `native` forwarded headers and the app port must stay on loopback |
| Smoke test fails at login | Run `scripts/seed-demo.sh --patrons-only`, then check `docker compose logs app` |
