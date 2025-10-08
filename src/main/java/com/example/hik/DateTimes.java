
package com.example.hik;

import java.time.*; import java.time.format.DateTimeFormatter;

public class DateTimes {
  private static final DateTimeFormatter MYSQL=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  public static String toMySQLDateTime(String input){
    if(input==null||input.isEmpty()) return null;
    try{ OffsetDateTime odt=OffsetDateTime.parse(input, DateTimeFormatter.ISO_OFFSET_DATE_TIME); return odt.toLocalDateTime().format(MYSQL);}catch(Exception ignore){}
    try{ LocalDateTime ldt=LocalDateTime.parse(input, DateTimeFormatter.ISO_LOCAL_DATE_TIME); return ldt.format(MYSQL);}catch(Exception ignore){}
    try{ String s=input.replace('T',' '); int p=s.indexOf('+'); if(p>0) s=s.substring(0,p); int m=s.indexOf('-',11); if(m>10) s=s.substring(0,m); int d=s.indexOf('.'); if(d>0) s=s.substring(0,d); if(s.length()>=19) s=s.substring(0,19); return s;}catch(Exception e){ return input; }
  }
}
