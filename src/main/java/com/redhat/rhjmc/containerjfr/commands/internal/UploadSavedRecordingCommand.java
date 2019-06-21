package com.redhat.rhjmc.containerjfr.commands.internal;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;

@Singleton
class UploadSavedRecordingCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final FileSystem fs;
    private final Path recordingsPath;

    @Inject
    UploadSavedRecordingCommand(ClientWriter cw, FileSystem fs, @Named("RECORDINGS_PATH") Path recordingsPath) {
        this.cw = cw;
        this.fs = fs;
        this.recordingsPath = recordingsPath;
    }

    @Override
    public String getName() {
        return "upload-saved";
    }

    @Override
    public void execute(String[] args) throws Exception {
        ResponseMessage response = doPost(args[0], args[1]);
        cw.println(String.format("[%s] %s", response.status, response.body));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            ResponseMessage response = doPost(args[0], args[1]);
            if (200 <= response.status.getStatusCode() && response.status.getStatusCode() <= 299) {
                return new FailureOutput(response.body);
            } else {
                return new StringOutput(response.body) ;
            }
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    // try-with-resources generates a "redundant" nullcheck in bytecode
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    private ResponseMessage doPost(String recordingName, String uploadUrl) throws IOException {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addBinaryBody("file", recordingsPath.resolve(recordingName).toFile(), ContentType.APPLICATION_OCTET_STREAM, recordingName);

        HttpPost post = new HttpPost(uploadUrl);
        post.setEntity(builder.build());

        try (
            CloseableHttpClient httpClient = HttpClients.createMinimal(new BasicHttpClientConnectionManager());
            CloseableHttpResponse response = httpClient.execute(post)
        ) {
            return new ResponseMessage(response.getStatusLine(), EntityUtils.toString(response.getEntity()));
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 2) {
            cw.println("Expected two arguments: recording name and upload URL");
            return false;
        }
        return true;
    }

    @Override
    public boolean isAvailable() {
        return fs.isDirectory(recordingsPath);
    }

    private static class ResponseMessage {
        final StatusLine status;
        final String body;

        ResponseMessage(StatusLine status, String body) {
            this.status = status;
            this.body = body;
        }
    }

}