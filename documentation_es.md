##### Instalación

Puedes instalar ZeroBounceSDK agregando la dependencia a tu archivo `pom.xml`:
```xml
<dependency>
    <groupId>com.zerobounce.java</groupId>
    <artifactId>zerobouncesdk</artifactId>
    <version>1.2.1</version>
</dependency>
```


#### Ejecución del proyecto

Para ejecutarlo con una versión más reciente de Java, debe agregar los siguientes parámetros al compilar el código: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED`. En IntelliJ IDEA, debe ir a `Editar configuración` y copiar y pegar estos parámetros en el campo de texto `Opciones de máquina virtual`.


#### Cómo utilizar el proyecto de ejemplo

1. Compila el archivo JAR para el proyecto SDK.
2. Asegúrate de que no haya otros archivos JAR ubicados en la caché de Maven. En mi máquina, se encuentra aquí: `~/.m2/repository/com/zerobounce/java/zerobouncesdk/`
3. Ejecuta el siguiente comando desde la raíz del proyecto:
    ```shell
    mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
        -Dfile=zero-bounce-sdk/out/artifacts/zerobouncesdk_jar/zerobouncesdk.jar \
        -DgroupId=com.zerobounce.java \
        -DartifactId=zerobouncesdk \
        -Dversion=1.2.1 \
        -Dpackaging=jar \
        -DlocalRepositoryPath=local-libs
    ```
4. Ejecuta `mvn clean dependency:purge-local-repository` en el proyecto de ejemplo.
5. Ejecuta `mvn compile` en el proyecto de ejemplo.
6. Vuelve a compilar el proyecto de ejemplo.
7. ¡Ejecuta y disfruta!

#### Cómo utilizar el SDK de este repositorio en tu proyecto

Recomendamos encarecidamente que uses la última versión disponible en Maven. Sin embargo, si planeas incluir la versión del repositorio del SDK en tu proyecto, esto es lo que debes hacer:
1. Crea una carpeta llamada *local-libs* en la raíz de tu proyecto, que actuará como un repositorio "local".
2. En tu archivo `pom.xml`, pega el siguiente código que utilizará un repositorio "local":
    ```xml
    <repositories>
        <repository>
            <id>local-repo</id>
            <name>Local Repo</name>
            <url>file://${project.basedir}/local-libs</url>
        </repository>
    </repositories>
    ```
3. Dentro del bloque `<dependencies></dependencies>`, pega el siguiente código:
    ```xml
    <dependency>
        <groupId>com.zerobounce.java</groupId>
        <artifactId>zerobouncesdk</artifactId>
        <version>1.2.1</version>
    </dependency>
    ```
4. Sigue los pas
    os del 1 al 5 de la sección ***Cómo utilizar el proyecto de ejemplo*** anteriormente.
5. Vuelve a compilar el proyecto.
6. ¡Disfruta!

#### Cómo implementar el SDK

1. Asegúrate de establecer el campo *autoReleaseAfterClose* en *false* en el archivo `pom.xml` de **zero-bounce-sdk** si no deseas que el artefacto se implemente automáticamente en Maven Central.
2. Utiliza el mismo comando que se encuentra en el archivo `publish.yml`: `mvn --no-transfer-progress --batch-mode -Dgpg.passphrase=<TU_FRASE_SECRETA> clean deploy -Prelease` desde la carpeta **zero-bounce-sdk**.

#### USO

Inicializa el SDK con tu clave de API:
```java
ZeroBounceSDK.getInstance().initialize("<TU_CLAVE_DE_API>");
```
```java
ZeroBounceSDK.getInstance().initialize("<YOUR_API_KEY>", timeoutInMillis);
```

#### Control del registro del SDK

De forma predeterminada, el SDK escribe los metadatos de las solicitudes HTTP y sus respuestas en la
salida estándar. Si esos valores pueden contener datos sensibles, desactiva el registro del SDK o
redirige los mensajes a tu propio sistema de registro antes de realizar cualquier llamada a la API:

```java
// Desactivar todos los registros del SDK
ZeroBounceSDK.setLoggingEnabled(false);

