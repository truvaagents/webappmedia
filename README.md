# Webappmedia

Build with Java 21 and the Maven wrapper:

```sh
./mvnw -v
./mvnw -B verify
```

The default media directories are `/volume1/music/Arti/`, `/volume1/video/Intranet/`,
and `/volume1/video/Intranet/video_kirtan/`. To change them, set
`media.audio-location`, `media.video-location`, and `media.video-kirtan-location`
respectively.

## Migrate playback records from H2 1.x to 2.x

H2 2.x cannot open the old database file directly. Export it with the **same
H2 1.x jar that last wrote it**, then import the SQL into a **new** database
with the H2 2.x jar resolved by the new build (`2.5.252`). Do not put the
legacy jar on the new application's classpath.

1. Stop the old application so no writes occur during cutover. Back up the old
   database files (`test.mv.db` or `test.h2.db` at the default `~/test` JDBC
   path), and work from a copy. The JDBC path does not include the file suffix.
2. Export the copy and import into a separate empty destination. Set these
   shell variables to your actual absolute paths; the example assumes the
   original defaults of user `sa` and an empty password. Substitute your
   configured credentials as needed. The SQL export contains database data
   and may include credential hashes, so keep it private.

   ```sh
   OLD_H2=/secure/path/to/legacy-h2.jar
   NEW_H2=/secure/path/to/h2-2.5.252.jar
   OLD_DB=/secure/backup-copy/test
   NEW_DB=/secure/data/webappmedia-h2v2
   EXPORT=/secure/backup-copy/playback.sql

   java -cp "$OLD_H2" org.h2.tools.Script \
     -url "jdbc:h2:file:$OLD_DB;IFEXISTS=TRUE" -user sa -password '' \
     -script "$EXPORT"
   java -cp "$NEW_H2" org.h2.tools.RunScript \
     -url "jdbc:h2:file:$NEW_DB" -user sa -password '' \
     -script "$EXPORT" -options FROM_1X
   ```

   If import fails, fix the cause and retry into a fresh destination; an
   interrupted import may leave a partially populated file.
3. Compare `SELECT ID, FILE_SEQUENCE, LAST_PLAYED FROM VIDEO_PLAYBACK ORDER BY ID`
   in the source and destination using their respective H2 versions. Confirm
   the same IDs, sequence values and timestamps. The import also carries over
   `HIBERNATE_SEQUENCE`; in the H2 2.x destination, check that
   `INFORMATION_SCHEMA.SEQUENCES.BASE_VALUE` for `HIBERNATE_SEQUENCE` is greater
   than every existing playback ID. Do not reset it to 1.
4. Start the new app against the imported path explicitly, with schema
   validation to catch a wrong or incomplete database:

   ```sh
   SPRING_DATASOURCE_URL="jdbc:h2:file:$NEW_DB;IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE" \
   SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
   java -jar target/webappmedia-0.0.6-SNAPSHOT.jar
   ```

   Check `/playback` and `/playback/records` before loading the homepage video,
   since a successful video request updates playback state. Then verify audio
   and video playback from the homepage.
5. Retain the original database and old application for rollback. Stop the new
   application before restarting the old one against its untouched H2 1.x
   database. Playback changes made after cutover will not be on the old copy.
