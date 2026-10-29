# Agentic Enterprise UI

A polished React + TypeScript frontend for the existing Spring Boot Agentic AI Chatbot POC.

## Connect to the backend

Default API:
`http://localhost:8080/api/chat`

Override it with:

```bash
VITE_API_URL=http://localhost:8080/api/chat
```

By default the backend requires HTTP Basic credentials. Configure the same username and
password in the backend environment before starting Spring Boot:

```text
APP_SECURITY_USERNAME=your-username
APP_SECURITY_PASSWORD=use-a-long-unique-password
```

If the UI is hosted at a different origin, also set
`APP_CORS_ALLOWED_ORIGIN` to its exact origin (scheme and host, including the
port when applicable).

The configured account becomes the initial administrator. The UI prompts for
its credentials and keeps them in memory only until sign out or page close.
After signing in as the administrator, use **Manage accounts** to create
regular user accounts and set their initial passwords. Share those credentials
with users through your organization's secure channel; there is no public
sign-up or invitation email flow.
Existing usernames are rejected with a readable conflict message; submitting
the account form is disabled while creation is pending.
Users (including the administrator) can use the key icon in the top bar to
change their own password. The current password is required, and the new
password must be different, at least 12 characters, and no more than 72 UTF-8
bytes. After a successful change, sign in again with the new password.
The bootstrap environment password is only used when initially creating the
administrator; restarting does not overwrite an existing account's password.
Each account only sees conversations created while signed in as that user.
Existing conversations are assigned to the initial administrator during the
database migration. Back up the MySQL database before the first migration.
Use HTTPS outside local development; HTTP Basic credentials are otherwise
exposed in transit.

## Optional Keycloak login (OAuth2/OIDC)

**Basic remains the default.** Keycloak is an explicit alternative, not an
automatic fallback. Enable it in both the backend and UI. The browser uses
Authorization Code with S256 PKCE (a public client, with no client secret).
Passwords are entered only on Keycloak's page, and API calls carry a refreshed
Bearer access token. Access/refresh tokens stay in memory, not localStorage.

### Start a local identity provider

The repository root contains `compose.keycloak.yaml` and
`keycloak\chatbot-realm.json`. With Docker Desktop running, open PowerShell at
the repository root and run:

```powershell
$admin = Get-Credential -Message "Choose Keycloak bootstrap administrator credentials"
$env:KEYCLOAK_ADMIN_USERNAME = $admin.UserName
$env:KEYCLOAK_ADMIN_PASSWORD = $admin.GetNetworkCredential().Password
try {
    docker compose -f .\compose.keycloak.yaml up -d
} finally {
    Remove-Item Env:\KEYCLOAK_ADMIN_USERNAME, Env:\KEYCLOAK_ADMIN_PASSWORD
    Remove-Variable admin
}
```

Open `http://localhost:8180/admin/` and sign in with those provider-admin
credentials. Select the **chatbot** realm, create an application user under
**Users**, and set a password under **Credentials** (a temporary password
forces a change on first login). New users inherit the API's **USER** role.
For chatbot administration, assign the **chatbot-api / ADMIN** client role
under the user's **Role mapping** tab. Filter by clients when finding the role.
No application users or passwords are seeded in the import.

Provider administration and chatbot administration are separate privileges:
the chatbot ADMIN role does not grant Keycloak administration rights.
**Manage accounts in Keycloak** opens the provider admin console and requires
separate provider-admin privileges. The top-bar account button opens the
signed-in user's Keycloak account console instead of the local password form.

The server image is pinned to 26.8.1; the JavaScript adapter has its own version
cycle. The container listens only on loopback and persists development data in
a named volume. Import occurs when the realm is absent: restarting does not
overwrite an existing realm's settings. Change existing realm settings in the
console. Do not remove the volume unless intentionally discarding identities.
To stop it, use `docker compose -f .\compose.keycloak.yaml stop` from the root.

If Docker is unavailable, download the same Keycloak server release from the
official distribution and use its supported JDK. Copy `chatbot-realm.json`
into that distribution's `data\import` directory. Set
`KC_BOOTSTRAP_ADMIN_USERNAME` and `KC_BOOTSTRAP_ADMIN_PASSWORD` securely in its
PowerShell process, then run from the distribution directory:

