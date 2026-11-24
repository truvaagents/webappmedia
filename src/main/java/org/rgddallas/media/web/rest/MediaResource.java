package org.rgddallas.media.web.rest;

import org.rgddallas.media.entity.VideoPlayback;
import org.rgddallas.media.entity.VideoPlaybackRepository;
import org.rgddallas.media.util.FileSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * This class contains resources the serves the media content - audio and video as REST controllers.
 * <p>
 * Created by Neelabh on 3/12/2017.
 */

@RestController
@RequestMapping("/api")
@CrossOrigin
public class MediaResource {
    public static final Logger log = LoggerFactory.getLogger(MediaResource.class);

    public static final String MP4_EXTN = ".mp4";
    public static final String MP3_EXTN = ".mp3";
    public static final String KIRTAN = ".mov";
    public static final String VIDEO_LOCATION = "/volume1/video/Intranet/";
    public static final String VIDEO_KIRTAN_LOCATION = "/volume1/video/Intranet/video_kirtan/";
    //public static final String VIDEO_KIRTAN_LOCATION = "\\\\192.168.1.144\\video\\Intranet\\video_kirtan\\";
    //public static final String VIDEO_LOCATION = "\\\\192.168.1.144\\video\\Intranet\\";
    public static final String AUDIO_LOCATION = "/volume1/music/Arti/";
    //public static final String AUDIO_LOCATION = "C:\\temp\\";

    private final VideoPlaybackRepository videoPlaybackRepo;
    private final String videoLocation;
    private final String videoKirtanLocation;
    private final String audioLocation;

    public MediaResource(VideoPlaybackRepository videoPlaybackRepo,
                         @Value("${media.video-location:" + VIDEO_LOCATION + "}") String videoLocation,
                         @Value("${media.video-kirtan-location:" + VIDEO_KIRTAN_LOCATION + "}") String videoKirtanLocation,
                         @Value("${media.audio-location:" + AUDIO_LOCATION + "}") String audioLocation) {
        this.videoPlaybackRepo = videoPlaybackRepo;
        this.videoLocation = videoLocation;
        this.videoKirtanLocation = videoKirtanLocation;
        this.audioLocation = audioLocation;
    }

    /**
     * Gets the audio file content from the server.
     *
     * @param fileName file to return, with no spaces
     * @return audio content as a byte stream
     */
    @GetMapping(value = {"/audio/{fileName}", "/audio/{fileName}/"}, produces = "audio/mpeg")
    public ResponseEntity<byte[]> getAudio(@PathVariable String fileName) {
        log.info("Got Audio file request for file {}", fileName);

        return getMediaAsStream(audioLocation, fileName, MP3_EXTN);
    }

    /**
     * REST controller to return the content of the video file to be played. This is based on the current sequence
     * number stored in the DB. This number gets updated once in a day. If the request is received on same day
     * then same file is returned and the sequence remains unchanged.
     *
     */
    @GetMapping(value = {"/video2", "/video2/"}, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void getTodaysVideoToPlay(OutputStream output) throws IOException {

        List<VideoPlayback> videoSeqList = videoPlaybackRepo.findAll();
        VideoPlayback videoPlayback = videoSeqList.isEmpty() ? new VideoPlayback() : videoSeqList.get(0);
        long sequenceToUse = videoPlayback.getFileSequence() == null ? 1L : videoPlayback.getFileSequence();
        if (videoPlayback.getLastPlayed() != null
                && LocalDateTime.now().toLocalDate().equals(videoPlayback.getLastPlayed().toLocalDate())) {
            sequenceToUse--;
        }

        Path file = findVideo(sequenceToUse);
        if (file != null && Files.isReadable(file)) {
            updateSequence(videoPlayback, sequenceToUse + 1L);
        } else {
            log.info("File not found for sequence {}, defaulting to Sequence number 1", sequenceToUse);
            file = findVideo(1L);
            if (file == null) {
                throw new FileNotFoundException("No video found for sequence 1");
            }
        }

        Files.copy(file, output);
    }

    private Path findVideo(long sequence) {
        List<String> files = new FileSearchService()
                .searchDirectory(new File(videoLocation), "[0]*" + sequence + "_.*\\.mp4", true);
        return files.isEmpty() ? null : Path.of(files.get(0));
    }

    /**
     * REST Endpoint to send the video kirtna file stream.
     *
     * @param output
     * @throws IOException
     */
    @GetMapping(value = {"/video-kirtan", "/video-kirtan/"}, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void getVideoToPlay(OutputStream output) throws IOException {

        File[] files = new File(videoKirtanLocation).listFiles(File::isFile);
        if (files == null || files.length == 0) {
            throw new FileNotFoundException("No kirtan video found");
        }

        int totalFiles = files.length;
        log.info("Total files inside the folder - {}", totalFiles);

        Random random = new Random();
        int index = random.nextInt(totalFiles);
        log.info("File - {}", index);

        Files.copy(files[index].toPath(), output);
    }

    /**
     * Increments the current sequence and save into the DB.
     *
     * @param videoPlayback the video playback entity
     * @param sequenceToSet optional sequence to set
     * @return the updated sequence
     */
    public Long updateSequence(VideoPlayback videoPlayback, Long sequenceToSet) {
        if (sequenceToSet == null) {
            sequenceToSet = videoPlayback.getFileSequence() + 1L;
        }

        videoPlayback.setFileSequence(sequenceToSet);
        videoPlayback.setLastPlayed(LocalDateTime.now());

        videoPlayback = videoPlaybackRepo.save(videoPlayback);

        log.debug("Incremented Sequence number and saved : {}", videoPlayback.getFileSequence());

        return videoPlayback.getFileSequence();
    }


    /**
     * Utility method to get the media content from file system and prepare a response entity.
     *
     * @param location      directory to search into
     * @param fileName      file name
     * @param fileExtension file extension - mp3 or mp4
     * @return the response entity with file content, if found
     */
    public ResponseEntity<byte[]> getMediaAsStream(String location, String fileName, String fileExtension) {
        ResponseEntity<byte[]> response;

        try {
            byte[] byteStream = getFileStreamAsByteArray(location, fileName, fileExtension);
            if (byteStream != null) {
                response = new ResponseEntity<>(byteStream, HttpStatus.OK);
            } else {
                throw new FileNotFoundException();
            }
        } catch (FileNotFoundException e) {
            log.error("No file found ", e);
            response = new ResponseEntity<>("File Not Found".getBytes(StandardCharsets.UTF_8), HttpStatus.BAD_REQUEST);
        } catch (IOException e) {
            log.error("IO Exception ", e);
            response = new ResponseEntity<>("Internal Server Error".getBytes(StandardCharsets.UTF_8),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    /**
     * Utility method.
     *
     * @param fileLocation
     * @param fileName
     * @param fileExtension
     * @return
     * @throws IOException
     */
    public byte[] getFileStreamAsByteArray(String fileLocation, String fileName, String fileExtension) throws IOException {
        List<String> filesFound = new FileSearchService().searchDirectory(new File(fileLocation),
                fileName + fileExtension, MP4_EXTN.equals(fileExtension));
        return filesFound.isEmpty() ? null : Files.readAllBytes(Path.of(filesFound.get(0)));
    }
}
