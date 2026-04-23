"use client";
import { useEffect, useRef } from "react";
import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";

interface VendorMapProps {
  address: string;
  lat?: number;
  lng?: number;
}

const VendorMap: React.FC<VendorMapProps> = ({ address, lat = 40.6892, lng = -73.9857 }) => {
  const mapContainer = useRef<HTMLDivElement>(null);
  const map = useRef<mapboxgl.Map | null>(null);

  useEffect(() => {
    if (!mapContainer.current) return;

    const token = process.env.NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN;
    if (!token) return;

    mapboxgl.accessToken = token;

    const mapInstance = new mapboxgl.Map({
      container: mapContainer.current,
      style: "mapbox://styles/mapbox/streets-v12",
      center: [lng, lat],
      zoom: 14,
    });
    map.current = mapInstance;

    mapInstance.addControl(new mapboxgl.NavigationControl(), "top-right");

    new mapboxgl.Marker({ color: "#2D6A4F" })
      .setLngLat([lng, lat])
      .setPopup(new mapboxgl.Popup().setText(address))
      .addTo(mapInstance);

    // Fix sizing when the map initializes in a flex/grid container
    // that may not have its final dimensions at mount time.
    mapInstance.on("load", () => mapInstance.resize());
    const resizeTimer = setTimeout(() => mapInstance.resize(), 100);

    const resizeObserver = new ResizeObserver(() => mapInstance.resize());
    resizeObserver.observe(mapContainer.current);

    return () => {
      clearTimeout(resizeTimer);
      resizeObserver.disconnect();
      mapInstance.remove();
      map.current = null;
    };
  }, [lat, lng, address]);

  const token = process.env.NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN;

  if (!token) {
    return (
      <div
        style={{
          width: "100%",
          height: "100%",
          background: "linear-gradient(135deg, #E8F5E9 0%, #C8E6C9 40%, #A5D6A7 100%)",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          borderRadius: "0 0 1rem 1rem",
        }}
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="rgba(45,106,79,0.5)" strokeWidth="1.5">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
          <circle cx="12" cy="10" r="3" />
        </svg>
        <span style={{ marginTop: "0.5rem", fontSize: "0.75rem", fontWeight: 500, color: "rgba(45,106,79,0.7)" }}>
          Set NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN to enable map
        </span>
      </div>
    );
  }

  return <div ref={mapContainer} style={{ width: "100%", height: "100%" }} />;
};

export default VendorMap;
