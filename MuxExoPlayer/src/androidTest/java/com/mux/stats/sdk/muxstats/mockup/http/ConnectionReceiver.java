package com.mux.stats.sdk.muxstats.mockup.http;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.LinkedBlockingDeque;


public class ConnectionReceiver extends Thread {

    static final String TAG = "HTTPTest";

    boolean isRunning;
    InputStream httpInput;
    HttpRequestParser httpParser = new HttpRequestParser();
    BufferedReader reader;
    LinkedBlockingDeque<ServerAction> actions = new LinkedBlockingDeque<>(100);

    public ConnectionReceiver(InputStream httpInput) {
        this.httpInput = httpInput;
        reader = new BufferedReader(new InputStreamReader(httpInput));
    }

    public void kill() {
        isRunning = false;
        interrupt();
    }

    public ServerAction getNextAction() throws InterruptedException {
        return actions.take();
    }

    public void run() {
        isRunning = true;
        while (isRunning) {
            try {
                String httpRequest = waitForRequest();
                if (httpRequest == null || httpRequest.length() == 0) {
                    sleep(50);
                } else {
                    Log.w(TAG, "Received request:\n" + httpRequest);
                    processRequest(httpRequest + "\r\n");
                }
            } catch (IOException e) {
                // Connection closed
                isRunning = false;
            } catch (InterruptedException e) {
                // Someone killed the thread
                isRunning = false;
            } catch (HttpFormatException e) {
                // TODO handle this better
                e.printStackTrace();
            }
        }
    }

    /*
     * Wait for http request
     */
    private String waitForRequest() throws IOException {
        StringBuilder total = new StringBuilder();
        String line = reader.readLine();
        while (line != null && line.length() > 0) {
            total.append(line).append('\n');
            line = reader.readLine();
        }
        return (total.toString());
    }

    /*
     * Parse HTTP request
     */
    private void processRequest(String httpRequest) throws
            IOException, HttpFormatException, InterruptedException {

        httpParser.parseRequest(httpRequest);
        int range = 0;
        String rangeHeader = httpParser.getHeaderParam("Range");
        if (rangeHeader != null) {
            range = Integer.valueOf(rangeHeader.replaceAll("[^0-9]", ""));
            Log.i(TAG, "Got range header value: " + range);
        }
        actions.put(new ServerAction(ServerAction.SERVE_MEDIA_DATA, range));
//        actions.add(new ServerAction(ServerAction.SERVE_MEDIA_DATA));
    }
}