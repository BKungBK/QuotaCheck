# Private Quota API Contract

## Decision

`FEASIBLE`

The authorized legacy cloud adapter uses normal HTTPS for token exchange,
project discovery, and quota requests. The primary response exposes a stable
bucket ID, billing-window label, remaining fraction, and reset time. Absolute
usage units are not exposed by the observed schema and must remain `null`.

This is an owner-accepted, unofficial compatibility integration for a
single-owner, sideload-only APK. Provider behavior and private endpoints may
change without notice.

## Authentication Exchange

### Request

- Method: `POST`
- HTTPS origin: `https://oauth2.googleapis.com`
- Path: `/token`
- Content type: `application/x-www-form-urlencoded`
- Request fields:
  - `client_id`: static compatibility value supplied at build time through a
    gitignored local property
  - `client_secret`: static compatibility value supplied the same way
  - `refresh_token`: owner-provided secret read from the encrypted credential
    vault
  - `grant_type`: literal `refresh_token`

Static OAuth values are intentionally represented only by placeholders in
this contract and are not present in fixtures.

### Response and expiry

- Required response field: `access_token` as a non-empty string.
- Unknown response fields are ignored.
- Access tokens remain in memory and are never persisted.
- The legacy compatibility behavior treats a newly exchanged token as valid
  for 55 minutes. If an explicit stored expiry is available, a token is reused
  only while more than five minutes remain.
- A malformed success body is `SchemaMismatch`. A rejected credential is
  `AuthRequired`. Response bodies must not be logged or surfaced to UI.

## Project Discovery

Project discovery is needed only when the quota request does not already have
an owner project ID.

### Primary discovery

- Method: `POST`
- HTTPS origin: `https://cloudcode-pa.googleapis.com`
- Path: `/v1internal:loadCodeAssist`
- Headers:
  - `Authorization: Bearer <access-token>`
  - `User-Agent: antigravity/1.104.0 windows/amd64`
  - `Client-Metadata: {"ideType":"ANTIGRAVITY","platform":"WINDOWS","pluginType":"GEMINI"}`
  - `Content-Type: application/json`
- JSON body:

```json
{
  "metadata": {
    "ideType": "ANTIGRAVITY",
    "platform": "WINDOWS",
    "pluginType": "GEMINI"
  }
}
```

- Project field: `cloudaicompanionProject` as a string.

### Compatibility fallback

- Method: `GET`
- HTTPS origin: `https://cloudresourcemanager.googleapis.com`
- Path: `/v1/projects`
- Header: `Authorization: Bearer <access-token>`
- Candidate fields: `projects[].projectId` and `projects[].labels`

Project IDs are account-scoped and must never be logged, committed, or used in
fixtures.

## Quota Request

### Primary request

- Method: `POST`
- HTTPS origin: `https://cloudcode-pa.googleapis.com`
- Path: `/v1internal:retrieveUserQuotaSummary`
- Headers:
  - `Authorization: Bearer <access-token>`
  - `User-Agent: antigravity/1.104.0 windows/amd64`
  - `Client-Metadata: {"ideType":"ANTIGRAVITY","platform":"WINDOWS","pluginType":"GEMINI"}`
  - `Content-Type: application/json`
- JSON body: `{"project":"<owner-project-id>"}` when known, otherwise `{}`.

Accepted response containers are:

- top-level `groups` and `pools`
- `userQuotaSummary.groups` and `userQuotaSummary.pools`
- `response.groups` and `response.pools`

The primary group/bucket field map is:

| Provider field | Type | Domain meaning |
| --- | --- | --- |
| `groups[].displayName` | nullable string | group display-name fallback |
| `groups[].buckets[].bucketId` | nullable string | stable pool ID; required for persistence |
| `groups[].buckets[].displayName` | nullable string | preferred display name |
| `groups[].buckets[].window` | nullable string | billing-window label |
| `groups[].buckets[].remainingFraction` | nullable number | remaining quota, valid range `0.0..1.0` |
| `groups[].buckets[].resetTime` | nullable string | cycle end/reset instant |
| `groups[].buckets[].disabled` | nullable boolean | disabled pools are excluded |
| `pools[].label` | nullable string | legacy display-name fallback |
| `pools[].remainingFraction` | nullable number | legacy remaining quota |
| `pools[].resetTime` | nullable string | legacy cycle end/reset instant |

For current persisted data, `bucketId` is the stable identifier. A bucket
without a non-empty ID is rejected rather than assigned an invented identity.
Display name falls back from bucket display name to group display name and
then bucket ID. `window` and `resetTime` describe the billing cycle. Cycle
start and absolute total/used/remaining units are unavailable and remain
`null`.

### Model-map fallback

- Method: `POST`
- HTTPS origin: `https://cloudcode-pa.googleapis.com`
- Path: `/v1internal:fetchAvailableModels`
- Headers and JSON body: identical to the primary quota request.
- Response map: `models`.
- Stable pool ID: each `models` object key.
- Fields:
  - `models.<id>.displayName`: nullable string
  - `models.<id>.quotaInfo.remainingFraction`: nullable number
  - `models.<id>.quotaInfo.resetTime`: nullable string

The fallback is used only when the primary response has no valid pools.

## Error Classification

| Condition | Domain error | Behavior |
| --- | --- | --- |
| HTTP 401 or 403 | `AuthRequired` | stop retrying and request credential replacement |
| HTTP 429 | `RateLimited` | retry with WorkManager backoff |
| HTTP 500-599 | `Retryable` | retain cached data and retry with backoff |
| DNS, timeout, or other network I/O | `Retryable` | retain cached data and retry |
| Invalid JSON, wrong field type, missing stable ID, out-of-range fraction, or invalid reset instant | `SchemaMismatch` | retain cached data and do not persist partial response |
| Other HTTP 400-499 | `NonRetryable` | retain cached data and show sanitized failure |

HTTP status is authoritative. Error response payloads are treated as opaque
and must not be logged or displayed.

## Safety

- No live token, cookie, account identifier, authorization value, OAuth static
  value, or raw private response is retained.
- All remote calls use the platform's normal TLS validation. No certificate
  override, TLS interception, or pinning bypass is permitted.
- The legacy local-loopback invalid-certificate path is outside this contract
  and must not be ported.
- The owner accepts that static OAuth compatibility metadata and compatibility
  headers can be extracted from the APK. Build-time indirection and R8 are not
  security boundaries.
- The pasted refresh token is encrypted with Android Keystore. Access tokens
  remain memory-only.
- Logging is limited to sanitized status/category metadata. Raw success and
  error bodies are prohibited.

## Synthetic Fixture Rules

Fixtures preserve contract field names and JSON types while using synthetic
IDs, names, timestamps, and messages. Error bodies are representative only;
the adapter must classify by HTTP status rather than depend on their shape.

## Evidence Anchors

Only the narrowly authorized legacy HTTP contract was inspected:

- `src-tauri/src/config.rs:15-16`: credential/config inputs
- `src-tauri/src/quota_client.rs:27-49`: token and model-map DTO fields
- `src-tauri/src/quota_client.rs:160-203`: primary summary DTO fields
- `src-tauri/src/quota_client.rs:1041-1071`: token exchange and expiry behavior
- `src-tauri/src/quota_client.rs:1076-1138`: project discovery
- `src-tauri/src/quota_client.rs:1146-1202`: primary and fallback quota requests
- `src-tauri/src/lib.rs:305-308`: normal-TLS cloud client
- `src-tauri/src/lib.rs:310-313`: excluded invalid-certificate local client

