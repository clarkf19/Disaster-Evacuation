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

## 💡 Notes
- **Render free tier** sleeps after 15 minutes of inactivity, and the first request can take ~1 minute. The UI shows "Offline" until the backend responds.
- **Rate limits** are per client IP (`X-Forwarded-For`). Set `RATE_LIMIT_ENABLED=false` only for load testing.
- Live hazards and shelter occupancy are stored in memory and reset when the service restarts.
