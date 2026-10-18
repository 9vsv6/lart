# Bridge protocol

The compat layer runs as a child process and talks to the desktop app over
stdin/stdout, one JSON object per line. Source: `src/host/BridgeServer.kt` and
`src/host/BridgeProtocol.kt`; the desktop side is `src-tauri/src/capstan.rs`.

## Frames

Request:

```json
{ "id": "7", "method": "search", "params": { "providerId": "...", "query": "..." } }
```

`params` is optional. `params.timeoutMs` bounds one call (1 s to 600 s,
default 130 s).

Answer:

```json
{ "id": "7", "ok": true, "result": { } }
{ "id": "7", "ok": false, "error": { "code": "provider_not_found", "message": "..." } }
```

Requests are answered concurrently, so answers can arrive out of order.

## Methods

| Method          | Params                                                             | Result                                   |
| --------------- | ------------------------------------------------------------------ | ---------------------------------------- |
| `ping`          | none                                                               | bridge status                            |
| `install`       | `path`                                                             | the installed extension                  |
| `uninstall`     | `id`                                                               | confirmation                             |
| `extensions`    | none                                                               | installed extensions                     |
| `providers`     | none                                                               | providers the extensions expose          |
| `catalogue`     | `providerId`                                                       | the provider's catalogue rows            |
| `cataloguePage` | `providerId`, `row`, `page` (default 1)                            | one page of a row, addressed by row name |
| `search`        | `providerId`, `query`, `page` (default 1), `quick` (default false) | matching items                           |
| `load`          | `providerId`, `url`                                                | the item's details                       |
| `loadLinks`     | `providerId`, `data`, `isCasting` (default false)                  | playable links                           |
| `shutdown`      | none                                                               | confirmation; the process exits          |

## Error codes

`bad_request`, `unknown_method`, `provider_not_found`, `extension_not_found`,
`install_failed`, `extension_error`, `timeout`.
