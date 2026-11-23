# Webappmedia

Webappmedia targets Java 21 and Spring Boot 4.2.0. The Maven wrapper is
configured for Maven 3.9.11. To build and run verification with local Maven:

```sh
mvn -B verify
```

## Default paths

| Setting | Default |
| --- | --- |
| `media.video.location` | `/volume1/video/Intranet/` |
| `media.kirtan.location` | `/volume1/video/Intranet/video_kirtan/` |
| `media.audio.location` | `/volume1/music/Arti/` |
| `spring.datasource.url` | `jdbc:h2:~/test;DB_CLOSE_ON_EXIT=FALSE` |
| `logging.file.name` | `./webappmedia.log` |

On a **new, empty database only**, initialize daily playback with
`PUT /playback/reset` before requesting `GET /api/video2`. Do not reset an
imported database just to start the upgraded app; reset changes playback state.

## Upgrading an existing H2 database

**Do not delete, overwrite, or discard your existing database.** The default
database name remains `~/test` under the application's OS user. H2 2.x cannot
open legacy H2 1.4 database files in place. Hibernate's `ddl-auto=update` does
not migrate H2's file format.

1. Stop the application and any other database clients. Make a separate,
   offline backup of the existing `test.mv.db` (or `test.h2.db` for older
   PageStore installations), or the files at your configured JDBC path. Keep
   the untouched backup and the previous application/H2 version for rollback.
2. Use the exact H2 JAR from your old deployment to export a **working copy**
   of the database to SQL. Use the actual credentials and database path
   (omit the `.mv.db` or `.h2.db` suffix). The example uses the default
   credentials and `IFEXISTS=TRUE` to avoid exporting an accidentally created
   empty database.

   ```sh
   java -cp /path/to/old/h2.jar org.h2.tools.Script \
     -url 'jdbc:h2:/backup/working-copy/test;IFEXISTS=TRUE' -user sa -password '' \
     -script /backup/webappmedia-export.sql
   ```

3. Import into a **new** database at a separate path with the H2 version
   managed by this build (2.5.252). Use `FROM_1X` for an H2 1.x export; omit
   it for a compatible 2.x SQL export.

   ```sh
   java -cp /path/to/h2-2.5.252.jar org.h2.tools.RunScript \
     -url 'jdbc:h2:/staging/test' -user sa -password '' \
     -script /backup/webappmedia-export.sql -options FROM_1X
   ```

4. Compare the staged `VIDEO_PLAYBACK` row count and the `ID`,
   `FILE_SEQUENCE`, and `LAST_PLAYED` values against the source. Check that
   `HIBERNATE_SEQUENCE` still increments by one and will issue an ID above
   the existing IDs. The upgraded app reuses this sequence. Start it against
   the staged URL via `SPRING_DATASOURCE_URL` and confirm `GET /playback` and
   `GET /playback/records` show the expected state before using endpoints
   that change it, including `GET /api/video2`.
5. After validation, either keep the staged URL or, while the app is stopped,
   move the original files to a safe archive and install the validated file at
   the original `~/test` location. Keep the original files and SQL export;
   if validation fails, stop and restore the old app with its original
   database instead of initializing an empty one.

Verification uses isolated databases, including a temporary file-backed H2
database seeded with a representative legacy SQL export. It does not open or
upgrade your existing database.
