### **FASE 01: Eliminación de aplicaciones instaladas de fábrica por defecto (Es subjetivo lo que quieras desinstalar, comandos usados para celular XIOMI)**



##### **PASO 01:** Eliminar el Navegador Mi de Xiaomi. Utilizando Android Studio. La instalación de Android Studio se omitirá. Asi que considerando que tenemos instalado Android Stidio procedemos a entrar a la consola y damos el siguiente codigo para eliminar la aplicación al inicio indicada.

##### 

##### *adb shell pm uninstall -k --user 0 com.mi.globalbrowser*

##### *adb shell pm uninstall -k --user 0 com.android.browser*

##### 

##### **NOTA:** Comandos de reeversión

##### 

##### *adb shell cmd package install-existing com.mi.globalbrowser*

##### *adb shell cmd package install-existing com.android.browser*

##### 

##### 

##### **PASO 02:** Eliminar GetApps (La Tienda de Xiaomi)

##### 

##### *adb shell pm uninstall -k --user 0 com.xiaomi.mipicks*

##### *adb shell pm uninstall -k --user 0 com.miui.discover*

##### 

##### **NOTA:** Comandos de reeversión

##### *adb shell cmd package install-existing com.xiaomi.mipicks*

##### *adb shell cmd package install-existing com.miui.discover*



##### 

##### **PASO 03 (Eliminar cualquier app arraigada que te incomode):** Das al comando en Android Studio:

##### 

##### *adb shell pm list packages*

##### 

##### Seleecionar el packages y le das al comando de esta forma (ejemplos de apps para desinstalar, no se pueden por la interfaz del celular ya que vienen instalados de fabrica y por eso se usa esta desinstalación forzada):

##### 

##### *adb shell pm uninstall -k --user 0 com.miui.videoplayer*

##### 

##### *adb shell pm uninstall -k --user 0 com.google.android.apps.youtube.music*

##### 

##### *adb shell pm uninstall -k --user 0 com.xiaomi.mipicks*

##### 

##### *adb shell pm uninstall -k --user 0 com.miui.discover*

##### 

##### NOTA: Los comandos de reeversión poseen esta forma:



##### *adb shell cmd package install-existing com.xiaomi.mipicks*



##### *adb shell cmd package install-existing com.miui.discover*

