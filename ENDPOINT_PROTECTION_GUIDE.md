# Endpoint Protection Guide

How to give logged-in and anonymous users different access in TakeMyBible.

This is a set of instructions. **No application code was changed** to produce it. Snippets are reference sketches, not drop-in files. Verify each step with the test plan in section 7.

Reviewed: every controller, `SecurityConfig`, `JwtAuthFilter`, `JwtUtil`, `CrossReferenceService`, the entities, and the frontend callers (`AuthContext`, `CrossReferenceCard`, `AddCrossReferenceModal`, `CrossReferences`, `WriteCommentField`, `fetchData`, `useCrossReferences`, `LoginPage`, `App`).

---

## 1. Target access model

| Capability | Anonymous | Logged in |
|---|---|---|
| Read Bible text (books, chapters, verses) | Yes | Yes |
| Read comments | Yes | Yes |
| Read cross references | Yes (`userVote` is always `0`) | Yes (includes their own `userVote`) |
| Vote on a cross reference | **No** (401) | Yes |
| Submit a new cross reference | **No** (401) | Yes |
| Post a comment | **No** (401) | Yes |

Rule of thumb: **reads are public, writes need a token, and the server decides who you are from the token, never from the URL or request body.**

### Endpoint inventory and proposed access

| Method and path | Today | Proposed |
|---|---|---|
| `POST /api/auth/signup`, `POST /api/auth/login` | public | **public** |
| `GET /api/kjv/books`, `/books/{id}`, `/books/{id}/chapters`, `/books/{id}/chapters/{ch}/verses`, `/books/{id}/chapters/count` | public | **public** |
| `GET /api/kjv/verses/{bookId}/{ch}/{verse}` and `.../{start}/{end}` | public | **public** |
| `GET /api/kjv/cross-references/{book}/{ch}/{verse}` | public, takes `?userId=` | **public, optional auth** (identity comes from the token) |
| `POST /api/kjv/cross-references/new` | public | **authenticated** |
| `POST /api/cross-reference/vote/{crossRefId}/{userId}/{value}` | public | **authenticated**, `{userId}` removed from the path |
| `GET /api/comments/verse/{verseId}` | public | **public** |
| `POST /api/comments` | public by config, fails in code | **authenticated** |
| `GET /api/bible/**` (legacy `BibleController`) | public | **public**, or delete it (the frontend now uses `/api/kjv/**`) |

---

## 2. What is wrong today (ranked)

1. **Every endpoint is public.** `SecurityConfig` starts with `.requestMatchers("/api/**").permitAll()`. Rules are evaluated top to bottom and the first match wins, so every rule after it, including `.anyRequest().authenticated()`, never applies. The only reason `POST /api/comments` rejects anonymous users is a lookup in the controller code, not security config.
2. **Votes can be forged.** The voter id comes from the URL (`/vote/{crossReferenceId}/{userId}/{voteValue}`). Anyone can vote as any user, with or without logging in.
3. **`voteValue` is not validated.** The comment says it is `1` or `-1`, but `POST .../vote/1/42/100` adds 100 votes.
4. **Cross-reference submission is anonymous and unattributed.** `POST /api/kjv/cross-references/new` needs no login, and `cross_references` has no `created_by` column, so you cannot tell who made a row, rate-limit them, or remove their data.
5. **The JWT's identity is thrown away.** `JwtUtil` puts a `userId` claim in the token, but `JwtAuthFilter` only reads the subject (email) and sets it as the principal with no authorities. Controllers then have to re-query the user, and `CommentController` casts the principal to `String`.
6. **Secrets are committed.** `application.properties` is tracked by git (2 commits in its history) with the JWT signing secret and DB password in plain text. There is no root `.gitignore`. Treat both values as exposed.
7. **`userId` is a query parameter on a public read.** `GET .../cross-references/...?userId=7` returns user 7's votes to anyone. The `-1` sentinel and the two separate code paths also mean anonymous results are not sorted by votes, while logged-in results are.
8. **Failed auth returns 403, not 401.** No authentication entry point is configured, so the frontend cannot tell "not logged in / expired" from "forbidden".
9. **The frontend never sends the token** except in `WriteCommentField`. `CrossReferenceCard` (vote) and `AddCrossReferenceModal` (submit) call the API with no `Authorization` header, so they will break the moment you lock the backend down.
10. **The frontend never notices an expired token.** `isAuthenticated` is `!!token`. After 24 hours the UI still shows you as logged in and writes fail.
11. **`AddCrossReferenceModal` fails silently.** `fetch` does not throw on 401/403/500. The code only handles `response.ok`, so a rejected submit shows nothing and the modal still closes.
12. **Stale cache across login/logout.** The react-query key for cross references has no user in it, so a list fetched while anonymous (all `userVote: 0`) is reused after logging in.

