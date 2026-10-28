package co.edu.unisalle.students.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * "/" es la ruta que usa el health check por defecto de Elastic Beanstalk.
 * El campo "version" es el que se cambia en la demo para probar el pipeline.
 */
@RestController
public class HealthController {

    private final String version;
    private final Instant startedAt = Instant.now();

    public HealthController(@Value("${app.version}") String version) {
        this.version = version;
    }

    @GetMapping({"/", "/health"})
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "students-pipeline-api",
                "version", version,
                "startedAt", startedAt.toString()
        );
    }
}
