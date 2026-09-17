package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.UniversityDto;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ExternalApiServiceImpl implements ExternalApiService {

    private final WebClient webClient;

    /**
     * Construye ExternalApiServiceImpl sin dependencias inyectadas.
     */
    public ExternalApiServiceImpl() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS)));

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("http://universities.hipolabs.com")
                .build();
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "universidades", key = "'ecuador'")
    /**
     * Get universities of ecuador.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    public List<UniversityDto> getUniversitiesOfEcuador() {
        log.info("Iniciando consumo de API externa de universidades de Hipo Labs...");
        try {
            return this.webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search").queryParam("country", "Ecuador").build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response -> {
                        log.error("Error cliente 4xx consumiendo API externa: {}", response.statusCode());
                        return Mono.error(new RuntimeException("API Externa retornó error de cliente: " + response.statusCode()));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, response -> {
                        log.error("Error servidor 5xx consumiendo API externa: {}", response.statusCode());
                        return Mono.error(new RuntimeException("API Externa retornó error de servidor: " + response.statusCode()));
                    })
                    .bodyToFlux(UniversityDto.class)
                    .collectList()
                    // Reintentos con Backoff Exponencial (3 intentos, inicial de 1 segundo)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(throwable -> !(throwable instanceof WebClientResponseException.BadRequest))
                            .doBeforeRetry(retrySignal -> log.warn("Reintentando petición a API externa. Intento: {}", retrySignal.totalRetries() + 1))
                    )
                    // Respuesta controlada ante errores (onErrorResume)
                    .onErrorResume(throwable -> {
                        log.error("Fallo definitivo tras reintentos al consumir API de universidades. Causa: {}", throwable.getMessage());
                        // Fallback con la universidad local UTEQ
                        UniversityDto localU = UniversityDto.builder()
                                .name("Universidad Técnica Estatal de Quevedo (Fallback Local)")
                                .country("Ecuador")
                                .domains(Collections.singletonList("uteq.edu.ec"))
                                .webPages(Collections.singletonList("https://www.uteq.edu.ec"))
                                .build();
                        return Mono.just(Collections.singletonList(localU));
                    })
                    .block();
        } catch (Exception e) {
            log.error("Error inesperado en getUniversitiesOfEcuador: {}", e.getMessage());
            return Collections.singletonList(
                    UniversityDto.builder()
                            .name("Universidad Técnica Estatal de Quevedo (Fallback Local)")
                            .country("Ecuador")
                            .domains(Collections.singletonList("uteq.edu.ec"))
                            .webPages(Collections.singletonList("https://www.uteq.edu.ec"))
                            .build()
            );
        }
    }
}