```powershell
.\bin\kc.bat start-dev --http-port=8180 --hostname=http://localhost:8180 --import-realm
```

### Switch the application explicitly

Back up MySQL before restarting: Flyway V3 adds external identity mappings.
V3 retains its original checksum for databases that already applied it.
V4 reduces the issuer column to 1,024 bytes with strict SQL mode so oversized
existing values fail rather than being truncated. If V4 fails, inspect the
stored issuer lengths before retrying; do not repair Flyway history to bypass it.
Set these variables in the **backend's** environment/run configuration:

```text
SPRING_PROFILES_ACTIVE=keycloak
KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/chatbot
KEYCLOAK_AUDIENCE=chatbot-api
APP_CORS_ALLOWED_ORIGIN=http://localhost:5174
```

The profile sets `app.security.mode=keycloak`. The JWK URL defaults to the
issuer plus `/protocol/openid-connect/certs`; override it with
`KEYCLOAK_JWK_SET_URI` only for a deliberately configured internal route.
Keep the issuer equal to the actual token issuer. The backend validates
signature, issuer, expiry/not-before and audience, and reads only USER/ADMIN
roles from `resource_access["chatbot-api"].roles`.

In this UI directory, copy `.env.keycloak.example` to `.env.local`, then
restart Vite (or rebuild the production UI):

```powershell
Copy-Item .\.env.keycloak.example .\.env.local
npm.cmd run dev -- --port 5174 --strictPort
```

The example sets `VITE_AUTH_MODE=keycloak`, provider URL
`http://localhost:8180`, realm `chatbot`, client `chatbot-ui`, and the local API.
Use the same UI origin in backend CORS and Keycloak's client settings.
The import permits localhost ports 5173 and 5174, with path-only redirect
wildcards; it does not permit arbitrary hosts, ports or web origins.
For another origin, update redirect URIs, post-logout redirect URIs, web
origins and backend CORS together. There is no UI client secret.

On **Sign in with Keycloak**, finish sign-in on the provider and return to
the UI. A backend/UI mode mismatch, unavailable provider or invalid token
produces an error; it never silently switches to Basic. In Keycloak mode,
local account/password APIs return a provider-management error; bootstrap
Basic credentials are not required and do not create an administrator.
The existing Basic failed-login limiter remains Basic-only; the imported
realm enables Keycloak brute-force protection with a temporary lockout.
These are different implementations, not a shared counter or identical policy.

### Ownership, logout and production boundaries

Existing Basic credentials are **not** automatically Keycloak credentials.
OIDC identities are mapped by immutable issuer + subject to separate local
owners needed for history and quotas. Matching display usernames never grants
access to existing Basic history. Display-name changes do not change ownership.
Any migration of old history requires an explicit, separately audited process.

Tokens are refreshed before API requests when nearing expiry. A refresh
failure clears them and asks for sign-in again. Sign out clears UI state and
redirects through the provider logout endpoint. JWT validation is local:
logout, password changes or disabling a user do not instantly revoke an
already-issued access token at the API. The demo uses a 5-minute access-token
lifetime. Consider provider introspection/revocation controls if immediate
revocation is required.

`start-dev`, local HTTP and the embedded provider database are **development
only**. Production needs HTTPS for UI/API/provider, hardened Keycloak with
an external database and backups, exact deployment URLs, secure secrets,
provider recovery configuration, and shared/edge abuse controls. A JWT is
signed, not encrypted; do not put sensitive business data in its claims.

To return to Basic, remove the backend Keycloak profile (and any explicit
`app.security.mode` override), restore the Basic bootstrap configuration,
and remove the UI's Keycloak variables or set `VITE_AUTH_MODE=basic`.
Restart both processes. Basic accounts and their old history remain intact.

## Faster employee lookups and conversation safeguards

### Idempotent session creation

The UI no longer generates timestamp-based conversation IDs.
`POST /api/conversations` accepts `{"creationKey":"<UUID>"}` and returns
`{"sessionId":"<server-generated UUID>"}` with HTTP 200 for both initial creation
and retries. The authenticated owner plus creation key uniquely identifies a
creation action. A short account-row lock and a database uniqueness constraint
prevent duplicate creation across concurrent requests and application instances.
The mapping persists in MySQL through Flyway V5, including across backend restarts.
Existing conversation IDs and history remain usable.

