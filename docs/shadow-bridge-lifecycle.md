# Shadow bridge lifecycle

Custodian exposes `ShadowContributor` as an observational Paper service. A bridge can start one
persisted epoch, heartbeat it, and submit `ScopeContribution` values. Contributions remain
`PARTIAL`: the bridge cannot register or own native scopes, submit `COMPLETE` reconciliation,
release authority, or mutate an adopted identity.

## Settled snapshot generations

A settled scanner uses one `observedAt` value for every scope examined in one scan. For a given
bridge epoch and identity:

- contributions with the same observation time accumulate as one generation, so distinct items
  in separate scopes still confirm a duplicate;
- the first contribution with a newer observation time atomically retires that bridge epoch's
  older observations for the identity;
- exact retries are idempotent and older generations are rejected;
- native epochs, other bridge epochs, durable identities, and unrelated scope ownership are not
  changed.

This prevents a legitimate move such as inventory to drop from appearing as both the old and new
physical instance for the freshness interval, without giving the contributor absence authority
over a native scope.

## Invalidation and restart

Invalidating a bridge or superseding it with a newer epoch atomically retires its current presence
rows. Active-presence reads also exclude inactive bridge epochs. A clean shutdown or rapid restart
therefore cannot retain a fresh ghost observation from the old bridge handle. Durable identity and
lifecycle records remain available.

This behavior uses the existing schema version 6; no migration change is required.

## Native double chests

Double-chest scope IDs are derived from the two actual `BlockState` locations, not the combined
inventory location exposed by Paper. The two locations are sorted before encoding, so opening
either half produces the same scope with two distinct coordinates.

## Verification

The full Custodian API, core, SQLite, and Paper suite passed with 37 tests and zero failures:

```text
GRADLE_USER_HOME=/tmp/custodian-gradle \
  /home/axl/.gradle/wrapper/dists/gradle-9.6.1-bin/4ticwg1pgcbps2hj28r8so764/gradle-9.6.1/bin/gradle \
  test :custodian-paper:jar --rerun-tasks --console=plain
```

Live Paper 26.2 fixture checks confirmed agreement for inventory, Ender Chest, drops, block and
double-chest containers, chest minecarts, real duplicates, duplicate removal, settled movement,
and a rapid restart followed by immediate movement.
