--- app/src/main/java/com/skippy/app/Model.kt
+++ app/src/main/java/com/skippy/app/Model.kt
@@ -24,6 +24,7 @@
     val location: String,
     val online: Boolean,
     val teachers: String,
+    val groups: String = "",
 )
 
 /** Result of GET api/reservation/{id}/details. */
