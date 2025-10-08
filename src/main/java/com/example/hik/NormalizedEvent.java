
package com.example.hik;

import com.fasterxml.jackson.databind.JsonNode;

public class NormalizedEvent {
  public String eventTime, device, direction, name, employeeNo, cardNo, cardType, rawJson;
  public Boolean success; public Integer major, minor, doorNo, readerNo;
  public static NormalizedEvent fromAcsInfo(JsonNode ev, Config cfg, Config.Device dev){
    NormalizedEvent ne=new NormalizedEvent();
    ne.eventTime= ev.path("time").asText(null);
    if(ne.eventTime==null) ne.eventTime=ev.path("dateTime").asText(null);
    ne.major= ev.path("major").isInt()?ev.get("major").asInt(): null;
    ne.minor= ev.path("minor").isInt()?ev.get("minor").asInt(): null;
    ne.name  = ev.path("name").asText(null);
    ne.employeeNo = ev.path("employeeNoString").asText(null);
    ne.cardNo = ev.path("cardNo").asText(null);
    ne.cardType = ev.path("cardType").asText(null);
    ne.doorNo = ev.path("doorNo").isInt()?ev.get("doorNo").asInt(): null;
    ne.readerNo = ev.path("readerNo").isInt()?ev.get("readerNo").asInt(): null;
    java.util.List<Integer> ok=(dev.mapping!=null&&dev.mapping.successMinorCodes!=null)?dev.mapping.successMinorCodes:(cfg.mapping!=null?cfg.mapping.successMinorCodes:null);
    ne.success=(ne.minor!=null && ok!=null)? ok.contains(ne.minor):null;
    java.util.Map<String,String> dir=(dev.mapping!=null&&dev.mapping.readerDirection!=null)?dev.mapping.readerDirection:(cfg.mapping!=null?cfg.mapping.readerDirection:null);
    if(ne.readerNo!=null && dir!=null){ String d=dir.get(String.valueOf(ne.readerNo)); ne.direction=d!=null?d:"UNKNOWN"; } else ne.direction="UNKNOWN";
    ne.rawJson=ev.toString(); return ne;
  }
}
