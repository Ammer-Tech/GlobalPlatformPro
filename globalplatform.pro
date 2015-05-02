-injars build
-injars lib/bcprov-jdk15on-151.jar(!META-INF/*)
-injars lib/jopt-simple-4.8.jar(!META-INF/*)
# JNA is library because we package everything back in
-libraryjars ext/apdu4j/apdu4j.jar
-libraryjars  <java.home>/lib/rt.jar
-libraryjars  <java.home>/lib/jce.jar
-outjars optimized-globalplatform.jar
-dontobfuscate
-dontoptimize
-keep public class pro.javacard.gp.GlobalPlatform {
    public <methods>;
}
-keep public class pro.javacard.gp.** { public <methods>; public <fields>; }

-keep public class pro.javacard.gp.GPTool {
    public static void main(java.lang.String[]);
}
# For enum-s (why this is not default?)
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-printseeds
-dontnote