---

## 3. Recommended order of work

Do it in this order so the running app never breaks halfway. Locking down the backend is deliberately **last**.

1. Backend: put the user id into the authentication principal and return 401 (additive, breaks nothing).
2. Frontend: central API helper that sends the token and handles 401 (additive).
3. Backend and frontend together: change the vote endpoint to derive the user from the token (breaking pair, change both in one go).
4. Backend: validate and attribute cross-reference creation (adds `created_by`).
5. Backend: lock down `SecurityConfig` (default deny).
6. Move secrets out of the repo and rotate them.
7. Hardening and optional extras.

---

## 4. Backend instructions

### 4.1 Put the user id into the principal (`security/`)

1. Create a small record, for example `security/AuthenticatedUser.java`:
   ```java
   public record AuthenticatedUser(Long id, String email) {}
   ```
2. In `JwtAuthFilter`, parse the token **once** (today `isTokenValid` and `getEmailFromToken` each parse it) and build the principal from the claims:
   - `Claims claims = jwtUtil.parseToken(token);` inside a try/catch. On any exception, do nothing and continue the chain (the request stays anonymous).
   - `Long id = ((Number) claims.get("userId")).longValue();`
   - `new UsernamePasswordAuthenticationToken(new AuthenticatedUser(id, claims.getSubject()), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))`
3. Keep the "ignore an invalid token and continue as anonymous" behavior. It is what lets public endpoints still work when the browser sends an expired token. Protected endpoints will then reject the request in 4.2.
4. **Update `CommentController` in the same change.** It currently does `(String) authentication.getPrincipal()`, which will throw `ClassCastException` once the principal is an `AuthenticatedUser`. Replace that parameter with `@AuthenticationPrincipal AuthenticatedUser user` and use `user.id()` (for example `userRepository.getReferenceById(user.id())` to set the comment author, no extra lookup needed).

### 4.2 Return 401 for unauthenticated requests (`SecurityConfig`)

Add to the filter chain:
```java
.exceptionHandling(e -> e.authenticationEntryPoint(
        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
```
(`org.springframework.security.web.authentication.HttpStatusEntryPoint`.) Result: no or invalid token on a protected route gives `401`; reserve `403` for "logged in but not allowed" later.

### 4.3 Change the vote endpoint (`CrossReferenceVotesController` + service)

1. New shape, with no `{userId}`:
   `POST /api/cross-reference/vote/{crossReferenceId}/{voteValue}`
   The controller takes `@AuthenticationPrincipal AuthenticatedUser user` and passes `user.id()` down.
2. Validate: if `voteValue` is not `1` or `-1`, return `400`.
3. Check the cross reference exists before saving a vote. If not, return `404`. Today a vote row is saved for a missing reference and the counter update is silently skipped.
4. Move the create / switch / undo logic out of the controller into `CrossReferenceService.vote(userId, crossReferenceId, value)` and annotate it `@Transactional`. The vote row and the `votes` counter must commit or roll back together.
5. Make the counter change atomic. `updateVotes` reads, adds, then saves, which loses updates when two users vote at once. Replace it with a single statement in `CrossReferenceRepository`:
   ```java
   @Modifying
   @Query("update CrossReference c set c.votes = c.votes + :delta where c.id = :id")
   int addVotes(@Param("id") Long id, @Param("delta") int delta);
   ```
6. Keep the existing semantics: new vote adds `value`, switching adds `value * 2`, repeating the same vote removes it.
7. Decide a policy (optional): block voting on your own submission. This needs `created_by` from 4.4.

### 4.4 Protect and attribute cross-reference creation

1. **Database:** add a nullable creator column. It must be nullable because existing rows have no creator.
   ```sql
   ALTER TABLE cross_references
     ADD COLUMN created_by BIGINT UNSIGNED NULL,
     ADD CONSTRAINT fk_cross_references_created_by
       FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;
   ```
   Add the same column to the `CrossReference` entity (`@Column(name = "created_by") private Long createdBy;`). Also add `cross_references`, `cross_reference_extras`, `cross_reference_votes` and the `kjv_*` tables to `database/migration/create_table.sql`. They currently exist only because `ddl-auto=update` creates them.
