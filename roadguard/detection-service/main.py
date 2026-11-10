"""
RoadGuard AI - Detection Service
---------------------------------
Real, runnable pothole-candidate detector using classical computer vision.
"""

from fastapi import FastAPI, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
import cv2
import numpy as np
from typing import Optional

app = FastAPI(title="RoadGuard Detection Service")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


def classify_severity(area_px: int, image_area_px: int) -> str:
    ratio = area_px / image_area_px
    if ratio > 0.05:
        return "CRITICAL"
    if ratio > 0.02:
        return "HIGH"
    if ratio > 0.005:
        return "MEDIUM"
    return "LOW"


def merge_overlapping_boxes(detections, distance_px=110):
    if not detections:
        return detections

    def boxes_close(a, b):
        ax1, ay1 = a["x"], a["y"]
        ax2, ay2 = a["x"] + a["width"], a["y"] + a["height"]
        bx1, by1 = b["x"], b["y"]
        bx2, by2 = b["x"] + b["width"], b["y"] + b["height"]
        return not (
            ax2 + distance_px < bx1 or bx2 + distance_px < ax1 or
            ay2 + distance_px < by1 or by2 + distance_px < ay1
        )

    merged = []
    used = [False] * len(detections)

    for i, det in enumerate(detections):
        if used[i]:
            continue
        group = [det]
        used[i] = True
        for j in range(i + 1, len(detections)):
            if used[j]:
                continue
            if boxes_close(det, detections[j]):
                group.append(detections[j])
                used[j] = True

        x1 = min(d["x"] for d in group)
        y1 = min(d["y"] for d in group)
        x2 = max(d["x"] + d["width"] for d in group)
        y2 = max(d["y"] + d["height"] for d in group)
        total_area = sum(d["area_px"] for d in group)
        best_conf = max(d["confidence"] for d in group)

        merged.append({
            "x": x1, "y": y1, "width": x2 - x1, "height": y2 - y1,
            "confidence": best_conf,
            "severity": max(group, key=lambda d: d["area_px"])["severity"],
            "area_px": total_area,
        })

    return merged


def detect_potholes(image_bytes: bytes):
    arr = np.frombuffer(image_bytes, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        return {"error": "Could not decode image"}, None

    h, w = img.shape[:2]
    image_area = h * w

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    blurred = cv2.GaussianBlur(gray, (7, 7), 0)

    thresh = cv2.adaptiveThreshold(
        blurred, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY_INV,
        blockSize=35, C=12
    )

    kernel = np.ones((5, 5), np.uint8)
    cleaned = cv2.morphologyEx(thresh, cv2.MORPH_OPEN, kernel, iterations=1)
    cleaned = cv2.morphologyEx(cleaned, cv2.MORPH_CLOSE, kernel, iterations=2)

    contours, _ = cv2.findContours(cleaned, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    detections = []
    min_area = image_area * 0.0015
    max_area = image_area * 0.35

    for c in contours:
        area = cv2.contourArea(c)
        if area < min_area or area > max_area:
            continue

        perimeter = cv2.arcLength(c, True)
        if perimeter == 0:
            continue

        circularity = 4 * np.pi * area / (perimeter * perimeter)
        if circularity < 0.06:
            continue

        x, y, bw, bh = cv2.boundingRect(c)
        aspect = bw / float(bh)
        if aspect > 4 or aspect < 0.25:
            continue

        confidence = round(min(0.95, 0.55 + circularity * 0.4), 2)

        detections.append({
            "x": int(x), "y": int(y), "width": int(bw), "height": int(bh),
            "confidence": confidence,
            "severity": classify_severity(int(area), image_area),
            "area_px": int(area),
        })

    detections.sort(key=lambda d: d["area_px"], reverse=True)
    detections = merge_overlapping_boxes(detections)
    return detections[:8], (w, h)


@app.get("/health")
def health():
    return {"status": "ok", "service": "roadguard-detection"}


@app.post("/detect")
async def detect(
    file: UploadFile = File(...),
    latitude: Optional[float] = Form(None),
    longitude: Optional[float] = Form(None),
):
    image_bytes = await file.read()
    detections, dims = detect_potholes(image_bytes)

    if dims is None:
        return {"error": "invalid_image"}

    return {
        "image_width": dims[0],
        "image_height": dims[1],
        "location": {"latitude": latitude, "longitude": longitude},
        "pothole_count": len(detections),
        "detections": detections,
    }