package com.squareup.okhttp;

import com.squareup.okhttp.Response.Body;
import com.squareup.okhttp.Response.Receiver;
import com.squareup.okhttp.internal.http.ResponseHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class Dispatcher {
    private final Map<Object, List<Job>> enqueuedJobs;
    private final ThreadPoolExecutor executorService;

    static class RealResponseBody extends Body {
        private final InputStream in;
        private final ResponseHeaders responseHeaders;

        RealResponseBody(ResponseHeaders responseHeaders, InputStream in) {
            this.responseHeaders = responseHeaders;
            this.in = in;
        }

        public boolean ready() throws IOException {
            return true;
        }

        public MediaType contentType() {
            String contentType = this.responseHeaders.getContentType();
            return contentType != null ? MediaType.parse(contentType) : null;
        }

        public long contentLength() {
            return this.responseHeaders.getContentLength();
        }

        public InputStream byteStream() throws IOException {
            return this.in;
        }
    }

    Dispatcher() {
        this.executorService = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue());
        this.enqueuedJobs = new LinkedHashMap();
    }

    public synchronized void enqueue(OkHttpClient client, Request request, Receiver responseReceiver) {
        Job job = new Job(this, client, request, responseReceiver);
        List<Job> jobsForTag = (List) this.enqueuedJobs.get(request.tag());
        if (jobsForTag == null) {
            jobsForTag = new ArrayList(2);
            this.enqueuedJobs.put(request.tag(), jobsForTag);
        }
        jobsForTag.add(job);
        this.executorService.execute(job);
    }

    public synchronized void cancel(Object tag) {
        List<Job> jobs = (List) this.enqueuedJobs.remove(tag);
        if (jobs != null) {
            for (Job job : jobs) {
                this.executorService.remove(job);
            }
        }
    }

    synchronized void finished(Job job) {
        List<Job> jobs = (List) this.enqueuedJobs.get(job.tag());
        if (jobs != null) {
            jobs.remove(job);
        }
    }
}
