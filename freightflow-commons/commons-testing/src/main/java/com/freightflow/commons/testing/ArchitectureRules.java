package com.freightflow.commons.testing;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * Reusable ArchUnit architecture rules for enforcing hexagonal architecture,
 * naming conventions, SOLID principles, and Spring best practices across all
 * FreightFlow microservices.
 *
 * <p>Each rule is a static {@code ArchRule} constant that any service can import
 * into its {@code @AnalyzeClasses} test. Rules are grouped into categories:</p>
 * <ul>
 *   <li><b>Hexagonal Architecture</b> — enforces dependency direction (domain → application → infrastructure)</li>
 *   <li><b>Naming Conventions</b> — enforces consistent class naming across services</li>
 *   <li><b>SOLID Principles</b> — enforces constructor injection, interface ports, etc.</li>
 *   <li><b>Spring Best Practices</b> — prevents anti-patterns like field injection</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @AnalyzeClasses(packages = "com.freightflow.booking")
 * class ArchitectureTest {
 *     @ArchTest
 *     static final ArchRule domainIndependence = ArchitectureRules.DOMAIN_SHOULD_NOT_DEPEND_ON_INFRASTRUCTURE;
 * }
 * }</pre>
 *
 * @see com.tngtech.archunit.lang.ArchRule
 */
public final class ArchitectureRules {

    private ArchitectureRules() {
        // Utility class — prevent instantiation
    }

    // ==================== Hexagonal Architecture Enforcement ====================

    /**
     * Domain layer must NOT depend on infrastructure layer.
     *
     * <p>In hexagonal architecture, the domain is the innermost ring and must have
     * zero knowledge of infrastructure concerns (databases, messaging, HTTP).</p>
     */
    public static final ArchRule DOMAIN_SHOULD_NOT_DEPEND_ON_INFRASTRUCTURE =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .as("Domain layer must not depend on infrastructure layer")
                    .because("the domain is the innermost ring in hexagonal architecture and must have " +
                            "zero knowledge of infrastructure concerns (databases, messaging, HTTP frameworks)");

    /**
     * Domain layer must NOT depend on application layer.
     *
     * <p>The domain model is self-contained — it defines ports (interfaces) but never
     * references application services, commands, or queries.</p>
     */
    public static final ArchRule DOMAIN_SHOULD_NOT_DEPEND_ON_APPLICATION =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..application..")
                    .as("Domain layer must not depend on application layer")
                    .because("the domain defines business rules and ports; it must not reference " +
                            "application services, commands, or orchestration logic");