Each intentional **New conversation** action uses a new random creation key.
Overlapping clicks are blocked; failed creation retries retain the same key and
recover the same session even if the previous response was lost after commit.
The initial blank chat is created lazily before its first message. Creation
failures are shown explicitly. Empty sessions are not listed in history until
they have messages, preserving existing history behavior.

Creation keys remain only in the page's memory and are cleared on sign-out.
A full browser reload starts a new creation action; it does not recover an
unsent draft or its key. This feature deduplicates **session creation only**,
not message submission, model calls or saved answers. Existing chat clients
can continue sending their legacy session IDs; the chat endpoint does not
require a session to have been allocated through this new API.

### Login rate limiting

HTTP Basic authentication is protected across all API endpoints: after 5 failed
credential attempts from the same direct client IP in a 10-minute fixed window,
further credential-bearing requests receive HTTP 429 and `Retry-After` seconds.
The window starts with the first credential-bearing request, rather than being
extended by each failure. Correct credentials cannot bypass an active block;
accounts are not disabled and can still be used from a different client IP.
Anonymous challenges, successful authentication, admin-role denials (403) and
browser CORS preflights do not count as failed credentials. Concurrent attempts
reserve budget slots until completed, preventing parallel password guesses from
bypassing the threshold. A burst of more than 5 simultaneous authenticated
requests may therefore also receive a temporary 429.

The UI displays the retry period. Configure `app.security.login.max-failures`,
`app.security.login.window` and `app.security.login.max-clients` (default 10,000
tracked IPs). When the tracker is full, new IPs receive 429 instead of evicting
active budgets. No credentials, tokens or usernames are logged by the limiter.

This is an in-memory, per-instance control: restart clears budgets. Users behind
the same NAT/proxy share an IP budget. Forwarded headers are deliberately not
trusted; behind a reverse proxy, configure a trusted client-address boundary
before relying on per-IP rules. Production multi-instance deployments need a
shared/edge limiter, HTTPS and broader abuse controls; this is not account
lockout or protection against distributed guessing.

### Answer presentation

New employee, local-clock and model answers start with **Answer**.
Single-employee answers use conversational sentences (specific field questions
include only those fields); employee lists show the returned count followed by
a **Details** Markdown table and respect the requested fields.
Model answers add **Details** and **Warnings** only when needed.
Errors remain readable failure messages rather than being presented as successful
answers. The API still returns its normal JSON
envelope and structured `data` for integrations; this is not printed as the answer.
RAG, web, general and multi-tool generation must return a validated internal
JSON object containing `summary`, `details` and `warnings`. The backend checks
types, required fields, lengths and array limits, rejects unknown/duplicate
fields and trailing content, then builds the display sections **Answer**,
**Details** and **Warnings** (empty sections are omitted). The internal JSON
never becomes the user-facing answer. Sources come only from tool metadata,
not a model-provided `sources` property. Invalid model output produces a
readable failure, not raw JSON or a successful plain-text fallback. There is
no additional formatting-model request or automatic repair retry.
All assistant answers use the same safe Markdown renderer (including reopened
and cached answers). Raw HTML is disabled, external images are not loaded, and
unsafe link protocols are filtered. Live web/document metadata is shown as a
Sources section when supplied; cached/history records currently only retain the
answer and source badge, not the original structured citation metadata.
Existing stored answers are not rewritten into the new database table format.

### Lower token budgets and task-specific prompts

Each model call has an explicit output budget below 500 tokens:

| Call | Maximum generated tokens | Backend property |
|---|---:|---|
| Classification | 250 | `app.ai.tokens.classification` |
| General, document or web answer | 400 | `app.ai.tokens.answer` |
| Multi-tool synthesis | 450 | `app.ai.tokens.synthesis` |

The provider default is also reduced to 450. Configured per-call budgets must
be between 1 and 499 or startup fails. Lower budgets can still cut off an answer:
invalid/incomplete structured output remains an explicit formatting error,
not raw JSON, and there is no token-consuming automatic repair retry.
Classification still uses the existing explicit fallback on invalid output.

