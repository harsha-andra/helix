package com.harshaandra.helix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A live demonstration of the Spring proxy self-invocation trap, kept in the codebase on purpose
 * and asserted by SelfInvocationDemoTest.
 *
 * WHY THIS EXISTS
 * ---------------
 * @Transactional is implemented with a proxy. Spring wraps the bean; the proxy opens a
 * transaction and then delegates to the real object. Anything that goes through the proxy is
 * advised. Anything that does not, is not.
 *
 * When one method of a bean calls another method of the same bean, the call is a plain Java
 * `this.method()` invocation on the target object. It never touches the proxy, so the
 * @Transactional on the inner method is silently ignored — no error, no warning, no transaction.
 *
 * This is the single most common cause of "my @Transactional isn't working" and it is invisible
 * in code review, because the annotation is right there on the method.
 *
 * See docs/adr/0002-transactional-boundaries.md for the three ways out and why HELIX chose to
 * put the boundary on a separate collaborator rather than self-inject or use AspectJ weaving.
 */
@Service
public class SelfInvocationDemo {

    private static final Logger log = LoggerFactory.getLogger(SelfInvocationDemo.class);

    /**
     * Entry point with NO transaction. Calls the annotated method below directly.
     * Because the call bypasses the proxy, {@link #annotatedButBypassed()} runs with no
     * transaction at all, despite carrying @Transactional(REQUIRES_NEW).
     *
     * @return whether a transaction was actually active inside the called method — false.
     */
    public boolean callsAnnotatedMethodOnSelf() {
        log.debug("Calling an annotated method via `this` — the proxy is not involved");
        return annotatedButBypassed();
    }

    /**
     * The annotation on this method only takes effect when the method is entered THROUGH the
     * proxy — that is, when some other bean calls {@code demo.annotatedButBypassed()}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean annotatedButBypassed() {
        boolean active = TransactionSynchronizationManager.isActualTransactionActive();
        log.debug("Inside the annotated method, actual transaction active = {}", active);
        return active;
    }

    /**
     * The same method reached through the proxy, which is what a caller in another bean gets.
     * This one really does run in a transaction.
     */
    @Transactional
    public boolean calledThroughProxy() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
}
