package com.harshaandra.helix.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Spring proxy self-invocation trap rather than asserting it in a comment.
 *
 * No database and no Spring Boot context — the trap is a property of how proxies work, so a
 * transaction manager that does nothing but record that it was entered is enough to demonstrate
 * it, and the test runs in milliseconds as part of every build.
 *
 * See docs/adr/0002-transactional-boundaries.md.
 */
@SpringJUnitConfig(SelfInvocationDemoTest.TestConfig.class)
class SelfInvocationDemoTest {

    @Autowired
    private SelfInvocationDemo demo;

    @Test
    @DisplayName("calling an @Transactional method on `this` silently skips the transaction")
    void selfInvocationBypassesTheProxy() {
        // The method being called carries @Transactional(REQUIRES_NEW). It is invoked from
        // another method of the same bean, so the call never leaves the target object and the
        // proxy's transaction advice never runs.
        boolean transactionWasActive = demo.callsAnnotatedMethodOnSelf();

        assertThat(transactionWasActive)
                .as("""
                    @Transactional was ignored. No exception, no warning, no transaction — \
                    which is exactly why this bug survives code review.""")
                .isFalse();
    }

    @Test
    @DisplayName("the same method reached through the proxy really is transactional")
    void proxiedInvocationIsAdvised() {
        // Identical method, identical annotation. The only difference is that the call arrives
        // from outside the bean and therefore passes through the proxy.
        assertThat(demo.calledThroughProxy())
                .as("a call from another bean goes through the proxy and is advised")
                .isTrue();
    }

    @Test
    @DisplayName("the injected bean is a proxy, not the raw object")
    void beanIsProxied() {
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(demo))
                .as("if this were false the two tests above would be meaningless")
                .isTrue();
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        SelfInvocationDemo selfInvocationDemo() {
            return new SelfInvocationDemo();
        }

        /**
         * A transaction manager that does nothing except let Spring's infrastructure mark a
         * transaction as active. That is all this test needs to observe.
         */
        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
                    // no-op: no real resource to bind
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                    // no-op
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                    // no-op
                }
            };
        }
    }
}
