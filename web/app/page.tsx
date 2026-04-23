'use client'
import { useState, useEffect, useRef } from "react";
import Image from "next/image";
import Link from "next/link";

const COLORS = {
  greenDark: "#1B4332",
  green: "#2D6A4F",
  greenMed: "#40916C",
  greenLight: "#52B788",
  greenAccent: "#A5D6A7",
  greenPale: "#E8F5E9",
  orange: "#D4700A",
  orangeLight: "#F4A261",
  orangePale: "#FFF3E0",
  red: "#C1292E",
  redPale: "#FFEBEE",
  blue: "#1565C0",
  bluePale: "#E3F2FD",
  text: "#1A1A1A",
  textSec: "#5A5550",
  textTri: "#8B8680",
  textQuat: "#AAA5A0",
  bg: "#F7F5F0",
  card: "#FFFFFF",
  cardInner: "#FAFAF7",
  subtle: "#F5F3EE",
  divider: "#F0EDE8",
  nav: "#EDEBE6",
};

function NeighborlyLogoMark({
  size,
  radius,
  shadow = "0 4px 14px rgba(45,106,79,0.25)",
  priority = false,
}: {
  size: number;
  radius?: number;
  shadow?: string;
  priority?: boolean;
}) {
  // The SVG is mostly padding in the viewBox; without scaling the mark looks tiny, and
  // a white card behind it double-stacks with the asset’s own light area.
  const r = radius ?? (size * 11) / 38;
  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: r,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        boxShadow: shadow,
        overflow: "hidden",
        flexShrink: 0,
        lineHeight: 0,
      }}
    >
      <Image
        src="/icon.svg"
        alt=""
        width={size}
        height={size}
        style={{
          objectFit: "cover",
          objectPosition: "center",
          width: size,
          height: size,
          transform: "scale(1.48)",
          transformOrigin: "center",
        }}
        priority={priority}
      />
    </div>
  );
}

