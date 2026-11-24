package org.rgddallas.media.web.rest;

import jakarta.servlet.http.HttpServletResponse;
import org.rgddallas.media.entity.VideoPlayback;
import org.rgddallas.media.entity.VideoPlaybackRepository;
import org.rgddallas.media.util.FileSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private final String audioLocation;
    private final String videoLocation;
    private final String videoKirtanLocation;

    public MediaResource(VideoPlaybackRepository videoPlaybackRepo,
                         @Value("${media.audio-location:" + AUDIO_LOCATION + "}") String audioLocation,
                         @Value("${media.video-location:" + VIDEO_LOCATION + "}") String videoLocation,
                         @Value("${media.video-kirtan-location:" + VIDEO_KIRTAN_LOCATION + "}") String videoKirtanLocation) {
        this.videoPlaybackRepo = videoPlaybackRepo;
        this.audioLocation = audioLocation;
        this.videoLocation = videoLocation;
        this.videoKirtanLocation = videoKirtanLocation;
    }

    /**
     * Gets the audio file content from the server.
     *
     * @param fileName file to return, with no spaces
     * @return audio content as a byte stream
     */
    @GetMapping(value = {"/audio/{fileName}", "/audio/{fileName}/",
            "/audio2/{fileName}", "/audio2/{fileName}/"}, produces = "audio/mpeg")
    public ResponseEntity<byte[]> getAudio(@PathVariable("fileName") String fileName) {
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
    public void getTodaysVideoToPlay(HttpServletResponse response) throws IOException {

        List<VideoPlayback> videoSeqList = (List<VideoPlayback>) videoPlaybackRepo.findAll();

        VideoPlayback videoPlayback = videoSeqList.isEmpty() ? new VideoPlayback() : videoSeqList.getFirst();
        long sequenceToUse = videoPlayback.getFileSequence() == null ? 1L : videoPlayback.getFileSequence();
        LocalDateTime lastPlayed = videoPlayback.getLastPlayed();
        if (lastPlayed != null && lastPlayed.toLocalDate().equals(LocalDateTime.now().toLocalDate())) {
            sequenceToUse--;
        }

        File file = new File(getFileAsInputStream(videoLocation, "0*" + sequenceToUse + "_.*", MP4_EXTN));
        if (file.isFile() && file.canRead()) {
            updateSequence(videoPlayback, sequenceToUse + 1L);
        } else {//reset the sequence to 1 to start playing from the first video
            log.info("File not found for sequence {}, defaulting to Sequence number 1", sequenceToUse);
            file = new File(getFileAsInputStream(videoLocation, "0*1_.*", MP4_EXTN));
        }
        writeVideo(file, response);
    }

    /**
     * REST Endpoint to send the video kirtna file stream.
     *
     * @param response
     * @throws IOException
     */
    @GetMapping(value = {"/video-kirtan", "/video-kirtan/"}, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void getVideoToPlay(HttpServletResponse response) throws IOException {
        File[] files = new File(videoKirtanLocation).listFiles(file -> file.isFile() && file.canRead());
        if (files == null || files.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        writeVideo(files[new Random().nextInt(files.length)], response);
    }

    private void writeVideo(File file, HttpServletResponse response) throws IOException {
        if (!file.isFile() || !file.canRead()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setContentLengthLong(file.length());
        Files.copy(file.toPath(), response.getOutputStream());
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
                response = ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg")).body(byteStream);
            } else {
                throw new FileNotFoundException();
            }
        } catch (FileNotFoundException e) {
            log.error("No file found ", e);
            response = ResponseEntity.badRequest().body("File Not Found".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("IO Exception ", e);
            response = ResponseEntity.internalServerError().body("Internal Server Error".getBytes(StandardCharsets.UTF_8));
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
        FileSearchService fileSearchService = new FileSearchService();

        String fileToSearch = fileName + fileExtension;
        log.debug("file to search {}", fileToSearch);
        List<String> filesFound = null;

        if (MP4_EXTN.equals(fileExtension)) {
            filesFound = fileSearchService.searchDirectory(new File(fileLocation), fileToSearch, true);

        } else {
            filesFound = fileSearchService.searchDirectory(new File(fileLocation), fileToSearch, false);

        }

        log.debug("files found : {}", filesFound);

        filesFound.forEach(file -> log.debug("List of files found {}", file));

        return filesFound.isEmpty() ? null : Files.readAllBytes(new File(filesFound.getFirst()).toPath());
    }

    /**
     * @param fileLocation
     * @param fileName
     * @param fileExtension
     * @return
     * @throws IOException
     */
    public String getFileAsInputStream(String fileLocation, String fileName, String fileExtension) throws IOException {
        FileSearchService fileSearchService = new FileSearchService();

        String fileFound = "";

        String fileToSearch = fileName + fileExtension;
        log.debug("file to search {}", fileToSearch);
        List<String> filesFound = null;

        if (MP4_EXTN.equals(fileExtension)) {
            filesFound = fileSearchService.searchDirectory(new File(fileLocation), fileToSearch, true);

        } else if (KIRTAN.equals(fileExtension)) {
            filesFound = fileSearchService.searchDirectory(new File(fileLocation), fileLocation, true);
        }

        else {
            filesFound = fileSearchService.searchDirectory(new File(fileLocation), fileToSearch, false);
        }

        log.debug("files found : {}", filesFound);

        filesFound.forEach(file -> log.debug("List of files found {}", file));

        if (!CollectionUtils.isEmpty(filesFound)) {
            fileFound = filesFound.get(0);

        }

        return fileFound;
    }
}
