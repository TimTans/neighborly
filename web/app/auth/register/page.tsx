import Link from "next/link";
import { signup } from "./action";

export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;

  return (
    <div
      className="flex min-h-screen flex-col items-center justify-center px-4 py-12"
      style={{ backgroundColor: "#f8f5ef" }}
    >
      <style>{`
        .nb-input {
          border: 1px solid #ddd6c9;
          background-color: #ffffff;
          color: #2c2c2c;
          transition: border-color 0.2s, box-shadow 0.2s;
        }
        .nb-input::placeholder {
          color: #bfb8ab;
        }
        .nb-input:focus {
          border-color: #4a8c2a;
          box-shadow: 0 0 0 3px rgba(74,140,42,0.1);
          outline: none;
        }
        .nb-btn {
          background-color: #3d7a1c;
          transition: background-color 0.2s, transform 0.1s;
        }
        .nb-btn:hover {
          background-color: #316315;
        }
        .nb-btn:active {
          transform: scale(0.98);
        }
        .nb-link {
          color: #3d7a1c;
          transition: color 0.15s;
        }
        .nb-link:hover {
          color: #316315;
        }
        .nb-back {
          color: #9b9283;
          transition: color 0.15s;
        }
        .nb-back:hover {
          color: #5a5347;
        }
        .nb-card {
          border: 1px solid #e4ded3;
          box-shadow: 0 1px 3px rgba(0,0,0,0.04), 0 4px 12px rgba(0,0,0,0.02);
        }
        .nb-role-input { position: absolute; opacity: 0; width: 0; height: 0; }
        .nb-role-label {
          display: flex; align-items: center; gap: 8px;
          padding: 10px 16px; border-radius: 12px;
          border: 1px solid #ddd6c9; cursor: pointer;
          font-size: 0.86rem; color: #5a5347;
          transition: all 0.2s; flex: 1; justify-content: center;
        }
        .nb-role-label:hover { border-color: #c5bfb2; }
        .nb-role-input:checked + .nb-role-label {
          border-color: #4a8c2a;
          background-color: rgba(74,140,42,0.06);
          color: #3d7a1c;
          font-weight: 600;
          box-shadow: 0 0 0 3px rgba(74,140,42,0.08);
        }
        .nb-vendor-note { display: none; flex-basis: 100%; }
        #role_vendor:checked ~ .nb-vendor-note { display: flex; }
      `}</style>

      {/* Logo */}
      <Link href="/" className="mb-10 flex items-center gap-3">
        <div
          className="flex h-11 w-11 items-center justify-center rounded-full text-lg font-bold text-white"
          style={{ backgroundColor: "#3d7a1c" }}
        >
          N
        </div>
        <div className="flex flex-col">
          <span
            className="text-[1.3rem] font-semibold leading-tight"
            style={{ color: "#2c2c2c" }}
          >
            Neighborly
          </span>
          <span
            className="text-[0.7rem] tracking-wide"
            style={{ color: "#9b9283" }}
          >
            Smart grocery planning
          </span>
        </div>
      </Link>

      {/* Card */}
      <div className="nb-card w-full max-w-[400px] rounded-2xl bg-white">
        {/* Header */}
        <div
          className="border-b px-8 pb-5 pt-7"
          style={{ borderColor: "#e4ded3" }}
        >
          <h1
            className="text-[1.55rem] font-semibold tracking-tight"
            style={{ color: "#2c2c2c", fontFamily: "Georgia, 'Times New Roman', serif" }}
          >
            Create an account
          </h1>
          <p className="mt-1.5 text-[0.84rem]" style={{ color: "#9b9283" }}>
            Join Neighborly and connect with your community
          </p>
        </div>

        {/* Form body */}
        <div className="px-8 pb-7 pt-6">
          {error && (
            <div
              className="mb-5 rounded-xl px-4 py-3 text-[0.84rem]"
              style={{
                backgroundColor: "#fdf3ec",
                color: "#bf4a0a",
                border: "1px solid #f0d4bb",
              }}
            >
              {decodeURIComponent(error)}
            </div>
          )}

          <form className="space-y-5">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label
                  htmlFor="first_name"
                  className="mb-1.5 block text-[0.82rem] font-medium"
                  style={{ color: "#5a5347" }}
                >
                  First name
                </label>
                <input
                  id="first_name"
                  name="first_name"
                  type="text"
                  required
                  autoComplete="given-name"
                  className="nb-input w-full rounded-xl px-3.5 py-2.5 text-[0.88rem]"
                  placeholder="John"
                />
              </div>
              <div>
                <label
                  htmlFor="last_name"
                  className="mb-1.5 block text-[0.82rem] font-medium"
                  style={{ color: "#5a5347" }}
                >
                  Last name
                </label>
                <input
                  id="last_name"
                  name="last_name"
                  type="text"
                  required
                  autoComplete="family-name"
                  className="nb-input w-full rounded-xl px-3.5 py-2.5 text-[0.88rem]"
                  placeholder="Doe"
                />
              </div>
            </div>

            <div>
              <label
                htmlFor="email"
                className="mb-1.5 block text-[0.82rem] font-medium"
                style={{ color: "#5a5347" }}
              >
                Email
              </label>
              <input
                id="email"
                name="email"
                type="email"
                required
                autoComplete="email"
                className="nb-input w-full rounded-xl px-3.5 py-2.5 text-[0.88rem]"
                placeholder="you@example.com"
              />
            </div>

            <div>
              <label
                htmlFor="password"
                className="mb-1.5 block text-[0.82rem] font-medium"
                style={{ color: "#5a5347" }}
              >
                Password
              </label>
              <input
                id="password"
                name="password"
                type="password"
                required
                autoComplete="new-password"
                minLength={8}
                className="nb-input w-full rounded-xl px-3.5 py-2.5 text-[0.88rem]"
                placeholder="••••••••"
              />
              <p
                className="mt-1.5 text-[0.75rem]"
                style={{ color: "#b0a899" }}
              >
                At least 8 characters
              </p>
            </div>

            <div>
              <label
                htmlFor="confirm_password"
                className="mb-1.5 block text-[0.82rem] font-medium"
                style={{ color: "#5a5347" }}
              >
                Confirm password
              </label>
              <input
                id="confirm_password"
                name="confirm_password"
                type="password"
                required
                autoComplete="new-password"
                className="nb-input w-full rounded-xl px-3.5 py-2.5 text-[0.88rem]"
                placeholder="••••••••"
              />
            </div>

            <div>
              <label
                className="mb-1.5 block text-[0.82rem] font-medium"
                style={{ color: "#5a5347" }}
              >
                I want to join as
              </label>
              <div className="flex flex-wrap gap-2">
                <input
                  type="radio"
                  name="account_type"
                  value="shopper"
                  id="role_shopper"
                  defaultChecked
                  className="nb-role-input"
                />
                <label htmlFor="role_shopper" className="nb-role-label">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/>
                    <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/>
                  </svg>
                  Shopper
                </label>

                <input
                  type="radio"
                  name="account_type"
                  value="vendor"
                  id="role_vendor"
                  className="nb-role-input"
                />
                <label htmlFor="role_vendor" className="nb-role-label">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                    <polyline points="9 22 9 12 15 12 15 22"/>
                  </svg>
                  Vendor
                </label>

                <div
                  className="nb-vendor-note items-center gap-2 mt-2 rounded-xl px-3.5 py-2.5 text-[0.8rem]"
                  style={{
                    backgroundColor: "#fef9ed",
                    color: "#92710c",
                    border: "1px solid #f0e4bb",
                  }}
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ flexShrink: 0 }}>
                    <circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>
                  </svg>
                  Vendor accounts require admin approval before access is granted.
                </div>
              </div>
            </div>

            <button
              formAction={signup}
              className="nb-btn w-full rounded-xl py-3 text-[0.88rem] font-semibold text-white"
            >
              Sign up →
            </button>
          </form>

          <div
            className="mt-6 border-t pt-5 text-center"
            style={{ borderColor: "#e4ded3" }}
          >
            <p className="text-[0.84rem]" style={{ color: "#9b9283" }}>
              Already have an account?{" "}
              <Link
                href="/auth/login"
                className="nb-link font-medium underline underline-offset-2"
              >
                Sign in
              </Link>
            </p>
          </div>
        </div>
      </div>

      <Link href="/" className="nb-back mt-8 text-[0.84rem]">
        ← Back to home
      </Link>
    </div>
  );
}