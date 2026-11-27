package dev.sivalabs.helpdesk;

import static com.tngtech.archunit.core.domain.JavaModifier.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.enofex.taikai.Taikai;
import com.enofex.taikai.checks.RequiredFilesCheck;
import com.enofex.taikai.core.ClassCriteria;
import com.enofex.taikai.exports.MetricsMarkdownExport;
import com.enofex.taikai.java.ImportsConfigurer;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

@Order(1)
class ArchUnitTests {
    private static final String BASE_PACKAGE = ArchUnitTests.class.getPackageName();
    private static JavaClasses importedClasses;

    @BeforeAll
    static void setup() {
        importedClasses = new ClassFileImporter().importPackages(BASE_PACKAGE);
    }

    private Taikai taikai() {
        return Taikai.builder()
                .namespace(BASE_PACKAGE)
                .java(java -> java.noUsageOfDeprecatedAPIs()
                        .methodsShouldNotDeclareGenericExceptions()
                        .utilityClassesShouldBeFinalAndHavePrivateConstructor()
                        .imports(ImportsConfigurer::shouldHaveNoCycles)
                        .naming(naming -> naming.fieldsShouldNotMatch(".*(List|Set|Map)$")
                                .constantsShouldFollowConventions()
                                .interfacesShouldNotHavePrefixI()))
                .logging(logging ->
                        logging.loggersShouldFollowConventions(Logger.class, "LOG", List.of(PRIVATE, STATIC, FINAL)))
                .test(test -> test.junit(
                        junit -> junit.classesShouldBePackagePrivate(ClassCriteria.simpleNameMatching(".*Test(s)"))
                                .classesShouldNotBeAnnotatedWithDisabled()
                                .methodsShouldNotBeAnnotatedWithDisabled()))
                .jpa(jpa -> jpa.entities(entities -> entities.shouldBePackagePrivate()
                        .idsShouldNotBeOfType(ClassCriteria.type(UUID.class))
                        .shouldHaveFieldAnnotatedWithVersion()
                        .fieldsShouldNotBeOfType(ClassCriteria.type(Date.class)
                                .or(ClassCriteria.assignableTo(Date.class))
                                .or(ClassCriteria.type(Calendar.class))
                                .or(ClassCriteria.assignableTo(Calendar.class)))
                        .namesShouldMatch(".+Entity")))
                .spring(spring -> spring.noAutowiredFields()
                        .noSelfInvocationOfProxiedMethods()
                        .boot(boot -> boot.applicationClassShouldResideInPackage(BASE_PACKAGE))
                        .configurations(c -> c.namesShouldMatch(".+Config"))
                        .controllers(controllers -> controllers
                                // .shouldBeAnnotatedWithRestController()
                                .namesShouldEndWithController()
                                .shouldNotDependOnOtherControllers()
                                .shouldBePackagePrivate())
                        .services(services -> services.shouldBeAnnotatedWithService()
                                .shouldNotDependOnControllers()
                                .namesShouldEndWithService())
                        .repositories(repositories -> repositories
                                .shouldBePackagePrivate()
                                .shouldNotDependOnServices()
                                .namesShouldEndWithRepository())
                        .transactional(transactional -> transactional
                                .methodsShouldBePublic()
                                .shouldNotBeSelfInvoked()
                                .shouldNotBeUsedInControllers()))
                .discoverExports(exports -> exports.configure(
                        MetricsMarkdownExport.class, MetricsMarkdownExport.file("docs/taikai/metrics/METRICS.md")))
                .discoverChecks(checks -> checks.configure(
                        RequiredFilesCheck.class, RequiredFilesCheck.files("README.md", "AGENTS.md", "renovate.json")))
                .build();
    }

    @Test
    void shouldFollowArchitectureRules() {
        taikai().checkAll();
    }

    @Test
    void exportTaikaiMetrics() {
        taikai().export();
    }

    // Services are public (accessible to inbound handlers in the same module),
    // but must only be accessed from within their own module — other modules must go through *API facades.
    @Test
    void servicesShouldNotBeUsedFromOtherModules() {
        classes()
                .that()
                .areAnnotatedWith(Service.class)
                .should(new ArchCondition<>("only be accessed from same module") {
                    @Override
                    public void check(JavaClass service, ConditionEvents events) {
                        String serviceModule = moduleOf(service);
                        for (Dependency dependency : service.getDirectDependenciesToSelf()) {
                            JavaClass origin = dependency.getOriginClass();
                            String originModule = moduleOf(origin);
                            if (!serviceModule.equals(originModule)) {
                                String message =
                                        origin.getName() + " accesses " + service.getName() + " from another module";
                                events.add(SimpleConditionEvent.violated(dependency, message));
                            }
                        }
                    }
                })
                .check(importedClasses);
    }

    private static String moduleOf(JavaClass clazz) {
        String pkg = clazz.getPackageName();
        String[] parts = pkg.split("\\.");
        return parts[BASE_PACKAGE.split("\\.").length];
    }
}
