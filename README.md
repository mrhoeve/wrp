# Websiteregister Rijksoverheid Parser

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=mrhoeve_wrp&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=mrhoeve_wrp)

WRP downloads the Websiteregister published by the Dutch government, parses the available ODS columns, and exposes the result as JSON over an unchanged REST interface.

## REST API

### `GET /registerdata`

Returns every register row as a JSON object. Column names are derived from the current ODS document; WRP does not depend on a fixed set or order of columns.

```json
[
  {
    "URL": "https://www.rijksoverheid.nl",
    "Organisatietype": "Rijksoverheid",
    "Organisatie": "AZ",
    "Websitetest Totaal": "ja",
    "Websitetest Testdatum": "21-06-2022",
    "E-mailtest Totaal": "ja",
    "E-mailtest Testdatum": "14-07-2022"
  }
]
```

WRP applies deterministic names when the source needs disambiguation:

- duplicate column names receive their ODS section prefix, such as `Websitetest Totaal` and `E-mailtest Totaal`;
- remaining duplicate names receive a numeric suffix, such as `Status (2)`;
- an empty header inside the populated range becomes `Kolom N`;
- a missing cell value is returned as an empty string.

### `GET /metadata`

Returns information about the active register and the exact effective column names used by `/registerdata`.

```json
{
  "documentURL": "https://www.communicatierijk.nl/path/to/websiteregister.ods",
  "discoveryDateTimeUTC": "2026-08-28T12:34:56.789Z",
  "registersFound": 1659,
  "columnHeaders": [
    "URL",
    "Organisatietype",
    "Organisatie",
    "Websitetest Totaal",
    "Websitetest Testdatum",
    "E-mailtest Totaal",
    "E-mailtest Testdatum"
  ]
}
```

### `GET /checkfornew`

Immediately checks the configured source page for a new ODS URL and returns `OK`. WRP also performs this check every whole hour.

The new file is downloaded and parsed before it becomes active. If downloading or parsing fails, WRP keeps the last valid register, removes the failed temporary file, skips the callback, and retries the URL during a later check.

An invalid `resourceurl` prevents startup. If a valid source is temporarily unavailable during startup, WRP remains running and retries on a later scheduled or manual check. Until the first register is loaded, the register endpoints cannot return data while `/health` still reports the process status as described below.

### `GET /health`

Returns `UP` with HTTP 200 while the service is running and `DOWN` with HTTP 503 otherwise. This endpoint intentionally reports process health only; it does not indicate whether a register is currently loaded.

### Actuator

- `GET /actuator/health` returns Spring Boot health information.
- `GET /actuator/info` returns application information.
- `GET /actuator/sbom/application` returns the generated CycloneDX Software Bill of Materials.

## Callback

When `callbackurl` is configured, WRP performs a GET request after a new register has been downloaded and parsed successfully. `callbackparameter` is appended as a raw query string, preserving query parameters already present in the callback URL.

Example:

```text
callbackurl=https://consumer.example/updated?source=wrp
callbackparameter=token=xyz
```

The resulting request is sent to:

```text
https://consumer.example/updated?source=wrp&token=xyz
```

Callback failures are logged but do not invalidate an already loaded register. Callback URLs, parameters, and response bodies are not written to the application log.

## Configuration

All existing configuration names remain supported.

| Property | Default | Description |
| --- | --- | --- |
| `resourceurl` | `https://www.communicatierijk.nl/documenten/2016/05/26/websiteregister` | Absolute HTTP(S) page containing a link to the ODS file. Relative, root-relative, protocol-relative and absolute ODS links are supported. |
| `callbackurl` | empty | Optional HTTP(S) endpoint called after successful activation of a new register. |
| `callbackparameter` | empty | Optional raw query string appended to `callbackurl`. |
| `cacheduration` | `15` | Positive whole-number cache duration. Invalid, zero, or negative values fall back to `15`. |
| `cachetimeunit` | `MINUTES` | `SECONDS`, `MINUTES`, `HOURS`, or `DAYS`; invalid values fall back to `MINUTES`. |
| `httpconnecttimeout` | `10s` | Maximum time for establishing an outbound connection. |
| `httpreadtimeout` | `60s` | Maximum wait for data from the source or callback. |
| `httpmaxdownloadsize` | `256MB` | Maximum downloaded ODS size. |
| `odsmaxuncompressedsize` | `256MB` | Maximum uncompressed size of `content.xml` inside the ODS archive. |
| `odsmaxrows` | `100000` | Maximum register rows; configurable up to the ODS limit of `1048576`. |

Timeout values use Spring Boot duration notation such as `500ms`, `10s`, or `2m`. Size values use data-size notation such as `10MB` or `1GB`. Timeout and size values must be greater than zero.

The cache expires after the configured period without access. When an entry expires, WRP recreates its JSON representation from the active temporary ODS file. Concurrent cache misses share one reload operation.

## ODS input handling

WRP parses the columns present in the first ODS spreadsheet table. It supports up to the ODS spreadsheet limit of 16,384 columns and does not prescribe a fixed schema.

The parser disables DTDs and external XML entities and rejects:

- missing or oversized `content.xml` data;
- invalid declared row counts;
- excessive row, column, or text-space repetition values;
- documents exceeding the configured download, expansion, or row limits.

## Running with Docker

```yaml
services:
  wrp:
    image: mrhoeve/wrp:latest
    ports:
      - "8080:8080"
    environment:
      resourceurl: https://resource.example/page-containing-the-ods-link
      callbackurl: https://consumer.example/updated
      callbackparameter: token=xyz
      cacheduration: 15
      cachetimeunit: MINUTES
      httpconnecttimeout: 10s
      httpreadtimeout: 60s
      httpmaxdownloadsize: 256MB
      odsmaxuncompressedsize: 256MB
      odsmaxrows: 100000
```

Start the service with:

```shell
docker compose up --build
```

The container build runs the same full Maven verification as CI and then copies only the application JAR into a non-root Java 21 runtime image.

## Building and testing

WRP targets Java 21. Use the Maven wrapper to run the complete build:

```shell
./mvnw clean verify
```

The build runs unit, parser, HTTP, concurrency, REST-contract, and full Spring application tests. It also generates the CycloneDX SBOM and enforces at least 90% line coverage and 70% branch coverage.

## License

See [LICENSE.md](LICENSE.md).
