package com.staybook;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 요구사항 4장의 의존 방향 검사.
 *
 * <pre>
 * review → reservation → inventory → accommodation → host → user
 * payment → reservation
 * favorite → accommodation
 * auth, terms → user
 * </pre>
 */
class DependencyRuleTest {

    private static final String BASE = "com.staybook.";

    /** feature → 직접 의존해도 되는 feature (화살표 한 칸). 화살표를 따라간 곳까지는 허용한다. */
    private static final Map<String, List<String>> ARROWS = new LinkedHashMap<>();

    static {
        ARROWS.put("user", List.of());
        ARROWS.put("auth", List.of("user"));
        ARROWS.put("terms", List.of("user"));
        ARROWS.put("host", List.of("user"));
        ARROWS.put("accommodation", List.of("host"));
        ARROWS.put("inventory", List.of("accommodation"));
        ARROWS.put("reservation", List.of("inventory"));
        ARROWS.put("payment", List.of("reservation"));
        ARROWS.put("review", List.of("reservation"));
        ARROWS.put("favorite", List.of("accommodation"));
    }

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.staybook");
    }

    static Stream<String> features() {
        return ARROWS.keySet().stream();
    }

    @Test
    void common은_어떤_feature도_import하지_않는다() {
        noClasses().that().resideInAPackage(BASE + "common..")
                .should().dependOnClassesThat().resideInAnyPackage(packagesOf(ARROWS.keySet()))
                .check(classes);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("features")
    void feature는_화살표_반대_방향을_import하지_않는다(String feature) {
        Set<String> forbidden = new LinkedHashSet<>(ARROWS.keySet());
        forbidden.remove(feature);
        forbidden.removeAll(reachable(feature));

        noClasses().that().resideInAPackage(BASE + feature + "..")
                .should().dependOnClassesThat().resideInAnyPackage(packagesOf(forbidden))
                .allowEmptyShould(true)
                .check(classes);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("features")
    void 다른_feature의_리포지터리와_엔티티를_직접_쓰지_않는다(String feature) {
        // 다른 feature는 서비스를 호출하고, 엔티티 대신 ID로 참조한다
        noClasses().that().resideOutsideOfPackage(BASE + feature + "..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(BASE + feature + ".repository..", BASE + feature + ".domain..")
                .allowEmptyShould(true)
                .check(classes);
    }

    /** 화살표를 따라 닿는 모든 feature. */
    private static Set<String> reachable(String feature) {
        Set<String> result = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>(ARROWS.get(feature));
        while (!queue.isEmpty()) {
            String next = queue.remove(0);
            if (result.add(next)) {
                queue.addAll(ARROWS.get(next));
            }
        }
        return result;
    }

    private static String[] packagesOf(Set<String> features) {
        return features.stream().map(f -> BASE + f + "..").toArray(String[]::new);
    }
}
