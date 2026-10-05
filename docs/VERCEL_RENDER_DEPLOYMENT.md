# 🚀 Deployment: Vercel (frontend) + Render (backend)

## 📋 Prerequisites
- The repository on GitHub
- Free accounts on [Render](https://render.com) and [Vercel](https://vercel.com)

---

## 🔹 Step 1: Backend on Render

1. In the Render dashboard, choose **New + → Web Service** and connect the repository.
2. Settings:
   - **Root Directory**: `backend`
   - **Runtime**: Docker (uses `backend/Dockerfile`)
   - **Health Check Path**: `/actuator/health`
   - **Instance Type**: Free
3. Environment variables:

   | Key | Value |
   |-----|-------|
   | `ADMIN_TOKEN` | A long random string. **Required**: without it, anyone can change live hazards and shelters. |
   | `TOMTOM_API_KEY` | *(optional)* live traffic |
   | `GEMINI_API_KEY` | *(optional)* AI assistant |
   | `GEMINI_MODEL` | *(optional)* override if the default model is retired |

4. Create the service. Once it's up, check that `https://<your-service>.onrender.com/actuator/health` returns `{"status":"UP"}`.

> Generate a token with `openssl rand -hex 24` and share it only with operators. Operators enter it in the **Hazards** tab. It is kept in the browser's sessionStorage and sent as the `X-Admin-Token` header.

---

## 🔹 Step 2: Point the frontend at the backend

`frontend/vercel.json` rewrites `/api/*` to the Render service. If your service URL is different, update the destination:

```json
{
  "version": 2,
  "rewrites": [
    { "source": "/api/:path*", "destination": "https://<your-service>.onrender.com/api/:path*" },
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

All third-party calls (TomTom, Photon, Nominatim, Gemini) go through the backend, so `/api` is the only rewrite needed.

---

## 🔹 Step 3: Frontend on Vercel

1. In Vercel, choose **Add New → Project** and import the repository.
2. Set **Framework Preset** to Vite and **Root Directory** to `frontend`.
3. Deploy.

---

## ✅ Verification
1. **Route planner**: plan a route, place a flood zone across it from the Hazards tab (operator token needed), and the route re-plans around the zone.
2. **Inside a zone**: set the start point inside a zone. The route leads out of it (status "Leave the Hazard Zone").
3. **Command Centre**: run the *Western Suburbs* stress test and compare the strategies on the map.
4. **Search**: type a place name. Suggestions come from `/api/search`.

## ⚡ Latency
- **Render's free tier sleeps** after 15 minutes without traffic, and waking takes 30–60 s. The repo includes `.github/workflows/keep-alive.yml`, which pings `/actuator/health` every 10 minutes so the backend stays warm (free for public repos; check the **Actions** tab that it runs). If your Render URL differs, set a repository variable `BACKEND_URL` (Settings → Secrets and variables → Actions → Variables). An external pinger such as UptimeRobot (5-minute interval) is an even more punctual alternative.
- **Region:** create the Render service in **Singapore** — it is the closest Render region to Mumbai, and every API call is faster than from a US region.
- **Startup:** the Docker image unpacks the jar and ships a Class Data Sharing archive created by a training start, which roughly halves JVM/Spring start-up on a single CPU.
- **Polling:** the UI fetches everything it needs from one `GET /api/live` call and retries every 3 s (showing "Waking up") while the server starts.

## 💡 Notes
- If the backend was asleep, the first visit shows "Waking up" for up to about a minute, then fills in automatically.
- **Rate limits** are per client IP (`X-Forwarded-For`). Set `RATE_LIMIT_ENABLED=false` only for load testing.
- Live hazards and shelter occupancy are stored in memory and reset when the service restarts.
- **Memory:** the full network (≈95k road nodes, 216k segments, residential streets included) runs the whole API test suite within a 300 MB heap, so it fits the free 512 MB instance (the Dockerfile caps the heap at 75 % of the container). If you run out of memory anyway, rebuild a lighter graph with `python scripts/build_datasets.py --no-residential` and redeploy.
- **Evaluation endpoints** are CPU-heavy (each Monte Carlo run is two full simulations). They are rate-limited to 6 requests/minute per IP.
