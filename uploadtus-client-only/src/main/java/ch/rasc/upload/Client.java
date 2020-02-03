package ch.rasc.upload;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.tus.java.client.ProtocolException;
import io.tus.java.client.TusClient;
import io.tus.java.client.TusExecutor;
import io.tus.java.client.TusURLMemoryStore;
import io.tus.java.client.TusUpload;
import io.tus.java.client.TusUploader;

public class Client {

  public static void main(String args[])
      throws IOException, ProtocolException, InterruptedException {
    //var httpClient = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();

    Path testFile = Paths.get("/tmp/upload.png");
    // Upload file to server
    final TusClient client = new TusClient();
    String url = "http://localhost:8080/upload?CUSTOM-PARAMETER=custom-parameter-value"; // CUSTOM-PARAMETER was ignored
    // The URL here has to match URL set on the server with withUploadURI(). So we can't pass parameter-like values in the URL itself.
    //String url = "http://localhost:8080/upload/identifier/hash/";
    client.setUploadCreationURL(URI.create(url).toURL());
    client.enableResuming(new TusURLMemoryStore());
    client.setHeaders(Collections.singletonMap("CUSTOM-HEADER", "custom-header-value"));

    final TusUpload upload = new TusUpload(testFile.toFile());

    TusExecutor executor = new TusExecutor() {

      @Override
      protected void makeAttempt() throws ProtocolException, IOException {
        TusUploader uploader = client.resumeOrCreateUpload(upload);
        uploader.setChunkSize(1024);

        do {
          long totalBytes = upload.getSize();
          long bytesUploaded = uploader.getOffset();
          double progress = (double) bytesUploaded / totalBytes * 100;

          System.out.printf("Upload at %6.2f %%.\n", progress);
        }
        while (uploader.uploadChunk() > -1);

        uploader.finish();
      }

    };

    boolean success = executor.makeAttempts();

    if (success) {
      System.out.println("Upload successful");
    }
    else {
      System.out.println("Upload interrupted");
    }

  }
}
