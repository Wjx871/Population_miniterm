package com.wjx871.population.reference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
class Issue8183MySqlRedProbe extends Issue8183BaselineRedProbe {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry p) { Issue8183MySqlGate.configure(p); }
    @Override void verifyDatabase(java.sql.Connection c) throws Exception { Issue8183MySqlGate.verify(c); }
}