2. **Controller:** inject `@AuthenticationPrincipal AuthenticatedUser user` into `newCrossReference` and call `crossReferenceService.create(dto, user.id())`. Set `createdBy` there and make `create` `@Transactional` (it saves two tables).
3. **Validate the input.** Add `spring-boot-starter-validation` to `pom.xml`, put `@Valid` on the `@RequestBody`, and constrain `NewCrossReferenceInputDto`:
   - book names not blank, with a max length.
   - chapter and verse numbers `>= 1`, `toVerseStart <= toVerseEnd`.
   - `themeTags`: cap the array size and each tag's length. `connectionType`: check against a whitelist.
   - Remove the `votes` field from the DTO. The service already forces `0`, and a client-supplied field only invites confusion.
   - Better still, confirm the book names exist in `kjv_books` and the chapter/verse exist in `kjv_verses`.
4. **Prevent duplicates and spam.** Reject (or return the existing row for) a second submission with the same from/to range, or add a unique key over the from/to columns. Optionally cap new references per user per hour using `created_by` and a `created_at` column.

### 4.5 Make the read endpoint identity-aware without a `userId` parameter

In `CrossReferenceController.getCrossReferences`:
1. Remove the `userId` request param and the `-1` sentinel.
2. Add `@AuthenticationPrincipal AuthenticatedUser user`. For an anonymous caller Spring leaves this `null` (the principal is the string `"anonymousUser"`, which does not match the parameter type). Confirm this in test 7.4.
3. Use one query for both cases: `findWithUserVote(book, chapter, verse, user == null ? null : user.id())`. The repository comment already says "null for anonymous → no join match → userVote 0". Change that parameter from `String` to `Long`. This also fixes the inconsistency where only logged-in results are sorted by votes, and it deletes the manual DTO-mapping branch in the controller.

### 4.6 Lock down `SecurityConfig` (do this last)

Replace the `authorizeHttpRequests` block. **Delete** the `/api/**` line. Reordering it is not enough.
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers(HttpMethod.GET, "/api/kjv/**", "/api/bible/**", "/api/comments/verse/**").permitAll()
    .anyRequest().authenticated()     // default deny: every POST/PUT/DELETE needs a token
)
```
Default deny means a new endpoint is protected unless you explicitly open it. CORS preflight is answered by the `CorsFilter` you already register through `.cors(...)`, so no `OPTIONS` rule is needed.

Also while here:
- Put the allowed CORS origin in a property (for example `app.cors.allowed-origins`) so production is not hard-coded to `http://localhost:5173`.
- `allowCredentials(true)` is not needed for a bearer-token API. Set it to `false` unless you move to cookies.

### 4.7 Secrets (do before anything is deployed)

1. In `application.properties`, replace the literals with placeholders:
   ```properties
   spring.datasource.password=${DB_PASSWORD}
   jwt.secret=${JWT_SECRET}
   ```
2. Set the values as environment variables (an IntelliJ run configuration, or `$env:JWT_SECRET = "..."` in PowerShell before `mvnw spring-boot:run`).
3. Generate a new signing secret:
   ```powershell
   [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
   ```
4. **Rotate both values.** The old secret and DB password remain in git history, so changing the file is not enough. Rotating the JWT secret logs everyone out, which is expected.
5. Add a root `.gitignore` entry for any local override files you create (for example `application-local.properties`, `.env`).

### 4.8 Hardening (after the core work)

- **Login brute force:** add per-IP and per-email attempt limiting on `/api/auth/login` (an in-memory counter is fine for a single instance, or Bucket4j).
- **Input validation on auth:** add `@Valid` and `@Email` / size rules to `AuthRequest`. Signup currently checks `existsByEmail` but not `existsByUsername`, and the `User` entity's `username` column is not marked unique even though the migration file declares it unique, so duplicates are possible while the table is managed by `ddl-auto=update`.
- **Token lifetime:** 24 hours is long for a bearer token with no revocation. Consider a shorter lifetime plus a refresh token if you need logout-everywhere.
- **Roles:** the `ROLE_USER` authority from 4.1 is enough now. If you later want moderators, add a `role` column on `users`, put it in the token, and use `hasRole("ADMIN")` on moderation routes.

---

## 5. Frontend instructions

### 5.1 One API helper that sends the token

`fetchData` in `lib/utils.ts` is used by the modal and by `SearchNextPassage`, but sends no `Authorization` header, and the vote call uses raw `fetch`. Extend `fetchData` (or add a new `lib/api.ts` and migrate the callers) so every call goes through one place:

