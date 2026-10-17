package com.villaserena.api.piso;
public final class ValidacionesPersona {
 private ValidacionesPersona(){}
 public static boolean telefono(String value){if(value==null)return false;String s=value.trim();int digits=s.replaceAll("[^0-9]","").length();return s.length()<=30&&s.matches("\\+?[0-9][0-9 ()-]*")&&digits>=7&&digits<=15;}
}
