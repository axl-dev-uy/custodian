package com.axl.custodian.core.sqlite;

import com.axl.custodian.api.IdentitySnapshot;
import com.axl.custodian.api.IdentityState;
import com.axl.custodian.api.IdentityOrigin;
import com.axl.custodian.api.PhysicalPresence;
import com.axl.custodian.api.PhysicalInstance;
import com.axl.custodian.api.ProcessEpoch;
import com.axl.custodian.core.CustodianStore;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQLite-only authority. It deliberately has no MariaDB fallback behavior. */
public final class SqliteCustodianStore implements CustodianStore {
    private final Connection connection;
    public SqliteCustodianStore(Path databaseFile) {
        try {
            Path parent = databaseFile.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
            try (Statement statement = connection.createStatement()) { statement.execute("PRAGMA foreign_keys = ON"); statement.execute("PRAGMA journal_mode = WAL"); }
            migrate();
        } catch (SQLException | IOException failure) { throw new StorageException("Unable to open SQLite authority", failure); }
    }
    private void migrate() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS identities (identity TEXT PRIMARY KEY, state TEXT NOT NULL CHECK(state IN ('PENDING','ACTIVE','QUARANTINED','REVOKED')), authority_id TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, reason TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS current_presence (identity TEXT NOT NULL REFERENCES identities(identity), server_id TEXT NOT NULL, process_epoch TEXT NOT NULL, location TEXT NOT NULL, observed_at TEXT NOT NULL, PRIMARY KEY(identity, server_id, process_epoch, location))");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS current_presence_freshness ON current_presence(identity, observed_at)");
            s.executeUpdate("INSERT OR IGNORE INTO schema_migrations(version, applied_at) VALUES (1, datetime('now'))");
        }
        if (!migrationApplied(2)) {
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("ALTER TABLE identities ADD COLUMN origin TEXT NOT NULL DEFAULT 'ADOPTED' CHECK(origin IN ('CUSTODIAN_NATIVE','ADOPTED'))");
                s.executeUpdate("INSERT INTO schema_migrations(version, applied_at) VALUES (2, datetime('now'))");
            }
        }
        if (!migrationApplied(3)) {
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("CREATE TABLE process_epochs (epoch_id TEXT PRIMARY KEY, server_id TEXT NOT NULL, started_at TEXT NOT NULL, heartbeat_at TEXT NOT NULL)");
                s.executeUpdate("INSERT OR IGNORE INTO process_epochs(epoch_id,server_id,started_at,heartbeat_at) SELECT process_epoch,server_id,MIN(observed_at),MAX(observed_at) FROM current_presence GROUP BY process_epoch,server_id");
                s.executeUpdate("CREATE TABLE current_presence_v3 (identity TEXT NOT NULL REFERENCES identities(identity), server_id TEXT NOT NULL, process_epoch TEXT NOT NULL REFERENCES process_epochs(epoch_id), physical_instance TEXT NOT NULL, location TEXT NOT NULL, observed_at TEXT NOT NULL, PRIMARY KEY(identity, process_epoch, physical_instance))");
                s.executeUpdate("INSERT INTO current_presence_v3(identity,server_id,process_epoch,physical_instance,location,observed_at) SELECT identity,server_id,process_epoch,location,location,observed_at FROM current_presence");
                s.executeUpdate("DROP TABLE current_presence"); s.executeUpdate("ALTER TABLE current_presence_v3 RENAME TO current_presence");
                s.executeUpdate("CREATE INDEX current_presence_freshness ON current_presence(identity, observed_at)");
                s.executeUpdate("INSERT INTO schema_migrations(version, applied_at) VALUES (3, datetime('now'))");
            }
        }
        if (!migrationApplied(4)) {
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("CREATE TABLE scope_owners (scope_id TEXT PRIMARY KEY, authority_id TEXT NOT NULL)");
                s.executeUpdate("INSERT INTO schema_migrations(version, applied_at) VALUES (4, datetime('now'))");
            }
        }
        if (!migrationApplied(5)) {
            try (Statement s = connection.createStatement()) { s.executeUpdate("ALTER TABLE process_epochs ADD COLUMN authority_id TEXT"); s.executeUpdate("INSERT INTO schema_migrations(version, applied_at) VALUES (5, datetime('now'))"); }
        }
        if (!migrationApplied(6)) { try(Statement s=connection.createStatement()){s.executeUpdate("CREATE TABLE bridge_epochs (epoch_id TEXT PRIMARY KEY, authority_id TEXT NOT NULL, server_id TEXT NOT NULL, started_at TEXT NOT NULL, heartbeat_at TEXT NOT NULL, active INTEGER NOT NULL)");s.executeUpdate("INSERT INTO schema_migrations(version, applied_at) VALUES (6, datetime('now'))");} }
    }
    private boolean migrationApplied(int version) throws SQLException { try (PreparedStatement q = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version=?")) { q.setInt(1, version); try (ResultSet rs = q.executeQuery()) { return rs.next(); } } }
    @Override public synchronized Optional<IdentitySnapshot> findIdentity(UUID identity) {
        try (PreparedStatement q = connection.prepareStatement("SELECT identity,state,origin,authority_id,created_at,updated_at,reason FROM identities WHERE identity=?")) {
            q.setString(1, identity.toString()); try (ResultSet rs = q.executeQuery()) { return rs.next() ? Optional.of(snapshot(rs)) : Optional.empty(); }
        } catch (SQLException failure) { throw new StorageException("Unable to read identity", failure); }
    }
    @Override public synchronized IdentitySnapshot adopt(UUID identity, String authorityId, Instant now) {
        var existing = findIdentity(identity); if (existing.isPresent()) return existing.get();
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO identities(identity,state,authority_id,created_at,updated_at,reason,origin) VALUES(?,?,?,?,?,?,?)")) {
            String timestamp = now.toString(); insert.setString(1, identity.toString()); insert.setString(2, IdentityState.ACTIVE.name()); insert.setString(3, authorityId); insert.setString(4, timestamp); insert.setString(5, timestamp); insert.setString(6, null); insert.setString(7, IdentityOrigin.ADOPTED.name()); insert.executeUpdate();
            return new IdentitySnapshot(identity, IdentityState.ACTIVE, IdentityOrigin.ADOPTED, authorityId, now, now, null);
        } catch (SQLException failure) { throw new StorageException("Unable to adopt identity", failure); }
    }
    @Override public synchronized void startEpoch(ProcessEpoch epoch) {
        String sql = "INSERT INTO process_epochs(epoch_id,server_id,started_at,heartbeat_at) VALUES(?,?,?,?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) { insert.setString(1, epoch.id().toString()); insert.setString(2, epoch.serverId()); insert.setString(3, epoch.startedAt().toString()); insert.setString(4, epoch.heartbeatAt().toString()); insert.executeUpdate(); }
        catch (SQLException failure) { throw new StorageException("Unable to start process epoch", failure); }
    }
    @Override public synchronized void heartbeat(UUID epochId, Instant observedAt) {
        try (PreparedStatement update = connection.prepareStatement("UPDATE process_epochs SET heartbeat_at=? WHERE epoch_id=?")) {
            update.setString(1, observedAt.toString()); update.setString(2, epochId.toString());
            if (update.executeUpdate() != 1) throw new IllegalArgumentException("Unknown process epoch");
        } catch (SQLException failure) { throw new StorageException("Unable to heartbeat process epoch", failure); }
    }
    @Override public synchronized void replacePresence(PhysicalPresence presence) {
        if (findIdentity(presence.identity()).isEmpty()) throw new IllegalArgumentException("Cannot observe an unknown identity");
        String sql = "INSERT INTO current_presence(identity,server_id,process_epoch,physical_instance,location,observed_at) VALUES(?,?,?,?,?,?) ON CONFLICT(identity,process_epoch,physical_instance) DO UPDATE SET location=excluded.location,observed_at=excluded.observed_at";
        try (PreparedStatement update = connection.prepareStatement(sql)) { update.setString(1, presence.identity().toString()); update.setString(2, presence.epoch().serverId()); update.setString(3, presence.epoch().id().toString()); update.setString(4, presence.instance().id()); update.setString(5, presence.location()); update.setString(6, presence.observedAt().toString()); update.executeUpdate(); }
        catch (SQLException failure) { throw new StorageException("Unable to replace presence", failure); }
    }
    @Override public synchronized void closePresence(UUID identity, UUID epochId, PhysicalInstance instance) {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM current_presence WHERE identity=? AND process_epoch=? AND physical_instance=?")) {
            delete.setString(1, identity.toString()); delete.setString(2, epochId.toString()); delete.setString(3, instance.id()); delete.executeUpdate();
        } catch (SQLException failure) { throw new StorageException("Unable to close presence", failure); }
    }
    @Override public synchronized void movePresence(PhysicalPresence presence, PhysicalInstance previous) {
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM current_presence WHERE identity=? AND process_epoch=? AND physical_instance=?");
                 PreparedStatement insert = connection.prepareStatement("INSERT INTO current_presence(identity,server_id,process_epoch,physical_instance,location,observed_at) VALUES(?,?,?,?,?,?) ON CONFLICT(identity,process_epoch,physical_instance) DO UPDATE SET location=excluded.location,observed_at=excluded.observed_at")) {
                delete.setString(1, presence.identity().toString()); delete.setString(2, presence.epoch().id().toString()); delete.setString(3, previous.id()); delete.executeUpdate();
                insert.setString(1, presence.identity().toString()); insert.setString(2, presence.epoch().serverId()); insert.setString(3, presence.epoch().id().toString()); insert.setString(4, presence.instance().id()); insert.setString(5, presence.location()); insert.setString(6, presence.observedAt().toString()); insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException failure) {
            try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
            throw new StorageException("Unable to atomically move presence", failure);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException failure) { throw new StorageException("Unable to restore SQLite transaction mode", failure); }
        }
    }
    @Override public synchronized void reconcileScope(ProcessEpoch epoch, String scopeId, List<PhysicalPresence> presences) {
        if (presences.stream().anyMatch(p -> !p.epoch().id().equals(epoch.id()) || !p.instance().id().startsWith(scopeId + ":"))) throw new IllegalArgumentException("Presence is outside reconciliation scope");
        if (!epochFresh(epoch.id(), epoch.heartbeatAt())) throw new IllegalArgumentException("Inactive process epoch");
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM current_presence WHERE process_epoch=? AND physical_instance LIKE ?");
                 PreparedStatement insert = connection.prepareStatement("INSERT INTO current_presence(identity,server_id,process_epoch,physical_instance,location,observed_at) VALUES(?,?,?,?,?,?)")) {
                delete.setString(1, epoch.id().toString()); delete.setString(2, scopeId + ":%"); delete.executeUpdate();
                for (PhysicalPresence presence : presences) { insert.setString(1, presence.identity().toString()); insert.setString(2, epoch.serverId()); insert.setString(3, epoch.id().toString()); insert.setString(4, presence.instance().id()); insert.setString(5, presence.location()); insert.setString(6, presence.observedAt().toString()); insert.addBatch(); }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException failure) {
            try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
            throw new StorageException("Unable to atomically reconcile presence scope", failure);
        } finally { try { connection.setAutoCommit(true); } catch (SQLException failure) { throw new StorageException("Unable to restore SQLite transaction mode", failure); } }
    }
    @Override public synchronized boolean epochFresh(UUID epochId, Instant freshAfter) {
        try (PreparedStatement q = connection.prepareStatement("SELECT 1 FROM process_epochs WHERE epoch_id=? AND heartbeat_at>=?")) {
            q.setString(1, epochId.toString()); q.setString(2, freshAfter.toString()); try (ResultSet rs = q.executeQuery()) { return rs.next(); }
        } catch (SQLException failure) { throw new StorageException("Unable to check epoch freshness", failure); }
    }
    @Override public synchronized void registerScope(String authorityId, String scopeId) {
        try (PreparedStatement q = connection.prepareStatement("SELECT authority_id FROM scope_owners WHERE scope_id=?")) {
            q.setString(1, scopeId); try (ResultSet rs=q.executeQuery()) { if(rs.next() && !authorityId.equals(rs.getString(1))) throw new IllegalArgumentException("Scope is owned by another authority"); }
        } catch(SQLException e){throw new StorageException("Unable to read scope owner",e);}
        try (PreparedStatement i=connection.prepareStatement("INSERT OR IGNORE INTO scope_owners(scope_id,authority_id) VALUES(?,?)")){i.setString(1,scopeId);i.setString(2,authorityId);i.executeUpdate();}catch(SQLException e){throw new StorageException("Unable to register scope",e);}
    }
    @Override public synchronized void bindEpoch(String authorityId, ProcessEpoch epoch) {
        startEpoch(epoch);
        try (PreparedStatement u=connection.prepareStatement("UPDATE process_epochs SET authority_id=? WHERE epoch_id=?")){u.setString(1,authorityId);u.setString(2,epoch.id().toString());u.executeUpdate();}catch(SQLException e){throw new StorageException("Unable to bind epoch",e);}
    }
    @Override public synchronized Optional<String> scopeOwner(String scopeId) { try(PreparedStatement q=connection.prepareStatement("SELECT authority_id FROM scope_owners WHERE scope_id=?")){q.setString(1,scopeId);try(ResultSet r=q.executeQuery()){return r.next()?Optional.of(r.getString(1)):Optional.empty();}}catch(SQLException e){throw new StorageException("Unable to read scope owner",e);} }
    @Override public synchronized Optional<String> epochOwner(UUID epochId) { try(PreparedStatement q=connection.prepareStatement("SELECT authority_id FROM process_epochs WHERE epoch_id=?")){q.setString(1,epochId.toString());try(ResultSet r=q.executeQuery()){return r.next()&&r.getString(1)!=null?Optional.of(r.getString(1)):Optional.empty();}}catch(SQLException e){throw new StorageException("Unable to read epoch owner",e);} }
    @Override public synchronized void startBridge(String a, ProcessEpoch e){try(PreparedStatement p=connection.prepareStatement("INSERT INTO bridge_epochs VALUES(?,?,?,?,?,1)")){p.setString(1,e.id().toString());p.setString(2,a);p.setString(3,e.serverId());p.setString(4,e.startedAt().toString());p.setString(5,e.heartbeatAt().toString());p.executeUpdate();}catch(SQLException x){throw new StorageException("Unable to start bridge",x);}}
    @Override public synchronized void heartbeatBridge(UUID e,Instant t){try(PreparedStatement p=connection.prepareStatement("UPDATE bridge_epochs SET heartbeat_at=? WHERE epoch_id=? AND active=1")){p.setString(1,t.toString());p.setString(2,e.toString());if(p.executeUpdate()!=1)throw new IllegalArgumentException("Inactive bridge epoch");}catch(SQLException x){throw new StorageException("Unable to heartbeat bridge",x);}}
    @Override public synchronized void invalidateBridge(UUID e){try(PreparedStatement p=connection.prepareStatement("UPDATE bridge_epochs SET active=0 WHERE epoch_id=?")){p.setString(1,e.toString());p.executeUpdate();}catch(SQLException x){throw new StorageException("Unable to invalidate bridge",x);}}
    @Override public synchronized Optional<com.axl.custodian.core.BridgeLifecycle> bridge(UUID e){try(PreparedStatement p=connection.prepareStatement("SELECT authority_id,server_id,started_at,heartbeat_at,active FROM bridge_epochs WHERE epoch_id=?")){p.setString(1,e.toString());try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(new com.axl.custodian.core.BridgeLifecycle(r.getString(1),e,r.getString(2),Instant.parse(r.getString(3)),Instant.parse(r.getString(4)),r.getInt(5)!=0)):Optional.empty();}}catch(SQLException x){throw new StorageException("Unable to read bridge",x);}}
    @Override public synchronized void releaseAuthority(String authorityId) {
        try { connection.setAutoCommit(false); try (PreparedStatement p=connection.prepareStatement("DELETE FROM current_presence WHERE physical_instance LIKE ?"); PreparedStatement q=connection.prepareStatement("SELECT scope_id FROM scope_owners WHERE authority_id=?"); PreparedStatement d=connection.prepareStatement("DELETE FROM scope_owners WHERE authority_id=?")){q.setString(1,authorityId);try(ResultSet r=q.executeQuery()){while(r.next()){p.setString(1,r.getString(1)+":%");p.addBatch();}}p.executeBatch();d.setString(1,authorityId);d.executeUpdate();} connection.commit(); }catch(SQLException e){try{connection.rollback();}catch(SQLException x){e.addSuppressed(x);}throw new StorageException("Unable to release authority",e);}finally{try{connection.setAutoCommit(true);}catch(SQLException e){throw new StorageException("Unable to restore transaction mode",e);}}
    }
    @Override public synchronized List<PhysicalPresence> activePresences(UUID identity, Instant freshAfter) {
        try (PreparedStatement q = connection.prepareStatement("SELECT p.server_id,p.process_epoch,p.physical_instance,p.location,p.observed_at,e.started_at,e.heartbeat_at FROM current_presence p JOIN process_epochs e ON e.epoch_id=p.process_epoch WHERE p.identity=? AND p.observed_at>=? AND e.heartbeat_at>=?")) {
            q.setString(1, identity.toString()); q.setString(2, freshAfter.toString()); q.setString(3, freshAfter.toString()); try (ResultSet rs = q.executeQuery()) { List<PhysicalPresence> result = new ArrayList<>(); while (rs.next()) { ProcessEpoch epoch = new ProcessEpoch(UUID.fromString(rs.getString(2)), rs.getString(1), Instant.parse(rs.getString(6)), Instant.parse(rs.getString(7))); result.add(new PhysicalPresence(identity, epoch, new PhysicalInstance(rs.getString(3)), rs.getString(4), Instant.parse(rs.getString(5)))); } return List.copyOf(result); }
        } catch (SQLException failure) { throw new StorageException("Unable to list presence", failure); }
    }
    @Override public synchronized void removeEpoch(UUID epoch) {
        try (PreparedStatement presences = connection.prepareStatement("DELETE FROM current_presence WHERE process_epoch=?"); PreparedStatement epochDelete = connection.prepareStatement("DELETE FROM process_epochs WHERE epoch_id=?")) {
            presences.setString(1, epoch.toString()); presences.executeUpdate(); epochDelete.setString(1, epoch.toString()); epochDelete.executeUpdate();
        } catch (SQLException failure) { throw new StorageException("Unable to remove epoch", failure); }
    }
    private static IdentitySnapshot snapshot(ResultSet rs) throws SQLException { return new IdentitySnapshot(UUID.fromString(rs.getString("identity")), IdentityState.valueOf(rs.getString("state")), IdentityOrigin.valueOf(rs.getString("origin")), rs.getString("authority_id"), Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at")), rs.getString("reason")); }
    @Override public synchronized void close() { try { connection.close(); } catch (SQLException failure) { throw new StorageException("Unable to close SQLite authority", failure); } }
}
