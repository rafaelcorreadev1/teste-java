package com.deroyque.activation;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import jakarta.persistence.Entity;

import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.deroyque.activation", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayersOrSpring = noClasses()
            .that().resideInAPackage("..request.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..request.api..", "..request.app..", "..request.infra..", "..provider..", "org.springframework..")
            .because("as regras de status precisam ser testáveis sem Spring e sem infraestrutura");

    @ArchTest
    static final ArchRule apiGoesThroughApplicationLayer = noClasses()
            .that().resideInAPackage("..request.api..")
            .should().dependOnClassesThat().resideInAPackage("..request.infra..")
            .because("o controller não acessa o repositório diretamente; o isolamento por cliente vive no serviço");

    @ArchTest
    static final ArchRule apiUseCaseDoesNotDependOnBackgroundProcessing = noClasses()
            .that().resideInAPackage("..request.app..")
            .should().dependOnClassesThat().resideInAPackage("..request.processing..")
            .because("o caso de uso da API só grava a solicitação; o envio ao provedor é assíncrono");

    @ArchTest
    static final ArchRule providerIntegrationIsIndependentOfRequestFeature = noClasses()
            .that().resideInAPackage("..provider..")
            .should().dependOnClassesThat().resideInAPackage("..request..")
            .because("o cliente do provedor só conhece o próprio contrato HTTP");

    @ArchTest
    static final ArchRule controllersLiveInApiPackages = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule endpointsNeverReturnEntities = noMethods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should().haveRawReturnType(annotatedWith(Entity.class))
            .because("a API expõe DTOs (records), nunca a entidade JPA");

    @ArchTest
    static final ArchRule responseBodiesAreRecords = classes()
            .that().resideInAPackage("..request.api..")
            .and().haveSimpleNameEndingWith("Response")
            .should().beRecords();
}
