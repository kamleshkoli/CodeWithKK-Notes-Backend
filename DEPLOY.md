# Deploying CodeWithKK Backend (free tier)

Stack: **Render** (compute) + **Aiven** (MySQL) + **Cloudinary** (PDFs) + **Vercel** (frontend).

All four have no-expiry free tiers and need no credit card.

---

## 1. Aiven — free MySQL

1. Sign up at https://console.aiven.io (GitHub or Google login).
2. **Create Service** -> choose **MySQL** -> pick the **Free** plan -> region closest to your users (e.g. `google-europe-west1` or `us-east-1`).
3. Wait for status **RUNNING**.
4. Open the service -> **Connection** tab. Copy:
   - Host, Port, Database, User, Password
   - Keep the **Service URI** handy, it looks like
     `mysql://USER:PASSWORD@HOST:PORT/DATABASE`

Aiven **enforces TLS**. The JDBC URL must include `sslMode=REQUIRED` or every
connection will be rejected. Build yours like this:

```
jdbc:mysql://<HOST>:<PORT>/<DATABASE>?sslMode=REQUIRED&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

> Free-tier note: 1 GB storage, single node, and the service powers off after a
> stretch of inactivity (you get an email first). The app reconnects on its own
> but the first request after a long pause can take up to a minute.

---

## 2. Cloudinary — PDF storage (required)

Render's disk is **ephemeral** — every redeploy wipes it. Without Cloudinary the
app quietly falls back to `uploads/pdfs` and all admin-uploaded PDFs are lost.

1. Sign up at https://cloudinary.com (free tier, 25 GB).
2. Dashboard -> **Settings** -> **API Keys**. Copy **Cloud name**, **API key**,
   **API Secret**.

---

## 3. Render — free web service

1. Sign in at https://render.com, then **New -> Web Service** -> connect GitHub.
2. Pick `kamleshkoli/CodeWithKK-Notes-Backend`, branch `main`.
3. Settings:
   - **Runtime**: `Docker`
   - **Instance type**: `Free`
   - **Region**: same as your Aiven service
4. **Environment** — add all of these:

   | Key | Value |
   |---|---|
   | `MYSQL_URL` | the Aiven JDBC URL from step 1 |
   | `CLOUDINARY_CLOUD_NAME` | from step 2 |
   | `CLOUDINARY_API_KEY` | from step 2 |
   | `CLOUDINARY_API_SECRET` | from step 2 |
   | `RAZORPAY_KEY_ID` | from Razorpay dashboard |
   | `RAZORPAY_KEY_SECRET` | from Razorpay dashboard |
   | `JWT_SECRET` | a **new** random string, at least 32 chars |
   | `CORS_ALLOWED_ORIGINS` | your Vercel URL, e.g. `https://your-app.vercel.app` |

   `PORT` is already set to `8080` in the Dockerfile — don't add it.

   Generate the JWT secret with:

   ```bash
   openssl rand -base64 48
   ```

   > The previous signing secret was committed to git, so treat it as public
   > and burned. Use a fresh one. Existing tokens stop working, which is what
   > you want.

5. **Create Web Service**. First build takes ~4-6 min (Maven downloads).

You now have a URL like `https://codewithkk-backend.onrender.com`.

Watch the deploy log. A healthy start ends with `Started BackendApplication`.

---

## 4. Verify the backend

```bash
curl https://codewithkk-backend.onrender.com/api/notes
```

Expect JSON, not a 404. First call after a deploy can take 30-60s while the
free instance spins up.

A seeded admin account is created automatically:

- email: `admin@codewithkk.com`
- password: `admin123`

**Change that password before going live.**

---

## 5. Point the frontend at it

In `codewithkk-notes/.env`:

```
VITE_API_BASE_URL=https://codewithkk-backend.onrender.com
VITE_CHECKOUT_URL=<your real checkout link>
```

Then set the same two values in the **Vercel** project settings and redeploy —
Vite inlines `VITE_*` vars at build time, so an env change alone won't do it.
Locally, clear the Vite cache and restart:

```bash
rm -rf node_modules/.vite && npm run dev
```

The `vite.config.js` dev proxy on `localhost:8080` is only for local backend
development and is bypassed once `VITE_API_BASE_URL` is set.

---

## Razorpay

No webhook is needed — checkout completes in the browser and the backend
verifies the signature server-side via `POST /api/payment/verify`.

For live keys: Razorpay Dashboard -> Settings -> API Keys -> Generate Live Key.
The key id is returned to the browser from `/api/payment/create-order`, so the
frontend needs no Razorpay configuration of its own.

---

## Known free-tier limits

- Backend sleeps after 15 min idle; first request takes 30-60s.
- Aiven DB powers off when idle, so a cold start can chain two delays.
- 1 GB storage. PDFs belong in Cloudinary, not the database.
- No SLA, single-node DB, no support beyond docs.
