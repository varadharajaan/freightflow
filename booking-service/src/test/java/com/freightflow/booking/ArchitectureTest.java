package com.freightflow.booking;

import com.freightflow.commons.testing.ArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture enforcement tests for the Booking Service.
 *
 * <p>Uses ArchUnit to verify that the booking-service codebase adheres to hexagonal
 * architecture principles, naming conventions, SOLID principles, and Spring best practices.
 * Rules are imported from the shared {@link ArchitectureRules} library in commons-testing,
 * plus booking-specific rules defined here.</p>
 *
 * <p>These tests run as part of the standard test suite and will fail the build if any
 * architectural constraint is violated — preventing architectural erosion over time.</p>
 *
 * @see ArchitectureRules
 */
@AnalyzeClasses(
        packages = "com.freightflow.booking",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    // ==================== Hexagonal Architecture (imported from commons-testing) ====================

    @ArchTest
    static final ArchRule domainShouldNotDependOnInfrastructure =
            ArchitectureRules.DOMAIN_SHOULD_NOT_DEPEND_ON_INFRASTRUCTURE;

    @ArchTest
    static final ArchRule domainShouldNotDependOnApplication =
            ArchitectureRules.DOMAIN_SHOULD_NOT_DEPEND_ON_APPLICATION;

    @ArchTest
    static final ArchRule domainShouldNotUseSpring =
            ArchitectureRules.DOMAIN_SHOULD_NOT_USE_SPRING;

    @ArchTest
    static final ArchRule applicationShouldNotDependOnInfrastructure =
            ArchitectureRules.APPLICATION_SHOULD_NOT_DEPEND_ON_INFRASTRUCTURE;

    @ArchTest
    static final ArchRule adaptersShouldNotDependOnEachOther =
            ArchitectureRules.ADAPTERS_SHOULD_NOT_DEPEND_ON_EACH_OTHER;

    @ArchTest
    static final ArchRule outboundAdaptersShouldNotDependOnInbound =
            ArchitectureRules.OUTBOUND_ADAPTERS_SHOULD_NOT_DEPEND_ON_INBOUND;

    // ==================== Naming Conventions (imported from commons-testing) ====================

    @ArchTest
    static final ArchRule controllersShouldEndWithController =
            ArchitectureRules.CONTROLLERS_SHOULD_END_WITH_CONTROLLER;

    @ArchTest
    static final ArchRule servicesShouldEndWithServiceOrHandler =
            ArchitectureRules.SERVICES_SHOULD_END_WITH_SERVICE_OR_HANDLER;

    @ArchTest
    static final ArchRule eventsShouldBeRecords =
            ArchitectureRules.EVENTS_SHOULD_BE_RECORDS;

    // ==================== SOLID Enforcement (imported from commons-testing) ====================

    @ArchTest
    static final ArchRule noFieldInjection =
            ArchitectureRules.NO_FIELD_INJECTION;

    @ArchTest
    static final ArchRule portsShouldBeInterfaces =
            ArchitectureRules.PORTS_SHOULD_BE_INTERFACES;

    // ==================== Spring Best Practices (imported from commons-testing) ====================

    @ArchTest
    static final ArchRule controllersShouldNotAccessRepositories =
            ArchitectureRules.CONTROLLERS_SHOULD_NOT_ACCESS_REPOSITORIES;

    // ==================== Booking-Specific Rules ====================

    /**
     * All BookingEvent subtypes must implement the BookingEvent sealed interface.
     *
     * <p>This ensures that all event records in the booking domain participate in
     * the sealed hierarchy, enabling exhaustive pattern matching in event handlers.</p>
     */
    @ArchTest
    static final ArchRule bookingEventSubtypesMustImplementBookingEvent =
            classes()
                    .that().resideInAPackage("..domain.event..")
                    .and().areRecords()
                    .should().implement(com.freightflow.booking.domain.event.BookingEvent.class)
                    .as("All event records in domain.event must implement the BookingEvent sealed interface")
                    .because("the sealed hierarchy enables exhaustive pattern matching in event handlers " +
                            "and ensures no event type is accidentally missed during processing");

    /**
     * All BookingCommand subtypes must implement the BookingCommand sealed interface.
     *
     * <p>This ensures that all command records participate in the sealed hierarchy,
     * enabling exhaustive pattern matching in the command handler's dispatch method.</p>
     */
    @ArchTest
    static final ArchRule bookingCommandSubtypesMustImplementBookingCommand =
            classes()
                    .that().resideInAPackage("..application.command..")
                    .and().areRecords()
                    .should().implement(com.freightflow.booking.application.command.BookingCommand.class)
                    .as("All command records in application.command must implement the BookingCommand sealed interface")
                    .because("the sealed hierarchy enables exhaustive pattern matching in the command handler " +
                            "and the compiler will enforce that all command types are handled");

    /**
     * Infrastructure adapter classes must NOT directly reference domain model internals
     * beyond the aggregate root and value objects exposed by its public API.
     *
     * <p>Adapters should interact with the domain through ports and the aggregate's public interface.</p>
     */
    @ArchTest
    static final ArchRule restAdaptersShouldNotAccessDomainPorts =
            noClasses()
                    .that().resideInAPackage("..adapter.in.rest..")
                    .should().dependOnClassesThat().resideInAPackage("..domain.port..")
                    .as("REST adapter classes must not directly access domain port interfaces")
                    .because("REST adapters should only interact with the application layer, " +
                            "which coordinates access to domain ports and manages transactions");
}
