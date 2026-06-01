package com.kemaless.core.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.ai")
public class PlatformAiProperties {

    private final Copilot copilot = new Copilot();
    private final Rag rag = new Rag();
    private final Media media = new Media();
    private final Validation validation = new Validation();
    private final Memory memory = new Memory();

    public Copilot getCopilot() {
        return copilot;
    }

    public Rag getRag() {
        return rag;
    }

    public Media getMedia() {
        return media;
    }

    public Validation getValidation() {
        return validation;
    }

    public Memory getMemory() {
        return memory;
    }

    public static class Copilot {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Rag {
        private boolean enabled = false;
        private int topK = 5;
        private double similarityThreshold = 0.65;
        private boolean bootstrapMiniappsOnStartup = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public boolean isBootstrapMiniappsOnStartup() {
            return bootstrapMiniappsOnStartup;
        }

        public void setBootstrapMiniappsOnStartup(boolean bootstrapMiniappsOnStartup) {
            this.bootstrapMiniappsOnStartup = bootstrapMiniappsOnStartup;
        }
    }

    public static class Media {
        private boolean enabled = false;
        private final Minio minio = new Minio();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Minio getMinio() {
            return minio;
        }

        public static class Minio {
            private String endpoint = "";
            private String publicBaseUrl = "";
            private String accessKey = "";
            private String secretKey = "";
            private String bucket = "";
            private boolean bootstrapBucketOnStartup = true;

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint;
            }

            public String getPublicBaseUrl() {
                return publicBaseUrl;
            }

            public void setPublicBaseUrl(String publicBaseUrl) {
                this.publicBaseUrl = publicBaseUrl;
            }

            public String getAccessKey() {
                return accessKey;
            }

            public void setAccessKey(String accessKey) {
                this.accessKey = accessKey;
            }

            public String getSecretKey() {
                return secretKey;
            }

            public void setSecretKey(String secretKey) {
                this.secretKey = secretKey;
            }

            public String getBucket() {
                return bucket;
            }

            public void setBucket(String bucket) {
                this.bucket = bucket;
            }

            public boolean isBootstrapBucketOnStartup() {
                return bootstrapBucketOnStartup;
            }

            public void setBootstrapBucketOnStartup(boolean bootstrapBucketOnStartup) {
                this.bootstrapBucketOnStartup = bootstrapBucketOnStartup;
            }

            public boolean isComplete() {
                return endpoint != null && !endpoint.isBlank()
                        && accessKey != null && !accessKey.isBlank()
                        && secretKey != null && !secretKey.isBlank()
                        && bucket != null && !bucket.isBlank();
            }
        }
    }

    public static class Validation {
        private boolean checkOnStartup = true;
        private int timeoutSeconds = 5;

        public boolean isCheckOnStartup() {
            return checkOnStartup;
        }

        public void setCheckOnStartup(boolean checkOnStartup) {
            this.checkOnStartup = checkOnStartup;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Memory {
        private int maxHistoryMessages = 20;

        public int getMaxHistoryMessages() {
            return maxHistoryMessages;
        }

        public void setMaxHistoryMessages(int maxHistoryMessages) {
            this.maxHistoryMessages = maxHistoryMessages;
        }
    }
}
