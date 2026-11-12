import java.sql.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/** 8-9/8-10：只操作独立answer8_test库中的answer8_*表。 */
public class MySqlExperiment {
    static final String URL = System.getenv("ANSWER_DB_URL");
    static final String USER = System.getenv("ANSWER_DB_USER");
    static final String PASSWORD = System.getenv("ANSWER_DB_PASSWORD");
    static Connection connection() throws SQLException {
        Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
        c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return c;
    }
    static void execute(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) { s.execute(sql); }
    }
    static long scalar(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            if (!r.next()) throw new AssertionError("missing result");
            return r.getLong(1);
        }
    }
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static void setup(Connection c) throws SQLException {
        execute(c, "CREATE TABLE IF NOT EXISTS answer8_class (id BIGINT PRIMARY KEY, teacher_id BIGINT NOT NULL, capacity INT NOT NULL CHECK(capacity > 0)) ENGINE=InnoDB");
        execute(c, "CREATE TABLE IF NOT EXISTS answer8_enrollment (id BIGINT PRIMARY KEY AUTO_INCREMENT, student_id BIGINT NOT NULL, class_id BIGINT NOT NULL, status VARCHAR(16) NOT NULL, deleted BOOLEAN NOT NULL DEFAULT FALSE, deleted_at DATETIME(6) NULL, deleted_by BIGINT NULL, FOREIGN KEY(class_id) REFERENCES answer8_class(id), UNIQUE(student_id,class_id)) ENGINE=InnoDB");
        reset(c);
    }
    static void reset(Connection c) throws SQLException {
        execute(c, "DELETE FROM answer8_enrollment");
        execute(c, "DELETE FROM answer8_class");
        execute(c, "INSERT INTO answer8_class VALUES(501,201,1)");
    }
    static void lockClass(Connection c) throws SQLException {
        scalar(c, "SELECT id FROM answer8_class WHERE id=501 FOR UPDATE");
    }
    static long countActive(Connection c) throws SQLException {
        return scalar(c, "SELECT COUNT(*) FROM answer8_enrollment WHERE class_id=501 AND status='ACTIVE' AND deleted=FALSE");
    }
    static boolean insertIfAvailable(Connection c, int student) throws SQLException {
        long count = countActive(c);
        long capacity = scalar(c, "SELECT capacity FROM answer8_class WHERE id=501");
        if (count >= capacity) return false;
        try (PreparedStatement s = c.prepareStatement("INSERT INTO answer8_enrollment(student_id,class_id,status) VALUES(?,501,'ACTIVE')")) {
            s.setInt(1, student); s.executeUpdate();
        }
        return true;
    }
    /** 观察服务器实际锁等待，确认B已阻塞后才放行A。 */
    static void awaitServerLockWait(Connection observer, long connectionId) throws SQLException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        String sql = "SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.threads t ON t.THREAD_ID=w.REQUESTING_THREAD_ID WHERE t.PROCESSLIST_ID=?";
        try (PreparedStatement s = observer.prepareStatement(sql)) {
            s.setLong(1, connectionId);
            while (System.nanoTime() < deadline) {
                try (ResultSet r = s.executeQuery()) { r.next(); if (r.getLong(1) > 0) return; }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
        }
        throw new AssertionError("B did not enter a server lock wait");
    }
    static void concurrencyScenario(boolean commitA) throws Exception {
        try (Connection a = connection(); Connection observer = connection()) {
            reset(observer);
            a.setAutoCommit(false);
            lockClass(a);
            CountDownLatch bWillLock = new CountDownLatch(1);
            AtomicLong bId = new AtomicLong();
            ExecutorService worker = Executors.newSingleThreadExecutor();
            Future<Boolean> result = worker.submit(() -> {
                try (Connection b = connection()) {
                    b.setAutoCommit(false);
                    bId.set(scalar(b, "SELECT CONNECTION_ID()"));
                    bWillLock.countDown();
                    lockClass(b);
                    boolean inserted = insertIfAvailable(b,102);
                    b.commit();
                    return inserted;
                }
            });
            try {
                check(bWillLock.await(10,TimeUnit.SECONDS), "B did not start");
                awaitServerLockWait(observer,bId.get());
                check(!result.isDone(), "B must wait for A");
                check(insertIfAvailable(a,101), "A must read and provisionally insert under lock");
                if (commitA) a.commit(); else a.rollback();
                boolean bInserted = result.get(10,TimeUnit.SECONDS);
                check(bInserted == !commitA, "B must re-read committed count");
                try (Connection verification = connection()) {
                    check(countActive(verification)==1, "exactly one active enrollment");
                    check(scalar(verification,"SELECT capacity FROM answer8_class WHERE id=501")==1,"capacity remains one");
                    int expectedStudent=commitA?101:102;
                    check(scalar(verification,"SELECT student_id FROM answer8_enrollment")==expectedStudent,"rollback leaves no row");
                }
                System.out.println(commitA ? "COMMIT: B waited, saw count=1, rejected; final=1/1" : "ROLLBACK: B waited, saw count=0, inserted; final=1/1; no A row");
            } finally {
                a.rollback();
                worker.shutdownNow();
            }
        }
    }
    static int logicalDelete(Connection c, long id, long actor, String utc) throws SQLException {
        String sql="UPDATE answer8_enrollment e JOIN answer8_class tc ON tc.id=e.class_id SET e.deleted=TRUE,e.deleted_at=?,e.deleted_by=? WHERE e.id=? AND tc.teacher_id=? AND e.status='WITHDRAWN' AND e.deleted=FALSE";
        try (PreparedStatement s=c.prepareStatement(sql)) {
            s.setString(1,utc);s.setLong(2,actor);s.setLong(3,id);s.setLong(4,actor);
            return s.executeUpdate();
        }
    }
    static void historyAndAudit() throws SQLException {
        try (Connection c=connection()) {
            reset(c);
            execute(c,"INSERT INTO answer8_enrollment(id,student_id,class_id,status) VALUES(1001,101,501,'ACTIVE')");
            check(logicalDelete(c,1001,201,"2026-09-01 02:00:00")==0,"active row cannot be deleted");
            for(String status:new String[]{"WITHDRAWN","ACTIVE","WITHDRAWN"}) {
                execute(c,"UPDATE answer8_enrollment SET status='"+status+"' WHERE id=1001");
                check(scalar(c,"SELECT COUNT(*) FROM answer8_enrollment WHERE id=1001")==1,"restore uses same row");
            }
            check(logicalDelete(c,1001,202,"2026-09-01 02:00:00")==0,"wrong teacher cannot delete");
            check(logicalDelete(c,1001,201,"2026-09-01 02:00:00")==1,"teacher may delete withdrawn row");
            check(logicalDelete(c,1001,201,"2026-09-02 02:00:00")==0,"repeat cannot overwrite audit");
            check(scalar(c,"SELECT COUNT(*) FROM answer8_enrollment WHERE id=1001 AND deleted=TRUE AND deleted_by=201 AND deleted_at='2026-09-01 02:00:00'")==1,"original audit retained");
            System.out.println("HISTORY/AUDIT: same id=1001 restored; active/wrong teacher blocked; original deletion audit retained");
        }
    }
    public static void main(String[] args) throws Exception {
        if(URL==null || !URL.matches("jdbc:mysql://[^/]+/answer8_test(?:\\?.*)?") || USER==null || PASSWORD==null)
            throw new IllegalArgumentException("Set ANSWER_DB_URL to dedicated answer8_test and set ANSWER_DB_USER/PASSWORD");
        try(Connection c=connection()) { setup(c); }
        concurrencyScenario(true);
        concurrencyScenario(false);
        historyAndAudit();
        System.out.println("8-9/8-10 MySQL PASS: observed lock waits, commit, rollback, history and audit");
    }
}
