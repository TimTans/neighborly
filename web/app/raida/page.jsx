'use client'

import React, { useMemo, useState } from "react";
import { motion } from "framer-motion";
import {
  MapPin,
  ShoppingCart,
  Route,
  Leaf,
  Users,
  SlidersHorizontal,
  Star,
  CheckCircle2,
  Clock,
  DollarSign,
  Navigation,
  ShieldCheck,
  Store,
  Upload,
  UserCheck,
  BarChart3,
  Search,
  Plus,
  XCircle,
  Check,
  Smartphone,
  Database,
  Brain,
  Globe2,
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

const products = [
  {
    item: "Bowl & Basket Whole Wheat Penne",
    category: "Pantry",
    freshmart: 1.69,
    marketplace: 1.69,
    urban: 2.29,
    tags: ["vegan", "low-sugar"],
  },
  {
    item: "Ben & Jerry's Milk & Cookies",
    category: "Frozen",
    freshmart: 8.99,
    marketplace: 7.69,
    urban: 8.99,
    tags: ["vegetarian", "kosher"],
  },
  {
    item: "Beetology Beet Veggie Juice",
    category: "Beverage",
    freshmart: 4.49,
    marketplace: 3.79,
    urban: 4.19,
    tags: ["vegan", "gluten-free"],
  },
  {
    item: "Garlic Butter Shrimp Pasta Kit",
    category: "Prepared Food",
    freshmart: 10.49,
    marketplace: 9.89,
    urban: 11.29,
    tags: ["high-protein"],
  },
];

const vendorInventory = [
  { name: "Bowl & Basket 100% Whole Wheat Penne", price: "$1.69", sale: "$1.49", status: "In stock", history: "+12 records" },
  { name: "Ben & Jerry's - Milk & Cookies", price: "$7.69", sale: "—", status: "In stock", history: "+8 records" },
  { name: "Beetology - Beet Veggie Juice", price: "$3.79", sale: "$3.49", status: "Low stock", history: "+5 records" },
  { name: "Barbara's Peanut Butter Puffins", price: "$4.99", sale: "—", status: "Out of stock", history: "+11 records" },
];

const adminVendors = [
  { name: "Timson's Market", email: "timson@market.com", status: "Pending", submitted: "Today" },
  { name: "Jawad's Market", email: "jawad@market.com", status: "Active", submitted: "Approved" },
  { name: "Darren's Market", email: "darren@market.com", status: "Active", submitted: "Approved" },
  { name: "Evan's Market", email: "evan@market.com", status: "Active", submitted: "Approved" },
];

const routeStops = ["Home", "ShopRite Brooklyn", "Marketplace Brooklyn", "Urban Market Brooklyn", "Home"];

function Header() {
  return (
    <header className="sticky top-0 z-50 border-b border-emerald-100 bg-white/90 backdrop-blur-xl">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
        <a href="#home" className="flex items-center gap-2">
          <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-emerald-700 text-white shadow-sm">
            <MapPin size={22} />
          </div>
          <div>
            <span className="block text-2xl font-bold leading-none tracking-tight text-slate-950">Neighborly</span>
            <span className="text-xs font-semibold uppercase tracking-widest text-emerald-700">Smart grocery trips</span>
          </div>
        </a>
        <nav className="hidden items-center gap-7 text-sm font-medium text-slate-600 lg:flex">
          <a href="#shopper" className="hover:text-emerald-700">Shopper App</a>
          <a href="#vendor" className="hover:text-emerald-700">Vendor Portal</a>
          <a href="#admin" className="hover:text-emerald-700">Admin Portal</a>
          <a href="#tech" className="hover:text-emerald-700">Technology</a>
        </nav>
        <Button className="rounded-2xl bg-emerald-700 px-5 hover:bg-emerald-800">View MVP</Button>
      </div>
    </header>
  );
}

function Hero() {
  return (
    <section id="home" className="relative overflow-hidden bg-gradient-to-br from-emerald-50 via-stone-50 to-white">
      <div className="absolute -right-24 -top-24 h-80 w-80 rounded-full bg-emerald-200/50 blur-3xl" />
      <div className="absolute -bottom-32 left-10 h-96 w-96 rounded-full bg-lime-200/50 blur-3xl" />
      <div className="mx-auto grid max-w-7xl items-center gap-12 px-6 py-20 md:grid-cols-2 md:py-28">
        <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6 }}>
          <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-2 text-sm font-medium text-emerald-700 shadow-sm">
            <Star size={16} /> Mobile app + vendor/admin web portal
          </div>
          <h1 className="max-w-2xl text-5xl font-bold tracking-tight text-slate-950 md:text-6xl">
            Personalized route and budget optimization for everyday shoppers.
          </h1>
          <p className="mt-6 max-w-xl text-lg leading-8 text-slate-600">
            Neighborly helps shoppers compare grocery prices, build healthier lists, and generate optimized multi-store routes while giving vendors and admins a reliable web dashboard for product and store data.
          </p>
          <div className="mt-8 flex flex-col gap-3 sm:flex-row">
            <Button className="rounded-2xl bg-emerald-700 px-7 py-6 text-base hover:bg-emerald-800">Explore the Design</Button>
            <Button variant="outline" className="rounded-2xl border-emerald-200 px-7 py-6 text-base text-emerald-700 hover:bg-emerald-50">See Web Portal</Button>
          </div>
        </motion.div>

        <motion.div initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} transition={{ duration: 0.7, delay: 0.1 }} className="relative">
          <Card className="rounded-[2rem] border-emerald-100 bg-white/90 shadow-2xl shadow-emerald-900/10">
            <CardContent className="p-6">
              <div className="mb-5 flex items-center justify-between rounded-3xl bg-gradient-to-br from-emerald-800 to-emerald-600 p-5 text-white">
                <div>
                  <p className="text-sm text-emerald-100">Good morning</p>
                  <h2 className="text-2xl font-bold">Your route is ready</h2>
                  <p className="mt-1 text-sm text-emerald-100">3 stores · 12 items · saving $6.70</p>
                </div>
                <Smartphone size={36} />
              </div>
              <div className="grid gap-3 sm:grid-cols-3">
                <MetricCard label="Total" value="$14.47" />
                <MetricCard label="Stores" value="3" />
                <MetricCard label="Miles" value="4.1" />
              </div>
              <div className="mt-5 space-y-3">
                {products.slice(0, 3).map((product) => {
                  const best = Math.min(product.freshmart, product.marketplace, product.urban);
                  return (
                    <div key={product.item} className="flex items-center justify-between rounded-2xl border border-slate-100 bg-slate-50 p-4">
                      <div>
                        <p className="font-semibold text-slate-950">{product.item}</p>
                        <p className="text-sm text-slate-500">Best available price</p>
                      </div>
                      <span className="rounded-full bg-emerald-100 px-3 py-1 text-sm font-bold text-emerald-700">${best.toFixed(2)}</span>
                    </div>
                  );
                })}
              </div>
            </CardContent>
          </Card>
        </motion.div>
      </div>
    </section>
  );
}

