package dev.emreuygun;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.PostgreSQLR2DBCDatabaseContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.function.Consumer;

@Slf4j
@Configuration
public class PostgresqlTestcontainerConfiguration {
    private static final DockerImageName dockerImage = DockerImageName.parse("postgres").withTag("15");
    private static final Logger postgresqlDockerLogger = LoggerFactory.getLogger("docker.postgresql");
    public static final String BEAN_NAME_EMBEDDED_POSTGRESQL = "postgresqlContainer";

    @Bean(name = BEAN_NAME_EMBEDDED_POSTGRESQL, destroyMethod = "stop")
    public PostgreSQLR2DBCDatabaseContainer postgreSQLContainer(@Value("r2dbc") String databaseName,
                                                      @Value("${spring.r2dbc.username}") String databaseUsername,
                                                      @Value("${spring.r2dbc.password}") String databasePassword) {
        var postgresql = new PostgreSQLContainer<>(dockerImage)
                .withUsername(databaseUsername)
                .withPassword(databasePassword)
                .withDatabaseName(databaseName);

        postgresql.setPortBindings(new ArrayList<>() {{
            add("5432:5432");
        }});
        postgresql = (PostgreSQLContainer<?>) configureCommons(postgresql, postgresqlDockerLogger);
        var r2dbcPostgresqlContainer = new PostgreSQLR2DBCDatabaseContainer(postgresql);

        r2dbcPostgresqlContainer.start();
        LOG.info("Started postgresql server. JDBC connection url: " + postgresql.getJdbcUrl());

        return r2dbcPostgresqlContainer;
    }

    // Post processors for dependent beans
    //------------------------------------------------------------------------------------------------------------------
    @Bean
    public static BeanFactoryPostProcessor datasourcePostgreSqlDependencyPostProcessor() {
        return new DependsOnPostProcessor(DataSource.class, BEAN_NAME_EMBEDDED_POSTGRESQL);
    }

    @Bean
    public static BeanFactoryPostProcessor r2dbcConnectionFactoryPostgreSqlDependencyPostProcessor() {
        return new DependsOnPostProcessor(DatabaseClient.class, BEAN_NAME_EMBEDDED_POSTGRESQL);
    }

    // Utils
    //------------------------------------------------------------------------------------------------------------------
    public static GenericContainer<?> configureCommons(GenericContainer<?> container, Logger logger) {
        LOG.info("Starting container with Docker image: {}", container.getDockerImageName());
        GenericContainer<?> updatedContainer = container
                .withStartupTimeout(Duration.ofSeconds(120))
                .withReuse(true)
                .withLogConsumer(containerLogsConsumer(logger))
                .withImagePullPolicy(PullPolicy.defaultPolicy());
        return updatedContainer;
    }

    public static Consumer<OutputFrame> containerLogsConsumer(Logger log) {
        return (OutputFrame outputFrame) -> {
            switch (outputFrame.getType()) {
                case STDERR -> log.error(outputFrame.getUtf8StringWithoutLineEnding());
                default -> log.info(outputFrame.getUtf8StringWithoutLineEnding());
            }
        };
    }
}
