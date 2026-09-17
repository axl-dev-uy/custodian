# Public API layout

Custodian v1 exposes exactly two service interfaces in `com.axl.custodian.api`:

- `CustodianApi` for native identity validation, adoption, observation, and epoch lifecycle.
- `ShadowContributor` for opt-in observational bridge contributions. Its contract is PARTIAL-only
  and cannot express COMPLETE reconciliation or scope ownership.

Public DTOs are grouped by domain:

- `com.axl.custodian.api.identity`: authority handles plus identity lifecycle and validation values.
- `com.axl.custodian.api.presence`: physical observations, epochs, scopes, reconciliation, and
  duplicate assessments.
- `com.axl.custodian.api.shadow`: opaque bridge handles and shadow scope contributions.

Consumers must depend only on `custodian-api` and these public packages. `custodian-core` and
`custodian-paper` are implementation modules; their storage, Paper observation, service registry,
and bridge classes are not integration contracts.
