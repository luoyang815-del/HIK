
package com.example.hik;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.*; import java.util.*;

public class Config {
  public static class Device { public String name, host, username, password; public int port; public boolean https, insecureTLS; public Mapping mapping; public Filter filter; }
  public static class Fetch { public boolean preferJson; public String historyApi; public int timeoutSeconds, streamReconnectSeconds; }
  public static class Mapping { public Map<String,String> readerDirection; public List<Integer> successMinorCodes; }
  public static class Filter { public List<String> allowedDirections; public boolean onlySuccess, includeUnknownDirection; }
  public static class Sink {
    public Http http; public Jdbc jdbc;
    public static class Http { public boolean enabled; public String mode,url; public Map<String,String> headers; public int batchSize,timeoutSeconds; public Retry retry;
      public String endpointBase, tableName, basicUsername, basicPassword, aesKey, aesIv, companyCode, enterpriseId, enterpriseName, secondGate, typeDefault; }
    public static class Jdbc { public boolean enabled; public String driver,url,username,password,insertSql; }
    public static class Retry { public int maxRetries, backoffMillis; }
  }
  public static class Debug { public boolean enabled; public int printFirstBytes; }
  public Device device; public List<Device> devices; public Fetch fetch; public Mapping mapping; public Filter filter; public Sink sink; public Debug debug;
  public static Config load() throws Exception {
    String prop=System.getProperty("config"), env=System.getenv("HIK_CONFIG");
    String[] cands = new String[]{prop,env,"application.yaml","config/application.yaml"};
    ObjectMapper yaml=new ObjectMapper(new YAMLFactory());
    for(String c:cands){ if(c==null||c.isEmpty()) continue; File f=new File(c); if(f.exists()&&f.isFile()) try(InputStream in=new FileInputStream(f)){ return yaml.readValue(in,Config.class);} }
    try(InputStream in=Config.class.getClassLoader().getResourceAsStream("application.yaml")){ if(in==null) throw new IllegalStateException("application.yaml not found"); return yaml.readValue(in,Config.class); }
  }
}
