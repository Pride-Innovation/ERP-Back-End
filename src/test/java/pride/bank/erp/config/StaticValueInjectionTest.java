package pride.bank.erp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @Value} must never land on a {@code static} field.
 *
 * <h2>What happens when it does</h2>
 * Spring applies {@code @Value} while populating a bean <b>instance</b>. A static field belongs to
 * the class rather than to any instance, so the container never touches it and the field keeps
 * whatever the class initialiser gave it — {@code null}.
 *
 * <p>It is <em>not</em> completely silent, and the precise version matters: Spring logs
 * {@code WARN ... Autowired annotation is not supported on static fields} and carries on. One warning
 * among several hundred lines of startup output, with the application reporting itself started — which
 * in practice is close enough to silent that this test is worth having anyway.
 *
 * <p>It was tried on {@link AssetCategoryFeatureSeeder}, to make the Infra and Admin fulfilment
 * mailboxes configurable per environment. Two static fields fed a {@code static final Map} built with
 * {@code Map.ofEntries}, which rejects null values — so class initialisation threw
 * {@code ExceptionInInitializerError} and <b>the application stopped starting altogether</b>.
 *
 * <h2>Why that was the lucky outcome, and why this test exists anyway</h2>
 * A failure at startup is the best version of this mistake. Swap {@code Map.ofEntries} for a
 * {@code HashMap}, or read the static field somewhere that tolerates null, and the same error becomes
 * silent: every asset category seeded with a null fulfilment mailbox, discovered weeks later as
 * requests routing nowhere. The mistake is easy to make precisely because it looks right, compiles
 * cleanly, and — in the quiet version — starts cleanly too.
 *
 * <p>So this checks the whole application rather than the one class that met it.
 */
@SpringBootTest
class StaticValueInjectionTest {

    @Autowired
    AssetCategoryFeatureSeeder seeder;

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    /**
     * Every {@code @Value} in the application sits on an instance field.
     *
     * <p>Reflective rather than textual: it loads the declared fields, so it cannot be fooled by
     * formatting, by the annotation and the field sitting on one line, or by a comment that happens
     * to mention {@code @Value}.
     */
    @Test
    void noConfigurationValueIsInjectedIntoAStaticField() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Class<?> type : applicationClasses()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Value.class) && Modifier.isStatic(field.getModifiers())) {
                    offenders.add(type.getSimpleName() + "." + field.getName()
                            + "  ->  " + field.getAnnotation(Value.class).value());
                }
            }
        }

        assertThat(offenders)
                .as("Spring cannot inject into a static field and will not say so. Make the field an "
                        + "instance field, and build anything derived from it after injection rather "
                        + "than in a static initialiser — see AssetCategoryFeatureSeeder.")
                .isEmpty();
    }

    /**
     * And the values this was actually about arrive.
     *
     * <p>The check above would pass on a class that declared the fields correctly and never read
     * them. This asserts the addresses resolve on the live bean, which is the thing the change was
     * for: mailboxes that follow the environment.
     */
    @Test
    void theFulfilmentMailboxesResolveFromConfiguration() throws Exception {
        assertThat(readField("infraEmail"))
                .as("application.head.office.infra-email did not resolve; check HEAD_OFFICE_INFRA_EMAIL")
                .isNotNull().isNotBlank();

        assertThat(readField("adminEmail"))
                .as("application.head.office.admin-email did not resolve; check HEAD_OFFICE_ADMIN_EMAIL")
                .isNotNull().isNotBlank();
    }

    private String readField(String name) throws Exception {
        Field field = AssetCategoryFeatureSeeder.class.getDeclaredField(name);
        assertThat(Modifier.isStatic(field.getModifiers()))
                .as("%s must be an instance field or @Value silently leaves it null", name)
                .isFalse();
        field.setAccessible(true);

        /*
         * Read off the target, not the proxy.
         *
         * `seedFeatures()` is @Transactional, so the injected bean is a CGLIB subclass. Its inherited
         * copy of the field is null — Spring populates the *target* instance, and the proxy only
         * forwards method calls. Reading the proxy's field therefore reproduces exactly the symptom
         * this test is about, for an entirely unrelated reason, which is its own small trap: the
         * first version of this test failed against perfectly correct code.
         */
        return (String) field.get(org.springframework.test.util.AopTestUtils.getUltimateTargetObject(seeder));
    }

    /** Every application class, loaded from the compiled sources. */
    private List<Class<?>> applicationClasses() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(StaticValueInjectionTest::classNameOf)
                    .map(StaticValueInjectionTest::loadOrNull)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
    }

    private static String classNameOf(Path path) {
        String relative = SOURCE_ROOT.relativize(path).toString();
        return relative.substring(0, relative.length() - ".java".length())
                .replace('\\', '.')
                .replace('/', '.');
    }

    private static Class<?> loadOrNull(String name) {
        try {
            // Not initialised: loading is enough to read the field declarations, and running static
            // initialisers is exactly what blew up last time.
            return Class.forName(name, false, StaticValueInjectionTest.class.getClassLoader());
        } catch (Throwable notAClass) {
            return null;
        }
    }
}