These limits control **generated output per call**, not input tokens or total
tokens for a question. A multi-tool request can involve classification, tool
answers and final synthesis, so its total may exceed 500. Actual provider usage
and answer completeness must be measured with representative live queries;
these budgets alone do not prove a particular cost or latency reduction.

Prompts are assembled for the actual task: general explanation, company-policy
grounding, web evidence or multi-tool synthesis. Weather-specific rules are
included only for weather web questions. Fixed safety/grounding and response
schema instructions stay in the system message; questions and evidence are
separate untrusted user data. This is conditional prompt assembly, not another
LLM call or user-editable safety instructions.

Classifier output omits unused JSON fields, and classifier history is limited
to the latest six messages, each at most 300 characters before the truncation
marker. Answer evidence is bounded to 6,000 characters (a character bound, not
an exact token count). Multi-tool evidence shares that budget across sources,
so a large employee result cannot displace all policy/web evidence.
Shortened evidence is explicitly marked and the application appends a coverage
warning regardless of whether the model supplies one; questions remain separate and intact.
Source metadata and structured API data remain outside the generated text.
Older history/cache answers keep their original formatting; use a new chat
to see the new answer presentation.

Complete ID lookups such as "Who is employee 101?" and "What is employee 101's
email?" go directly to the controlled employee lookup, without AI classification.
Unsupported requested fields (for example salary) still reach the normal
"not available" response; they are not silently converted into all-fields queries.
Mixed-source questions, employee names and ambiguous follow-ups still use the AI.
The primary-key employee lookup was already indexed. A new Flyway V2 migration
adds `(owner_username, session_id, id)` to accelerate context/cache/history access.
Back up MySQL before restarting to apply migrations.

The latency panel now separates **Direct employee lookup**, **Employee database
query** and **Request safety checks**. Compare fresh sessions, first requests and
warmed requests separately. Skipping AI removes one source of delay; it does not
guarantee a latency target or remove cold database/JVM work.

Backend-enforced defaults:

- 2,000 UTF-16 characters per question and 100 questions per owner/thread.
  `app.chat.max-message-chars` and `app.chat.max-questions` configure these values.
  `GET /api/chat/limits` supplies them to the signed-in UI.
- After 100 accepted questions, HTTP 409 asks the user to start a new chat.
  Cached questions count too; rejected messages do not. Old threads remain
  readable and also respect this cap. A failed assistant/provider call still
  counts if its user question was saved.
- The question count and user-message insert share a transaction, using a short
  account-row lock to prevent concurrent requests bypassing the cap. The lock
  is released before calling AI/tools.
- AI context is limited to the latest 10 messages and 6,000 UTF-16 characters;
  each context message is limited to 1,000. This does not delete stored history
  or implement a data-retention policy.
- A local, English rule-based guard rejects clear abusive/harmful requests,
  credential/prompt-extraction attempts and specified unsafe operations, before
  reading cached answers, saving messages or calling providers. Rejections are
  readable HTTP 400 errors, not account bans. Only categories, not rejected
  message contents, are logged.

These rules are a conservative first layer, **not comprehensive content
moderation or a prompt-injection guarantee**. Obfuscation and other languages
can evade them, and false positives remain possible. Authentication, admin
endpoint restrictions, owner-scoped history and controlled database methods are
the actual access boundaries. Existing users can still read the employee fields
allowed by this application; no per-employee/department access policy is implied.
Changing those permissions requires a separate business access policy.

## Run

The sign-in and chatbot share a dark violet/pink/cyan visual identity, with
gradient typography, a locally rendered animated SVG intelligence orb,
translucent sign-in panel and topic suggestion cards. The workspace includes
restyled history, chat bubbles, composer and agent panel, with a matching light
theme. Mobile navigation exposes history and admin controls through the menu
button; it closes with its close button, backdrop or Escape. No external images
or animation libraries are required. Reduced-motion preferences disable
decorative animations, and the sign-in panel has a fallback when backdrop blur
is unavailable. Basic and optional Keycloak authentication are unchanged.
Buttons include pointer/touch and keyboard click ripples, press feedback and
send-icon hover movement. Messages, dialogs and navigation have short entrance
transitions. Click overlays never intercept input and are removed after 550 ms;
their timers are cleared on unmount. Reduced-motion preferences suppress
ripples and motion feedback. These effects do not delay API requests.