// O reenviarlos al registrador de tu aplicación
ZeroBounceSDK.setLogger(message -> LOGGER.info(message));
```

Pasar `null` a `ZeroBounceSDK.setLogger(...)` restablece el registrador a una implementación que no
realiza ninguna acción, lo cual equivale a desactivar el registro.

#### Ejemplos

A continuación, puedes utilizar cualquiera de los métodos del SDK. Por ejemplo:

* ####### Validar una dirección de correo electrónico
    ```java
    ZeroBounceSDK.getInstance().validate(
        "<CUALQUIER_DIRECCIÓN_DE_CORREO>",
        "<DIRECCIÓN_IP_OPCIONAL>",
        new ZeroBounceSDK.OnSuccessCallback<ZBValidateResponse>() {
            @Override
            public void onSuccess(ZBValidateResponse response) {
                System.out.println("validate response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("validate error=" + errorMessage);
            }
        });
    );
    ```

* ####### Validar un lote de una lista de direcciones de correo electrónico
    ```java
    List<ZBValidateBatchData> datosDeEmails = new ArrayList<ZBValidateBatchData>();
    datosDeEmails.add(new ZBValidateBatchData("valid@example.com", "1.1.1.1"));
    datosDeEmails.add(new ZBValidateBatchData("invalid@example.com", "1.1.1.1"));
    datosDeEmails.add(new ZBValidateBatchData("disposable@example.com", null));

    ZeroBounceSDK.getInstance().validateBatch(
        datosDeEmails,
        new ZeroBounceSDK.OnSuccessCallback<ZBValidateBatchResponse>() {
            @Override
            public void onSuccess(ZBValidateBatchResponse response) {
                System.out.println("validateBatch response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("validateBatch error=" + errorMessage);
            }
        });
    );
    ```

* ####### Encuentre el correo electrónico y otros formatos de dominio según un nombre de dominio determinado
    ```java
    ZeroBounceSDK.getInstance().guessFormat(
        "<DOMINIO_A_PRUEBA>",
        null,
        null,
        null,
        new ZeroBounceSDK.OnSuccessCallback<ZBEmailFinderResponse>() {
            @Override
            public void onSuccess(ZBEmailFinderResponse response) {
                System.out.println("guessFormat response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("guessFormat error=" + errorMessage);
            }
        });
    );
    ```

* ####### Comprobar cuántos créditos te quedan en tu cuenta
    ```java
    ZeroBounceSDK.getInstance().getCredits(
        new ZeroBounceSDK.OnSuccessCallback<ZBCreditsResponse>() {
            @Override
            public void onSuccess(ZBCreditsResponse response) {
                System.out.println("getCredits response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("getCredits error=" + errorMessage);
            }
        });
    ```

* ####### Ver el uso de tu API durante un período de tiempo determinado
    ```java
    Date startDate = new Date();    // La fecha de inicio de cuando deseas ver el uso de la API
    Date endDate = new Date();      // La fecha de finalización de cuando deseas ver el uso de la API
    


    ZeroBounceSDK.getInstance().getApiUsage(
        startDate, 
        endDate, 
        new ZeroBounceSDK.OnSuccessCallback<ZBGetApiUsageResponse>() {
            @Override
            public void onSuccess(ZBGetApiUsageResponse response) {
                System.out.println("getApiUsage response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("getApiUsage error=" + errorMessage);
            }
        });
    ```

* ####### La API *sendFile* permite al usuario enviar un archivo para la validación masiva de correo electrónico
    ```java
    File miArchivo = new File("<RUTA_DEL_ARCHIVO>");  // El archivo csv o txt
    int columnaDeDireccionDeCorreoElectronico = 3;             // El índice de columna de la dirección de correo electrónico en el archivo. El índice comienza en 1
    
    ZeroBounceSDK.getInstance().sendFile(
        miArchivo,
        columnaDeDireccionDeCorreoElectronico,
        null,
        new ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse>() {
            @Override
            public void onSuccess(ZBSendFileResponse response) {
                System.out.println("sendFile response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("sendFile error=" + errorMessage);
            }
        });
    ```

* ####### La API *getFile* permite a los usuarios obtener el archivo de resultados de validación para el archivo enviado mediante la API *sendFile*
    ```java
    String fileId = "<ID_DEL_ARCHIVO>";                    // El ID de archivo devuelto al llamar a la API sendfile
    String rutaDeDescarga = "<RUTA_DE_DESCARGA_DEL_ARCHIVO>";   // La ruta donde se descargará el archivo
    
    ZeroBounceSDK.getInstance().getFile(
        fileId,
        rutaDeDescarga,
        new ZeroBounceSDK.OnSuccessCallback<ZBGetFileResponse>() {
            @Override
            public void onSuccess(ZBGetFileResponse response) {
                System.out.println("getFile response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("getFile error=" + errorMessage);
            }
        });
    ```

* ####### Comprobar el estado de un archivo cargado mediante la API *sendFile*
    ```java
    String fileId = "<ID_DEL_ARCHIVO>";    // El ID de archivo devuelto al llamar a la API sendfile
    
    ZeroBounceSDK.getInstance().fileStatus(
        fileId,
        new ZeroBounceSDK.OnSuccessCallback<ZBFileStatusResponse>() {
            @Override
            public void onSuccess(ZBFileStatusResponse response) {
                System.out.println("fileStatus response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("fileStatus error=" + errorMessage);
            }
        });
    ```

* ####### Eliminar el archivo que se envió utilizando la API *sendFile*. El archivo solo se puede eliminar cuando su estado es `Complete`
    ```java
    String fileId = "<ID_DEL_ARCHIVO>";    // El ID de archivo devuelto al llamar a la API sendfile
    
    ZeroBounceSDK.getInstance().deleteFile(
        fileId,
        new ZeroBounceSDK.OnSuccessCallback<ZB

DeleteFileResponse>() {
@Override
public void onSuccess(ZBDeleteFileResponse response) {
System.out.println("deleteFile response=" + response.toString());
}
}, new ZeroBounceSDK.OnErrorCallback() {
@Override
public void onError(String errorMessage) {
System.out.println("deleteFile error=" + errorMessage);
}
});
```

* ####### Obtener información sobre el compromiso general de correo electrónico de tus suscriptores. La solicitud devuelve datos sobre aperturas, clics, reenvíos y cancelaciones de suscripción que han ocurrido en los últimos 30, 90, 180 o 365 días.
    ```java
    ZeroBounceSDK.getInstance().getActivityData(
        "<CUALQUIER_DIRECCIÓN_DE_CORREO>",
        new ZeroBounceSDK.OnSuccessCallback<ZBActivityDataResponse>() {
            @Override
            public void onSuccess(ZBActivityDataResponse response) {
                System.out.println("getActivityData response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("getActivityData error=" + errorMessage);
            }
        });
    ```

##### API de puntuación AI

* ####### La API *scoringSendFile* permite al usuario enviar un archivo para la validación masiva de correo electrónico
    ```java
    File miArchivo = new File("<RUTA_DEL_ARCHIVO>");  // El archivo csv o txt
    int columnaDeDireccionDeCorreoElectronico = 3;             // El índice de columna de la dirección de correo electrónico en el archivo. El índice comienza en 1
    
    ZeroBounceSDK.getInstance().scoringSendFile(
        miArchivo,
        columnaDeDireccionDeCorreoElectronico,
        null,
        new ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse>() {
            @Override
            public void onSuccess(ZBSendFileResponse response) {
                System.out.println("scoringSendFile response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("scoringSendFile error=" + errorMessage);
            }
        });
    ```

* ####### La API *scoringGetFile* permite a los usuarios obtener el archivo de resultados de validación para el archivo enviado mediante la API *scoringSendFile*
    ```java
    String fileId = "<ID_DEL_ARCHIVO>";                    // El ID de archivo devuelto al llamar a la API scoringSendfile
    String rutaDeDescarga = "<RUTA_DE_DESCARGA_DEL_ARCHIVO>";   // La ruta donde se descargará el archivo
    
    ZeroBounceSDK.getInstance().scoringGetFile(
        fileId,
        rutaDeDescarga,
        new ZeroBounceSDK.OnSuccessCallback<ZBGetFileResponse>() {
            @Override
            public void onSuccess(ZBGetFileResponse response) {
                System.out.println("scoringGetFile response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("scoringGetFile error=" + errorMessage);
            }
        });
    ```

* ####### Comprobar el estado de un archivo cargado mediante la API *scoringSendFile*
    ```java
    String fileId = "<ID_DEL_ARCHIVO>";    // El ID de archivo devuelto al llamar a la API scoringSendfile
    
    ZeroBounceSDK.getInstance().

scoringFileStatus(
fileId,
new ZeroBounceSDK.OnSuccessCallback<ZBFileStatusResponse>() {
@Override
public void onSuccess(ZBFileStatusResponse response) {
System.out.println("scoringFileStatus response=" + response.toString());
}
}, new ZeroBounceSDK.OnErrorCallback() {
@Override
public void onError(String errorMessage) {
System.out.println("scoringFileStatus error=" + errorMessage);
}
});
```

* ####### Elimina el archivo que se envió utilizando la API *scoringSendFile*. El archivo solo se puede eliminar cuando su estado es `Complete`
    ```java
    String fileId = "<ID_DEL_ARCHIVO>";    // El ID de archivo devuelto al llamar a la API scoringSendfile
    
    ZeroBounceSDK.getInstance().scoringDeleteFile(
        fileId,
        new ZeroBounceSDK.OnSuccessCallback<ZBDeleteFileResponse>() {
            @Override
            public void onSuccess(ZBDeleteFileResponse response) {
                System.out.println("scoringDeleteFile response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("scoringDeleteFile error=" + errorMessage);
            }
        });
    ```

#### Documentación
Puedes generar la documentación utilizando tu IDE favorito o el comando javadoc de Maven.

#### Publicación
Cada vez que se crea una nueva versión, el flujo de trabajo de CI/CD se ejecutará y se lanzará un nuevo artefacto en Maven Central. ¡No olvides actualizar la versión antes de hacer un lanzamiento!
Si cambias las credenciales de inicio de sesión de OSSRH, también deberás actualizar las variables del repositorio en Github.

##### Configuración local para la versión manual
Para poder publicar en el repositorio Nexus desde tu máquina local, debes seguir estos pasos:
Si deseas publicar manualmente en el repositorio Nexus (y luego publicarlo en Maven Central), debes:

1. Importar la clave GPG a tu máquina local (ver más abajo).
2. Establecer el valor de *autoReleaseAfterClose* en el archivo `pom.xml` de zero-bounce-sdk en *false*.
3. Ejecutar el siguiente comando:
   ```shell
   ### Para publicar en el repositorio de preparación
   mvn --no-transfer-progress --batch-mode -Dgpg.passphrase=<TU_FRASE_SECRETA> clean deploy -Prelease
   ```

Luego, debes ir a [Nexus Sonatype](https://s01.oss.sonatype.org/), iniciar sesión y luego abrir *Staging Repositories* y hacer clic en *Refresh*. Aquí verás el artefacto que acabas de cargar. Para publicarlo, debes **cerrarlo** y luego **publicarlo**. Estas acciones tardarán algunos minutos en completarse. Después de **publicar** el artefacto, llevará:
- algunas horas antes de que puedas verlo en el [Repositorio Maven](https://repo1.maven.org/maven2/com/zerobounce/java/zerobouncesdk/) y en la [Búsqueda de Sonatype](https://central.sonatype.com/artifact/com.zerobounce.java/zerobouncesdk/1.2.1)
- 1-3 días antes de que puedas verlo en el [Repositorio MVN](https://mvnrepository.com/artifact/com.zerobounce.java/zerobouncesdk)

#### Exportación e importación de claves PGP
1. Exporta las claves:
   ```shell
   gpg --list-keys  ### Para obtener el hash de la clave para el siguiente paso
   gpg --export -a <ÚLTIMOS_8_DÍGITOS> > public.key
   gpg --export-secret-key -a <ÚLTIMOS_8_DÍGITOS> > private.key
   ```

2. Importa las claves:
   ```shell
   gpg --import public.key
   gpg --import private.key
   ```

3. Verifica que las nuevas claves se hayan importado correctamente:
   ```shell
   gpg --list-keys
   gpg --list-secret-keys
   ```
