# Deploying TicketHub

Three pieces: **MySQL**, the **backend** (Docker), the **frontend** (static). This guide uses
Railway for the first two and Vercel for the third; Netlify or Cloudflare Pages work the same way.

> Run `mvn clean verify` locally and get CI green **before** deploying. The code has to build first.

## 1. Push to GitHub

```bash
git init && git add . && git commit -m "TicketHub"
git branch -M main
git remote add origin https://github.com/<you>/tickethub.git
git push -u origin main
```

Open the **Actions** tab. `CI` must go green (backend tests on MySQL 8, frontend build, Docker build).
Replace `<you>` in the README badge.

## 2. Database and backend on Railway

1. **New Project → Provision MySQL.** Keep the default service name `MySQL`.
2. **New → GitHub Repo → tickethub.** Railway reads `railway.json` and builds the `Dockerfile`.
3. On the backend service open **Variables** and add (the `${{ }}` parts are Railway reference variables):

| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&serverTimezone=UTC` |
| `DB_USER` | `${{MySQL.MYSQLUSER}}` |
| `DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `JWT_SECRET` | output of `openssl rand -base64 48` |
| `PAYMENT_WEBHOOK_SECRET` | any long random string |
| `CORS_ORIGINS` | your frontend URL, filled in at step 4 |

4. **Settings → Networking → Generate Domain.**
5. Check it is alive:

```bash
curl https://<backend-domain>/actuator/health      # {"status":"UP"}
```

Flyway creates the schema on first boot. The healthcheck only passes once the database is reachable.

## 3. Frontend on Vercel

1. **Add New → Project → import the repo.**
2. **Root Directory:** `frontend`. Framework preset: Vite.
3. **Environment variable:** `VITE_API_URL` = the backend URL from step 2.4, no trailing slash.
4. Deploy, then copy the Vercel URL into the backend's `CORS_ORIGINS` and let Railway redeploy.

`VITE_API_URL` is baked in at build time, so changing it means a redeploy.

## 4. Getting an admin and some data

There is no public admin sign-up, on purpose. Two options:

**Real deployment (recommended):** register through the app, then promote yourself in the
Railway MySQL **Data** tab:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';
```

Log in again to get a token with the ADMIN role, then create venues, halls, events and shows from
`/swagger-ui.html` (Authorize with your token).

**Portfolio demo:** set `SPRING_PROFILES_ACTIVE=demo`. This seeds a catalogue and two logins with
**publicly known passwords** (`admin@tickethub.dev` / `Admin123!`). It also enables
`POST /api/v1/dev/bookings/{ref}/simulate-payment`, which is what lets the mock gateway confirm a
booking from the UI. Fine for a demo; never for real money.

## 5. Constraints to know about

- **Run exactly one backend instance.** Live seat updates (SSE) and the rate limiter live in memory.
  Two instances means clients miss updates and limits apply per instance. Scaling out needs Redis
  pub/sub, which is a documented v2 item.
- **Payments are a mock.** Wiring Stripe or Razorpay means implementing the `PaymentGateway`
  interface and pointing the gateway's webhook at `POST /api/v1/payments/webhook`.
- **Free tiers sleep or limit resources.** Cold starts on the JVM take 20-40 seconds.
- **Rotate `JWT_SECRET` and `PAYMENT_WEBHOOK_SECRET`** if they ever land in a log or a commit.

## 6. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Healthcheck times out | `DB_URL` reference variable misspelled, or the MySQL service is named something other than `MySQL` |
| `Schema-validation: wrong column type` | Hibernate `ddl-auto: validate` disagrees with a column: paste the message and fix the mapping |
| Browser shows a CORS error | `CORS_ORIGINS` missing the exact frontend origin, or has a trailing slash |
| Login works, seat map never updates | SSE blocked by a proxy; the map still refreshes on reload |
| `429` on login | Rate limit is 10 per minute per IP |
