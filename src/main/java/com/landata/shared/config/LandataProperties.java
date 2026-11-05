package com.landata.shared.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "landata")
public class LandataProperties {
  private final Jwt jwt = new Jwt();
  private final Crypto crypto = new Crypto();
  private final Security security = new Security();
  private final Datasource datasource = new Datasource();
  private final Auth auth = new Auth();
  private final Ai ai = new Ai();
  private List<String> platformAdminEmails = new ArrayList<>(List.of("m@gmail.com"));
  private List<Integer> platformAdminBranchCodes = new ArrayList<>();

  public Jwt getJwt() { return jwt; }
  public Crypto getCrypto() { return crypto; }
  public Security getSecurity() { return security; }
  public Datasource getDatasource() { return datasource; }
  public Auth getAuth() { return auth; }
  public Ai getAi() { return ai; }
  public List<String> getPlatformAdminEmails() { return platformAdminEmails; }
  public void setPlatformAdminEmails(List<String> platformAdminEmails) {
    this.platformAdminEmails = platformAdminEmails == null ? new ArrayList<>() : platformAdminEmails;
  }
  public List<Integer> getPlatformAdminBranchCodes() { return platformAdminBranchCodes; }
  public void setPlatformAdminBranchCodes(List<Integer> platformAdminBranchCodes) {
    this.platformAdminBranchCodes = platformAdminBranchCodes == null ? new ArrayList<>() : platformAdminBranchCodes;
  }

  public boolean isPlatformAdminEmail(String email) {
    if (email == null || email.isBlank()) return false;
    String value = email.trim();
    return platformAdminEmails.stream().anyMatch(item -> item != null && value.equalsIgnoreCase(item.trim()));
  }

  public boolean hasPlatformAdminBranch(List<Integer> branches) {
    if (branches == null || branches.isEmpty() || platformAdminBranchCodes.isEmpty()) return false;
    return branches.stream().anyMatch(platformAdminBranchCodes::contains);
  }

  public static class Jwt {
    private String secret;
    private int accessMinutes = 15;
    private int refreshDays = 14;
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public int getAccessMinutes() { return accessMinutes; }
    public void setAccessMinutes(int accessMinutes) { this.accessMinutes = accessMinutes; }
    public int getRefreshDays() { return refreshDays; }
    public void setRefreshDays(int refreshDays) { this.refreshDays = refreshDays; }
  }

  public static class Crypto {
    private String keysetPath = "./data/tink-keyset.json";
    public String getKeysetPath() { return keysetPath; }
    public void setKeysetPath(String keysetPath) { this.keysetPath = keysetPath; }
  }

  public static class Security {
    private boolean allowPrivateNetworks = true;
    public boolean isAllowPrivateNetworks() { return allowPrivateNetworks; }
    public void setAllowPrivateNetworks(boolean allowPrivateNetworks) { this.allowPrivateNetworks = allowPrivateNetworks; }
  }

  public static class Datasource {
    private String allowedPorts = "1433,3306,5432,5433,1521,3307";
    public String getAllowedPorts() { return allowedPorts; }
    public void setAllowedPorts(String allowedPorts) { this.allowedPorts = allowedPorts; }
  }

  public static class Auth {
    private boolean exposeDevTokens = false;
    public boolean isExposeDevTokens() { return exposeDevTokens; }
    public void setExposeDevTokens(boolean exposeDevTokens) { this.exposeDevTokens = exposeDevTokens; }
  }

  public static class Ai {
    private String cursorBaseUrl = "https://api.cursor.com";
    private String platformApiKey = "";
    public String getCursorBaseUrl() { return cursorBaseUrl; }
    public void setCursorBaseUrl(String cursorBaseUrl) { this.cursorBaseUrl = cursorBaseUrl; }
    public String getPlatformApiKey() { return platformApiKey; }
    public void setPlatformApiKey(String platformApiKey) { this.platformApiKey = platformApiKey; }
  }
}