```ts
export async function apiFetch(url: string, method = "GET", body?: unknown) {
  const token = localStorage.getItem("token");
  const res = await fetch(url, {
    method,
    headers: {
      ...(body !== undefined && { "Content-Type": "application/json" }),
      ...(token && { Authorization: `Bearer ${token}` }),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401 && token) {
    window.dispatchEvent(new Event("auth:expired")); // token was sent but rejected
  }
  return res;
}
```

Sending the token on public GETs is harmless: the backend ignores an invalid token and serves public routes anyway.

### 5.2 Make `AuthContext` aware of expiry

In `contexts/AuthContext.tsx`:
1. Add an `isExpired(token)` helper that decodes the JWT payload (`token.split(".")[1]`, convert base64url to base64 by swapping `-`/`_` for `+`/`/`, then `atob` and `JSON.parse`) and compares `exp * 1000` with `Date.now()`. No library is required.
2. On startup, if the stored token is expired, clear `token` and `user` from state and `localStorage`.
3. Set a timeout for the remaining lifetime that calls `logout()`.
4. Listen for the `auth:expired` event from 5.1: call `logout()` and show a toast ("Your session expired. Please log in again.") with a Log in action.
5. Define `isAuthenticated` as "token present and not expired", and use it everywhere. `CrossReferenceCard` and `CrossReferences` currently check `user === null` while `WriteCommentField` checks `isAuthenticated`, so the checks can disagree.

### 5.3 Voting (`CrossReferenceCard.handleVote`)

1. Replace the raw `fetch` with the helper from 5.1 and change the URL to `.../api/cross-reference/vote/${reference.id}/${direction}` (no `user.userId`).
2. Keep the optimistic update and rollback you already have.
3. Handle `401` separately from other failures. The 5.1 helper already triggers the "session expired" flow, so the card only needs to roll back and skip the generic "Vote unsuccessful" toast.
4. Keep the existing "log in to vote" toast for anonymous users. It is a UX nicety. The backend is what enforces the rule.

### 5.4 Submitting a cross reference (`AddCrossReferenceModal.onSubmit`)

1. Use the helper from 5.1.
2. Handle the non-OK branch. Today only `response.ok` is handled, so a 401/400 does nothing visible. Show an error toast with the server message, and keep the modal open on failure. Right now `handleSubmit` calls `onClose()` after `onSubmit` returns, whether it worked or not, so have `onSubmit` return a success boolean (or throw) and close only on success.
3. Fix the copy in `CrossReferences.handleAddReference`: the toast says "Sign in to vote / log in to vote on cross references" for the add-reference button.
4. Extract the "show toast, offer Log in" logic that is duplicated in `CrossReferenceCard` and `CrossReferences` into one `useRequireAuth()` hook that returns a function you call before any protected action.

### 5.5 Reading cross references (`api/crossReferences.ts` + `hooks/useCrossReferences.ts`)

1. Remove the `userId` argument and the `?userId=` query string from `fetchCrossReferences`. Send the token through the helper so the backend can return `userVote`.
2. Add the user to the query key, for example `user?.userId ?? "anon"`, in `crossReferenceKeys.byVerse` and pass it from `useCrossReferences`. Without this, the list fetched while anonymous is reused after login and shows no votes, and the reverse after logout.

### 5.6 Route guards

`ProtectedRoute` in `App.tsx` is currently a pass-through, and that is correct for now because reading is public. Do not wrap the reading routes. Implement a real guard (`isAuthenticated ? children : <Navigate to="/login" state={{ from: location }} replace />`) and use it only for pages that need login. `Profile.tsx` already links to `/account`, which has no route yet; that is the natural first use. Frontend guards are only UX. The backend rules in section 4 are the actual protection.

### 5.7 Login page bugs worth fixing in the same pass

In `pages/LoginPage.tsx`:
- `if (user) { navigate("/"); return }` runs **before** `useForm(...)`. Hooks must not come after a conditional return, and calling `navigate` during render is not allowed. Call all hooks first, then redirect with `<Navigate to="/" replace />` (or a `useEffect`).
- After login, send the user back to `location.state?.from` (set by the route guard) instead of always `"/"`.
- `SignupPage` has the same pattern if it also redirects early. Check it.

### 5.8 Token storage note

The token lives in `localStorage`, which any script on the page can read. I found no `dangerouslySetInnerHTML` or `innerHTML` in `frontend/src`, so the current exposure is low. If you later render user-generated HTML (for example rich-text comments), move to an `HttpOnly`, `SameSite` cookie, or sanitize rigorously. Moving to cookies also means re-enabling CSRF protection on the backend, which is a larger change, so it is not recommended now.

