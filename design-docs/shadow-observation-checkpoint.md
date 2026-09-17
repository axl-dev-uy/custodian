# Authorized shadow observation checkpoint

## Completed Custodian work

- Java 17 API/core/Paper modules and SQLite migrations through bridge lifecycle schema version 6.
- Immutable identity, presence, process-epoch, physical-instance, and duplicate-assessment contracts.
- SQLite identity adoption as `ADOPTED`, presence freshness, transactional movement/reconciliation, and authorization records for scopes and epochs.
- Built-in Paper observation boundaries for native inventories and drops; native scopes use the built-in authority while unbound virtual scopes are excluded.
- Bridge lifecycle persistence atomically binds bridge and process epochs to their authority, advances monotonic heartbeats, supersedes stale epochs, and invalidates bridge state during authority release.
- Migration 6 constrains bridge activity and supports one active bridge per authority/server while retaining durable identities and unrelated scope ownership.
- `ShadowContributor` is registered as a Paper service using in-memory opaque handles backed by persisted bridge epochs.
- The Paper service translates opaque public epoch tokens to private persisted epochs and accepts only `PARTIAL` scope contributions.
- Forged, foreign, stale, ended, and shutdown-invalidated handles are rejected before contribution writes.

## Checks actually run

- The complete core suite passed against fresh SQLite databases and an explicit version-5-to-version-6 migration fixture.
- API isolation, all core tests, all Paper tests, and the Paper assembly passed together: 32 tests, zero failures.
- Exact verification command:

  ```text
  GRADLE_USER_HOME=/tmp/gradle-home /home/axl/.gradle/wrapper/dists/gradle-9.3.1-bin/23ovyewtku6u96viwx3xl3oks/gradle-9.3.1/bin/gradle --no-daemon :custodian-api:test :custodian-core:test :custodian-paper:test :custodian-paper:assemble --rerun-tasks --console=plain
  ```

## Unverified / pending

The InfinityGear settled shadow scanner is not implemented or wired. No InfinityGear changes are included in this checkpoint.

## Exact next slice

Wire InfinityGear's post-debounce scanner to one bridge epoch. Derive stable native scope and physical-instance IDs, submit settled identity subsets as `PARTIAL`, and log comparison assessments only. Do not mutate PDC/profile data or change legacy deduplication/enforcement behavior.