function MetricCard({ label, value }) {
  return (
    <div className="rounded-2xl bg-emerald-50 p-4 text-center">
      <p className="text-xs font-semibold uppercase tracking-widest text-emerald-700">{label}</p>
      <p className="mt-1 text-2xl font-bold text-slate-950">{value}</p>
    </div>
  );
}

function FeatureCard({ icon: Icon, title, text }) {
  return (
    <Card className="rounded-3xl border-slate-100 bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-lg">
      <CardContent className="p-6">
        <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-700">
          <Icon size={24} />
        </div>
        <h3 className="text-xl font-bold text-slate-950">{title}</h3>
        <p className="mt-3 leading-7 text-slate-600">{text}</p>
      </CardContent>
    </Card>
  );
}

function Overview() {
  return (
    <section className="bg-white px-6 py-20">
      <div className="mx-auto max-w-7xl">
        <div className="mx-auto mb-12 max-w-3xl text-center">
          <p className="font-semibold uppercase tracking-widest text-emerald-600">Project Scope</p>
          <h2 className="mt-3 text-4xl font-bold tracking-tight text-slate-950">Three connected user surfaces</h2>
          <p className="mt-4 leading-8 text-slate-600">The design follows the proposal: shopper mobile experience, vendor web dashboard, and admin web dashboard backed by shared pricing and store data.</p>
        </div>
        <div className="grid gap-6 md:grid-cols-3">
          <FeatureCard icon={ShoppingCart} title="Shopper App" text="Build grocery lists, compare store prices, apply dietary filters, and generate optimized routes." />
          <FeatureCard icon={Store} title="Vendor Portal" text="Allow stores to manage products, prices, stock availability, bulk uploads, and public store profiles." />
          <FeatureCard icon={UserCheck} title="Admin Portal" text="Review vendor applications, approve or reject stores, and monitor active vendor data." />
        </div>
      </div>
    </section>
  );
}

