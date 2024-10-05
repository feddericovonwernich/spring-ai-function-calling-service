package io.github.feddericovonwernich.spring_ai.example_application;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest(classes = ExampleApplication.class)
@ContextConfiguration(initializers = {BasicApplicationIntegrationTest.Initializer.class})
public class BasicApplicationIntegrationTest {

	// Will be shared between test methods
	private static final MySQLContainer MY_SQL_CONTAINER = (MySQLContainer) new MySQLContainer()
			.withReuse(true);

	@BeforeAll
	public static void beforeAll() {
		MY_SQL_CONTAINER.start();
	}

	static class Initializer
			implements ApplicationContextInitializer<ConfigurableApplicationContext> {
		public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
			TestPropertyValues.of(
					"spring.datasource.url=" + MY_SQL_CONTAINER.getJdbcUrl(),
					"spring.datasource.username=" + MY_SQL_CONTAINER.getUsername(),
					"spring.datasource.password=" + MY_SQL_CONTAINER.getPassword(),
					"spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",

					"spring.jpa.show-sql=true",
					"spring.jpa.hibernate.ddl-auto=update"
			).applyTo(configurableApplicationContext.getEnvironment());
		}
	}

}