```bash
npm install
npm run dev
```

Open the Vite URL shown in the terminal.

The frontend sends:

```json
{
  "sessionId": "ui-...",
  "message": "Who is employee 101?"
}
```

to the existing `POST /api/chat` endpoint.

## Learning P95/P99 latency

### Similarity search checks and evaluation

Policy retrieval now explicitly requests `app.rag.top-k=2` and
`app.rag.similarity-threshold=0.65`. With cosine distance this means
`distance < 0.35` in the native PostgreSQL query, before limiting results.
Previously distance filtering happened in Java after the vector store's default
top-4 search, using `distance <= 0.35`. Exact boundary matches are now excluded
by Spring AI's native strict comparison. A higher score threshold is stricter,
not more permissive. These defaults preserve the previous intended cutoff;
they have **not** been tuned against a live semantic evaluation yet.

The admin latency panel separates **Policy retrieval (embedding + vector
search)** and **Policy answer generation**. Retrieval timing includes the
external embedding request, database connection and vector search; it is
**not database-only latency**. Empty retrieval is a completed operation, not an
error or proof that the question was correctly rejected. Provider/database
exceptions appear as error samples.

Local inspection on 18 October 2026 found PostgreSQL 18.6, pgvector 0.8.7,
five stored policy vectors of dimension 3,072 and only the primary-key index.
The local psql executable lives in a `PostgreSQL\17` directory; its path does
not indicate the running server version. Leave exact search configured for
this tiny corpus. pgvector's standard `vector` HNSW/IVFFlat index supports
at most 2,000 dimensions, so simply changing `index-type` to HNSW is not a valid
fix for this schema. At larger scale, evaluate compatible half-precision or
lower-dimensional embeddings, recall and reindexing before changing storage.
Changing embedding models/dimensions requires re-embedding documents consistently.

From the repository root, run the database-only benchmark with the existing
database connection environment (`PGHOST`, `PGPORT`, `PGUSER`, `PGDATABASE`,
`PGPASSWORD`) and the actual installed psql path:

```powershell
& 'C:\Program Files\PostgreSQL\17\bin\psql.exe' -X -w -f .\tools\benchmark-vector-search.sql
```

It uses existing document vectors as query vectors, creates only temporary
tables, verifies old/new result IDs on those queries and reports nearest-rank
P95/P99 plus `EXPLAIN (ANALYZE, BUFFERS)`. It never calls an embedding/chat
provider or modifies persistent vectors. This is a database microbenchmark,
**not** natural-language retrieval quality or end-to-end latency.
The measured run had 1,000 warmed searches: **P95 0.223 ms, P99 0.384 ms,
max 0.861 ms**. Five first-per-vector samples had max 5.338 ms; these are not
true cold-cache measurements (the verification step already read the vectors).
Results are specific to this machine, corpus and cache state; no application
SLO or before/after speedup is established.

`tools\policy-retrieval-cases.json` contains 10 supported and 5 unsupported
questions with reviewed expected source labels. Unsupported-fact cases are
important: a related policy is not necessarily evidence for a numerical limit.
The opt-in evaluator calls only the embedding provider and PostgreSQL, never
the answer model. Set `GEMINI_API_KEY` and the `PG*` environment variables
securely in the evaluation terminal; never place secrets in command arguments
or committed files. Confirm the **exact ingestion embedding model/options**
first, then run (replace the model name with that confirmed value):

```powershell
python .\tools\evaluate-policy-retrieval.py --model YOUR_INGESTION_MODEL --psql 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
```

Default dimension is 3,072; specify `--task-type` only to match the application's
query embedding options. The script does not discover ingestion model provenance.
Do not compare embeddings from different models. It reports source hit rate,
unsupported-question abstention, scores and embedding/client-query timings.
Exit status is 1 below the initial acceptance targets (90% supported source hits
and 80% unsupported abstention), 2 for incomplete execution, and 0 when both pass.
Those are evaluation targets, not achieved results. Review false positives
before tuning `--threshold`/`--top-k`, and apply accepted settings to the backend.
Use a held-out set before claiming generalization. Fifteen queries are too few
to establish reliable P99; database-client time includes psql process startup.
The semantic evaluation has not been executed locally because the embedding
key is not available in this terminal.