    /**
     * Domain classes must NOT use Spring framework annotations.
     *
     * <p>Domain objects should be plain Java objects (POJOs) with no framework coupling.
     * Spring annotations like @Component, @Service, @Repository, or @Controller
     * indicate infrastructure or application concerns leaking into the domain.</p>
     */
    public static final ArchRule DOMAIN_SHOULD_NOT_USE_SPRING =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().beAnnotatedWith("org.springframework.stereotype.Component")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Service")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                    .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.Controller")
                    .as("Domain classes must not be annotated with Spring stereotypes (@Component, @Service, @Repository, @Controller)")
                    .because("domain objects must be framework-agnostic POJOs — Spring annotations indicate " +
                            "infrastructure concerns leaking into the domain layer");

    /**
     * Application layer must NOT depend on infrastructure adapter implementations.
     *
     * <p>The application layer orchestrates use cases via domain ports (interfaces).
     * It must never import concrete adapter classes from the infrastructure layer.</p>
     */
    public static final ArchRule APPLICATION_SHOULD_NOT_DEPEND_ON_INFRASTRUCTURE =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure.adapter..")
                    .as("Application layer must not depend on infrastructure adapters")
                    .because("the application layer orchestrates use cases through domain ports (interfaces), " +
                            "not concrete infrastructure implementations — this is the Dependency Inversion Principle");

    /**
     * Inbound adapters (REST) must NOT depend on outbound adapters (persistence) and vice versa.
     *
     * <p>Adapters communicate through the application layer, never directly with each other.
     * This prevents tight coupling between infrastructure concerns.</p>
     */
    public static final ArchRule ADAPTERS_SHOULD_NOT_DEPEND_ON_EACH_OTHER =
            noClasses()
                    .that().resideInAPackage("..adapter.in..")
                    .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
                    .as("Inbound adapters (REST) must not depend on outbound adapters (persistence)")
                    .because("adapters communicate through the application layer, never directly — " +
                            "this prevents tight coupling between infrastructure concerns");

    // ==================== Naming Conventions ====================

    /**
     * Classes in the {@code adapter.in.rest} package must have the suffix "Controller".
     *
     * <p>Consistent naming makes it easy to locate REST endpoints and
     * understand a class's role at a glance.</p>
     */
    public static final ArchRule CONTROLLERS_SHOULD_END_WITH_CONTROLLER =
            classes()
                    .that().resideInAPackage("..adapter.in.rest")
                    .and().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .should().haveSimpleNameEndingWith("Controller")
                    .as("REST controller classes must have the suffix 'Controller'")
                    .because("consistent naming conventions make it easy to locate REST endpoints " +
                            "and understand a class's role at a glance");

    /**
     * Classes annotated with {@code @Service} must end with "Service" or "Handler".
     *
     * <p>This enforces a clear naming convention for application-layer services
     * and CQRS command/query handlers.</p>
     */
    public static final ArchRule SERVICES_SHOULD_END_WITH_SERVICE_OR_HANDLER =
            classes()
                    .that().areAnnotatedWith("org.springframework.stereotype.Service")
                    .should().haveSimpleNameEndingWith("Service")
                    .orShould().haveSimpleNameEndingWith("Handler")
                    .as("Classes annotated with @Service must end with 'Service' or 'Handler'")
                    .because("consistent naming conventions distinguish application services from " +
                            "CQRS command/query handlers and make the codebase navigable");

    /**
     * Classes in the {@code domain.event} package should be records.
     *
     * <p>Domain events are immutable data carriers — Java records are the ideal
     * representation, providing immutability, equals/hashCode, and toString for free.</p>
     */
    public static final ArchRule EVENTS_SHOULD_BE_RECORDS =
            classes()
                    .that().resideInAPackage("..domain.event..")
                    .and().areNotInterfaces()
                    .should().beRecords()
                    .as("Domain event classes must be records")
                    .because("domain events are immutable data carriers — Java records provide " +
                            "immutability, equals/hashCode, and toString by default");

    // ==================== SOLID Enforcement ====================

    /**
     * No fields should be annotated with {@code @Autowired}.
     *
     * <p>Field injection hides dependencies, makes testing harder, and violates
     * the Single Responsibility Principle. Constructor injection is preferred
     * because it makes dependencies explicit and enables immutability.</p>
     */
    public static final ArchRule NO_FIELD_INJECTION =
            noFields()
                    .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .as("No fields should use @Autowired (field injection)")
                    .because("field injection hides dependencies and makes testing harder — " +
                            "use constructor injection to make dependencies explicit and enable immutability");

    /**
     * Classes in the {@code domain.port} package must be interfaces.
     *
     * <p>Ports define contracts between the domain and the outside world.
     * They must be interfaces so that adapters can provide concrete implementations
     * (Dependency Inversion Principle).</p>
     */
    public static final ArchRule PORTS_SHOULD_BE_INTERFACES =
            classes()
                    .that().resideInAPackage("..domain.port..")
                    .should().beInterfaces()
                    .as("Classes in domain.port package must be interfaces")
                    .because("ports define contracts between the domain and the outside world — " +
                            "they must be interfaces so adapters can provide implementations (Dependency Inversion Principle)");

    // ==================== Spring Best Practices ====================

    /**
     * REST controllers must NOT directly access repository interfaces.
     *
     * <p>Controllers should delegate to application services (or command/query handlers),
     * which then interact with repositories. Direct controller→repository access
     * bypasses business logic and transaction boundaries.</p>
     */
    public static final ArchRule CONTROLLERS_SHOULD_NOT_ACCESS_REPOSITORIES =
            noClasses()
                    .that().resideInAPackage("..adapter.in.rest..")
                    .should().dependOnClassesThat().resideInAPackage("..domain.port..")
                    .as("REST controllers must not directly access domain port interfaces (repositories)")
                    .because("controllers should delegate to application services which handle " +
                            "business logic, transaction boundaries, and event publishing — " +
                            "direct repository access bypasses these safeguards");

    /**
     * Outbound adapters (persistence) must NOT depend on inbound adapters (REST).
     *
     * <p>This is the reverse direction check complementing {@link #ADAPTERS_SHOULD_NOT_DEPEND_ON_EACH_OTHER}.
     * Persistence adapters should only know about the domain model and ports.</p>
     */
    public static final ArchRule OUTBOUND_ADAPTERS_SHOULD_NOT_DEPEND_ON_INBOUND =
            noClasses()
                    .that().resideInAPackage("..adapter.out..")
                    .should().dependOnClassesThat().resideInAPackage("..adapter.in..")
                    .as("Outbound adapters (persistence) must not depend on inbound adapters (REST)")
                    .because("persistence adapters should only know about the domain model and ports — " +
                            "depending on REST adapters creates circular coupling");
}
