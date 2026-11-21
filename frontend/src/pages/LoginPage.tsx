import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import MainBg from "@/assets/main-bg-v2.png";
import { BACKEND_BASE_URL } from "@/lib/constants.ts";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
  CardFooter,
} from "@/components/ui/card";
import {
  Field,
  FieldLabel,
  FieldError,
} from "@/components/ui/field";

const loginSchema = z.object({
  email: z.string().email("Please enter a valid email"),
  password: z.string().min(1, "Password is required"),
});

type LoginFormData = z.infer<typeof loginSchema>;

interface RedirectState {
  from?: { pathname?: string; search?: string; hash?: string };
}

/** Where to go once logged in: the page the route guard sent us away from, else home. */
function getPostLoginPath(state: unknown): string {
  const from = (state as RedirectState | null)?.from;
  const pathname = from?.pathname;
  if (
    !pathname ||
    !pathname.startsWith("/") ||
    pathname.startsWith("//") || // "//host" would be read as another origin
    pathname === "/login" ||
    pathname === "/signup"
  ) {
    return "/";
  }
  return `${pathname}${from?.search ?? ""}${from?.hash ?? ""}`;
}

export default function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const location = useLocation();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data: LoginFormData) => {
    setServerError(null);

    try {
      const response = await fetch(`${BACKEND_BASE_URL}api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });

      const result = await response.json();

      if (!response.ok) {
        setServerError(result.error || "Invalid email or password");
        return;
      }

      login(result.token, {
        userId: result.userId,
        email: result.email,
        username: result.username,
      });
      // No navigate() here: login() flips isAuthenticated and the redirect below takes over.
    } catch {
      setServerError("Network error. Please try again.");
    }
  };

  // All hooks are above this line. Redirect with <Navigate>, never navigate() during render.
  if (isAuthenticated) {
    return <Navigate to={getPostLoginPath(location.state)} replace />;
  }

  return (
    <div
        className="flex min-h-screen w-full items-center justify-center font-geist bg-cover bg-bottom bg-no-repeat p-4"
        style={{ backgroundImage: `url(${MainBg})` }}
    >
      <Card className="w-full max-w-md ">
        <CardHeader>
          <CardTitle className={"font-geist"}>Welcome back</CardTitle>
          <CardDescription className={"font-geist"}>
            Log in to your <span className={" text-yellow-300 font-bold text-lg"}>Isaiah1:18</span> account.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
            {serverError && (
              <div className="rounded-lg bg-destructive/10 p-3 text-sm text-destructive">
                {serverError}
              </div>
            )}

            <Field>
              <FieldLabel htmlFor="email">Email</FieldLabel>
              <Input
                id="email"
                type="email"
                placeholder="you@example.com"
                {...register("email")}
                aria-invalid={!!errors.email}
              />
              {errors.email && (
                <FieldError>{errors.email.message}</FieldError>
              )}
            </Field>

            <Field>
              <FieldLabel htmlFor="password">Password</FieldLabel>
              <Input
                id="password"
                type="password"
                placeholder="Enter your password"
                {...register("password")}
                aria-invalid={!!errors.password}
              />
              {errors.password && (
                <FieldError>{errors.password.message}</FieldError>
              )}
            </Field>

            <Button type="submit" disabled={isSubmitting} className="w-full">
              {isSubmitting ? "Logging in..." : "Log In"}
            </Button>
          </form>
        </CardContent>
        <CardFooter className="justify-center">
          <p className="text-sm text-muted-foreground">
            Don't have an account?{" "}
            <Link to="/signup" className="text-primary underline underline-offset-4 hover:text-primary/80">
              Sign up
            </Link>
          </p>
        </CardFooter>
      </Card>
    </div>
  );
}
