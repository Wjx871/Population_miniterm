package com.wjx871.population.reference;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/** Explicit -Dtest only. Run before production edits; assertions specify the intended safe contract. */
class Issue8183BaselineRedProbe extends Issue8183HttpSupport {
    @Test void createUnderDisabledParentMustConflict() {
        String a = node("a", null, 1);
        check(state(a, "disable", 0), 200);
        var before=snapshot();int logs=audits();
        var r = create("b", a, 2);
        evidence("RED #81 create", r);
        check(r, 409);
        assertThat(snapshot()).isEqualTo(before);assertThat(audits()).isEqualTo(logs);
    }
    @Test void moveUnderDisabledParentMustConflict() {
        String a = node("a", null, 1), b = node("b", null, 1), c = node("c", a, 2);
        check(state(b, "disable", 0), 200);
        var before=snapshot();int logs=audits();
        var r = move(c, b, 2, 0);
        evidence("RED #81 move", r);
        check(r, 409);
        assertThat(snapshot()).isEqualTo(before);assertThat(audits()).isEqualTo(logs);
    }
    @Test void disabledChildMustNotHideEnabledGrandchild() {
        String a = node("a", null, 1), b = node("b", a, 2);
        node("c", b, 3);
        historicalStatus(b, "DISABLED"); // Isolated historical anomaly, never a production state-machine change.
        var before=snapshot();int logs=audits();
        System.out.println("ISSUE8183 RED #83 before=" + snapshot());
        var r = state(a, "disable", 0);
        evidence("RED #83 disable", r);
        check(r, 409);
        assertThat(snapshot()).isEqualTo(before);assertThat(audits()).isEqualTo(logs);
    }
}
