## 1. Implementation

- [ ] 1.1 `HealthController` (`src/main/kotlin/ch/fasteque/sdd_demo/HealthController.kt`) already exists with a `GET /health` handler but returns `{"status" to "ok"}`; update it to return `{"status" to "UP"}` to match the spec

## 2. Tests

- [ ] 2.1 Add a MockMvc test verifying `GET /health` returns 200 and the expected JSON body, co-located under `src/test/kotlin` mirroring the controller's package
