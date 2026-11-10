import React, { useState, useRef, useEffect } from "react";

const BACKEND = "http://localhost:8080";

function haversine(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function speak(text) {
  try {
    const u = new SpeechSynthesisUtterance(text);
    window.speechSynthesis.speak(u);
  } catch (e) {
    /* speech unsupported — alert log still shows the text */
  }
}

async function geocode(address) {
  const res = await fetch(
    `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(
      address + ", Bengaluru, India"
    )}`
  );
  const data = await res.json();
  if (!data.length) throw new Error(`Could not find location: ${address}`);
  return { lat: parseFloat(data[0].lat), lng: parseFloat(data[0].lon) };
}

async function getRoute(start, end) {
  const res = await fetch(
    `https://router.project-osrm.org/route/v1/driving/${start.lng},${start.lat};${end.lng},${end.lat}?overview=full&geometries=geojson`
  );
  const data = await res.json();
  if (!data.routes || !data.routes.length) throw new Error("No route found");
  const coords = data.routes[0].geometry.coordinates; // [lng, lat][]
  return {
    points: coords.map(([lng, lat]) => ({ lat, lng })),
    distanceMeters: data.routes[0].distance,
    durationSeconds: data.routes[0].duration,
  };
}

// ---------------- Detect tab ----------------
function DetectTab() {
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [startRoad, setStartRoad] = useState("Koramangala");
  const [endRoad, setEndRoad] = useState("Whitefield");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const onFile = (e) => {
    const f = e.target.files[0];
    if (!f) return;
    setFile(f);
    setPreview(URL.createObjectURL(f));
    setResult(null);
    setError(null);
  };

  const getPhotoLocation = () =>
    new Promise((resolve, reject) => {
      if (!navigator.geolocation) {
        reject(new Error("Geolocation not supported"));
        return;
      }
      navigator.geolocation.getCurrentPosition(
        (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
        (err) => reject(err),
        { enableHighAccuracy: true, timeout: 8000 }
      );
    });

  const run = async () => {
    if (!file || !startRoad || !endRoad) return;
    setLoading(true);
    setError(null);
    try {
      const startPt = await geocode(startRoad);
      const endPt = await geocode(endRoad);
      const route = await getRoute(startPt, endPt);

      const form = new FormData();
      form.append("file", file);
      form.append("routePoints", JSON.stringify(route.points));
      form.append("startLabel", startRoad);
      form.append("endLabel", endRoad);

      const res = await fetch(`${BACKEND}/api/detect`, { method: "POST", body: form });
      if (!res.ok) throw new Error(`Backend returned ${res.status}`);
      const data = await res.json();
      setResult(data);
    } catch (e) {
      setError(
        `${e.message} — is the backend running on :8080 and the detection service on :8001?`
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="panel">
      <h2>Detect pothole from a photo</h2>
      <p className="muted">
        Uploads to the Spring Boot backend, which forwards it to the Python
        detection service and saves any hits along your route so they show
        up next time someone navigates the same road.
      </p>

      <div className="row">
        <input value={startRoad} onChange={(e) => setStartRoad(e.target.value)} placeholder="Start road" />
        <input value={endRoad} onChange={(e) => setEndRoad(e.target.value)} placeholder="End road" />
      </div>

      <input type="file" accept="image/*" onChange={onFile} />
      {preview && <img src={preview} alt="preview" className="preview" />}

      <button className="btn" disabled={!file || !startRoad || !endRoad || loading} onClick={run}>
        {loading ? "Detecting…" : "Run detection"}
      </button>

      {error && <p className="error">{error}</p>}

      {result && (
        <div className="result">
          <p>
            <b>{result.detectionResult.pothole_count ?? 0}</b> hazard(s) detected,{" "}
            <b>{result.savedHazards.length}</b> saved along {startRoad} → {endRoad}.
          </p>
          {result.detectionResult.detections?.map((d, i) => (
            <div key={i} className="detection-row">
              Pothole — severity {d.severity} — confidence {d.confidence}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
// ---------------- Navigate tab ----------------
function NavigateTab() {
  const [start, setStart] = useState("Koramangala");
  const [end, setEnd] = useState("Whitefield");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [route, setRoute] = useState(null); // { points, distanceMeters, durationSeconds }
  const [hazards, setHazards] = useState([]); // RouteHazardResult[]
  const [driving, setDriving] = useState(false);
  const [log, setLog] = useState([]);

  const mapRef = useRef(null);
  const mapInstance = useRef(null);
  const layerGroup = useRef(null);
  const carMarker = useRef(null);
  const triggeredRef = useRef(new Set());
  const rafRef = useRef(null);
  const startTimeRef = useRef(0);

  useEffect(() => {
    if (!mapRef.current || mapInstance.current) return;
    const L = window.L;
    mapInstance.current = L.map(mapRef.current).setView([12.95, 77.66], 12);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; OpenStreetMap contributors",
    }).addTo(mapInstance.current);
    layerGroup.current = L.layerGroup().addTo(mapInstance.current);
  }, []);

  const findRoute = async () => {
    setLoading(true);
    setError(null);
    setRoute(null);
    setHazards([]);
    try {
      const startPt = await geocode(start);
      const endPt = await geocode(end);
      const r = await getRoute(startPt, endPt);
      setRoute(r);

      const res = await fetch(`${BACKEND}/api/routes/hazards`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ points: r.points, thresholdMeters: 200 }),
      });
      if (!res.ok) throw new Error(`Backend returned ${res.status}`);
      const hazardResults = await res.json();
      setHazards(hazardResults);
      drawMap(r, hazardResults);
    } catch (e) {
      setError(`${e.message} — is the backend running on :8080?`);
    } finally {
      setLoading(false);
    }
  };

  const drawMap = (r, hazardResults) => {
    const L = window.L;
    layerGroup.current.clearLayers();

    const latlngs = r.points.map((p) => [p.lat, p.lng]);
    L.polyline(latlngs, { color: "#FFC145", weight: 4 }).addTo(layerGroup.current);
    L.circleMarker(latlngs[0], { radius: 7, color: "#4FA7C9", fillOpacity: 1 }).addTo(layerGroup.current);
    L.circleMarker(latlngs[latlngs.length - 1], { radius: 7, color: "#3DDC84", fillOpacity: 1 }).addTo(
      layerGroup.current
    );

    hazardResults.forEach((h) => {
      const color = h.hazard.severity === "HIGH" || h.hazard.severity === "CRITICAL" ? "#FF6A39" : "#FFC145";
      L.circleMarker([h.hazard.latitude, h.hazard.longitude], {
        radius: 8,
        color,
        fillOpacity: 0.9,
      })
        .bindTooltip(`${h.hazard.type} — ${h.hazard.severity}`)
        .addTo(layerGroup.current);
    });

    mapInstance.current.fitBounds(latlngs);
  };

  const pushLog = (text) => setLog((l) => [...l, text].slice(-6));

  const clearAllHazards = async () => {
  if (!window.confirm("Delete ALL saved hazards? This can't be undone.")) return;
  const res = await fetch(`${BACKEND}/api/hazards/all`, { method: "DELETE" });
  const data = await res.json();
  alert(`Deleted ${data.deleted} hazards`);
};

  const startDriving = () => {
    if (!route) return;
    triggeredRef.current = new Set();
    setLog([]);
    setDriving(true);
    startTimeRef.current = performance.now();

    // cumulative distances along the route
    const cum = [0];
    for (let i = 1; i < route.points.length; i++) {
      const a = route.points[i - 1], b = route.points[i];
      cum.push(cum[i - 1] + haversine(a.lat, a.lng, b.lat, b.lng));
    }
    const totalM = cum[cum.length - 1];
    const simDurationMs = Math.max(15000, Math.min(45000, route.durationSeconds * 30)); // compressed sim time

    const L = window.L;
    const tick = () => {
      const elapsed = performance.now() - startTimeRef.current;
      const frac = Math.min(1, elapsed / simDurationMs);
      const curM = frac * totalM;

      // find current position by walking cumulative distances
      let idx = 0;
      while (idx < cum.length - 1 && cum[idx + 1] < curM) idx++;
      const pos = route.points[Math.min(idx, route.points.length - 1)];

      if (!carMarker.current) {
        carMarker.current = L.circleMarker([pos.lat, pos.lng], {
          radius: 7,
          color: "#4FA7C9",
          fillOpacity: 1,
        }).addTo(layerGroup.current);
      } else {
        carMarker.current.setLatLng([pos.lat, pos.lng]);
      }

      const speedMps = totalM / (simDurationMs / 1000); // simulated meters per second

      hazards.forEach((h) => {
        const aheadMeters = h.distanceAlongRouteMeters - curM;
        const aheadSeconds = aheadMeters / speedMps;
        const label = h.hazard.type === "POTHOLE" ? "pothole" : h.hazard.type === "SPEED_BREAKER" ? "speed breaker" : "road damage";
        const key500 = h.hazard.id + "_500", key200 = h.hazard.id + "_200", keyAt = h.hazard.id + "_at";

        if (aheadSeconds <= 8 && aheadSeconds > 3 && !triggeredRef.current.has(key500)) {
          triggeredRef.current.add(key500);
          const msg = `Warning. ${h.hazard.severity.toLowerCase()} ${label} detected ahead.`;
          speak(msg);
          pushLog(`🔊 ${msg}`);
        } else if (aheadSeconds <= 3 && aheadSeconds > 0.5 && !triggeredRef.current.has(key200)) {
          triggeredRef.current.add(key200);
          const msg = `${label} coming up. Please slow down.`;
          speak(msg);
          pushLog(`🔊 ${msg}`);
        } else if (aheadSeconds <= 0.5 && aheadMeters > -50 && !triggeredRef.current.has(keyAt)) {
          triggeredRef.current.add(keyAt);
          const msg = `Caution! ${label} here.`;
          speak(msg);
          pushLog(`📳 ${msg}`);
        }
      });

      if (frac < 1) {
        rafRef.current = requestAnimationFrame(tick);
      } else {
        setDriving(false);
        speak("You have arrived at your destination.");
        pushLog("🏁 Arrived");
      }
    };
    tick();
  };

  useEffect(() => () => rafRef.current && cancelAnimationFrame(rafRef.current), []);

  return (
    <div className="panel">
      <h2>Navigate with real hazard data</h2>
      <p className="muted">
        Geocodes with OpenStreetMap Nominatim, routes with OSRM, then asks the
        backend which saved hazards actually sit on that route.
      </p>

      <div className="row">
        <input value={start} onChange={(e) => setStart(e.target.value)} placeholder="Start point" />
        <input value={end} onChange={(e) => setEnd(e.target.value)} placeholder="End point" />
        <button className="btn" onClick={findRoute} disabled={loading}>
          {loading ? "Finding route…" : "Find route"}
        </button>
        <button className="btn" style={{ background: "#2E313C", color: "#f2f1ec" }} onClick={clearAllHazards}>
          Clear all hazards (dev)
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      <div ref={mapRef} className="map" />

      {route && (
        <div className="route-info">
          <p className="mono">
            {(route.distanceMeters / 1000).toFixed(1)} km · {hazards.length} hazard(s) on this route
          </p>
          {!driving ? (
            <button className="btn" onClick={startDriving}>Start journey simulation</button>
          ) : (
            <span className="live-pill">🔊 Voice alerts on</span>
          )}
        </div>
      )}

      <div className="alert-log">
        {log.length === 0 && <p className="muted small">Alerts will appear here as you drive.</p>}
        {log.map((l, i) => <p key={i} className="mono small">{l}</p>)}
      </div>
    </div>
  );
}

export default function App() {
  const [tab, setTab] = useState("navigate");

  return (
    <div className="app">
      <style>{`
        .app { max-width: 780px; margin: 0 auto; padding: 24px 16px 60px; }
        h1 { font-size: 20px; margin-bottom: 4px; }
        .tagline { color: #8B8E9B; font-size: 13px; margin-top: 0; }
        .tabs { display: flex; gap: 8px; margin: 16px 0; }
        .tab { padding: 8px 16px; border-radius: 999px; border: 1px solid #2E313C; background: transparent; color: #8B8E9B; cursor: pointer; }
        .tab.active { border-color: #FF6A39; color: #f2f1ec; background: rgba(255,106,57,0.08); }
        .panel { background: #1B1D24; border: 1px solid #2E313C; border-radius: 14px; padding: 20px; }
        .muted { color: #8B8E9B; font-size: 13px; }
        .small { font-size: 12px; }
        .mono { font-family: monospace; }
        .btn { background: #FF6A39; color: #1a0d06; border: none; padding: 10px 16px; border-radius: 8px; font-weight: 600; cursor: pointer; }
        .btn:disabled { opacity: 0.5; cursor: default; }
        .row { display: flex; gap: 8px; margin: 14px 0; flex-wrap: wrap; }
        input { background: #21232C; border: 1px solid #2E313C; color: #f2f1ec; padding: 9px 12px; border-radius: 8px; flex: 1; min-width: 140px; }
        .map { height: 320px; border-radius: 10px; margin: 14px 0; }
        .preview { display: block; max-width: 280px; border-radius: 10px; margin: 12px 0; }
        .error { color: #FF6A39; font-size: 13px; }
        .result { margin-top: 14px; }
        .detection-row { font-size: 13px; padding: 4px 0; }
        .route-info { display: flex; align-items: center; gap: 14px; margin: 10px 0; }
        .live-pill { color: #3DDC84; font-family: monospace; font-size: 12px; }
        .alert-log { margin-top: 12px; background: #21232C; border: 1px solid #2E313C; border-radius: 10px; padding: 10px 12px; min-height: 80px; }
      `}</style>

      <h1>RoadGuard AI</h1>
      <p className="tagline">Your AI co-pilot for safer Indian roads — live build</p>

      <div className="tabs">
        <button className={`tab ${tab === "navigate" ? "active" : ""}`} onClick={() => setTab("navigate")}>Navigate</button>
        <button className={`tab ${tab === "detect" ? "active" : ""}`} onClick={() => setTab("detect")}>Detect pothole</button>
      </div>

      {tab === "navigate" ? <NavigateTab /> : <DetectTab />}
    </div>
  );
}