function ShopperExperience() {
  const [dietFilter, setDietFilter] = useState("all");
  const filteredItems = useMemo(() => {
    if (dietFilter === "all") return products;
    return products.filter((item) => item.tags.includes(dietFilter));
  }, [dietFilter]);

  return (
    <section id="shopper" className="bg-slate-50 px-6 py-20">
      <div className="mx-auto max-w-7xl">
        <div className="mb-8 flex flex-col justify-between gap-5 md:flex-row md:items-end">
          <div>
            <p className="font-semibold uppercase tracking-widest text-emerald-600">Shopper Mobile Flow</p>
            <h2 className="mt-3 text-4xl font-bold text-slate-950">Compare prices, apply wellness filters, and create a route</h2>
            <p className="mt-3 max-w-2xl text-slate-600">This section mirrors the mobile screens from the proposal: grocery list creation, item search, item details, preferences, and route generation.</p>
          </div>
          <select value={dietFilter} onChange={(e) => setDietFilter(e.target.value)} className="rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-700 shadow-sm outline-none focus:border-emerald-400">
            <option value="all">All products</option>
            <option value="vegan">Vegan</option>
            <option value="vegetarian">Vegetarian</option>
            <option value="gluten-free">Gluten-Free</option>
            <option value="kosher">Kosher</option>
            <option value="low-sugar">Low Sugar</option>
          </select>
        </div>

        <div className="grid gap-6 lg:grid-cols-[1.35fr_0.85fr]">
          <Card className="rounded-3xl border-slate-100 bg-white shadow-sm">
            <CardContent className="p-0">
              <div className="overflow-hidden rounded-3xl">
                <table className="w-full text-left">
                  <thead className="bg-emerald-700 text-white">
                    <tr>
                      <th className="px-6 py-4 text-sm font-semibold">Item</th>
                      <th className="px-6 py-4 text-sm font-semibold">FreshMart</th>
                      <th className="px-6 py-4 text-sm font-semibold">Marketplace</th>
                      <th className="px-6 py-4 text-sm font-semibold">Urban Market</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredItems.map((item) => {
                      const best = Math.min(item.freshmart, item.marketplace, item.urban);
                      return (
                        <tr key={item.item} className="border-b border-slate-100 last:border-none">
                          <td className="px-6 py-5">
                            <p className="font-semibold text-slate-950">{item.item}</p>
                            <p className="text-sm text-slate-500">{item.category}</p>
                          </td>
                          {[item.freshmart, item.marketplace, item.urban].map((price, index) => (
                            <td key={index} className="px-6 py-5">
                              <span className={`rounded-full px-3 py-1 text-sm font-semibold ${price === best ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-600"}`}>${price.toFixed(2)}</span>
                            </td>
                          ))}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>

          <Card className="rounded-3xl border-slate-100 bg-white shadow-sm">
            <CardContent className="p-6">
              <h3 className="mb-5 text-xl font-bold text-slate-950">Generated route</h3>
              <div className="rounded-3xl bg-gradient-to-br from-emerald-100 to-stone-100 p-5">
                <div className="mb-5 grid grid-cols-3 gap-3 text-center">
                  <MetricCard label="Cost" value="$14.47" />
                  <MetricCard label="Stops" value="3" />
                  <MetricCard label="Miles" value="4.1" />
                </div>
                <div className="space-y-3">
                  {routeStops.map((stop, index) => (
                    <div key={`${stop}-${index}`} className="flex items-center gap-3 rounded-2xl bg-white p-3 shadow-sm">
                      <div className="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-700 text-xs font-bold text-white">{index + 1}</div>
                      <p className="font-semibold text-slate-800">{stop}</p>
                    </div>
                  ))}
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </section>
  );
}

function WellnessAndAI() {
  return (
    <section className="bg-emerald-50 px-6 py-20">
      <div className="mx-auto grid max-w-7xl gap-8 lg:grid-cols-[0.9fr_1.1fr]">
        <Card className="rounded-3xl border-emerald-100 bg-white shadow-sm">
          <CardContent className="p-6">
            <div className="mb-5 flex items-center gap-3">
              <SlidersHorizontal className="text-emerald-700" />
              <h3 className="text-xl font-bold text-slate-950">User preferences</h3>
            </div>
            <div className="space-y-5">
              <PreferenceRange label="Maximum travel distance" value="8 mi" />
              <PreferenceRange label="Maximum number of stops" value="6 stops" />
              <div>
                <span className="text-sm font-medium text-slate-600">Modes of transport</span>
                <div className="mt-3 grid grid-cols-3 gap-2">
                  {['Walking', 'Public Transit', 'Driving'].map((type, index) => <span key={type} className={`rounded-xl px-3 py-2 text-center text-sm font-semibold ${index !== 1 ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-600"}`}>{type}</span>)}
                </div>
              </div>
              <div>
                <span className="text-sm font-medium text-slate-600">Diet type</span>
                <div className="mt-3 grid grid-cols-2 gap-2">
                  {['Vegan', 'Gluten-Free', 'Kosher', 'Halal', 'Low Carb', 'Keto'].map((type, index) => <span key={type} className={`rounded-xl px-3 py-2 text-sm font-semibold ${index < 2 ? "bg-emerald-700 text-white" : "bg-slate-100 text-slate-600"}`}>{type}</span>)}
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="rounded-3xl border-emerald-100 bg-white shadow-sm">
          <CardContent className="p-6">
            <div className="mb-5 flex items-center gap-3">
              <Brain className="text-emerald-700" />
              <h3 className="text-xl font-bold text-slate-950">AI-powered recipe suggestion</h3>
            </div>
            <div className="rounded-3xl bg-gradient-to-br from-stone-50 to-emerald-50 p-6">
              <p className="text-sm font-semibold uppercase tracking-widest text-emerald-700">Recipe for you</p>
              <h4 className="mt-2 text-3xl font-bold text-slate-950">Garlic Butter Shrimp Pasta</h4>
              <p className="mt-3 leading-7 text-slate-600">A quick recipe generated from active wellness preferences, available ingredients, and nutrition targets.</p>
              <div className="mt-5 grid gap-3 md:grid-cols-3">
                {['Structured JSON output', 'Nutrition notes included', 'Prep + cook times'].map((note) => (
                  <div key={note} className="rounded-2xl bg-white p-4 text-sm font-semibold text-slate-700 shadow-sm"><CheckCircle2 className="mb-2 text-emerald-700" size={18} />{note}</div>
                ))}
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </section>
  );
}

function PreferenceRange({ label, value }) {
  return (
    <label className="block">
      <div className="flex justify-between text-sm font-medium text-slate-600"><span>{label}</span><span>{value}</span></div>
      <input type="range" min="1" max="10" defaultValue="7" className="mt-3 w-full accent-emerald-700" />
    </label>
  );
}

function VendorPortal() {
  return (
    <section id="vendor" className="bg-white px-6 py-20">
      <div className="mx-auto max-w-7xl">
        <div className="mb-10 flex flex-col justify-between gap-5 md:flex-row md:items-end">
          <div>
            <p className="font-semibold uppercase tracking-widest text-orange-500">Vendor Web Portal</p>
            <h2 className="mt-3 text-4xl font-bold text-slate-950">Product management for local stores</h2>
            <p className="mt-3 max-w-2xl text-slate-600">Vendors can manage store profiles, product listings, stock, sale prices, price history, and CSV/XLSX bulk inventory uploads.</p>
          </div>
          <Button className="rounded-2xl bg-emerald-700 px-5 hover:bg-emerald-800"><Plus size={18} /> Add Product</Button>
        </div>

        <div className="grid gap-6 lg:grid-cols-[0.7fr_1.3fr]">
          <div className="space-y-6">
            <Card className="rounded-3xl border-slate-100 bg-gradient-to-br from-emerald-800 to-emerald-600 text-white shadow-sm">
              <CardContent className="p-6">
                <p className="text-sm text-emerald-100">Welcome back,</p>
                <h3 className="text-2xl font-bold">ShopRite Brooklyn</h3>
                <p className="mt-2 text-sm text-emerald-100">Manage prices, inventory, and local deals.</p>
              </CardContent>
            </Card>
            <Card className="rounded-3xl border-slate-100 bg-white shadow-sm">
              <CardContent className="p-6">
                <h3 className="mb-4 text-xl font-bold text-slate-950">Dashboard stats</h3>
                <div className="grid grid-cols-2 gap-3">
                  <MetricCard label="Products" value="1074" />
                  <MetricCard label="Errors" value="0" />
                  <MetricCard label="Updates" value="42" />
                  <MetricCard label="Sales" value="18" />
                </div>
              </CardContent>
            </Card>
            <Card className="rounded-3xl border-slate-100 bg-white shadow-sm">
              <CardContent className="p-6">
                <div className="mb-3 flex items-center gap-2"><Upload className="text-emerald-700" /><h3 className="font-bold text-slate-950">Bulk import preview</h3></div>
                <div className="space-y-2 text-sm">
                  <ImportRow label="New products" value="18" icon={Plus} />
                  <ImportRow label="Updates" value="64" icon={Check} />
                  <ImportRow label="Unchanged" value="921" icon={CheckCircle2} />
                  <ImportRow label="Errors" value="0" icon={XCircle} />
                </div>
              </CardContent>
            </Card>
          </div>

          <Card className="rounded-3xl border-slate-100 bg-white shadow-sm">
            <CardContent className="p-6">
              <div className="mb-5 flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                <h3 className="text-xl font-bold text-slate-950">Inventory management</h3>
                <div className="flex items-center gap-2 rounded-2xl border border-slate-200 px-3 py-2 text-slate-500"><Search size={18} /><span className="text-sm">Search product catalog...</span></div>
              </div>
              <div className="overflow-hidden rounded-2xl border border-slate-100">
                <table className="w-full text-left">
                  <thead className="bg-slate-50 text-sm text-slate-500">
                    <tr><th className="px-4 py-3">Product</th><th className="px-4 py-3">Price</th><th className="px-4 py-3">Sale</th><th className="px-4 py-3">Status</th><th className="px-4 py-3">History</th></tr>
                  </thead>
                  <tbody>
                    {vendorInventory.map((item) => (
                      <tr key={item.name} className="border-t border-slate-100">
                        <td className="px-4 py-4 font-semibold text-slate-900">{item.name}</td>
                        <td className="px-4 py-4 text-slate-600">{item.price}</td>
                        <td className="px-4 py-4 text-emerald-700">{item.sale}</td>
                        <td className="px-4 py-4"><span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700">{item.status}</span></td>
                        <td className="px-4 py-4 text-slate-500">{item.history}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </section>
  );
}

function ImportRow({ label, value, icon: Icon }) {
  return (
    <div className="flex items-center justify-between rounded-xl bg-slate-50 px-3 py-2"><span className="flex items-center gap-2 text-slate-600"><Icon size={16} className="text-emerald-700" />{label}</span><strong>{value}</strong></div>
  );
}

function AdminPortal() {
  return (
    <section id="admin" className="bg-slate-50 px-6 py-20">
      <div className="mx-auto max-w-7xl">
        <div className="mb-10">
          <p className="font-semibold uppercase tracking-widest text-emerald-600">Admin Web Portal</p>
          <h2 className="mt-3 text-4xl font-bold text-slate-950">Approve vendors and monitor store access</h2>
          <p className="mt-3 max-w-2xl text-slate-600">New vendor accounts remain pending until an admin approves them, protecting the platform from unreliable or unauthorized store submissions.</p>
        </div>
        <div className="grid gap-6 lg:grid-cols-[0.8fr_1.2fr]">
          <Card className="rounded-3xl border-slate-100 bg-white shadow-sm">
            <CardContent className="p-6">
              <h3 className="mb-5 text-xl font-bold text-slate-950">Admin summary</h3>
              <div className="grid gap-4">
                <MetricCard label="Pending" value="1" />
                <MetricCard label="Active Vendors" value="3" />
                <MetricCard label="Total Users" value="8" />
              </div>
            </CardContent>
          </Card>
          <Card className="rounded-3xl border-slate-100 bg-white shadow-sm">
            <CardContent className="p-6">
              <h3 className="mb-5 text-xl font-bold text-slate-950">Vendor roster</h3>
              <div className="space-y-3">
                {adminVendors.map((vendor) => (
                  <div key={vendor.name} className="flex flex-col justify-between gap-3 rounded-2xl border border-slate-100 p-4 md:flex-row md:items-center">
                    <div>
                      <p className="font-bold text-slate-950">{vendor.name}</p>
                      <p className="text-sm text-slate-500">{vendor.email}</p>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`rounded-full px-3 py-1 text-xs font-semibold ${vendor.status === "Pending" ? "bg-orange-100 text-orange-700" : "bg-emerald-100 text-emerald-700"}`}>{vendor.status}</span>
                      {vendor.status === "Pending" && <><Button size="sm" className="rounded-xl bg-emerald-700 hover:bg-emerald-800">Approve</Button><Button size="sm" variant="outline" className="rounded-xl text-red-600">Reject</Button></>}
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </section>
  );
}

function TechLandscape() {
  const tech = [
    { icon: Smartphone, title: "Clients", text: "iOS with Swift, Android with Kotlin, and web portal with Next.js." },
    { icon: Globe2, title: "Backend", text: "FastAPI service exposing product search, routes, recipes, scrapers, and nutrition enrichment." },
    { icon: Database, title: "Database", text: "Supabase PostgreSQL with role-based access and Row-Level Security for vendor data isolation." },
    { icon: Navigation, title: "Maps", text: "Mapbox for route visualization and walking directions." },
    { icon: Brain, title: "AI", text: "OpenAI API with structured JSON output for recipe generation." },
    { icon: BarChart3, title: "Data", text: "Store APIs, Playwright scrapers, USDA FoodData Central, and vendor-submitted inventory." },
  ];

  return (
    <section id="tech" className="bg-white px-6 py-20">
      <div className="mx-auto max-w-7xl">
        <div className="mx-auto mb-12 max-w-3xl text-center">
          <p className="font-semibold uppercase tracking-widest text-emerald-600">Technology Landscape</p>
          <h2 className="mt-3 text-4xl font-bold tracking-tight text-slate-950">Designed around the proposal stack</h2>
        </div>
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {tech.map((item) => <FeatureCard key={item.title} icon={item.icon} title={item.title} text={item.text} />)}
        </div>
      </div>
    </section>
  );
}

function CTA() {
  return (
    <section className="bg-slate-950 px-6 py-20 text-white">
      <div className="mx-auto max-w-4xl text-center">
        <h2 className="text-4xl font-bold tracking-tight md:text-5xl">Neighborly brings shopping, pricing, routing, and wellness into one platform.</h2>
        <p className="mx-auto mt-5 max-w-2xl text-lg leading-8 text-slate-300">This front-end design can be connected later to FastAPI, Supabase, Mapbox, USDA FoodData Central, and OpenAI endpoints.</p>
        <Button className="mt-8 rounded-2xl bg-emerald-500 px-8 py-6 text-base text-white hover:bg-emerald-600">Continue Building</Button>
      </div>
    </section>
  );
}

export default function NeighborlyWebsite() {
  return (
    <main className="min-h-screen bg-white font-sans text-slate-900">
      <Header />
      <Hero />
      <Overview />
      <ShopperExperience />
      <WellnessAndAI />
      <VendorPortal />
      <AdminPortal />
      <TechLandscape />
      <CTA />
      <footer className="border-t border-slate-100 bg-white px-6 py-8">
        <div className="mx-auto flex max-w-7xl flex-col justify-between gap-4 text-sm text-slate-500 md:flex-row">
          <p>© 2026 Neighborly. Personalized Route and Budget Optimizer.</p>
          <p>Shopper app • Vendor portal • Admin portal • FastAPI • Supabase • Mapbox</p>
        </div>
      </footer>
    </main>
  );
}
