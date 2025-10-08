
package com.example.hik;

public class Main {
  public static void main(String[] args) throws Exception{
    if(args.length==0){ System.out.println("Usage:\n  pull <startISO> <endISO>\n  stream\n  config"); System.exit(1); }
    Config cfg=Config.load();
    switch(args[0]){
      case "pull":
        if(args.length<3){ System.err.println("pull 需要 startISO 和 endISO"); System.exit(2); }
        for(Config.Device dev:effective(cfg)){ System.out.println("== Pull from "+(dev.name!=null?dev.name:dev.host)+" =="); PullAcsEvents.pullOne(cfg,dev,args[1],args[2]); }
        break;
        case "stream":
          // 实时追尾
          StreamAcsEvents.run(cfg);
          break;
      case "config":
        System.out.println(new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(cfg)); break;
      case "poll":
        PollAcsEvents.run(cfg);
        break;
      default:
        System.err.println("Unknown command: "+args[0]);
    }
  }
  private static java.util.List<Config.Device> effective(Config cfg){ java.util.List<Config.Device> l=new java.util.ArrayList<>(); if(cfg.devices!=null&&!cfg.devices.isEmpty()) l.addAll(cfg.devices); else if(cfg.device!=null) l.add(cfg.device); else throw new IllegalStateException("No device configured"); return l; }
}