P95 is the duration within which 95% of requests finish; P99 covers 99%.
For example, P95 = 2 seconds means roughly 5% of requests take longer.
These are not averages or success percentages.

Restart the backend and send chat messages. Then, as an administrator, call
`GET http://localhost:8080/api/admin/latency`. It returns exact P95, P99 and
max **in milliseconds** for requests completed in the last 5 minutes
(configurable with `app.metrics.latency-window`). When nothing happened in
that window, `metrics` is empty. The window is in memory, per instance, and
resets on restart. With fewer than 100 samples, P99 equals the max.
The UI's in-memory credentials are not shared with other browser tabs, and
some browsers do not show a login prompt; use PowerShell:

```powershell
$credential = Get-Credential -Message "Chatbot admin credentials"
$pair = $credential.UserName + ":" + $credential.GetNetworkCredential().Password
$token = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
try {
    $report = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/latency" -Headers @{ Authorization = "Basic $token" }
    $report.metrics | Format-Table name, outcome, count, p95Ms, p99Ms, maxMs -AutoSize
} finally {
    Remove-Variable credential, pair, token -ErrorAction SilentlyContinue
}
```

Regular users cannot access `/api/admin/**` or `/actuator/**`. Do not put
passwords in URLs or committed scripts.

`total_request` is the full chat request. Stage rows separate `cache_lookup`,
`load_context`, `save_user`, `save_assistant`, `process`, `classification`
and `tool_execution`. `classification_fast_path` counts greetings, thanks and
plain arithmetic routed without the LLM classifier. Classification is limited
by `app.ai.classification-timeout` (default 8 seconds); timeouts and failures
appear as `classification` with outcome `error`, followed by a keyword-based
`classification_fallback`, which cannot resolve pronouns from history.
Nested stages overlap, so do not add their percentiles together. Cache hits
skip AI processing. `completed` means no exception, not a correct AI answer.
HTTP status labels (`2xx`, `4xx`, `5xx`) distinguish HTTP outcomes, not business
success: an unsuccessful tool result can still have HTTP 200.

Total request timing covers synchronous server execution, including security
and response serialization, not browser/network round-trip time. Metrics
contain no usernames, session IDs, prompts or answers.

The separate `chat_request_duration_seconds_bucket` histogram supports
aggregation in a Prometheus server configured to scrape this endpoint:

```promql
histogram_quantile(0.95, sum by (le) (rate(chat_request_duration_seconds_bucket{status="2xx"}[5m])))
histogram_quantile(0.99, sum by (le) (rate(chat_request_duration_seconds_bucket{status="2xx"}[5m])))
```

For stage P95, use:

```promql
histogram_quantile(0.95, sum by (le, stage) (rate(chat_stage_duration_seconds_bucket{outcome="completed"}[5m])))
```

Histogram bounds of 1 ms to 120 seconds control bucket sizing, not request
timeouts. Examine errors and requests beyond the largest bucket separately.
Prometheus counts and sums are cumulative since startup; `/api/admin/latency` is rolling.

A few manual requests confirm instrumentation, not a reliable baseline.
Aim for at least 1,000 representative requests, and more for stable P99;
record concurrency, duration, route mix and errors. Compare cached/uncached
and cold/warmed workloads separately. Repeated questions in the same session
can hit the response cache and hide AI latency. Fresh sessions can bypass
that cache but incur provider costs and create stored messages.
Agree a load/cost budget before load testing; no paid load is generated
automatically. Measure first, identify the slow stage, optimize, then repeat
the same workload. Instrumentation itself does not reduce latency.

## What is included

- Enterprise-style dark UI
- Responsive layout
- Chat history for the active session
- Existing backend integration
- Supervisor activity visualization
- Database / RAG / Web / LLM tool cards
- Source and intent badges from `ChatResponse`
- Loading state
- Error state
- Dark/light mode
- Quick-start prompts
- HTTP Basic sign-in
- Administrator-provisioned user accounts and private conversation history
