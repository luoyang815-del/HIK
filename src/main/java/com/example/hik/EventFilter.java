
package com.example.hik;

import java.util.List;

public class EventFilter {
  private final Config cfg; private final Config.Device dev;
  public EventFilter(Config cfg, Config.Device dev){ this.cfg=cfg; this.dev=dev; }
  public boolean accept(NormalizedEvent e){
    List<String> allow=(dev.filter!=null&&dev.filter.allowedDirections!=null&&!dev.filter.allowedDirections.isEmpty())?dev.filter.allowedDirections:(cfg.filter!=null?cfg.filter.allowedDirections:null);
    if(allow!=null && !allow.isEmpty()){
      boolean contains=false; for(String want:allow){ if("ANY".equalsIgnoreCase(want)) {contains=true;break;} if(want.equalsIgnoreCase(e.direction)) {contains=true;break;} }
      if(!contains) return false;
      if("UNKNOWN".equalsIgnoreCase(e.direction)){
        boolean includeUnknown=(dev.filter!=null)?dev.filter.includeUnknownDirection:(cfg.filter!=null && cfg.filter.includeUnknownDirection);
        if(!includeUnknown) return false;
      }
    }
    boolean onlySuccess=(dev.filter!=null)?dev.filter.onlySuccess:(cfg.filter!=null && cfg.filter.onlySuccess);
    if(onlySuccess){ if(e.success==null || !e.success) return false; }
    return true;
  }
}
