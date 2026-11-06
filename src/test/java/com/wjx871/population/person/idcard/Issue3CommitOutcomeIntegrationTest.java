package com.wjx871.population.person.idcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.wjx871.population.common.BusinessException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Actual JDBC commit acknowledgement loss, not just an annotation/mock transaction assertion. */
@SpringBootTest(properties = "application.upload.dir=./target/issue3-uncertain-uploads")
@Import(Issue3CommitOutcomeIntegrationTest.CommitFaultConfiguration.class)
class Issue3CommitOutcomeIntegrationTest {
    static final AtomicBoolean failCommit = new AtomicBoolean();
    static volatile boolean committedAtDatabase;
    @Autowired PersonIdCardImageService service;
    @Autowired JdbcTemplate jdbc;
    @SpyBean PersonIdCardImageMapper mapper;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void uncertainCommitRetainsOwnedFileWhetherOrNotDatabaseCommitted(boolean physicalCommit) throws Exception {
        committedAtDatabase = physicalCommit;
        List<Integer> observedCompletion = new ArrayList<>();
        Set<String> before = files();
        doAnswer(invocation -> {
            Object inserted = org.mockito.Mockito.mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { observedCompletion.add(status); }
            });
            failCommit.set(true);
            return inserted;
        }).when(mapper).insertDraft(any());
        try {
            assertThatThrownBy(() -> service.uploadAndRecognize(new MockMultipartFile("file", "issue3-ack.jpg",
                    "image/jpeg", UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), true, 2L, null))
                    .isInstanceOf(BusinessException.class).hasMessage("身份证影印本处理失败");
            assertThat(observedCompletion).containsExactly(TransactionSynchronization.STATUS_UNKNOWN);
            assertThat(TransactionSynchronizationManager.hasResource(jdbc.getDataSource())).isFalse();
            Set<String> retained = files(); retained.removeAll(before);
            assertThat(retained).hasSize(1);
            String name = retained.iterator().next();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person_idcard_image WHERE stored_filename=?", Integer.class, name))
                    .isEqualTo(physicalCommit ? 1 : 0);
            // File retained in BOTH cases. Production has no durable mechanism to distinguish them.
        } finally {
            failCommit.set(false);
            // Test harness knows which physical action its JDBC fault performed; production does not.
            if (!physicalCommit) {
                Set<String> retained = files(); retained.removeAll(before);
                for (String name : retained) {
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person_idcard_image WHERE stored_filename=?", Integer.class, name)).isZero();
                    Files.delete(Path.of("target/issue3-uncertain-uploads").resolve(name));
                }
            }
        }
    }

    Set<String> files() throws Exception {
        Path root = Path.of("target/issue3-uncertain-uploads");
        if (!Files.exists(root)) return new java.util.HashSet<>();
        try (var paths = Files.list(root)) { return paths.map(p -> p.getFileName().toString()).collect(Collectors.toSet()); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CommitFaultConfiguration {
        @Bean static BeanPostProcessor commitFaultDataSource() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource source)) return bean;
                    return new DelegatingDataSource(source) {
                        @Override public Connection getConnection() throws SQLException { return wrap(super.getConnection()); }
                        @Override public Connection getConnection(String user, String password) throws SQLException {
                            return wrap(super.getConnection(user, password));
                        }
                        Connection wrap(Connection connection) {
                            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("commit") && failCommit.compareAndSet(true, false)) {
                                            if (committedAtDatabase) connection.commit(); else connection.rollback();
                                            throw new SQLException("SYNTHETIC_COMMIT_ACK_LOST", "08006");
                                        }
                                        try { return method.invoke(connection, args); }
                                        catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
                                    });
                        }
                    };
                }
            };
        }
    }
}
