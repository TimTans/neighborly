import * as React from "react";

type ButtonVariant = "primary" | "secondary" | "outline" | "ghost" | "destructive";
type ButtonSize = "sm" | "md" | "lg" | "icon";

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
};

const variantClasses: Record<ButtonVariant, string> = {
  primary: "bg-green-800 text-white hover:bg-green-900 focus-visible:ring-green-800/25",
  secondary: "bg-orange-100 text-orange-700 hover:bg-orange-200 focus-visible:ring-orange-600/20",
  outline:
    "border border-stone-200 bg-white text-stone-700 hover:bg-stone-50 focus-visible:ring-green-800/15",
  ghost: "bg-transparent text-stone-700 hover:bg-stone-100 focus-visible:ring-stone-500/15",
  destructive: "bg-red-600 text-white hover:bg-red-700 focus-visible:ring-red-600/25",
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: "h-9 px-3 text-xs",
  md: "h-10 px-4 text-sm",
  lg: "h-12 px-6 text-base",
  icon: "h-10 w-10 p-0",
};

function cn(...classes: Array<string | false | null | undefined>) {
  return classes.filter(Boolean).join(" ");
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = "primary", size = "md", type = "button", ...props }, ref) => {
    return (
      <button
        ref={ref}
        type={type}
        className={cn(
          "inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition-all",
          "cursor-pointer select-none whitespace-nowrap",
          "focus-visible:outline-none focus-visible:ring-4",
          "disabled:pointer-events-none disabled:opacity-50",
          variantClasses[variant],
          sizeClasses[size],
          className,
        )}
        {...props}
      />
    );
  },
);

Button.displayName = "Button";

export { Button };
export type { ButtonProps, ButtonSize, ButtonVariant };
