# Webappmedia

Build with JDK 21 and Maven 3.6.3 or newer: `mvn -B verify`. The Maven Wrapper
has been updated to download Maven 3.10.2 from Maven Central. Run the packaged app with
`java -jar target/webappmedia-0.0.6-SNAPSHOT.jar`.

The default log remains `./webappmedia.log`. The default H2 database remains
`jdbc:h2:~/test;DB_CLOSE_ON_EXIT=FALSE` (`~` is the application user's home).
Media paths are unchanged and can be overridden with Spring properties:

| Property | Default |
| --- | --- |
| `media.video-location` | `/volume1/video/Intranet/` |
| `media.video-kirtan-location` | `/volume1/video/Intranet/video_kirtan/` |
| `media.audio-location` | `/volume1/music/Arti/` |

## Maven build security

OSV identifies `org.codehaus.plexus:plexus-utils` as fixed in 3.6.1 and
`org.apache.commons:commons-lang3` as fixed in 3.18.0. The POM pins
`plexus-utils` for `maven-resources-plugin` 3.5.0 and both libraries for
`spring-boot-maven-plugin` 4.2.0. These pins replace vulnerable versions
only on the affected plugins' classpaths; they do not change application
dependencies or the Spring Boot version.

## Upgrade an existing H2 database before starting the new app

Spring Boot 4.2.0 uses H2 2.5.252. H2 1.x files (including the old
`test.mv.db` or `test.h2.db`) cannot be upgraded in place by Hibernate.
**Never delete or overwrite the old database.** In particular, a newly
created `test.mv.db` alongside a legacy `test.h2.db` is not a migration.
Fresh installations with no existing database do not need these steps.

1. Stop the old app and any other writers. Make and verify an offline copy
   of the database files, and keep an untouched backup. Do the export
   against a separate working copy. Use the H2 jar that last wrote the old
   database (the original Boot 2.0.1.RELEASE BOM used H2 1.4.197). Use the
   actual username and password; examples below use the app defaults.
2. Export a full SQL script with the old jar. The JDBC URL uses the
   database basename, not its file extension. Use the old URL options if
   customized; for a legacy `.h2.db` add `;MV_STORE=FALSE` when using the
   old tool. `IFEXISTS=TRUE` prevents accidentally exporting an empty DB.

   ```sh
   java -cp /path/to/old-h2.jar org.h2.tools.Script \
     -url 'jdbc:h2:file:/migration/working-copy/test;IFEXISTS=TRUE' \
     -user sa -password '' -script /migration/test-export.sql
   ```

3. Import into a **new, unused** location with the Boot-managed H2 jar.
   Keep the SQL export as a backup too. If an import fails, retain the old
   files and use another unused destination after resolving the error.

   ```sh
   java -cp /path/to/h2-2.5.252.jar org.h2.tools.RunScript \
     -url 'jdbc:h2:file:/migration/upgraded/test' \
     -user sa -password '' -script /migration/test-export.sql -options FROM_1X
   ```

4. Compare the old copy and imported database: check the row count and
   `SELECT ID, FILE_SEQUENCE, LAST_PLAYED FROM VIDEO_PLAYBACK ORDER BY ID`.
   Keep `HIBERNATE_SEQUENCE` as well: its next value must be greater than
   all existing playback IDs. Test startup against the imported database
   using validation, not schema creation:

   ```sh
   java -jar target/webappmedia-0.0.6-SNAPSHOT.jar \
     '--spring.datasource.url=jdbc:h2:file:/migration/upgraded/test;IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE' \
     --spring.jpa.hibernate.ddl-auto=validate
   ```

5. After verifying the imported data, either keep the explicit datasource
   URL or stop the app and put the verified H2 2.x file at `~/test.mv.db`.
   Before using the default location, move any existing `test` database
   files to a separate, verified archive; **do not remove or overwrite
   them**. To roll back, stop the new app and point the old app and old H2
   driver to the archived original, not the H2 2.x file. Retain the upgraded
   file as well if it has received new writes.

The application keeps the existing `VIDEO_PLAYBACK` table and
`HIBERNATE_SEQUENCE` ID generator. The default Hibernate schema setting
remains `update`, not `create` or `create-drop`.
