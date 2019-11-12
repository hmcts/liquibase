package liquibase.lockservice;

import static liquibase.lockservice.PostgresAdvisoryLockService.TRY_ACQUIRE_LOCK_SQL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

import liquibase.configuration.GlobalConfiguration;
import liquibase.configuration.LiquibaseConfiguration;
import liquibase.database.core.H2Database;
import liquibase.database.core.PostgresDatabase;
import liquibase.exception.DatabaseException;
import liquibase.exception.LockException;
import liquibase.executor.Executor;
import liquibase.servicelocator.PrioritizedService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Random;


public class PostgresAdvisoryLockServiceTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private PostgresAdvisoryLockService lockService;

    @Mock
    private PostgresDatabase database;

    @Mock
    private Executor executor;

    @Before
    public void setUp() {
        lockService = spy( new PostgresAdvisoryLockService());
        ReflectionTestUtils.setField(lockService, // inject into this object
            "database", // assign to this field
            database); // object to be injected
        //doReturn(executor).when(lockService).getExecutor();
    }

    @Test
    public void tryAcquireLockTrue() throws DatabaseException, LockException {
        when(executor.queryForObject(TRY_ACQUIRE_LOCK_SQL, Boolean.class)).thenReturn(true);

        final boolean acquired = lockService.tryAcquireLock(executor);

        assertThat(acquired, is(true));
    }

    @Test
    public void tryAcquireLockFalse() throws DatabaseException, LockException {
        when(executor.queryForObject(TRY_ACQUIRE_LOCK_SQL, Boolean.class)).thenReturn(false);

        final boolean acquired = lockService.tryAcquireLock(executor);

        assertThat(acquired, is(false));
    }

    @Test
    public void supportsPostgresReturnFalseIfUseDbLockIsFalse() throws DatabaseException {
        doReturn(9).when(database).getDatabaseMajorVersion();
        doReturn(1).when(database).getDatabaseMinorVersion();

        LiquibaseConfiguration.getInstance()
            .getConfiguration(GlobalConfiguration.class)
            .setUseDbLock(false);
        assertThat(lockService.supports(database), is(false));
    }

    @Test
    public void supportsPostgres() throws DatabaseException {
        doReturn(9).when(database).getDatabaseMajorVersion();
        doReturn(1).when(database).getDatabaseMinorVersion();
        LiquibaseConfiguration.getInstance()
            .getConfiguration(GlobalConfiguration.class)
            .setUseDbLock(true);

        assertThat(lockService.supports(database), is(true));
    }


    @Test
    public void supportsH2() {
        assertThat(lockService.supports(mock(H2Database.class)), is(false));
    }

   @Test
    public void changeLogLockWaitTimeDefault() {
        final Long expected = LiquibaseConfiguration.getInstance()
            .getConfiguration(GlobalConfiguration.class)
            .getDatabaseChangeLogLockWaitTime();

        final long actual = lockService.getChangeLogLockWaitTime();

        assertThat(actual, is(expected));
    }

    @Test
    public void setChangeLogLockWaitTime() {
        final long expected = new Random().nextInt();
        lockService.setChangeLogLockWaitTime(expected);

        final long actual = lockService.getChangeLogLockWaitTime();

        assertThat(actual, is(expected));
    }

    @Test
    public void changeLogLockRecheckTimeDefault() {
        final Long expected = LiquibaseConfiguration.getInstance()
            .getConfiguration(GlobalConfiguration.class)
            .getDatabaseChangeLogLockPollRate();

        final long actual = lockService.getChangeLogLocRecheckTime();

        assertThat(actual, is(expected));
    }

    @Test
    public void setChangeLogLockRecheckTime() {
        final long expected = new Random().nextInt();
        lockService.setChangeLogLockRecheckTime(expected);

        final long actual = lockService.getChangeLogLocRecheckTime();

        assertThat(actual, is(expected));
    }

    @Test
    public void hasChangeLogLockDefaultToFalse() {
        assertThat(lockService.hasChangeLogLock(), is(false));
    }

    @Test
    public void hasChangeLogLockTrue() throws DatabaseException, LockException {
        doReturn( executor ).when( lockService ).getExecutor();
        doReturn(true).when(executor).queryForObject(TRY_ACQUIRE_LOCK_SQL, Boolean.class);

        lockService.acquireLock();

        assertThat(lockService.hasChangeLogLock(), is(true));
    }

    @Test
    public void hasChangeLogLockFalse() throws DatabaseException, LockException {
        doReturn(executor).when(lockService).getExecutor();
        doReturn(false).when(executor).queryForObject(TRY_ACQUIRE_LOCK_SQL, Boolean.class);
        lockService.acquireLock();

        assertThat(lockService.hasChangeLogLock(), is(false));
    }

    @Test
    public void waitForLock() throws DatabaseException, LockException {
        doReturn(executor).when(lockService).getExecutor();
        doReturn(true).when(executor).queryForObject(TRY_ACQUIRE_LOCK_SQL, Boolean.class);

        lockService.waitForLock();

        assertThat(lockService.hasChangeLogLock(), is(true));
    }

    @Test
    public void waitForLockRetry() throws DatabaseException, LockException {
        lockService.setChangeLogLockRecheckTime(1);
        when(lockService.getExecutor()).thenReturn(executor);
        when(executor.queryForObject(TRY_ACQUIRE_LOCK_SQL, Boolean.class))
            .thenReturn(false)
            .thenReturn(false)
            .thenReturn(true);

        lockService.waitForLock();

        verify(lockService, times(3)).acquireLock();
        assertThat(lockService.hasChangeLogLock(), is(true));
    }

    @Test
    public void listLocks() {
        final DatabaseChangeLogLock[] locks = lockService.listLocks();

        assertThat(locks, emptyArray());
    }

    @Test
    public void getPriority() {
        final int expected = PrioritizedService.PRIORITY_DEFAULT + 1;

        final int actual = lockService.getPriority();

        assertThat(actual, is(expected));
    }
}
