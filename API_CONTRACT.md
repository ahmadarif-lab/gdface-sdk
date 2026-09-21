# Model authorization API

The GdFace SDK does not ship its `.csta` model files (~170 MB in total) inside the
AAR. On the first `init()` on a device, `GdFaceModelProvider` asks an authorization
endpoint for short-lived download links, downloads the five model files, verifies their
SHA-256 and caches them. After that the SDK works offline.

The hosted service run by GreatDay implements this contract and is the default
(`GdFaceConfig.AUTHORIZE_URL`). The contract is documented so you can also point
`authorizeUrl` at a backend of your own.

## 1. Authorize

```
POST https://gdsupport.greatdayhr.com/api/sdk/gdface/authorize
```

### Request

| Header | Required | Notes |
|---|---|---|
| `X-API-Key` | yes | API key. The free public key is in `GdFaceConfig.PUBLIC_API_KEY`. |
| `Content-Type` | yes | `application/json` |

```json
{
  "packageId": "com.example.myapp",
  "sdkVersion": "0.2.0",
  "modelVariant": "full"
}
```

- `packageId` is `context.packageName` of the calling app, sent as is. It must look like
  an Android application ID (`com.example.app`), otherwise the request is rejected.
- `sdkVersion` is informational and is logged.
- `modelVariant` is currently always `"full"`.

### 200 – authorized

```json
{
  "authorized": true,
  "expiresAt": "2026-09-21T02:51:11.000Z",
  "models": [
    { "name": "face_detector.csta",        "url": "https://...", "sha256": "<hex64>", "sizeBytes": 4054932 },
    { "name": "face_recognizer.csta",      "url": "https://...", "sha256": "<hex64>", "sizeBytes": 102551956 },
    { "name": "face_landmarker_pts5.csta", "url": "https://...", "sha256": "<hex64>", "sizeBytes": 421988 },
    { "name": "fas_first.csta",            "url": "https://...", "sha256": "<hex64>", "sizeBytes": 44729700 },
    { "name": "fas_second.csta",           "url": "https://...", "sha256": "<hex64>", "sizeBytes": 19102340 }
  ]
}
```

All five entries must be present with exactly these names (`GdFaceModelProvider.REQUIRED_MODEL_NAMES`);
the client treats a response that lacks one as `MalformedResponse`.

A sixth, optional entry is used by mask detection (`GdFaceEngine.initMaskDetection()`):

```json
{ "name": "mask_detector.csta", "url": "https://...", "sha256": "<hex64>", "sizeBytes": 938356 }
```

It is not among the required models, so a backend that does not list it keeps working for
everything else. It is only asked for by `initMaskDetection()`, which fails with
`MalformedResponse` when the entry is missing. Whatever a response lists is downloaded and
verified like the other models, so once the entry is there a new device fetches it during
its first `init()` too (0.9 MB). A device that already has the five models sends one more
authorization request, once, the first time `initMaskDetection()` is called.

- `url`: a signed, short-lived link (30 minutes on the hosted service). The client
  issues a plain `GET`, no `X-API-Key` header.
- `sha256`: hex digest of the file. The client deletes the file and throws
  `ChecksumMismatch` if it does not match after download.
- `sizeBytes`: used to validate the local cache on later launches.
- `expiresAt`: informational.

A `200` with `"authorized": false` is also understood by the client.

### Errors

Errors from the authorization logic have the body `{ "authorized": false, "reason": "<REASON>" }`. The rate limit (`429`) is enforced before that logic and answers with a generic body that carries the wait time in seconds as `retryAfter`; no `Retry-After` header is sent.

| HTTP | `reason` | When | Client exception |
|---|---|---|---|
| 400 | `INVALID_REQUEST` | `packageId` missing or malformed | `ServerError` |
| 401 | `INVALID_API_KEY` | key missing or unknown | `InvalidApiKey` |
| 401 | `KEY_REVOKED` | key was revoked | `InvalidApiKey` |
| 401 | `KEY_EXPIRED` | key passed its expiry date | `InvalidApiKey` |
| 403 | `PACKAGE_NOT_AUTHORIZED` | restricted key used from a package that is not on its list | `PackageNotAuthorized` |
| 429 | (rate limit) | more than 60 requests per minute from one IP | `RateLimited` |
| 503 | `MODELS_UNAVAILABLE` / `SERVER_MISCONFIGURED` | server side problem | `ServerError` |

## 2. Download

The client downloads each `url` with a plain `GET` and reads the raw body (the binary
`.csta` file). The hosted service serves them with `Accept-Ranges` and answers `206` to
range requests; the current client always downloads from the start.

## 3. Key types and usage logging

- **Restricted key**: valid only for the package IDs on its list. A valid key used from
  any other package is rejected with `403`.
- **Public key**: accepts any well-formed package ID. This is the key that ships with the
  open SDK and is free for everyone.

Every authorization attempt, accepted or rejected, is logged with the package ID, SDK
version, result and IP. The hosted service aggregates that log to see how many apps use
the SDK. The SDK only contacts the server when its local model cache is empty or invalid
(first run, cleared data, model update), so the numbers measure new installs, not daily
active use. Package IDs are reported by the client and are not verified.

## 4. Implementing your own backend

If you host the models yourself:

1. Validate `X-API-Key` and, if you use restricted keys, the `(key, packageId)` pair.
2. Return the five models with accurate `sha256` and `sizeBytes`; compute the hash once
   at upload time rather than on every request.
3. Serve short-lived signed URLs so a leaked link is not usable forever.
4. Rate limit generously: a device makes one authorization call and five downloads on
   its first `init()`, and none afterwards.
