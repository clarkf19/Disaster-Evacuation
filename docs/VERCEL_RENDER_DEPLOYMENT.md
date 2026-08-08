# 🚀 Step-by-Step Deployment Guide: Vercel (Frontend) + Render (Backend)

This guide walks you through deploying the **Mumbai Disaster Evacuation System** to production using **Render.com** for the Spring Boot backend and **Vercel.com** for the React frontend.

---

## 📋 Prerequisites
1. A **GitHub account** with your project repository pushed.
2. A free account on [Render.com](https://render.com).
3. A free account on [Vercel.com](https://vercel.com).

---

## 🔹 STEP 1: Deploy Backend to Render (Spring Boot)

1. Log into your [Render Dashboard](https://dashboard.render.com).
2. Click **New +** (top right) and select **Web Service**.
3. Connect your GitHub repository (`Disaster-Evacuation`).
4. Configure the Web Service settings:
   - **Name**: `mumbai-evac-backend` (or your preferred name)
   - **Region**: Singapore or nearest region
   - **Root Directory**: `backend`
   - **Runtime**: **Docker** (Render detects `backend/Dockerfile` automatically)
   - **Instance Type**: **Free**

5. Scroll down to **Environment Variables** and add:
   | Key | Value |
   |-----|-------|
   | `TOMTOM_API_KEY` | `your_tomtom_api_key_here` |
   | `GEMINI_API_KEY` | `your_gemini_api_key_here` |

6. Click **Create Web Service**.
7. Render will build and launch your container (~2–3 minutes).
8. Once complete, copy your live backend URL from the top of the Render dashboard, for example:
   `https://mumbai-evac-backend.onrender.com`

> 🧪 **Verification**: Open `https://mumbai-evac-backend.onrender.com/api/shelters` in your browser. You should receive a JSON response listing Mumbai shelters!

---

## 🔹 STEP 2: Configure Frontend Vercel Proxy

1. Open `frontend/vercel.json` in your code editor.
2. Replace `https://YOUR-RENDER-BACKEND-URL.onrender.com` with your real Render URL from Step 1:
   ```json
   {
     "version": 2,
     "rewrites": [
       {
         "source": "/api/:path*",
         "destination": "https://mumbai-evac-backend.onrender.com/api/:path*"
       },
       {
         "source": "/(.*)",
         "destination": "/index.html"
       }
     ]
   }
   ```
3. Commit and push this change to GitHub:
   ```bash
   git add frontend/vercel.json
   git commit -m "Configure production backend URL in vercel.json"
   git push origin main
   ```

---

## 🔹 STEP 3: Deploy Frontend to Vercel (React + Vite)

1. Log into your [Vercel Dashboard](https://vercel.com/dashboard).
2. Click **Add New...** → **Project**.
3. Import your GitHub repository (`Disaster-Evacuation`).
4. On the configuration screen:
   - **Framework Preset**: Vite
   - **Root Directory**: Click **Edit** and select `frontend`
5. Click **Deploy**.
6. Within 60 seconds, Vercel will complete the deployment and provide your live URL, e.g.:
   `https://mumbai-evac.vercel.app`

---

## 🎉 STEP 4: Live Verification & Testing

Open your live Vercel link (`https://mumbai-evac.vercel.app`):
1. **Live Route Planning**: Type a start and destination (e.g. Virar to Colaba) and calculate route.
2. **Disaster Placement & Evacuation**: Place a disaster circle on the map and watch route re-routing in real time.
3. **Disaster Protection Hub**: Open the Disasters tab to view First Aid steps, Mumbai hospital hotlines, and Emergency Kit checklists.
4. **Emergency AI Assistant**: Open the Chatbot widget to ask safety questions.

---

## 💡 Troubleshooting & Notes
- **Render Free Tier Spin-up**: Render's free tier puts inactive services to sleep after 15 minutes. The first request after sleep takes ~30 seconds to spin up.
- **CORS**: All `/api/*` traffic is proxied through Vercel's edge network directly to Render, eliminating CORS issues.
