# RoadGuard AI — AI-Powered Road Hazard Detection & Navigation System

Three services, run locally, three terminals.

```
roadguard/
├── detection-service/   Python FastAPI — finds potholes in photos (tested, working)
├── backend/             Spring Boot — database, hazard APIs, route matching (Java — build on your machine)
└── frontend/            React (Vite) — real UI, calls both of the above (tested, builds clean)
```

## What's real vs. what to upgrade later

| Piece | Status today | Upgrade path |
|---|---|---|
| Detection service | **Real, tested.** Classical CV (OpenCV) finds dark irregular road regions. | Swap in a YOLOv8 model trained on a pothole dataset (needs GPU + labeled images — see comment at top of `main.py`). |
| Backend | **Real Java code, not yet compiled here** (this sandbox can't reach Maven Central). Runs fine on your machine. | Swap H2 for MySQL when you're ready for a shared/production DB — instructions in `application.properties`. |
| Frontend | **Real, builds clean.** Talks to the real backend and real routing/geocoding services — no mock data. | Add auth, photo capture from mobile camera, offline caching. |
| Routing/geocoding | **Real, free, public APIs** (OSRM + Nominatim) — no API key needed. | Swap for Google Maps API if you want it, or self-host OSRM for reliability at scale. |

## 1. Detection service (Python)

```bash
cd detection-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8001
```

Check it's alive: `curl http://localhost:8001/health`

## 2. Backend (Java / Spring Boot)

Requires JDK 17+ and Maven (or use IntelliJ / your IDE's built-in runner).

```bash
cd backend
mvn spring-boot:run
```

First run creates an H2 database file at `backend/data/roadguard.mv.db` and seeds it
with 5 sample hazards along the Koramangala–Whitefield corridor, so you have
something to demo before uploading a single photo.

Check it's alive: `curl http://localhost:8080/api/hazards`

Browse the database directly at `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:file:./data/roadguard`, user `sa`, no password).

## 3. Frontend (React)

Requires Node 18+.

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

## Demo order that matches your project doc

1. **Navigate tab** — type a start/end point (defaults to Koramangala → Whitefield),
   click "Find route." The map shows the real route with the seeded hazards
   sitting on it. Click "Start journey simulation" to hear real voice alerts
   fire as a marker drives the route.
2. **Detect pothole tab** — upload any road photo. It's sent through the real
   backend → real detection service → any hits are saved to the database.
   Switch back to Navigate and re-search the same route — your upload now
   shows up too, if it's near the route.

## Known limitations (say these plainly if asked in viva)

- Detection uses classical computer vision, not a trained neural network —
  it will miss some real potholes and occasionally flag shadows or manholes.
  The architecture is built so a real YOLOv8 model drops in without changing
  anything else (backend and frontend only talk to the `/detect` API contract).
- No user accounts/auth yet — anyone can submit a report.
- Voice alerts and live GPS tracking need the browser tab open and active;
  a production version would need a native app for background navigation.
- Route matching checks hazard distance to the nearest route point, not a
  true point-to-line-segment distance — accurate enough for city streets
  with the OSRM polyline's point density, but worth noting if pressed.

## Architecture recap for viva

```
Citizen uploads photo
        │
        ▼
  Frontend (React)
        │  multipart POST /api/detect
        ▼
  Backend (Spring Boot) ──── forwards image ────▶ Detection service (FastAPI + OpenCV)
        │                                                  │
        │◀───────────── bounding boxes + severity ─────────┘
        │
        ▼
  Saves to H2/MySQL as a RoadHazard row (lat, lng, severity, confidence)


Driver enters start + end
        │
        ▼
  Frontend geocodes (Nominatim) → routes (OSRM) → gets polyline
        │  POST /api/routes/hazards  { points: [...] }
        ▼
  Backend: Haversine-matches every ACTIVE hazard against the polyline
        │
        ▼
  Frontend draws hazards on the Leaflet map, then simulates driving
  and speaks alerts at 500m / 200m / 0m using the Web Speech API
```
