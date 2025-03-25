package org.argocd.workshop;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Map;

@Path("/api/info")
public class InfoResource {

  @Inject @ConfigProperty(name = "app.name")
  String appName;

  @Inject @ConfigProperty(name = "app.version")
  String appVersion;

  @Inject @ConfigProperty(name = "app.environment")
  String environment;

  @Inject @ConfigProperty(name = "app.feature.toggle")
  boolean featureToggle;

  @Inject @ConfigProperty(name = "app.secret")
  String secret;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public AppInfo getInfo() {
    return new AppInfo(
      appName,
      appVersion,
      environment,
      featureToggle,
      secret.equals("not-set") ? "NO SECRET CONFIGURED" : secret,
      System.getenv().getOrDefault("HOSTNAME", "unknown")
    );
  }
}