---

## 6. Database changes (summary)

- `cross_references.created_by BIGINT UNSIGNED NULL` plus a foreign key to `users(id)` (4.4).
- Optional: a unique key over the from/to range columns to stop duplicate submissions.
- Optional: `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP` on `cross_references` for rate limiting.
- Add the missing tables to `database/migration/create_table.sql` (`cross_references`, `cross_reference_extras`, `cross_reference_votes`, `kjv_books`, `kjv_verses`) so the migration reflects the real schema.

---

## 7. Test plan

In PowerShell use `curl.exe` (plain `curl` is an alias for `Invoke-WebRequest`). Restart the backend after changes.

| # | Request | Expected |
|---|---|---|
| 7.1 | `curl.exe -i http://localhost:9090/api/kjv/books` | `200` (public read) |
| 7.2 | `curl.exe -i -X POST http://localhost:9090/api/cross-reference/vote/1/1` (no token) | `401` |
| 7.3 | `curl.exe -i -X POST -H "Content-Type: application/json" -d "{}" http://localhost:9090/api/kjv/cross-references/new` (no token) | `401` |
| 7.4 | `curl.exe -i http://localhost:9090/api/kjv/cross-references/Genesis/1/1` (no token) | `200`, every `userVote` is `0`, results ordered by `votes` |
| 7.5 | Log in, then call `/api/cross-reference/vote/1/1` with `-H "Authorization: Bearer $t"` | `201` (first vote), `204` on repeat (undo) |
| 7.6 | Same call with value `5` | `400` |
| 7.7 | Vote on a cross reference id that does not exist | `404`, and no row in `cross_reference_votes` |
| 7.8 | Old URL shape `/api/cross-reference/vote/1/999/1`, **with** a valid token | `404`. A user id must no longer be accepted from the URL. (Without a token you get `401` first, because security runs before routing.) |
| 7.9 | `Authorization: Bearer garbage` on a protected route | `401` |
| 7.10 | `Authorization: Bearer garbage` on `GET /api/kjv/books` | `200` (invalid token is ignored on public routes) |
| 7.11 | Submit a cross reference with a token, then check the DB | row exists, `created_by` equals your user id |
| 7.12 | Post a comment with a token | `201` (confirms the `CommentController` principal change) |

Getting a token:
```powershell
$r = Invoke-RestMethod -Method Post -Uri http://localhost:9090/api/auth/login `
     -ContentType "application/json" `
     -Body '{"email":"you@example.com","password":"yourpassword"}'
$t = $r.token
```

Frontend checks:
1. Logged out: clicking vote or "Add Ref" shows the log-in toast and sends no request.
2. Logged in: vote persists after a page reload, and the arrows show your vote.
3. Temporarily set `jwt.expiration-ms=60000`, log in, wait one minute, then vote. Expected: one "session expired" toast, you are logged out, and the vote is rolled back.
4. Log in, vote, log out. The same verse should show `userVote` cleared (confirms the query-key fix in 5.5).

---

## 8. Checklist

Backend
- [ ] `AuthenticatedUser` record, filter builds it from claims, `ROLE_USER` authority
- [ ] `CommentController` switched to `@AuthenticationPrincipal`
- [ ] 401 entry point configured
- [ ] Vote endpoint: no `{userId}`, value validated, existence checked, transactional, atomic counter
- [ ] Cross-reference create: authenticated, validated, `created_by` set, duplicates handled
- [ ] Cross-reference read: `userId` param removed, one query, token-derived identity
- [ ] `SecurityConfig`: `/api/**` permitAll **removed**, default deny, public GET list only
- [ ] Secrets moved to environment variables and rotated
- [ ] Migration file updated

Frontend
- [ ] `apiFetch` attaches the token and raises `auth:expired` on 401
- [ ] `AuthContext` handles expiry and logs out on `auth:expired`
- [ ] Vote call uses the new URL and the helper
- [ ] Modal handles errors and closes only on success
- [ ] `useRequireAuth()` replaces duplicated toast code
- [ ] Query key includes the user
- [ ] Real `ProtectedRoute` on `/account` (and any future private page)
- [ ] `LoginPage` hook-order and redirect bug fixed

---

## 9. Out of scope for now

- Per-resource rules such as "only the author can delete their cross reference or comment". This needs `created_by` (4.4) and a `403` check in the service.
- Email verification, password reset, and OAuth login.
- Refresh tokens and server-side revocation.
