package ch.rasc.upload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import me.desair.tus.server.TusFileUploadService;
import me.desair.tus.server.exception.TusException;
import me.desair.tus.server.upload.UploadInfo;

@Controller
@CrossOrigin(exposedHeaders = { "Location", "Upload-Offset" })
public class UploadController {

  private final TusFileUploadService tusFileUploadService;

  private final Path uploadDirectory;

  private final Path tusUploadDirectory;

  public UploadController(TusFileUploadService tusFileUploadService,
      AppProperties appProperties) {
    this.tusFileUploadService = tusFileUploadService;

    this.uploadDirectory = Paths.get(appProperties.getAppUploadDirectory());
    try {
      Files.createDirectories(this.uploadDirectory);
    }
    catch (IOException e) {
      Application.logger.error("create upload directory", e);
    }

    this.tusUploadDirectory = Paths.get(appProperties.getTusUploadDirectory());
  }

  // When we had 'customparam' here, it was passed through only on the first request per file. Any subsequent requests (for the rest of the same file) didn't have customparam (neither in HttpServletRequest, not passed through Spring).
  @RequestMapping(value = { "/upload/{id}/{hash}/*"/*, "/upload/**"*/ }, method = { RequestMethod.POST,
      RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.DELETE, RequestMethod.GET }/*,
      params = {"customparam"}/**/)
  public void upload(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
    /*, @RequestParam(required=false, defaultValue="NONE") String customparam/**/
    @PathVariable String id, @PathVariable String hash
  ) throws IOException {
    String uploadURI = servletRequest.getRequestURI();
    Application.logger.info("UPLOAD HTTP request method: " +servletRequest.getMethod());
    Application.logger.info("UPLOAD URI: " +uploadURI);
    Application.logger.info("CUSTOM-HEADER: " +servletRequest.getHeader("CUSTOM-HEADER"));
    Application.logger.info("customparam: " +servletRequest.getParameter("customparam"));
    Application.logger.info("URI custom first part (identifier): " +id);
    Application.logger.info("URI custom second part (hash): " +hash);
    this.tusFileUploadService.process(servletRequest, servletResponse);

    UploadInfo uploadInfo = null;
    try {
      uploadInfo = this.tusFileUploadService.getUploadInfo(uploadURI);
    }
    catch (IOException | TusException e) {
      Application.logger.error("get upload info", e);
    }

    if (uploadInfo != null) Application.logger.info("UPLOAD FILENAME: " +uploadInfo.getFileName());
    if (uploadInfo != null && !uploadInfo.isUploadInProgress()) {
      try (InputStream is = this.tusFileUploadService.getUploadedBytes(uploadURI)) {
        Path output = this.uploadDirectory.resolve(uploadInfo.getFileName());
        Files.copy(is, output, StandardCopyOption.REPLACE_EXISTING);
      }
      catch (IOException | TusException e) {
        Application.logger.error("get uploaded bytes", e);
      }

      try {
        this.tusFileUploadService.deleteUpload(uploadURI);
      }
      catch (IOException | TusException e) {
        Application.logger.error("delete upload", e);
      }
    }
  }


  @Scheduled(fixedDelayString = "PT24H")
  private void cleanup() {
    Path locksDir = this.tusUploadDirectory.resolve("locks");
    if (Files.exists(locksDir)) {
      try {
        this.tusFileUploadService.cleanup();
      }
      catch (IOException e) {
        Application.logger.error("error during cleanup", e);
      }
    }
  }

}
