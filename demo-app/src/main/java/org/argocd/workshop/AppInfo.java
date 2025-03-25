package org.argocd.workshop;

public class AppInfo {
    public String appName;
    public String appVersion;
    public String environment;
    public boolean featureToggle;
    public String secret;
    public String podName;

    public AppInfo(String appName, String appVersion, String environment,
                   boolean featureToggle, String secret, String podName) {
        this.appName = appName;
        this.appVersion = appVersion;
        this.environment = environment;
        this.featureToggle = featureToggle;
        this.secret = secret;
        this.podName = podName;
    }
}