package dev.emreuygun;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Slf4j
@SpringBootApplication
@EnableR2dbcRepositories(considerNestedRepositories = true)
@Configuration
@RequiredArgsConstructor
public class ChatApplication implements InitializingBean {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }

    private final MessageReactiveRepository repository;
    private final ConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;
    private PostgresqlConnection connection;

    @Override
    public void afterPropertiesSet() {
        this.connection = Mono.from(connectionFactory.create())
                .cast(PostgresqlConnection.class)
                .block();
        connection.createStatement("LISTEN message_insert_channel")
                .execute()
                .subscribe();
    }

    private Message toMessage(String message) {
        try {
            return objectMapper.readValue(message, Message.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Flux<Message> getMessages() {
       return repository
               .findAll()
               .concatWith(
                   connection.getNotifications()
                       .map(Notification::getParameter)
                       .map(this::toMessage)
               );
    }

    // Routes
    //------------------------------------------------------------------------------------------------------------------
    @Bean
    public RouterFunction<ServerResponse> messagesRoute() {
        return route(GET("/messages"), req -> ok().body(getMessages(), Message.class));
    }

    @Bean
    public RouterFunction<ServerResponse> postMessageRoute() {
        return route(POST("/messages"), req -> {
            repository.save(req.bodyToMono(Message.class).block());
            return ok().build();
        });
    }


    // Repository
    //------------------------------------------------------------------------------------------------------------------
    interface MessageReactiveRepository extends ReactiveCrudRepository<Message, Long> {}

    // Model
    //------------------------------------------------------------------------------------------------------------------
    @Table
    public record Message(@Id Long id, String message, MessageOwner messageOwner) {}
    public enum MessageOwner { USER, SYSTEM }
}