export default function NeighborlyHomepage() {
  const [scrollY, setScrollY] = useState(0);
  const [mobileMenu, setMobileMenu] = useState(false);
  const [activeFeature, setActiveFeature] = useState(0);

  useEffect(() => {
    const onScroll = () => setScrollY(window.scrollY);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  // Auto-cycle features
  useEffect(() => {
    const timer = setInterval(() => setActiveFeature((p) => (p + 1) % 4), 4000);
    return () => clearInterval(timer);
  }, []);

  const navSolid = scrollY > 60;

  return (
    <div style={{ fontFamily: "'DM Sans', sans-serif", color: COLORS.text, background: COLORS.bg, overflowX: "hidden" }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=DM+Sans:opsz,wght@9..40,300;9..40,400;9..40,500;9..40,600;9..40,700&family=Fraunces:opsz,wght@9..144,300;9..144,400;9..144,500;9..144,600;9..144,700&display=swap');

        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        html { scroll-behavior: smooth; }

        ::selection { background: ${COLORS.greenAccent}; color: ${COLORS.greenDark}; }

        @keyframes fadeUp {
          from { opacity: 0; transform: translateY(32px); }
          to { opacity: 1; transform: translateY(0); }
        }
        @keyframes fadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }
        @keyframes float {
          0%, 100% { transform: translateY(0px); }
          50% { transform: translateY(-12px); }
        }
        @keyframes pulse {
          0%, 100% { transform: scale(1); opacity: 0.6; }
          50% { transform: scale(1.15); opacity: 0.3; }
        }
        @keyframes slideIn {
          from { opacity: 0; transform: translateX(-20px); }
          to { opacity: 1; transform: translateX(0); }
        }
        @keyframes marquee {
          from { transform: translateX(0); }
          to { transform: translateX(-50%); }
        }
        @keyframes countUp {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }

        .fade-up { animation: fadeUp 0.7s ease-out both; }
        .fade-up-d1 { animation: fadeUp 0.7s ease-out 0.1s both; }
        .fade-up-d2 { animation: fadeUp 0.7s ease-out 0.2s both; }
        .fade-up-d3 { animation: fadeUp 0.7s ease-out 0.3s both; }
        .fade-up-d4 { animation: fadeUp 0.7s ease-out 0.4s both; }
        .fade-up-d5 { animation: fadeUp 0.7s ease-out 0.5s both; }

        .hover-lift { transition: transform 0.3s ease, box-shadow 0.3s ease; }
        .hover-lift:hover { transform: translateY(-6px); box-shadow: 0 16px 40px rgba(0,0,0,0.08); }

        .hover-glow { transition: all 0.3s ease; }
        .hover-glow:hover { box-shadow: 0 0 0 4px ${COLORS.greenAccent}40; }

        .btn-primary {
          display: inline-flex; align-items: center; gap: 8px;
          padding: 16px 32px; border-radius: 14px; border: none;
          background: ${COLORS.green}; color: white;
          font-family: 'DM Sans', sans-serif; font-size: 15px; font-weight: 600;
          cursor: pointer; transition: all 0.25s ease; text-decoration: none;
        }
        .btn-primary:hover { background: ${COLORS.greenDark}; transform: translateY(-2px); box-shadow: 0 8px 24px rgba(45,106,79,0.3); }

        .btn-secondary {
          display: inline-flex; align-items: center; gap: 8px;
          padding: 16px 32px; border-radius: 14px;
          border: 1.5px solid ${COLORS.green}; background: transparent; color: ${COLORS.green};
          font-family: 'DM Sans', sans-serif; font-size: 15px; font-weight: 600;
          cursor: pointer; transition: all 0.25s ease; text-decoration: none;
        }
        .btn-secondary:hover { background: ${COLORS.greenPale}; transform: translateY(-2px); }

        .section-label {
          font-size: 12px; font-weight: 600; letter-spacing: 0.1em; text-transform: uppercase;
          color: ${COLORS.green}; margin-bottom: 12px;
          display: inline-flex; align-items: center; gap: 8px;
        }
        .section-label::before {
          content: ''; display: block; width: 20px; height: 2px;
          background: ${COLORS.green}; border-radius: 2px;
        }

        .heading-xl {
          font-family: 'Fraunces', serif; font-weight: 600; font-size: clamp(36px, 5vw, 56px);
          line-height: 1.1; letter-spacing: -0.03em; color: ${COLORS.text};
        }
        .heading-lg {
          font-family: 'Fraunces', serif; font-weight: 600; font-size: clamp(28px, 3.5vw, 42px);
          line-height: 1.15; letter-spacing: -0.02em; color: ${COLORS.text};
        }
        .heading-md {
          font-family: 'Fraunces', serif; font-weight: 600; font-size: 22px;
          line-height: 1.25; letter-spacing: -0.01em;
        }

        .body-lg { font-size: 17px; line-height: 1.65; color: ${COLORS.textSec}; }
        .body-md { font-size: 15px; line-height: 1.6; color: ${COLORS.textTri}; }

        .container { max-width: 1200px; margin: 0 auto; padding: 0 24px; }

        .noise-overlay {
          position: absolute; inset: 0; opacity: 0.03; pointer-events: none;
          background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
        }

        @media (max-width: 768px) {
          .container { padding: 0 16px; }
          .desktop-only { display: none !important; }
        }
        @media (min-width: 769px) {
          .mobile-only { display: none !important; }
        }
      `}</style>

      {/* ─── NAVBAR ───────────────────────────────────────────────────── */}
      <nav style={{
        position: "fixed", top: 0, left: 0, right: 0, zIndex: 100,
        padding: "14px 0",
        background: navSolid ? "rgba(247,245,240,0.88)" : "transparent",
        backdropFilter: navSolid ? "blur(20px)" : "none",
        WebkitBackdropFilter: navSolid ? "blur(20px)" : "none",
        borderBottom: navSolid ? `1px solid ${COLORS.divider}` : "1px solid transparent",
        transition: "all 0.35s ease",
      }}>
        <div className="container" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <NeighborlyLogoMark size={38} priority />
            <span style={{ fontFamily: "'Fraunces', serif", fontWeight: 700, fontSize: 19, letterSpacing: "-0.02em" }}>Neighborly</span>
          </div>

          <div className="desktop-only" style={{ display: "flex", alignItems: "center", gap: 36 }}>
            {["Features", "How It Works", "Community", "Vendors"].map((item) => (
              <a key={item} href={`#${item.toLowerCase().replace(/ /g, "-")}`} style={{
                textDecoration: "none", color: COLORS.textSec, fontSize: 14, fontWeight: 500,
                transition: "color 0.2s",
              }} onMouseEnter={(e) => e.currentTarget.style.color = COLORS.green}
                 onMouseLeave={(e) => e.currentTarget.style.color = COLORS.textSec}
              >{item}</a>
            ))}
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <Link href="/auth/login" className="desktop-only" style={{ textDecoration: "none", color: COLORS.textSec, fontSize: 14, fontWeight: 600, padding: "10px 16px" }}>Sign In</Link>
            <Link href="/auth/register" className="btn-primary" style={{ padding: "10px 22px", fontSize: 13 }}>Get Started</Link>
          </div>
        </div>
      </nav>

      {/* ─── HERO ─────────────────────────────────────────────────────── */}
      <section style={{
        position: "relative", minHeight: "100vh", display: "flex", alignItems: "center",
        overflow: "hidden", paddingTop: 100,
      }}>
        {/* Background shapes */}
        <div style={{
          position: "absolute", width: 600, height: 600, borderRadius: "50%",
          background: `radial-gradient(circle, ${COLORS.greenPale} 0%, transparent 70%)`,
          top: "-10%", right: "-10%", animation: "pulse 8s ease-in-out infinite",
        }} />
        <div style={{
          position: "absolute", width: 400, height: 400, borderRadius: "50%",
          background: `radial-gradient(circle, ${COLORS.orangePale} 0%, transparent 70%)`,
          bottom: "5%", left: "-5%", animation: "pulse 10s ease-in-out infinite 2s",
        }} />
        <div className="noise-overlay" />

        <div className="container" style={{ position: "relative", zIndex: 2 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 60, alignItems: "center" }}>
            {/* Left content */}
            <div>
              <div className="fade-up" style={{
                display: "inline-flex", alignItems: "center", gap: 8,
                background: COLORS.greenPale, borderRadius: 100, padding: "7px 16px 7px 8px",
                marginBottom: 28, border: `1px solid ${COLORS.greenAccent}50`,
              }}>
                <span style={{
                  background: COLORS.green, color: "white", fontSize: 10, fontWeight: 700,
                  padding: "3px 8px", borderRadius: 100, letterSpacing: "0.05em",
                }}>NEW</span>
                <span style={{ fontSize: 13, fontWeight: 500, color: COLORS.green }}>Route optimization is live</span>
              </div>

              <h1 className="heading-xl fade-up-d1">
                Shop smarter,<br />
                <span style={{ color: COLORS.green }}>save more</span>,<br />
                eat better.
              </h1>

              <p className="body-lg fade-up-d2" style={{ marginTop: 24, maxWidth: 480 }}>
                Neighborly combines real-time price comparison, intelligent route planning, and dietary preferences into one
                seamless grocery experience.
              </p>

              <div className="fade-up-d3" style={{ display: "flex", gap: 14, marginTop: 36, flexWrap: "wrap" }}>
                <a href="#" className="btn-primary">
                  Download App
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
                </a>
                <a href="#how-it-works" className="btn-secondary">See How It Works</a>
              </div>

              <div className="fade-up-d4" style={{ display: "flex", gap: 32, marginTop: 48 }}>
                {[
                  { val: "23.6%", sub: "food price rise since 2020" },
                  { val: "60+", sub: "hrs/year on grocery shopping" },
                  { val: "$42", sub: "avg monthly savings" },
                ].map((s, i) => (
                  <div key={i}>
                    <div style={{ fontFamily: "'Fraunces', serif", fontSize: 26, fontWeight: 600, color: COLORS.green }}>{s.val}</div>
                    <div style={{ fontSize: 12, color: COLORS.textTri, marginTop: 2, maxWidth: 110, lineHeight: 1.4 }}>{s.sub}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Right — Phone mockup */}
            <div className="fade-up-d3 desktop-only" style={{ display: "flex", justifyContent: "center" }}>
              <div style={{
                width: 300, height: 620, borderRadius: 40, position: "relative",
                background: COLORS.text, padding: 10,
                boxShadow: "0 40px 80px rgba(0,0,0,0.15), 0 0 0 1px rgba(255,255,255,0.05) inset",
                animation: "float 6s ease-in-out infinite",
              }}>
                {/* Notch */}
                <div style={{
                  position: "absolute", top: 10, left: "50%", transform: "translateX(-50%)",
                  width: 100, height: 28, borderRadius: "0 0 16px 16px",
                  background: COLORS.text, zIndex: 5,
                }} />
                {/* Screen */}
                <div style={{
                  width: "100%", height: "100%", borderRadius: 30, overflow: "hidden",
                  background: COLORS.bg,
                }}>
                  {/* Mini app UI */}
                  <div style={{ padding: 18, paddingTop: 44 }}>
                    {/* Status */}
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, fontWeight: 600, marginBottom: 16 }}>
                      <span>9:41</span>
                      <div style={{ display: "flex", gap: 4 }}>
                        <div style={{ width: 14, height: 10, borderRadius: 2, border: `1.5px solid ${COLORS.text}` }}>
                          <div style={{ width: "70%", height: "100%", background: COLORS.green, borderRadius: 1 }} />
                        </div>
                      </div>
                    </div>
                    {/* Mini header */}
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <NeighborlyLogoMark size={28} radius={8} shadow="0 2px 8px rgba(45,106,79,0.2)" />
                        <span style={{ fontFamily: "'Fraunces',serif", fontWeight: 700, fontSize: 14 }}>Neighborly</span>
                      </div>
                      <div style={{ width: 24, height: 24, borderRadius: 7, background: COLORS.nav }} />
                    </div>
                    {/* Mini banner */}
                    <div style={{
                      background: `linear-gradient(135deg, ${COLORS.greenDark}, ${COLORS.green})`,
                      borderRadius: 14, padding: 14, marginBottom: 12, color: "white",
                    }}>
                      <div style={{ fontSize: 9, opacity: 0.6 }}>Good morning</div>
                      <div style={{ fontFamily: "'Fraunces',serif", fontSize: 16, fontWeight: 600 }}>User</div>
                      <div style={{ fontSize: 8, opacity: 0.6, marginTop: 4 }}>3 stores · saving $6.70</div>
                      <div style={{
                        marginTop: 10, background: "white", borderRadius: 8, padding: "7px 0",
                        textAlign: "center", color: COLORS.green, fontSize: 10, fontWeight: 600,
                      }}>Start Trip →</div>
                    </div>
                    {/* Mini stats */}
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 12 }}>
                      <div style={{ background: "white", borderRadius: 12, padding: 10, border: `1px solid ${COLORS.divider}` }}>
                        <div style={{ fontSize: 8, color: COLORS.textTri }}>Budget</div>
                        <div style={{ fontFamily: "'Fraunces',serif", fontSize: 16, fontWeight: 600, color: COLORS.green }}>$57</div>
                        <div style={{ height: 4, borderRadius: 4, background: COLORS.divider, marginTop: 6 }}>
                          <div style={{ width: "48%", height: "100%", borderRadius: 4, background: COLORS.green }} />
                        </div>
                      </div>
                      <div style={{ background: "white", borderRadius: 12, padding: 10, border: `1px solid ${COLORS.divider}` }}>
                        <div style={{ fontSize: 8, color: COLORS.textTri }}>Saved</div>
                        <div style={{ fontFamily: "'Fraunces',serif", fontSize: 16, fontWeight: 600, color: COLORS.orange }}>$42</div>
                        <div style={{ display: "flex", gap: 2, alignItems: "flex-end", height: 16, marginTop: 6 }}>
                          {[35, 55, 40, 70, 50, 85].map((h, i) => (
                            <div key={i} style={{ flex: 1, height: `${h}%`, borderRadius: 2, background: i === 5 ? COLORS.orange : COLORS.orangePale }} />
                          ))}
                        </div>
                      </div>
                    </div>
                    {/* Mini grocery items */}
                    {["Organic Bananas", "Whole Milk", "Chicken Breast"].map((item, i) => (
                      <div key={i} style={{
                        display: "flex", justifyContent: "space-between", alignItems: "center",
                        padding: "8px 0", borderBottom: i < 2 ? `1px solid ${COLORS.divider}` : "none",
                      }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                          <div style={{ width: 5, height: 5, borderRadius: "50%", background: [COLORS.green, COLORS.blue, COLORS.red][i] }} />
                          <span style={{ fontSize: 10, fontWeight: 500 }}>{item}</span>
                        </div>
                        <span style={{ fontSize: 10, fontWeight: 600 }}>${[1.29, 3.49, 5.99][i]}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── LOGO MARQUEE ─────────────────────────────────────────────── */}
      <section style={{ padding: "60px 0 40px", overflow: "hidden" }}>
        <p style={{ textAlign: "center", fontSize: 12, fontWeight: 600, letterSpacing: "0.08em", textTransform: "uppercase", color: COLORS.textQuat, marginBottom: 24 }}>
          Prices aggregated from stores you love
        </p>
        <div style={{ display: "flex", animation: "marquee 20s linear infinite", width: "fit-content" }}>
          {[...Array(2)].flatMap(() => [ "ShopRite", "MORE COMING SOON", "ShopRite", "MORE COMING SOON", "ShopRite", "MORE COMING SOON"]).map((name, i) => (
            <div key={i} style={{
              padding: "12px 36px", marginRight: 24,
              background: COLORS.card, borderRadius: 12, border: `1px solid ${COLORS.divider}`,
              fontSize: 14, fontWeight: 600, color: COLORS.textTri, whiteSpace: "nowrap",
            }}>{name}</div>
          ))}
        </div>
      </section>

      {/* ─── FEATURES ─────────────────────────────────────────────────── */}
      <section id="features" style={{ padding: "80px 0" }}>
        <div className="container">
          <div style={{ textAlign: "center", maxWidth: 580, margin: "0 auto 60px" }}>
            <div className="section-label" style={{ justifyContent: "center" }}>Core Features</div>
            <h2 className="heading-lg">Everything you need for<br /><span style={{ color: COLORS.green }}>smarter grocery trips</span></h2>
            <p className="body-md" style={{ marginTop: 16 }}>
              No more switching between apps. Neighborly handles price comparison, route optimization, and dietary needs in one place.
            </p>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 20 }}>
            {[
              {
                icon: "💰", title: "Price Comparison",
                desc: "Aggregate real-time pricing from store APIs, vendor submissions, and community data. See side-by-side comparisons across stores within your chosen radius.",
                color: COLORS.green, bg: COLORS.greenPale,
                stats: [{ v: "8+", l: "Store sources" }, { v: "Real-time", l: "Price updates" }]
              },
              {
                icon: "🗺️", title: "Route Optimization",
                desc: "Our K-Means clustering + A* pathfinding engine generates the most efficient multi-stop trip, balancing cost, distance, and number of stops.",
                color: COLORS.orange, bg: COLORS.orangePale,
                stats: [{ v: "K-Means", l: "Store clustering" }, { v: "A*", l: "Pathfinding" }]
              },
              {
                icon: "🥗", title: "Dietary Preferences",
                desc: "Set diet types like vegan, keto, halal or kosher, plus numerical targets for sodium, cholesterol, or sugar. Products are filtered automatically.",
                color: COLORS.blue, bg: COLORS.bluePale,
                stats: [{ v: "9+", l: "Diet types" }, { v: "Custom", l: "Nutrition limits" }]
              },
              {
                icon: "🏪", title: "Vendor Portal",
                desc: "Local store owners can submit and verify prices, update inventory, and share deals through our companion web dashboard — keeping data accurate.",
                color: COLORS.red, bg: COLORS.redPale,
                stats: [{ v: "Web app", l: "For vendors" }, { v: "Live", l: "Inventory sync" }]
              },
            ].map((f, i) => (
              <div key={i} className="hover-lift" style={{
                background: COLORS.card, borderRadius: 24, padding: 32,
                border: `1px solid ${COLORS.divider}`, position: "relative", overflow: "hidden",
              }}>
                {/* Corner accent */}
                <div style={{
                  position: "absolute", top: 0, right: 0, width: 120, height: 120,
                  background: `radial-gradient(circle at top right, ${f.bg}, transparent 70%)`,
                }} />

                <div style={{
                  width: 52, height: 52, borderRadius: 16, background: f.bg,
                  display: "flex", alignItems: "center", justifyContent: "center", fontSize: 24,
                  marginBottom: 20, position: "relative",
                }}>{f.icon}</div>

                <h3 className="heading-md" style={{ marginBottom: 10 }}>{f.title}</h3>
                <p className="body-md" style={{ marginBottom: 24, lineHeight: 1.55 }}>{f.desc}</p>

                <div style={{ display: "flex", gap: 16 }}>
                  {f.stats.map((s, j) => (
                    <div key={j} style={{
                      background: COLORS.cardInner, borderRadius: 10, padding: "10px 14px",
                      border: `1px solid ${COLORS.divider}`,
                    }}>
                      <div style={{ fontSize: 16, fontWeight: 700, color: f.color }}>{s.v}</div>
                      <div style={{ fontSize: 11, color: COLORS.textTri }}>{s.l}</div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ─── HOW IT WORKS ─────────────────────────────────────────────── */}
      <section id="how-it-works" style={{
        padding: "80px 0", position: "relative",
        background: `linear-gradient(180deg, ${COLORS.bg} 0%, ${COLORS.greenPale}40 50%, ${COLORS.bg} 100%)`,
      }}>
        <div className="container">
          <div style={{ textAlign: "center", maxWidth: 500, margin: "0 auto 64px" }}>
            <div className="section-label" style={{ justifyContent: "center" }}>How It Works</div>
            <h2 className="heading-lg">Three steps to a<br /><span style={{ color: COLORS.green }}>better grocery trip</span></h2>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))", gap: 24, position: "relative" }}>
            {/* Connecting line (desktop) */}
            <div className="desktop-only" style={{
              position: "absolute", top: 52, left: "20%", right: "20%", height: 2,
              background: `repeating-linear-gradient(90deg, ${COLORS.greenAccent} 0px, ${COLORS.greenAccent} 8px, transparent 8px, transparent 16px)`,
              zIndex: 0,
            }} />

            {[
              { step: "01", title: "Build Your List", desc: "Add grocery items and set your preferences — budget priority, max distance, dietary needs, and travel mode.", icon: "📝" },
              { step: "02", title: "Compare & Optimize", desc: "Neighborly finds the best prices across stores and calculates the most efficient route with K-Means + A* algorithms.", icon: "⚡" },
              { step: "03", title: "Shop & Save", desc: "Follow your optimized route, check off items, and watch your savings add up. Community-verified pricing keeps it accurate.", icon: "🛒" },
            ].map((s, i) => (
              <div key={i} style={{
                background: COLORS.card, borderRadius: 24, padding: 36,
                border: `1px solid ${COLORS.divider}`, textAlign: "center",
                position: "relative", zIndex: 1,
              }}>
                <div style={{
                  width: 64, height: 64, borderRadius: 20, margin: "0 auto 20px",
                  background: `linear-gradient(135deg, ${COLORS.greenDark}, ${COLORS.green})`,
                  display: "flex", alignItems: "center", justifyContent: "center",
                  fontSize: 28, boxShadow: "0 8px 24px rgba(45,106,79,0.2)",
                }}>{s.icon}</div>

                <div style={{
                  fontFamily: "'Fraunces', serif", fontSize: 12, fontWeight: 600,
                  color: COLORS.greenLight, letterSpacing: "0.05em", marginBottom: 8,
                }}>STEP {s.step}</div>

                <h3 className="heading-md" style={{ marginBottom: 10 }}>{s.title}</h3>
                <p className="body-md">{s.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ─── PERSONALIZATION ──────────────────────────────────────────── */}
      <section style={{ padding: "80px 0" }}>
        <div className="container">
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 60, alignItems: "center" }}>
            {/* Left — preferences preview */}
            <div style={{
              background: COLORS.card, borderRadius: 28, padding: 36,
              border: `1px solid ${COLORS.divider}`,
              boxShadow: "0 24px 60px rgba(0,0,0,0.05)",
            }}>
              <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: "0.08em", textTransform: "uppercase", color: COLORS.textTri, marginBottom: 20 }}>
                YOUR PREFERENCES
              </div>

              {/* Optimization mode */}
              <div style={{ marginBottom: 20 }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: COLORS.textSec, marginBottom: 10 }}>Optimization Mode</div>
                <div style={{ display: "flex", gap: 8 }}>
                  {["Lowest Cost", "Fewest Stops", "Shortest Distance"].map((m, i) => (
                    <div key={i} style={{
                      padding: "8px 14px", borderRadius: 10, fontSize: 12, fontWeight: 600,
                      background: i === 0 ? COLORS.green : COLORS.subtle,
                      color: i === 0 ? "white" : COLORS.textTri,
                      border: `1px solid ${i === 0 ? COLORS.green : COLORS.divider}`,
                    }}>{m}</div>
                  ))}
                </div>
              </div>

              {/* Radius slider mock */}
              <div style={{ marginBottom: 20 }}>
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
                  <span style={{ fontSize: 13, fontWeight: 600, color: COLORS.textSec }}>Max Radius</span>
                  <span style={{ fontSize: 13, fontWeight: 700, color: COLORS.green }}>5 miles</span>
                </div>
                <div style={{ height: 6, borderRadius: 6, background: COLORS.divider }}>
                  <div style={{ width: "50%", height: "100%", borderRadius: 6, background: `linear-gradient(90deg, ${COLORS.green}, ${COLORS.greenLight})`, position: "relative" }}>
                    <div style={{
                      position: "absolute", right: -8, top: -5,
                      width: 16, height: 16, borderRadius: "50%",
                      background: "white", border: `3px solid ${COLORS.green}`,
                      boxShadow: "0 2px 8px rgba(45,106,79,0.3)",
                    }} />
                  </div>
                </div>
              </div>

              {/* Travel mode */}
              <div style={{ marginBottom: 20 }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: COLORS.textSec, marginBottom: 10 }}>Travel Mode</div>
                <div style={{ display: "flex", gap: 8 }}>
                  {[{ e: "🚗", l: "Drive" }, { e: "🚶", l: "Walk" }, { e: "🚌", l: "Transit" }].map((m, i) => (
                    <div key={i} style={{
                      padding: "10px 16px", borderRadius: 12, fontSize: 12, fontWeight: 500,
                      background: i === 0 ? COLORS.greenPale : COLORS.subtle,
                      color: i === 0 ? COLORS.green : COLORS.textTri,
                      border: `1px solid ${i === 0 ? COLORS.greenAccent : COLORS.divider}`,
                      display: "flex", alignItems: "center", gap: 6,
                    }}>{m.e} {m.l}</div>
                  ))}
                </div>
              </div>

              {/* Diet tags */}
              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: COLORS.textSec, marginBottom: 10 }}>Dietary Filters</div>
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  {[
                    { n: "Halal", a: true }, { n: "Low Sodium", a: true }, { n: "High Protein", a: true },
                    { n: "Vegan", a: false }, { n: "Keto", a: false },
                  ].map((d, i) => (
                    <div key={i} style={{
                      padding: "6px 12px", borderRadius: 8, fontSize: 12, fontWeight: 500,
                      background: d.a ? COLORS.greenPale : COLORS.subtle,
                      color: d.a ? COLORS.green : COLORS.textTri,
                      border: `1px solid ${d.a ? COLORS.greenAccent : COLORS.divider}`,
                    }}>{d.n} {d.a && "✓"}</div>
                  ))}
                </div>
              </div>
            </div>

            {/* Right — text */}
            <div>
              <div className="section-label">Personalization</div>
              <h2 className="heading-lg" style={{ marginBottom: 16 }}>
                Your trip,<br />your rules.
              </h2>
              <p className="body-lg" style={{ marginBottom: 32 }}>
                Neighborly adapts to how <em>you</em> shop. Choose between lowest cost, fewest stops, or shortest distance. Set your max radius, pick your travel mode, and enable dietary filters — every recommendation is tailored to your needs.
              </p>
              <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                {[
                  { t: "Adjustable optimization", d: "Toggle between cost, distance, or convenience priorities" },
                  { t: "Dietary intelligence", d: "Vegan, gluten-free, halal, keto and more — with nutrition limits" },
                  { t: "Flexible routing", d: "Set max stops, distance limits, and preferred transport" },
                ].map((item, i) => (
                  <div key={i} style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
                    <div style={{
                      width: 28, height: 28, borderRadius: 8, flexShrink: 0, marginTop: 2,
                      background: COLORS.greenPale, display: "flex", alignItems: "center", justifyContent: "center",
                    }}>
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={COLORS.green} strokeWidth="3" strokeLinecap="round"><polyline points="20 6 9 17 4 12"/></svg>
                    </div>
                    <div>
                      <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 2 }}>{item.t}</div>
                      <div style={{ fontSize: 13, color: COLORS.textTri, lineHeight: 1.5 }}>{item.d}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── COMMUNITY ────────────────────────────────────────────────── */}
      <section id="community" style={{
        padding: "80px 0", position: "relative",
        background: `linear-gradient(135deg, ${COLORS.greenDark} 0%, ${COLORS.green} 50%, ${COLORS.greenMed} 100%)`,
        overflow: "hidden",
      }}>
        <div style={{
          position: "absolute", width: 500, height: 500, borderRadius: "50%",
          background: "rgba(255,255,255,0.03)", top: "-20%", right: "-10%",
        }} />
        <div style={{
          position: "absolute", width: 300, height: 300, borderRadius: "50%",
          background: "rgba(255,255,255,0.02)", bottom: "-10%", left: "5%",
        }} />
        <div className="noise-overlay" style={{ opacity: 0.04 }} />

        <div className="container" style={{ position: "relative", zIndex: 2 }}>
          <div style={{ textAlign: "center", maxWidth: 560, margin: "0 auto" }}>
            <div style={{ fontSize: 12, fontWeight: 600, letterSpacing: "0.1em", textTransform: "uppercase", color: COLORS.greenAccent, marginBottom: 12, display: "inline-flex", alignItems: "center", gap: 8 }}>
              <div style={{ width: 20, height: 2, background: COLORS.greenAccent, borderRadius: 2 }} />
              Community Powered
            </div>
            <h2 style={{
              fontFamily: "'Fraunces', serif", fontWeight: 600, fontSize: "clamp(28px, 3.5vw, 42px)",
              lineHeight: 1.15, color: "white", marginBottom: 16,
            }}>
              Better data together
            </h2>
            <p style={{ fontSize: 16, lineHeight: 1.6, color: "rgba(255,255,255,0.65)", marginBottom: 48 }}>
              Neighborly's database grows stronger with every user. Report local prices, verify store data, flag deals — together we ensure smaller stores are represented and data stays accurate.
            </p>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 16 }}>
            {[
              { icon: "📍", title: "Report Prices", desc: "Spot a deal? Submit live prices from any store." },
              { icon: "✅", title: "Verify Data", desc: "Confirm pricing accuracy to help your community." },
              { icon: "🏷️", title: "Flag Deals", desc: "Share local deals others might miss." },
              { icon: "⭐", title: "Earn Badges", desc: "Get rewarded for verified contributions." },
            ].map((c, i) => (
              <div key={i} style={{
                background: "rgba(255,255,255,0.08)", backdropFilter: "blur(10px)",
                borderRadius: 20, padding: 28, border: "1px solid rgba(255,255,255,0.1)",
                textAlign: "center", transition: "background 0.3s",
              }}
                onMouseEnter={(e) => e.currentTarget.style.background = "rgba(255,255,255,0.12)"}
                onMouseLeave={(e) => e.currentTarget.style.background = "rgba(255,255,255,0.08)"}
              >
                <div style={{ fontSize: 32, marginBottom: 14 }}>{c.icon}</div>
                <div style={{ fontSize: 16, fontWeight: 600, color: "white", marginBottom: 6 }}>{c.title}</div>
                <div style={{ fontSize: 13, color: "rgba(255,255,255,0.55)", lineHeight: 1.5 }}>{c.desc}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ─── VENDOR SECTION ───────────────────────────────────────────── */}
      <section id="vendors" style={{ padding: "80px 0" }}>
        <div className="container">
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 60, alignItems: "center" }}>
            <div>
              <div className="section-label">For Store Owners</div>
              <h2 className="heading-lg" style={{ marginBottom: 16 }}>
                A simple portal for<br /><span style={{ color: COLORS.green }}>local vendors</span>
              </h2>
              <p className="body-lg" style={{ marginBottom: 32 }}>
                Our companion web app lets local store owners submit prices, update product availability, and share deals — helping shoppers find you while keeping your listings accurate and competitive.
              </p>
              <div style={{ display: "flex", gap: 14 }}>
                <a href="#" className="btn-primary">Vendor Sign Up</a>
                <a href="#" className="btn-secondary">Learn More</a>
              </div>
            </div>

            {/* Vendor dashboard preview */}
            <div style={{
              background: COLORS.card, borderRadius: 24, overflow: "hidden",
              border: `1px solid ${COLORS.divider}`, boxShadow: "0 24px 60px rgba(0,0,0,0.06)",
            }}>
              {/* Title bar */}
              <div style={{
                padding: "14px 20px", background: COLORS.subtle,
                display: "flex", alignItems: "center", gap: 8,
                borderBottom: `1px solid ${COLORS.divider}`,
              }}>
                <div style={{ width: 10, height: 10, borderRadius: "50%", background: "#FF5F57" }} />
                <div style={{ width: 10, height: 10, borderRadius: "50%", background: "#FEBC2E" }} />
                <div style={{ width: 10, height: 10, borderRadius: "50%", background: "#28C840" }} />
                <div style={{
                  marginLeft: 12, flex: 1, background: COLORS.card, borderRadius: 6,
                  padding: "5px 14px", fontSize: 11, color: COLORS.textTri,
                }}>vendor.neighborly.app</div>
              </div>
              {/* Content */}
              <div style={{ padding: 24 }}>
                <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: "0.06em", textTransform: "uppercase", color: COLORS.textTri, marginBottom: 14 }}>
                  PRODUCT PRICING — THIS WEEK
                </div>
                {[
                  { name: "Organic Bananas", price: "$1.29", status: "Active" },
                  { name: "Whole Milk 1 Gal", price: "$3.49", status: "Active" },
                  { name: "Sourdough Bread", price: "$3.99", status: "Sale" },
                  { name: "Greek Yogurt 32oz", price: "$4.29", status: "Low Stock" },
                ].map((p, i) => (
                  <div key={i} style={{
                    display: "flex", justifyContent: "space-between", alignItems: "center",
                    padding: "10px 0", borderBottom: i < 3 ? `1px solid ${COLORS.divider}` : "none",
                  }}>
                    <span style={{ fontSize: 13, fontWeight: 500 }}>{p.name}</span>
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <span style={{ fontSize: 13, fontWeight: 600 }}>{p.price}</span>
                      <span style={{
                        fontSize: 10, fontWeight: 600, padding: "3px 8px", borderRadius: 6,
                        background: p.status === "Active" ? COLORS.greenPale : p.status === "Sale" ? COLORS.orangePale : COLORS.redPale,
                        color: p.status === "Active" ? COLORS.green : p.status === "Sale" ? COLORS.orange : COLORS.red,
                      }}>{p.status}</span>
                    </div>
                  </div>
                ))}
                <div style={{
                  marginTop: 16, padding: "10px 0", textAlign: "center",
                  background: COLORS.green, borderRadius: 10,
                  color: "white", fontSize: 12, fontWeight: 600,
                }}>+ Add New Product</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── PLATFORMS ────────────────────────────────────────────────── */}
      <section style={{ padding: "60px 0 80px" }}>
        <div className="container">
          <div style={{
            background: COLORS.card, borderRadius: 28, padding: "48px 40",
            border: `1px solid ${COLORS.divider}`, textAlign: "center",
            position: "relative", overflow: "hidden",
          }}>
            <div style={{
              position: "absolute", top: 0, left: 0, right: 0, height: 4,
              background: `linear-gradient(90deg, ${COLORS.green}, ${COLORS.greenLight}, ${COLORS.orange}, ${COLORS.orangeLight})`,
            }} />
            <div className="section-label" style={{ justifyContent: "center" }}>Available Everywhere</div>
            <h2 className="heading-lg" style={{ marginBottom: 12 }}>
              Built for every platform
            </h2>
            <p className="body-md" style={{ maxWidth: 480, margin: "0 auto 40px" }}>
              Native Android, iOS, and a vendor web portal — consistent experience across all your devices.
            </p>
            <div style={{ display: "flex", justifyContent: "center", gap: 20, flexWrap: "wrap" }}>
              {[
                { name: "Android", sub: "Kotlin", icon: "🤖", color: COLORS.green },
                { name: "iOS", sub: "Swift", icon: "🍎", color: COLORS.text },
                { name: "Web", sub: "Next.js", icon: "🌐", color: COLORS.blue },
              ].map((p, i) => (
                <div key={i} className="hover-lift" style={{
                  padding: "24px 36px", borderRadius: 18,
                  background: COLORS.cardInner, border: `1px solid ${COLORS.divider}`,
                  display: "flex", alignItems: "center", gap: 14, cursor: "default",
                }}>
                  <span style={{ fontSize: 28 }}>{p.icon}</span>
                  <div style={{ textAlign: "left" }}>
                    <div style={{ fontSize: 16, fontWeight: 700, color: p.color }}>{p.name}</div>
                    <div style={{ fontSize: 12, color: COLORS.textTri }}>{p.sub}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* ─── CTA ──────────────────────────────────────────────────────── */}
      <section style={{ padding: "0 0 100px" }}>
        <div className="container">
          <div style={{
            background: `linear-gradient(135deg, ${COLORS.greenDark}, ${COLORS.green})`,
            borderRadius: 32, padding: "64px 48", textAlign: "center",
            position: "relative", overflow: "hidden",
          }}>
            <div style={{
              position: "absolute", width: 300, height: 300, borderRadius: "50%",
              background: "rgba(255,255,255,0.04)", top: "-30%", left: "10%",
            }} />
            <div style={{
              position: "absolute", width: 200, height: 200, borderRadius: "50%",
              background: "rgba(255,255,255,0.03)", bottom: "-20%", right: "15%",
            }} />
            <div className="noise-overlay" style={{ opacity: 0.04 }} />

            <div style={{ position: "relative", zIndex: 2 }}>
              <h2 style={{
                fontFamily: "'Fraunces', serif", fontWeight: 600,
                fontSize: "clamp(28px, 4vw, 44px)", lineHeight: 1.15,
                color: "white", marginBottom: 16, letterSpacing: "-0.02em",
              }}>
                Ready to save on<br />your next grocery trip?
              </h2>
              <p style={{ fontSize: 16, color: "rgba(255,255,255,0.6)", maxWidth: 440, margin: "0 auto 36px", lineHeight: 1.6 }}>
                Join thousands of shoppers who are spending less, shopping smarter, and eating healthier with Neighborly.
              </p>
              <div style={{ display: "flex", justifyContent: "center", gap: 14, flexWrap: "wrap" }}>
                <a href="#" className="btn-primary" style={{ background: "white", color: COLORS.green, padding: "16px 36px", fontSize: 16 }}>
                  Get Started Free
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
                </a>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── FOOTER ───────────────────────────────────────────────────── */}
      <footer style={{
        padding: "48px 0 32px",
        borderTop: `1px solid ${COLORS.divider}`,
      }}>
        <div className="container">
          <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr 1fr", gap: 40, marginBottom: 40 }}>
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
                <NeighborlyLogoMark size={34} radius={10} />
                <span style={{ fontFamily: "'Fraunces', serif", fontWeight: 700, fontSize: 17 }}>Neighborly</span>
              </div>
              <p style={{ fontSize: 13, color: COLORS.textTri, lineHeight: 1.6, maxWidth: 280 }}>
                Making grocery shopping simpler, cheaper, and more mindful of your health goals.
              </p>
            </div>

            {[
              { title: "Product [COMING SOON]", links: ["Features", "Pricing", "Route Planner", "Vendor Portal"] },
              { title: "Company [COMING SOON]", links: ["About", "Blog", "Careers", "Contact"] },
              { title: "Legal [COMING SOON]", links: ["Privacy Policy", "Terms of Service", "Cookie Policy"] },
            ].map((col, i) => (
              <div key={i}>
                <div style={{ fontSize: 12, fontWeight: 600, letterSpacing: "0.06em", textTransform: "uppercase", color: COLORS.textTri, marginBottom: 14 }}>
                  {col.title}
                </div>
                {col.links.map((link, j) => (
                  <a key={j} href="#" style={{
                    display: "block", fontSize: 13, color: COLORS.textSec,
                    textDecoration: "none", padding: "5px 0", transition: "color 0.2s",
                  }}
                    onMouseEnter={(e) => e.currentTarget.style.color = COLORS.green}
                    onMouseLeave={(e) => e.currentTarget.style.color = COLORS.textSec}
                  >{link}</a>
                ))}
              </div>
            ))}
          </div>

          <div style={{
            display: "flex", justifyContent: "space-between", alignItems: "center",
            paddingTop: 24, borderTop: `1px solid ${COLORS.divider}`,
            fontSize: 12, color: COLORS.textQuat,
          }}>
            <span>© 2026 Neighborly. Built by Timson, Evan, Darren, Jawad & Raida.</span>
            <div style={{ display: "flex", gap: 20 }}>
              {["Twitter", "GitHub", "LinkedIn"].map((s) => (
                <a key={s} href="#" style={{ color: COLORS.textQuat, textDecoration: "none", fontSize: 12, transition: "color 0.2s" }}
                  onMouseEnter={(e) => e.currentTarget.style.color = COLORS.green}
                  onMouseLeave={(e) => e.currentTarget.style.color = COLORS.textQuat}
                >{s}</a>
              ))}
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}