# Security

What the application does to protect accounts and data, and where the honest gaps are.

## Passwords and credentials

- Passwords are hashed with **BCrypt**. The hash is never returned by any endpoint, never logged,
  and never serialized (services map entities to DTOs; entities are not exposed).
- `JWT_SECRET` is **required** and has no committed fallback. Startup fails if it is missing or
  shorter than 32 characters, so a weak key cannot reach production.
- The prod profile removes every credential and CORS default, so a misconfigured deploy fails
  closed instead of falling back to development values.
- No secret is present in the repository. CI runs a scan that fails the build on obvious credential
  patterns.

## Sessions

| Concern | Approach |
| --- | --- |
| Access token | Short-lived HS256 JWT (15 minutes default), returned in the response body and kept **in memory** by the frontend — never in `localStorage`, so an injected script cannot read it back from storage |
| Refresh token | Opaque 256-bit random value in an `httpOnly`, `SameSite=Lax` cookie scoped to `/api/auth`; unreadable by JavaScript |
| Storage at rest | Only the **SHA-256 hash** of a refresh token is stored, so a database leak yields nothing usable |
| Rotation | Every refresh rotates the token; replaying a rotated token revokes the user's whole token family |
| Logout | Revokes all of the user's active refresh tokens server-side and clears the cookie |

## Authorization

- Every job, resume and cover letter query is scoped by `user_id`.
- Cross-user access returns **404**, not 403, so the API never confirms that someone else's record
  exists.
- Attaching a resume or cover letter to a job you do not own returns 404.
- `JwtAuthenticationFilter` validates signature and expiry; unauthenticated requests get a JSON 401,
  forbidden ones a JSON 403.

## Abuse protection

- `/api/auth/login`, `/register` and `/refresh` are rate limited per client IP (fixed window,
  default 10 per 15 minutes) and answer `429` with `Retry-After`.
- The limiter is in-process. **A multi-instance deployment should move it to Redis or a gateway** —
  otherwise each instance enforces its own budget.

## Uploads

- PDF and DOCX only, 10 MB maximum, enforced server-side.
- The bytes are checked against the file signature (`%PDF-` or `PK\x03\x04`), not the browser-supplied
  content type, so a renamed file cannot be stored as a document.
- Documents are written under a generated UUID name, so the user-supplied filename never influences
  the path on disk.
- Storage keys are resolved inside the storage root and anything escaping it is rejected, blocking
  path traversal.
- Downloads stream from storage and always carry `Content-Disposition: attachment`.

## Response hardening

Set by the API: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
`Referrer-Policy: no-referrer`, HSTS, and a restrictive `Permissions-Policy`.

A `Content-Security-Policy` is applied by the **nginx frontend image**, not globally on the API,
because Swagger UI requires inline script and a strict global policy would break the API docs.

## AI features

- Keys come from the environment only, are never logged, and are never sent to the browser.
- Prompts contain only data the user already owns — the job they saved and text they supplied.
- The system prompt forbids inventing skills, employers, dates, metrics or qualifications, and
  forbids claiming guaranteed outcomes.
- Matching is deterministic and reported as keyword overlap with a disclaimer. It is **not** an ATS
  score, and the product does not claim to be.
- With no key configured, generation returns `503` with a setup instruction. No fabricated content
  is ever shipped.

## Known gaps

Being explicit about what is *not* covered, so nobody assumes otherwise:

1. **Refresh tokens are not bound to a device or IP.** A stolen refresh token works from anywhere
   until it is rotated or the user logs out.
2. **No email verification or password reset.** Both need an email provider.
3. **No account lockout.** Rate limiting slows brute force; it does not stop a slow, distributed one.
4. **No antivirus scanning** of uploads. Signature validation rejects non-documents but does not
   inspect a genuine PDF for embedded threats. An AV hook is the mitigation.
5. **No audit log** of who changed what beyond the job status history.
6. **`db` and `uploads` are not encrypted at rest** by the application. Use encrypted volumes or a
   managed database with encryption enabled.
7. **In-process rate limiting** is per-instance, as noted above.
8. **Swagger UI is public** in the default configuration. Behind a private network it can be turned
   off with `springdoc.api-docs.enabled=false`.

## Reporting

This is a personal project, not a supported product. If you deploy it publicly, review the gaps above
against your own threat model before trusting it with anything sensitive.
