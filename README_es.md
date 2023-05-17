{\rtf1\ansi\ansicpg1252\cocoartf2709
\cocoatextscaling0\cocoaplatform0{\fonttbl\f0\fswiss\fcharset0 Helvetica;}
{\colortbl;\red255\green255\blue255;}
{\*\expandedcolortbl;;}
\paperw11900\paperh16840\margl1440\margr1440\vieww11520\viewh8400\viewkind0
\pard\tx566\tx1133\tx1700\tx2267\tx2834\tx3401\tx3968\tx4535\tx5102\tx5669\tx6236\tx6803\pardirnatural\partightenfactor0

\f0\fs24 \cf0 ## ZeroBounce Java SDK\
\
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.zerobounce.java/zerobouncesdk/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.zerobounce.java/zerobouncesdk) [![Build Status](https://github.com/zerobounce/zero-bounce-java-sdk-setup/actions/workflows/publish.yml/badge.svg?branch=master)](https://github.com/zerobounce/zero-bounce-java-sdk-setup/actions/workflows/publish.yml)\
\
Este SDK contiene m\'e9todos para interactuar f\'e1cilmente con la API de ZeroBounce.\
Puedes encontrar m\'e1s informaci\'f3n sobre ZeroBounce en la [documentaci\'f3n oficial](https://www.zerobounce.net/docs/).\
Este SDK est\'e1 construido utilizando la versi\'f3n Java 1.8.\
\
### Instalaci\'f3n\
\
Puedes instalar ZeroBounceSDK agregando la dependencia a tu archivo `pom.xml`:\
```xml\
<dependency>\
    <groupId>com.zerobounce.java</groupId>\
    <artifactId>zerobouncesdk</artifactId>\
    <version>1.1.4</version>\
</dependency>\
```\
\
## C\'f3mo utilizar el proyecto de ejemplo\
\
1. Compila el archivo JAR para el proyecto SDK.\
2. Aseg\'farate de que no haya otros archivos JAR ubicados en la cach\'e9 de Maven. En mi m\'e1quina, se encuentra aqu\'ed: `/.m2/repository/com/zerobounce/java/zerobouncesdk/`\
3. Ejecuta el siguiente comando desde la ra\'edz del proyecto:\
    ```shell\
    mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \\\
        -Dfile=zero-bounce-sdk/out/artifacts/zerobouncesdk_jar/zerobouncesdk.jar \\\
        -DgroupId=com.zerobounce.java \\\
        -DartifactId=zerobouncesdk \\\
        -Dversion=1.1.4 \\\
        -Dpackaging=jar \\\
        -DlocalRepositoryPath=local-libs\
    ```\
4. Ejecuta `mvn clean dependency:purge-local-repository` en el proyecto de ejemplo.\
5. Ejecuta `mvn compile` en el proyecto de ejemplo.\
6. Vuelve a compilar el proyecto de ejemplo.\
7. \'a1Ejecuta y disfruta!\
\
## C\'f3mo utilizar el SDK de este repositorio en tu proyecto\
\
Recomendamos encarecidamente que uses la \'faltima versi\'f3n disponible en Maven. Sin embargo, si planeas incluir la versi\'f3n del repositorio del SDK en tu proyecto, esto es lo que debes hacer:\
1. Crea una carpeta llamada *local-libs* en la ra\'edz de tu proyecto, que actuar\'e1 como un repositorio "local".\
2. En tu archivo `pom.xml`, pega el siguiente c\'f3digo que utilizar\'e1 un repositorio "local":\
    ```xml\
    <repositories>\
        <repository>\
            <id>local-repo</id>\
            <name>Local Repo</name>\
            <url>file://$\{project.basedir\}/local-libs</url>\
        </repository>\
    </repositories>\
    ```\
3. Dentro del bloque `<dependencies></dependencies>`, pega el siguiente c\'f3digo:\
    ```xml\
    <dependency>\
        <groupId>com.zerobounce.java</groupId>\
        <artifactId>zerobouncesdk</artifactId>\
        <version>1.1.4</version>\
    </dependency>\
    ```\
4. Sigue los pas\
\
os del 1 al 5 de la secci\'f3n ***C\'f3mo utilizar el proyecto de ejemplo*** anteriormente.\
5. Vuelve a compilar el proyecto.\
6. \'a1Disfruta!\
\
## C\'f3mo implementar el SDK\
\
1. Aseg\'farate de establecer el campo *autoReleaseAfterClose* en *false* en el archivo `pom.xml` de **zero-bounce-sdk** si no deseas que el artefacto se implemente autom\'e1ticamente en Maven Central.\
2. Utiliza el mismo comando que se encuentra en el archivo `publish.yml`: `mvn --no-transfer-progress --batch-mode -Dgpg.passphrase=<TU_FRASE_SECRETA> clean deploy -Prelease` desde la carpeta **zero-bounce-sdk**.\
\
## USO\
\
Inicializa el SDK con tu clave de API:\
```java\
ZeroBounceSDK.getInstance().initialize("<TU_CLAVE_DE_API>");\
```\
\
## Ejemplos\
\
A continuaci\'f3n, puedes utilizar cualquiera de los m\'e9todos del SDK. Por ejemplo:\
\
* ##### Validar una direcci\'f3n de correo electr\'f3nico\
    ```java\
    ZeroBounceSDK.getInstance().validate(\
        "<CUALQUIER_DIRECCI\'d3N_DE_CORREO>",\
        "<DIRECCI\'d3N_IP_OPCIONAL>",\
        new ZeroBounceSDK.OnSuccessCallback<ZBValidateResponse>() \{\
            @Override\
            public void onSuccess(ZBValidateResponse response) \{\
                System.out.println("validate response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("validate error=" + errorMessage);\
            \}\
        \});\
    );\
    ```\
\
* ##### Validar un lote de una lista de direcciones de correo electr\'f3nico\
    ```java\
    List<ZBValidateBatchData> datosDeEmails = new ArrayList<ZBValidateBatchData>();\
    datosDeEmails.add(new ZBValidateBatchData("valid@example.com", "1.1.1.1"));\
    datosDeEmails.add(new ZBValidateBatchData("invalid@example.com", "1.1.1.1"));\
    datosDeEmails.add(new ZBValidateBatchData("disposable@example.com", null));\
\
    ZeroBounceSDK.getInstance().validateBatch(\
        datosDeEmails,\
        new ZeroBounceSDK.OnSuccessCallback<ZBValidateBatchResponse>() \{\
            @Override\
            public void onSuccess(ZBValidateBatchResponse response) \{\
                System.out.println("validateBatch response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("validateBatch error=" + errorMessage);\
            \}\
        \});\
    );\
    ```\
\
* ##### Comprobar cu\'e1ntos cr\'e9ditos te quedan en tu cuenta\
    ```java\
    ZeroBounceSDK.getInstance().getCredits(\
        new ZeroBounceSDK.OnSuccessCallback<ZBCreditsResponse>() \{\
            @Override\
            public void onSuccess(ZBCreditsResponse response) \{\
                System.out.println("getCredits response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("getCredits error=" + errorMessage);\
            \}\
        \});\
    ```\
\
* ##### Ver el uso de tu API durante un per\'edodo de tiempo determinado\
    ```java\
    Date startDate = new Date();    // La fecha de inicio de cuando deseas ver el uso de la API\
    Date endDate = new Date();      // La fecha de finalizaci\'f3n de cuando deseas ver el uso de la API\
    \
\
\
    ZeroBounceSDK.getInstance().getApiUsage(\
        startDate, \
        endDate, \
        new ZeroBounceSDK.OnSuccessCallback<ZBGetApiUsageResponse>() \{\
            @Override\
            public void onSuccess(ZBGetApiUsageResponse response) \{\
                System.out.println("getApiUsage response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("getApiUsage error=" + errorMessage);\
            \}\
        \});\
    ```\
\
* ##### La API *sendFile* permite al usuario enviar un archivo para la validaci\'f3n masiva de correo electr\'f3nico\
    ```java\
    File miArchivo = new File("<RUTA_DEL_ARCHIVO>");  // El archivo csv o txt\
    int columnaDeDireccionDeCorreoElectronico = 3;             // El \'edndice de columna de la direcci\'f3n de correo electr\'f3nico en el archivo. El \'edndice comienza en 1\
    \
    ZeroBounceSDK.getInstance().sendFile(\
        miArchivo,\
        columnaDeDireccionDeCorreoElectronico,\
        null,\
        new ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse>() \{\
            @Override\
            public void onSuccess(ZBSendFileResponse response) \{\
                System.out.println("sendFile response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("sendFile error=" + errorMessage);\
            \}\
        \});\
    ```\
\
* ##### La API *getFile* permite a los usuarios obtener el archivo de resultados de validaci\'f3n para el archivo enviado mediante la API *sendFile*\
    ```java\
    String fileId = "<ID_DEL_ARCHIVO>";                    // El ID de archivo devuelto al llamar a la API sendfile\
    String rutaDeDescarga = "<RUTA_DE_DESCARGA_DEL_ARCHIVO>";   // La ruta donde se descargar\'e1 el archivo\
    \
    ZeroBounceSDK.getInstance().getFile(\
        fileId,\
        rutaDeDescarga,\
        new ZeroBounceSDK.OnSuccessCallback<ZBGetFileResponse>() \{\
            @Override\
            public void onSuccess(ZBGetFileResponse response) \{\
                System.out.println("getFile response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("getFile error=" + errorMessage);\
            \}\
        \});\
    ```\
\
* ##### Comprobar el estado de un archivo cargado mediante la API *sendFile*\
    ```java\
    String fileId = "<ID_DEL_ARCHIVO>";    // El ID de archivo devuelto al llamar a la API sendfile\
    \
    ZeroBounceSDK.getInstance().fileStatus(\
        fileId,\
        new ZeroBounceSDK.OnSuccessCallback<ZBFileStatusResponse>() \{\
            @Override\
            public void onSuccess(ZBFileStatusResponse response) \{\
                System.out.println("fileStatus response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("fileStatus error=" + errorMessage);\
            \}\
        \});\
    ```\
\
* ##### Eliminar el archivo que se envi\'f3 utilizando la API *sendFile*. El archivo solo se puede eliminar cuando su estado es `Complete`\
    ```java\
    String fileId = "<ID_DEL_ARCHIVO>";    // El ID de archivo devuelto al llamar a la API sendfile\
    \
    ZeroBounceSDK.getInstance().deleteFile(\
        fileId,\
        new ZeroBounceSDK.OnSuccessCallback<ZB\
\
DeleteFileResponse>() \{\
            @Override\
            public void onSuccess(ZBDeleteFileResponse response) \{\
                System.out.println("deleteFile response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("deleteFile error=" + errorMessage);\
            \}\
        \});\
    ```\
\
* ##### Obtener informaci\'f3n sobre el compromiso general de correo electr\'f3nico de tus suscriptores. La solicitud devuelve datos sobre aperturas, clics, reenv\'edos y cancelaciones de suscripci\'f3n que han ocurrido en los \'faltimos 30, 90, 180 o 365 d\'edas.\
    ```java\
    ZeroBounceSDK.getInstance().getActivityData(\
        "<CUALQUIER_DIRECCI\'d3N_DE_CORREO>",\
        new ZeroBounceSDK.OnSuccessCallback<ZBActivityDataResponse>() \{\
            @Override\
            public void onSuccess(ZBActivityDataResponse response) \{\
                System.out.println("getActivityData response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("getActivityData error=" + errorMessage);\
            \}\
        \});\
    ```\
\
### API de puntuaci\'f3n AI\
\
* ##### La API *scoringSendFile* permite al usuario enviar un archivo para la validaci\'f3n masiva de correo electr\'f3nico\
    ```java\
    File miArchivo = new File("<RUTA_DEL_ARCHIVO>");  // El archivo csv o txt\
    int columnaDeDireccionDeCorreoElectronico = 3;             // El \'edndice de columna de la direcci\'f3n de correo electr\'f3nico en el archivo. El \'edndice comienza en 1\
    \
    ZeroBounceSDK.getInstance().scoringSendFile(\
        miArchivo,\
        columnaDeDireccionDeCorreoElectronico,\
        null,\
        new ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse>() \{\
            @Override\
            public void onSuccess(ZBSendFileResponse response) \{\
                System.out.println("scoringSendFile response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("scoringSendFile error=" + errorMessage);\
            \}\
        \});\
    ```\
\
* ##### La API *scoringGetFile* permite a los usuarios obtener el archivo de resultados de validaci\'f3n para el archivo enviado mediante la API *scoringSendFile*\
    ```java\
    String fileId = "<ID_DEL_ARCHIVO>";                    // El ID de archivo devuelto al llamar a la API scoringSendfile\
    String rutaDeDescarga = "<RUTA_DE_DESCARGA_DEL_ARCHIVO>";   // La ruta donde se descargar\'e1 el archivo\
    \
    ZeroBounceSDK.getInstance().scoringGetFile(\
        fileId,\
        rutaDeDescarga,\
        new ZeroBounceSDK.OnSuccessCallback<ZBGetFileResponse>() \{\
            @Override\
            public void onSuccess(ZBGetFileResponse response) \{\
                System.out.println("scoringGetFile response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("scoringGetFile error=" + errorMessage);\
            \}\
        \});\
    ```\
\
* ##### Comprobar el estado de un archivo cargado mediante la API *scoringSendFile*\
    ```java\
    String fileId = "<ID_DEL_ARCHIVO>";    // El ID de archivo devuelto al llamar a la API scoringSendfile\
    \
    ZeroBounceSDK.getInstance().\
\
scoringFileStatus(\
        fileId,\
        new ZeroBounceSDK.OnSuccessCallback<ZBFileStatusResponse>() \{\
            @Override\
            public void onSuccess(ZBFileStatusResponse response) \{\
                System.out.println("scoringFileStatus response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("scoringFileStatus error=" + errorMessage);\
            \}\
        \});\
    ```\
\
* ##### Elimina el archivo que se envi\'f3 utilizando la API *scoringSendFile*. El archivo solo se puede eliminar cuando su estado es `Complete`\
    ```java\
    String fileId = "<ID_DEL_ARCHIVO>";    // El ID de archivo devuelto al llamar a la API scoringSendfile\
    \
    ZeroBounceSDK.getInstance().scoringDeleteFile(\
        fileId,\
        new ZeroBounceSDK.OnSuccessCallback<ZBDeleteFileResponse>() \{\
            @Override\
            public void onSuccess(ZBDeleteFileResponse response) \{\
                System.out.println("scoringDeleteFile response=" + response.toString());\
            \}\
        \}, new ZeroBounceSDK.OnErrorCallback() \{\
            @Override\
            public void onError(String errorMessage) \{\
                System.out.println("scoringDeleteFile error=" + errorMessage);\
            \}\
        \});\
    ```\
\
## Documentaci\'f3n\
Puedes generar la documentaci\'f3n utilizando tu IDE favorito o el comando javadoc de Maven.\
\
## Publicaci\'f3n\
Cada vez que se crea una nueva versi\'f3n, el flujo de trabajo de CI/CD se ejecutar\'e1 y se lanzar\'e1 un nuevo artefacto en Maven Central. \'a1No olvides actualizar la versi\'f3n antes de hacer un lanzamiento!\
Si cambias las credenciales de inicio de sesi\'f3n de OSSRH, tambi\'e9n deber\'e1s actualizar las variables del repositorio en Github.\
\
### Configuraci\'f3n local para la versi\'f3n manual\
Para poder publicar en el repositorio Nexus desde tu m\'e1quina local, debes seguir estos pasos:\
Si deseas publicar manualmente en el repositorio Nexus (y luego publicarlo en Maven Central), debes:\
\
1. Importar la clave GPG a tu m\'e1quina local (ver m\'e1s abajo).\
2. Establecer el valor de *autoReleaseAfterClose* en el archivo `pom.xml` de zero-bounce-sdk en *false*.\
3. Ejecutar el siguiente comando:\
   ```shell\
   # Para publicar en el repositorio de preparaci\'f3n\
   mvn --no-transfer-progress --batch-mode -Dgpg.passphrase=<TU_FRASE_SECRETA> clean deploy -Prelease\
   ```\
\
Luego, debes ir a [Nexus Sonatype](https://s01.oss.sonatype.org/), iniciar sesi\'f3n y luego abrir *Staging Repositories* y hacer clic en *Refresh*. Aqu\'ed ver\'e1s el artefacto que acabas de cargar. Para publicarlo, debes **cerrarlo** y luego **publicarlo**. Estas acciones tardar\'e1n algunos minutos en completarse. Despu\'e9s de **publicar** el artefacto, llevar\'e1:\
- algunas horas antes de que puedas verlo en el [Repositorio Maven](https://repo1.maven.org/maven2/com/zerobounce/android/zerobouncesdk/) y en la [B\'fasqueda de Sonatype](https://central.sonatype.com/artifact/com.zerobounce.android/zerobouncesdk/1.1.4)\
- 1-3 d\'edas antes de que puedas verlo en el [Repositorio MVN](https://mvnrepository.com/artifact/com.zerobounce.android/zerobouncesdk)\
\
## Exportaci\'f3n e importaci\'f3n de claves PGP\
1. Exporta las claves:\
   ```shell\
   gpg --list-keys  # Para obtener el hash de la clave para el siguiente paso\
   gpg --export -a <\'daLTIMOS_8_D\'cdGITOS> > public.key\
   gpg --export-secret-key -a <\'daLTIMOS_8_D\'cdGITOS> > private.key\
   ```\
\
2. Importa las claves:\
   ```shell\
   gpg --import public.key\
   gpg --import private.key\
   ```\
\
3. Verifica que las nuevas claves se hayan importado correctamente:\
   ```shell\
   gpg --list-keys\
   gpg --list-secret-keys\
   ```}