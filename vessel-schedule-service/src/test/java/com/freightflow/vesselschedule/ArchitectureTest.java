package com.freightflow.vesselschedule;

import com.freightflow.commons.testing.ArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.freightflow.vesselschedule",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

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

    @ArchTest
    static final ArchRule controllersShouldEndWithController =
            ArchitectureRules.CONTROLLERS_SHOULD_END_WITH_CONTROLLER;

    @ArchTest
    static final ArchRule servicesShouldEndWithServiceOrHandler =
            ArchitectureRules.SERVICES_SHOULD_END_WITH_SERVICE_OR_HANDLER;

    @ArchTest
    static final ArchRule eventsShouldBeRecords =
            ArchitectureRules.EVENTS_SHOULD_BE_RECORDS;

    @ArchTest
    static final ArchRule noFieldInjection =
            ArchitectureRules.NO_FIELD_INJECTION;

    @ArchTest
    static final ArchRule portsShouldBeInterfaces =
            ArchitectureRules.PORTS_SHOULD_BE_INTERFACES;

    @ArchTest
    static final ArchRule controllersShouldNotAccessRepositories =
            ArchitectureRules.CONTROLLERS_SHOULD_NOT_ACCESS_REPOSITORIES;
}
