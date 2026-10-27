package com.deroyque.activation.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "activation.worker.enabled=false",
        "activation.provider.read-timeout=1s",
        "activation.security.issuer=" + TestTokenIssuer.ISSUER
})
@AutoConfigureMockMvc
@AutoConfigureObservability
@Import({TestcontainersConfiguration.class, ProviderMockConfiguration.class, DatabaseCleaner.class})
public @interface IntegrationTest {
}
