# Retraction Watch mirror (device side)

The `retractions` table (see migration 0017) is populated server-side by
`scripts/ingest-retraction-watch.mjs`. On the device, `RetractionCheck`
queries it through the existing PostgREST client and tags any claim whose
evidence set cites a retracted source, so it lands in the moderation queue.

## Usage from Kotlin

```kotlin
val check = RetractionCheck(postgRestClient)
val retracted = check.retractedDois(listOf("10.1038/ex1", "10.1101/y"))
if (retracted.isNotEmpty()) {
    // flag via POST /api/v1/moderation/flags
}
```
