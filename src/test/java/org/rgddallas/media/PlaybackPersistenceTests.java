package org.rgddallas.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rgddallas.media.entity.VideoPlayback;
import org.rgddallas.media.entity.VideoPlaybackRepository;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybackPersistenceTests {

    @Test
    void importsLegacyPlaybackAndKeepsRowsAndSequenceAcrossRestart(@TempDir Path directory) throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("imported").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        Path script = directory.resolve("legacy.sql");
        try (var fixture = getClass().getResourceAsStream("/legacy-playback.sql")) {
            Files.copy(Objects.requireNonNull(fixture), script);
        }
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("RUNSCRIPT FROM '" + script.toString().replace("'", "''") + "' FROM_1X");
        }

        LocalDateTime legacyTimestamp = LocalDateTime.parse("2018-05-15T13:42:00.123456");
        LocalDateTime newTimestamp = LocalDateTime.parse("2026-11-22T08:30:00");
        try (var context = start(url)) {
            var repository = context.getBean(VideoPlaybackRepository.class);
            var legacy = repository.findById(42L).orElseThrow();
            assertThat(legacy.getFileSequence()).isEqualTo(17L);
            assertThat(legacy.getLastPlayed()).isEqualTo(legacyTimestamp);
            legacy.setFileSequence(18L);
            repository.save(legacy);

            var next = new VideoPlayback();
            next.setFileSequence(23L);
            next.setLastPlayed(newTimestamp);
            assertThat(repository.save(next).getId()).isEqualTo(43L);
        }

        try (var context = start(url)) {
            var repository = context.getBean(VideoPlaybackRepository.class);
            assertThat(repository.findAll()).hasSize(2);
            var legacy = repository.findById(42L).orElseThrow();
            assertThat(legacy.getFileSequence()).isEqualTo(18L);
            assertThat(legacy.getLastPlayed()).isEqualTo(legacyTimestamp);
            var next = repository.findById(43L).orElseThrow();
            assertThat(next.getFileSequence()).isEqualTo(23L);
            assertThat(next.getLastPlayed()).isEqualTo(newTimestamp);
            assertThat(repository.save(new VideoPlayback()).getId()).isEqualTo(44L);
        }
    }

    private ConfigurableApplicationContext start(String url) {
        return new SpringApplicationBuilder(WebappmediaApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + url + ";IFEXISTS=TRUE",
                        "--spring.datasource.username=sa", "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=validate", "--logging.file.name=");
    }
}
