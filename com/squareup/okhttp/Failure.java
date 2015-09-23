package com.squareup.okhttp;

class Failure {
    private final Throwable exception;
    private final Request request;

    public static class Builder {
        private Throwable exception;
        private Request request;

        public Builder request(Request request) {
            this.request = request;
            return this;
        }

        public Builder exception(Throwable exception) {
            this.exception = exception;
            return this;
        }

        public Failure build() {
            return new Failure();
        }
    }

    private Failure(Builder builder) {
        this.request = builder.request;
        this.exception = builder.exception;
    }

    public Request request() {
        return this.request;
    }

    public Throwable exception() {
        return this.exception;
    }
}
