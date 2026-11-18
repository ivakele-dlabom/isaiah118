import { Link } from "react-router-dom";
import { BadgeCheck, LogIn, LogOut } from "lucide-react";

import { useAuth } from "@/contexts/AuthContext.tsx";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuGroup,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { AuthUser } from "@/types/main.ts";

const circle =
    "absolute top-4 right-4 flex size-10 items-center justify-center rounded-full bg-black text-white";

export const Profile = () => {
    const { user, isAuthenticated } = useAuth();

    if (!isAuthenticated || !user) {
        return (
            <Link to="/login" aria-label="Sign in" className={circle}>
                <LogIn className="size-4" />
            </Link>
        );
    }

    return <NavUser user={user} />;
};

export function NavUser({ user }: { user: AuthUser }) {
    const { logout } = useAuth(); // assumes your AuthContext exposes this

    return (
        <DropdownMenu>
            <DropdownMenuTrigger
                render={<button aria-label="Account menu" className={circle} />}
            >
                <span className="font-bold uppercase">
                    {user.username.at(0) ?? "?"}
                </span>
            </DropdownMenuTrigger>

            <DropdownMenuContent align="end" side="bottom" sideOffset={8} className="min-w-56 rounded-lg">
                <DropdownMenuGroup>
                    <DropdownMenuLabel className="font-normal">
                        <div className="grid text-left text-sm leading-tight">
                            <span className="truncate font-medium">{user.username}</span>
                            <span className="truncate text-xs text-muted-foreground">
                                {user.email}
                            </span>
                        </div>
                    </DropdownMenuLabel>
                </DropdownMenuGroup>

                <DropdownMenuSeparator />

                <DropdownMenuGroup>
                    <DropdownMenuItem render={<Link to="/account" />}>
                        <BadgeCheck />
                        Account
                    </DropdownMenuItem>
                </DropdownMenuGroup>

                <DropdownMenuSeparator />

                <DropdownMenuItem onClick={() => logout()}>
                    <LogOut />
                    Log out
                </DropdownMenuItem>
            </DropdownMenuContent>
        </DropdownMenu>
    );
}