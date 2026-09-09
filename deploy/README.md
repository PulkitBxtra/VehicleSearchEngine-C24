# Deploying to a VPS

Assumes Docker and Docker Compose on the host, and DNS already pointing at it.

```bash
# 1. Firewall: SSH and web only.
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable

# 2. Swap, so a heap spike during seeding cannot trigger the OOM killer.
#    Skip if the box already has swap or more than 8 GB of RAM.
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 3. Secrets. Never commit this file.
cat > deploy/.env <<'ENV'
SITE_ADDRESS=search.example.com
DB_PASSWORD=<generate a long random one>
GEMINI_API_KEY=<from Google AI Studio>
ENV

# 4. Up.
cd deploy && docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

## Checks after deploy

```bash
curl -s https://your-domain/actuator/health         # {"status":"UP"}
docker compose -f docker-compose.prod.yml ps        # db healthy, app running
ss -tlnp | grep -E '5432|8080'                      # must show nothing on 0.0.0.0
```

That last check is the important one. If Postgres appears on `0.0.0.0`, a port
got published somewhere and `ufw` will not save you — Docker's iptables rules in
the `DOCKER-USER` chain are evaluated before ufw's.
