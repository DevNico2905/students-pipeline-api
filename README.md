# students-pipeline-api

API REST de gestión de estudiantes (Java 21 + Spring Boot) desplegada automáticamente con
**AWS CodePipeline → AWS CodeBuild → AWS Elastic Beanstalk**.

Taller — Actividad 5, Unidad 3 (Integración y entrega continua), Herramientas de Software II, Universidad de La Salle.

## Arquitectura del pipeline

```mermaid
flowchart LR
    Dev[Desarrollador<br/>git push main] --> GH[(GitHub<br/>repositorio)]
    GH -->|webhook / conexión| S1
    subgraph CP[AWS CodePipeline]
      S1[1. Fuente<br/>GitHub] --> S2[2. Construcción<br/>CodeBuild<br/>mvn verify]
      S2 --> S3[3. Despliegue<br/>Elastic Beanstalk]
    end
    S1 -.->|"artefacto: código fuente"| A[(S3<br/>bucket de artefactos)]
    S2 -.->|"artefacto: app.jar + Procfile"| A
    S3 --> EB[Entorno EB<br/>EC2 + nginx<br/>Corretto 21]
    EB --> U[Usuario<br/>http://...elasticbeanstalk.com]
```

| Etapa | Servicio | Qué hace | Entrada → Salida |
|---|---|---|---|
| Fuente | GitHub (conexión de CodePipeline) | Detecta el push a `main` y descarga el código | repo → `SourceArtifact` (zip en S3) |
| Construcción | CodeBuild | Compila, corre 5 tests JUnit y empaqueta | `SourceArtifact` → `BuildArtifact` (`app.jar` + `Procfile`) |
| Despliegue | Elastic Beanstalk | Reemplaza la versión en la instancia EC2 | `BuildArtifact` → nueva versión en línea |

Si un test falla, CodeBuild termina en `FAILED` y el pipeline se detiene: **no se despliega código roto**.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/` y `/health` | Estado + `version` (lo que se cambia en la demo) |
| GET | `/api/students` | Lista estudiantes |
| GET | `/api/students/{id}` | Obtiene uno (404 si no existe) |
| POST | `/api/students` | Crea (valida nombre, email y programa) |
| PUT | `/api/students/{id}` | Actualiza |
| DELETE | `/api/students/{id}` | Elimina |

Datos en memoria: se reinician en cada despliegue (suficiente para demostrar el pipeline).

## Archivos clave para el pipeline

- `buildspec.yml` — instrucciones de CodeBuild (Corretto 21, `mvn verify`, reportes JUnit, artefacto).
- `Procfile` — le dice a Beanstalk cómo arrancar: `java -jar app.jar`.
- `application.properties` — `server.port=${PORT:5000}` (nginx de Beanstalk reenvía al 5000) y `app.version`.
- `pom.xml` — `<finalName>app</finalName>` para que el jar siempre se llame `app.jar`.

---

# Guía paso a paso (AWS Academy Learner Lab)

> **Región:** `us-east-1` (N. Virginia) en todos los servicios.
> **Regla de oro del Learner Lab:** no puedes crear roles IAM. En **cada** asistente que pida un rol,
> elige el existente **`LabRole`** (y para EC2, **`LabInstanceProfile`**). Si el asistente intenta
> crear un rol nuevo, va a fallar.
>
> 📸 = captura para el informe / video.

## Paso 0 — Probar en local (5 min)

```bash
mvn verify                  # debe terminar con: Tests run: 5, Failures: 0 ... BUILD SUCCESS
java -jar target/app.jar    # abrir http://localhost:5000/health
```
**Por qué:** si falla aquí, también fallará en CodeBuild; es más rápido corregirlo en tu máquina.

## Paso 1 — Subir el repo a GitHub

```bash
git init && git add . && git commit -m "feat: API de estudiantes + buildspec para CodePipeline"
git branch -M main
git remote add origin https://github.com/<tu-usuario>/students-pipeline-api.git
git push -u origin main
```
📸 Repo en GitHub mostrando `buildspec.yml` y `Procfile` en la raíz.

## Paso 2 — Verificar la conexión con GitHub (ANTES de lo demás)

Consola → **CodePipeline** → menú izquierdo **Settings → Connections** → **Create connection** →
proveedor **GitHub** → nombre `github-unisalle` → **Connect to GitHub** → **Install a new app**
(autoriza el repo) → **Connect**. Debe quedar en estado **Available**.

**Por qué primero:** en algunos Learner Lab esta conexión está bloqueada. Si falla, no
pierdas tiempo configurando lo demás; avísame y pasamos al **Plan B** (al final de esta guía).

📸 Conexión en estado *Available*.

## Paso 3 — Crear la aplicación y el entorno de Elastic Beanstalk

Consola → **Elastic Beanstalk** → **Create application**:

1. **Environment tier:** Web server environment.
2. **Application name:** `students-pipeline-api`. Environment name: `students-pipeline-api-env`.
3. **Platform:** Java → branch **Corretto 21 running on 64bit Amazon Linux 2023** → versión recomendada.
4. **Application code:** *Sample application* (el pipeline subirá la nuestra después).
5. **Presets:** *Single instance (free tier eligible)* — sin balanceador, más barato y rápido.
6. **Service access:**
   - Service role: **Use an existing service role → `LabRole`**
   - EC2 key pair: `vockey` (opcional)
   - EC2 instance profile: **`LabInstanceProfile`**
7. Lo demás por defecto (instancia `t3.micro` o `t3.small`) → **Create**.

Espera 5–8 min hasta **Health: Ok** y abre la URL del entorno (verás la página de ejemplo de AWS).

**Por qué:** la etapa de despliegue de CodePipeline necesita un entorno de Beanstalk existente al cual apuntar.

📸 Entorno en verde con su URL.

## Paso 4 — Crear el pipeline (incluye el proyecto de CodeBuild)

Consola → **CodePipeline** → **Create pipeline** → **Build custom pipeline**:

**4.1 Configuración**
- Name: `students-pipeline`
- Execution mode: *Superseded* · Pipeline type: V2
- Service role: **Existing service role → `LabRole`**
- Advanced settings: Artifact store por defecto (crea el bucket S3 de artefactos).

**4.2 Etapa de Fuente**
- Provider: **GitHub (via GitHub App)** → conexión `github-unisalle`
- Repository: `<tu-usuario>/students-pipeline-api` · Branch: `main`
- Output artifact format: *CodePipeline default*
- Trigger: *Push* en `main` (normalmente ya viene así)

**4.3 Etapa de Construcción** → Provider **AWS CodeBuild** → **Create project** (abre otra ventana):
- Project name: `students-pipeline-build`
- Environment: Managed image · **EC2** · OS **Amazon Linux** · Runtime **Standard** ·
  Image **`aws/codebuild/amazonlinux-x86_64-standard:5.0`**
- Service role: **Existing service role → `LabRole`** y **desmarca**
  *“Allow AWS CodeBuild to modify this service role”*
- Buildspec: **Use a buildspec file** (toma el `buildspec.yml` del repo)
- Logs: CloudWatch activado → **Continue to CodePipeline**
- De vuelta: Build type *Single build* → Next.

**4.4 Etapa de Despliegue**
- Provider: **AWS Elastic Beanstalk**
- Application: `students-pipeline-api` · Environment: `students-pipeline-api-env`
- Input artifact: `BuildArtifact` (el de CodeBuild, **no** el de la fuente)

**4.5** Revisar → **Create pipeline**. Arranca solo la primera ejecución.

**Por qué el input del deploy debe ser `BuildArtifact`:** el código fuente no trae `app.jar`;
solo la salida de CodeBuild contiene el jar compilado y el `Procfile`.

📸 Cada pantalla del asistente (fuente, build, deploy) y el resumen final.

## Paso 5 — Ver la primera ejecución

Las tres etapas deben quedar en verde (~5–8 min en total).
- En **CodeBuild → Build history → (build) → Reports** se ven los 5 tests pasando.
- En la URL de Beanstalk, `/health` debe responder `"version":"1.0.0"`.

📸 Pipeline en verde · logs de CodeBuild · pestaña Reports · respuesta de `/health` y `/api/students`.

## Paso 6 — Probar el despliegue automático (el corazón del video)

1. En `src/main/resources/application.properties` cambia `app.version=1.0.0` → `app.version=1.1.0`.
2. `git commit -am "chore: version 1.1.0" && git push`
3. En CodePipeline verás una **nueva ejecución iniciada sola** con el mensaje del commit.
4. Al terminar, recarga `/health` → `"version":"1.1.0"`.

📸 Antes/después de `/health` · historial de ejecuciones con los dos commits.

**Demo extra (recomendada para el video):** rompe un test a propósito, por ejemplo cambiando
`.value("UP")` por `.value("DOWN")`, y haz push. El pipeline **se detiene en Construcción** y la app sigue
en 1.1.0. Esto demuestra la integración continua: el código roto no llega a producción. Luego revierte el cambio.

## Paso 7 — Limpieza (presupuesto del Learner Lab)

Al terminar de grabar: **Elastic Beanstalk → Actions → Terminate environment**. Opcionalmente, elimina también el pipeline,
el proyecto de CodeBuild y vacía el bucket de artefactos.
**Por qué:** el entorno de Beanstalk se vuelve a encender cada vez que inicias el lab y consume crédito.

---

## Plan B — si la conexión con GitHub está bloqueada en el Learner Lab

Usar **Amazon S3 como fuente**:
1. Crea un bucket con **versionado activado**.
2. En la etapa de Fuente elige **Amazon S3**, bucket y clave `source/students-pipeline-api.zip`;
   detección de cambios con **EventBridge** (recomendado).
3. Para "subir un cambio": `git archive -o students-pipeline-api.zip HEAD` y
   `aws s3 cp students-pipeline-api.zip s3://<bucket>/source/` (desde tu máquina con las
   credenciales del lab, en *AWS Details*, o desde CloudShell).

El resto del pipeline (CodeBuild + Beanstalk) es idéntico.

## Problemas comunes

| Síntoma | Causa probable | Solución |
|---|---|---|
| Error de permisos al crear pipeline/proyecto | El asistente intentó crear un rol | Elegir `LabRole` y desmarcar *modify role* |
| CodeBuild: `Unknown runtime version` | Imagen equivocada | Usar `amazonlinux-x86_64-standard:5.0` |
| Beanstalk *Degraded* / 502 Bad Gateway | La app no escucha en el 5000 o no arrancó | Revisar `server.port` y los logs: Beanstalk → Logs → *Request last 100 lines* |
| Deploy falla: no encuentra el jar | Input del deploy = `SourceArtifact` | Cambiarlo a `BuildArtifact` |
