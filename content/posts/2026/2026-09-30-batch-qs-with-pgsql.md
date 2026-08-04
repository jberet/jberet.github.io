---
layout: post
title:      "Enhancing the WildFly Batch-Processing Quickstart"
subtitle:   "A guide to database migration, credential management, TLS encryption, and authentication"
date:       2026-09-30
author:     Ashwin Mehendale
---

# Introduction

The WildFly [batch-processing quickstart](https://github.com/wildfly/quickstart/tree/main/batch-processing) demonstrates Jakarta Batch (JSR-352) job execution using an embedded H2 database as its job repository. H2 is convenient for local development but is not suitable for production: it runs in-process, stores data in memory by default, and offers no network-level security.

This guide walks through four incremental improvements to the quickstart:

1. **PostgreSQL as the job repository**: Replace the in-memory H2 database with a containerised PostgreSQL instance so that job execution data persists across server restarts.
2. **TLS for database connections**: Encrypt traffic between WildFly and PostgreSQL, and reject any unencrypted connection attempts.
3. **Application authentication**: Protect the web UI with an Elytron filesystem security realm so that only authorised users can access the application.
4. **HTTPS for the web application**: Terminate TLS at the WildFly HTTPS listener so that all browser traffic is encrypted end to end.

**Note**: The post only covers the `provisioned-server` Maven profile:


## Prerequisites

Before starting, ensure you have:

- **JDK 21** or later.
- **Maven 3.4** or later.
- **Podman** or **Docker** for running PostgreSQL.

**Windows users:** Commands in this guide are written for Linux and macOS (Bash). PowerShell equivalents are provided after each Bash block where the syntax differs. Ensure the following are available on your `PATH`:
- [OpenSSL for Windows](https://slproweb.com/products/Win32OpenSSL.html) (or use the `openssl` binary bundled with Git for Windows)
- [Podman Desktop for Windows](https://podman-desktop.io/) with the default WSL2 machine running
- `keytool` from your JDK installation (for example, `C:\Program Files\Eclipse Adoptium\jdk-21...\bin`)

## Part 1: PostgreSQL Container with TLS

Encrypt the communication between the PostgreSQL database and WildFly with TLS.

### Step 1: Generate TLS Certificates

Create a self-signed certificate that the PostgreSQL database presents to WildFly for verification.

***NOTE***: For production environments, ensure that you use only certificate authority (CA)-signed certificates.

Create a directory for certificates:

```bash
mkdir -p ~/postgres-certs
cd ~/postgres-certs
```

**Windows (PowerShell):**
```powershell
New-Item -ItemType Directory -Force -Path "$HOME\postgres-certs"
Set-Location "$HOME\postgres-certs"
```

Generate the server private key and a self-signed certificate:

```bash
# Generate a private key
openssl genrsa -out server.key 2048

# Set restrictive permissions to meet PostgreSQL requirements
chmod 600 server.key

# Generate a self-signed certificate
openssl req -new -x509 -key server.key -out server.crt -days 365 \
  -subj "/C=CA/ST=AB/L=Calgary/O=Organization/CN=localhost"

# Set permissions for the certificate
chmod 644 server.crt
```

**Windows (PowerShell):**
```powershell
# Generate a private key
openssl genrsa -out server.key 2048

# Restrict access to the private key (current user read-only)
icacls server.key /inheritance:r /grant:r "${env:USERNAME}:(R)"

# Generate a self-signed certificate
openssl req -new -x509 -key server.key -out server.crt -days 365 `
  -subj "/C=CA/ST=AB/L=Calgary/O=Organization/CN=localhost"

# No permission change needed for server.crt on Windows
```

**Important**: The Common Name (CN) must match how WildFly connects to PostgreSQL.

### Step 2: Map Certificate Ownership into the User Namespace

Assign the ownership of the certificates to UID 999. This ownership assignment is required when running rootless Podman, as done in this procedure.

```bash
podman unshare chown 999:999 ~/postgres-certs/server.key ~/postgres-certs/server.crt
```

Confirm the result:

```bash
podman unshare ls -n ~/postgres-certs/
```

Expected output:

```
-rw-r--r--. 1   0   0  352 ... pg_hba.conf
-rw-r--r--. 1   0   0  409 ... postgresql.conf
-rw-r--r--. 1 999 999 1294 ... server.crt
-rw-------. 1 999 999 1704 ... server.key
```

Note that `server.key` and `server.crt` are now owned by the UID `999`.

**Important:**
- You can no longer read `server.key` as your normal user. Use `podman unshare cat ~/postgres-certs/server.key` if you need to read it.
- Re-run this step every time you regenerate the certificates.

**Windows:** Skip this step. Podman Desktop for Windows runs containers inside a WSL2 virtual machine that handles UID mapping automatically. No manual `podman unshare` step is required.

### Step 3: Create PostgreSQL Configuration Files

Create `postgresql.conf` to enable TLS:

```bash
cat > postgresql.conf << 'EOF'
# TLS/SSL Configuration
ssl = on
ssl_cert_file = '/var/lib/postgresql/server.crt'
ssl_key_file = '/var/lib/postgresql/server.key'
ssl_ciphers = 'HIGH:MEDIUM:+3DES:!aNULL'
ssl_prefer_server_ciphers = on
ssl_min_protocol_version = 'TLSv1.3'

# Connection Settings
listen_addresses = '*'
max_connections = 100

# Enable prepared transactions (required for XA if needed in future)
max_prepared_transactions = 100
EOF
```

**Windows (PowerShell):**
```powershell
@'
# TLS/SSL Configuration
ssl = on
ssl_cert_file = '/var/lib/postgresql/server.crt'
ssl_key_file = '/var/lib/postgresql/server.key'
ssl_ciphers = 'HIGH:MEDIUM:+3DES:!aNULL'
ssl_prefer_server_ciphers = on
ssl_min_protocol_version = 'TLSv1.3'

# Connection Settings
listen_addresses = '*'
max_connections = 100

# Enable prepared transactions (required for XA if needed in future)
max_prepared_transactions = 100
'@ | Set-Content postgresql.conf
```

Create `pg_hba.conf` to require SSL connections:

```bash
cat > pg_hba.conf << 'EOF'
# TYPE  DATABASE        USER            ADDRESS                 METHOD
# Require SSL for all connections
hostssl all             all             0.0.0.0/0               scram-sha-256
hostssl all             all             ::0/0                   scram-sha-256

# Local connections
local   all             all                                     trust
EOF
```

**Windows (PowerShell):**
```powershell
@'
# TYPE  DATABASE        USER            ADDRESS                 METHOD
# Require SSL for all connections
hostssl all             all             0.0.0.0/0               scram-sha-256
hostssl all             all             ::0/0                   scram-sha-256

# Local connections
local   all             all                                     trust
'@ | Set-Content pg_hba.conf
```

### Step 4: Start PostgreSQL Container with TLS

Pull the PostgreSQL image:

```bash
podman pull docker.io/library/postgres:latest
```

Start the container with TLS enabled:

```bash
podman run -d \
  --name postgres-jberet-secure \
  -e POSTGRES_USER=batch_user \
  -e POSTGRES_PASSWORD=SecurePass123! \
  -e POSTGRES_DB=batch_db \
  -v ~/postgres-certs/server.crt:/var/lib/postgresql/server.crt:z \
  -v ~/postgres-certs/server.key:/var/lib/postgresql/server.key:z \
  -v ~/postgres-certs/postgresql.conf:/var/lib/postgresql/postgresql.conf:z \
  -v ~/postgres-certs/pg_hba.conf:/var/lib/postgresql/pg_hba.conf:z \
  -p 5432:5432 \
  postgres:16 \
  -c config_file=/var/lib/postgresql/postgresql.conf \
  -c hba_file=/var/lib/postgresql/pg_hba.conf
```

**Windows (PowerShell):**
```powershell
podman run -d `
  --name postgres-jberet-secure `
  -e POSTGRES_USER=batch_user `
  -e POSTGRES_PASSWORD=SecurePass123! `
  -e POSTGRES_DB=batch_db `
  -v "$HOME\postgres-certs\server.crt:/var/lib/postgresql/server.crt:z" `
  -v "$HOME\postgres-certs\server.key:/var/lib/postgresql/server.key:z" `
  -v "$HOME\postgres-certs\postgresql.conf:/var/lib/postgresql/postgresql.conf:z" `
  -v "$HOME\postgres-certs\pg_hba.conf:/var/lib/postgresql/pg_hba.conf:z" `
  -p 5432:5432 `
  postgres:16 `
  -c config_file=/var/lib/postgresql/postgresql.conf `
  -c hba_file=/var/lib/postgresql/pg_hba.conf
```

### Step 5: Verify PostgreSQL TLS Configuration

Check that PostgreSQL accepts SSL connections:

```bash
podman exec postgres-jberet-secure psql -U batch_user -d batch_db -c "SHOW ssl;"
```

Expected output:
```
 ssl 
-----
 on
(1 row)
```

Verify the TLS version:

```bash
podman exec -e PGPASSWORD='SecurePass123!' postgres-jberet-secure \
  psql "host=127.0.0.1 port=5432 user=batch_user dbname=batch_db sslmode=require" \
  -c "SELECT ssl, version AS tls_version, cipher
      FROM pg_stat_ssl JOIN pg_stat_activity USING (pid)
      WHERE pid = pg_backend_pid();"
```

**Windows (PowerShell):**
```powershell
$env:PGPASSWORD = 'SecurePass123!'
podman exec -e PGPASSWORD=$env:PGPASSWORD postgres-jberet-secure `
  psql "host=127.0.0.1 port=5432 user=batch_user dbname=batch_db sslmode=require" `
  -c "SELECT ssl, version AS tls_version, cipher FROM pg_stat_ssl JOIN pg_stat_activity USING (pid) WHERE pid = pg_backend_pid();"
```

Expected output:
```
 ssl | tls_version |         cipher         
-----+-------------+------------------------
 t   | TLSv1.3     | TLS_AES_256_GCM_SHA384
(1 row)
```

Confirm that unencrypted connections are refused:

```bash
podman exec -e PGPASSWORD='SecurePass123!' postgres-jberet-secure \
  psql "host=127.0.0.1 port=5432 user=batch_user dbname=batch_db sslmode=disable" -c "SELECT 1;"
```

**Windows (PowerShell):**
```powershell
$env:PGPASSWORD = 'SecurePass123!'
podman exec -e PGPASSWORD=$env:PGPASSWORD postgres-jberet-secure `
  psql "host=127.0.0.1 port=5432 user=batch_user dbname=batch_db sslmode=disable" -c "SELECT 1;"
```

Expected output:

```
psql: error: connection to server at "127.0.0.1", port 5432 failed: FATAL:  no pg_hba.conf entry
for host "127.0.0.1", user "batch_user", database "batch_db", no encryption
```

The connection failure confirms that unencrypted connections are refused.

## Part 2: WildFly Configuration for Provisioned Server

The `provisioned-server` profile packages a custom WildFly server using Maven.


### Step 1: Navigate to the Quickstart

```bash
cd <quickstart_home>/batch-processing
```

**Windows (PowerShell):**
```powershell
Set-Location <quickstart_home>\batch-processing
```

Replace `<quickstart_home>` with the actual path to the quickstart root directory.

### Step 2: Update pom.xml Dependencies

**Change 1**: Update the manifest entry at lines 201–202:

```xml
<manifestEntries>
    <!-- Replace H2 with PostgreSQL JDBC driver -->
    <Dependencies>org.postgresql.jdbc</Dependencies>
</manifestEntries>
```

**Change 2**: Update the `provisioned-server` profile add-ons at lines 224–226 to provision the server with a PostgreSQL module and a management CLI client:

```xml
<discover-provisioning-info>
    <version>${version.server}</version>
    <addOns>
        <addOn>wildfly-cli</addOn>
        <addOn>postgresql</addOn>
    </addOns>
</discover-provisioning-info>
```

**Change 3**: Add packaging scripts after `</discover-provisioning-info>` to configure WildFly to use the PostgreSQL database for the `batch-jberet` subsystem:

```xml
<packaging-scripts>
    <packaging-script>
        <commands>
            <!-- Create credential store for database credentials -->
            <command>/subsystem=elytron/credential-store=batch-credentials:add(location=credentials/batch-store.jceks, relative-to=jboss.server.data.dir, credential-reference={clear-text=storePass123!}, create=true)</command>
            
            <!-- Add database password to credential store -->
            <command>/subsystem=elytron/credential-store=batch-credentials:add-alias(alias=db.password, secret-value=SecurePass123!)</command>
            
            <!-- Configure PostgreSQL datasource with credential reference and TLS -->
            <command>/subsystem=datasources/data-source=batch-processingDS:add( \
                jndi-name=java:jboss/datasources/batch-processingDS, \
                driver-name=postgresql, \
                connection-url="jdbc:postgresql://localhost:5432/batch_db?ssl=true&amp;sslmode=require", \
                user-name=batch_user, \
                credential-reference={store=batch-credentials, alias=db.password}, \
                enabled=true, \
                valid-connection-checker-class-name=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLValidConnectionChecker, \
                exception-sorter-class-name=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLExceptionSorter, \
                validate-on-match=true, \
                background-validation=false)</command>
            
            <!-- Configure JBeret JDBC job repository to use the same datasource -->
            <command>/subsystem=batch-jberet/jdbc-job-repository=JSR352_JobRepository:add(data-source=batch-processingDS)</command>
            
            <!-- Set as default job repository -->
            <command>/subsystem=batch-jberet/:write-attribute(name=default-job-repository,value=JSR352_JobRepository)</command>
        </commands>
    </packaging-script>
</packaging-scripts>
```

**Important Security Notes**:
The credential store is protected by a password (`storePass123!`). In production environments, do not use a hardcoded password. Instead, use an external source, such as a HashiCorp vault, to supply the password.

### Step 3: Configure SSL Trust for PostgreSQL Certificate

To trust the PostgreSQL self-signed certificate, import it into a truststore that WildFly uses.

Create the truststore:

```bash
cd ~/postgres-certs

# Import PostgreSQL certificate into a JKS truststore
keytool -import -trustcacerts -alias postgres-server \
  -file server.crt \
  -keystore pg-truststore.jks \
  -storepass trustPass123! \
  -noprompt
```

**Windows (PowerShell):**
```powershell
Set-Location "$HOME\postgres-certs"

# Import PostgreSQL certificate into a JKS truststore
keytool -import -trustcacerts -alias postgres-server `
  -file server.crt `
  -keystore pg-truststore.jks `
  -storepass trustPass123! `
  -noprompt
```

Add the following CLI command to the packaging scripts in `pom.xml`:

```xml
<packaging-scripts>
    <packaging-script>
        <commands>
            <!-- Previous commands... -->
            
            <!-- Configure SSL context for PostgreSQL certificate trust -->
            <command>/subsystem=elytron/key-store=postgresql-truststore:add(path=pg-truststore.jks, relative-to=jboss.server.config.dir, credential-reference={clear-text=trustPass123!}, type=JKS)</command>
            <command>/subsystem=elytron/trust-manager=postgresql-trust:add(key-store=postgresql-truststore)</command>
            <command>/subsystem=elytron/client-ssl-context=postgresql-ssl:add(trust-manager=postgresql-trust, protocols=["TLSv1.3"])</command>
        </commands>
    </packaging-script>
</packaging-scripts>
```

### Step 4: Remove Application-Level DataSource Definition

Open `<quickstart_home>/batch-processing/src/main/java/org/jboss/as/quickstarts/batch/model/Contact.java`.

**Remove lines 25-30** (the `@DataSourceDefinition` annotation) so that the server-level datasource is used by both the application and the `batch-jberet` subsystem:

```java
@DataSourceDefinition(name="java:jboss/datasources/batch-processingDS",
        className="org.h2.jdbcx.JdbcDataSource",
        url="jdbc:h2:mem:batch-processing;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1",
        user="sa",
        password="sa"
)
```

### Step 5: Update Persistence Configuration

Open `<quickstart_home>/batch-processing/src/main/resources/META-INF/persistence.xml`.

Update the properties section to use PostgreSQL dialect:

```xml
<persistence-unit name="primary" transaction-type="JTA">
   <jta-data-source>java:jboss/datasources/batch-processingDS</jta-data-source>
   <properties>
      <!-- Properties for Hibernate with PostgreSQL -->
      <property name="hibernate.dialect" value="org.hibernate.dialect.PostgreSQLDialect" />
      <property name="hibernate.hbm2ddl.auto" value="create-drop" />
      <property name="hibernate.show_sql" value="false" />
   </properties>
</persistence-unit>
```

### Step 6: Build and Test Provisioned Server

Build the provisioned server:

```bash
cd <quickstart_home>/batch-processing
mvn clean package
```

**Windows (PowerShell):**
```powershell
Set-Location <quickstart_home>\batch-processing
mvn clean package
```

Copy the truststore so that WildFly trusts the certificate presented by the PostgreSQL database:

```bash
cp ~/postgres-certs/pg-truststore.jks target/server/standalone/configuration/
```

**Windows (PowerShell):**
```powershell
Copy-Item "$HOME\postgres-certs\pg-truststore.jks" target\server\standalone\configuration\
```

Start the provisioned server:

```bash
target/server/bin/standalone.sh
```

**Windows (PowerShell):**
```powershell
target\server\bin\standalone.bat
```

Access the application:

```
http://localhost:8080/batch-processing/
```

Test the batch processing functionality:

1. Click "Generate a new file and start import job"
2. Click "Update jobs list" to verify the job completed
3. Check server logs for successful database operations

## Part 3: Authentication

### Step 1: Define a Filesystem Realm to Add Application Users

Add the following user configuration to `pom.xml` (line 270), below `</packaging-scripts>`:

```xml
<command>/subsystem=elytron/filesystem-realm=exampleSecurityRealm:add(path=fs-realm-users,relative-to=jboss.server.config.dir)</command>
<command>/subsystem=elytron/filesystem-realm=exampleSecurityRealm:add-identity(identity=user1)</command>
<command>/subsystem=elytron/filesystem-realm=exampleSecurityRealm:set-password(identity=user1, clear={password="passwordUser1"})</command>
<command>/subsystem=elytron/filesystem-realm=exampleSecurityRealm:add-identity-attribute(identity=user1, name=Roles, value=["Admin","Guest"])</command>
<command>/subsystem=elytron/security-domain=exampleSecurityDomain:add(default-realm=exampleSecurityRealm,permission-mapper=default-permission-mapper,realms=[{realm=exampleSecurityRealm}])</command>
<command>/subsystem=undertow/application-security-domain=exampleApplicationSecurityDomain:add(security-domain=exampleSecurityDomain)</command>
<command>/subsystem=undertow:write-attribute(name=default-security-domain,value=exampleApplicationSecurityDomain)</command>
```
**Note:** You can use the Elytron credential store to securely store the password for `user1` so that you do not need to include the plaintext password in the command. The steps to store the password in a credential store are similar to those used in Step 2, Change 3 of Part 2: WildFly Configuration for Provisioned Server of the guide. The steps have been omitted here for brevity.

### Step 2: Configure Security in web.xml

Create a file `<quickstart_home>/batch-processing/src/main/webapp/WEB-INF/web.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
         https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">
         
 <security-constraint>
        <web-resource-collection>
            <web-resource-name>all</web-resource-name>
            <url-pattern>/*</url-pattern>
        </web-resource-collection>

        <!-- Define the role that can access the protected resource -->
        <auth-constraint>
            <role-name>Admin</role-name>
            <!-- To disable authentication you can use the wildcard *
            	 To authenticate but allow any role, use the wildcard **. -->
        </auth-constraint>
    </security-constraint>

    <login-config>
        <auth-method>BASIC</auth-method>

        <realm-name>exampleSecurityRealm</realm-name>
    </login-config>
</web-app>
```
### Step 3: Map Security Roles

Create `<quickstart_home>/batch-processing/src/main/webapp/WEB-INF/jboss-web.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jboss-web xmlns="http://www.jboss.com/xml/ns/javaee"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://www.jboss.com/xml/ns/javaee
           http://www.jboss.org/schema/jbossas/jboss-web_14_0.xsd">
    <security-domain>exampleApplicationSecurityDomain</security-domain>
</jboss-web>
```

### Step 4: Build and Test Provisioned Server

Build the provisioned server:

```bash
cd <quickstart_home>/batch-processing
mvn clean package
```

**Windows (PowerShell):**
```powershell
Set-Location <quickstart_home>\batch-processing
mvn clean package
```

Copy the truststore so that WildFly trusts the certificate presented by the PostgreSQL database:

```bash
cp ~/postgres-certs/pg-truststore.jks target/server/standalone/configuration/
```

**Windows (PowerShell):**
```powershell
Copy-Item "$HOME\postgres-certs\pg-truststore.jks" target\server\standalone\configuration\
```

Start the provisioned server:

```bash
target/server/bin/standalone.sh
```

**Windows (PowerShell):**
```powershell
target\server\bin\standalone.bat
```

Access the application:

```
http://localhost:8080/batch-processing/
```

Log in with the credentials:

- username: `user1`
- password: `passwordUser1`

Test the batch processing functionality:

1. Click "Generate a new file and start import job"
2. Click "Update jobs list" to verify the job completed
3. Check server logs for successful database operations


## Part 4: HTTPS for Web Application

### Step 1: Generate a certificate

Create certificates for TLS:

```bash
mkdir -p ~/wildfly-certs
cd ~/wildfly-certs

# Generate keystore with self-signed certificate
keytool -genkeypair -alias wildfly-https \
  -keyalg RSA -keysize 2048 \
  -validity 365 \
  -keystore wildfly-https.jks \
  -storepass httpsPass123! \
  -keypass httpsPass123! \
  -dname "CN=localhost, OU=Dev, O=Organization, L=TO, ST=ON, C=CA"
```

**Windows (PowerShell):**
```powershell
New-Item -ItemType Directory -Force -Path "$HOME\wildfly-certs"
Set-Location "$HOME\wildfly-certs"

# Generate keystore with self-signed certificate
keytool -genkeypair -alias wildfly-https `
  -keyalg RSA -keysize 2048 `
  -validity 365 `
  -keystore wildfly-https.jks `
  -storepass httpsPass123! `
  -keypass httpsPass123! `
  -dname "CN=localhost, OU=Dev, O=Organization, L=TO, ST=ON, C=CA"
```

### Step 2: Configure HTTPS in WildFly

Add these commands to the packaging scripts in `pom.xml` for the provisioned server:

```xml
<!-- Configure HTTPS -->
<command>/subsystem=elytron/key-store=https-keystore:add(path=~/wildfly-certs/wildfly-https.jks, credential-reference={clear-text=httpsPass123!}, type=JKS)</command>
<command>/subsystem=elytron/key-manager=https-key-manager:add(key-store=https-keystore, credential-reference={clear-text=httpsPass123!})</command>
<command>/subsystem=elytron/server-ssl-context=https-ssl-context:add(key-manager=https-key-manager, protocols=["TLSv1.3"])</command>
<command>/subsystem=undertow/server=default-server/https-listener=https:add(socket-binding=https, ssl-context=https-ssl-context, enable-http2=true)</command>
```

### Step 3: Build and Test Provisioned Server

Build the provisioned server:

```bash
cd <quickstart_home>/batch-processing
mvn clean package
```

**Windows (PowerShell):**
```powershell
Set-Location <quickstart_home>\batch-processing
mvn clean package
```

Copy the truststore so that WildFly trusts the certificate presented by the PostgreSQL database:

```bash
cp ~/postgres-certs/pg-truststore.jks target/server/standalone/configuration/
```

**Windows (PowerShell):**
```powershell
Copy-Item "$HOME\postgres-certs\pg-truststore.jks" target\server\standalone\configuration\
```

Start the provisioned server:

```bash
target/server/bin/standalone.sh
```

**Windows (PowerShell):**
```powershell
target\server\bin\standalone.bat
```

Access the application over HTTPS:

```
https://localhost:8443/batch-processing/
```

**Note**: Your browser will warn about the self-signed certificate.

Log in with the credentials:

- username: `user1`
- password: `passwordUser1`

Test the batch processing functionality:

1. Click "Generate a new file and start import job"
2. Click "Update jobs list" to verify the job completed
3. Check server logs for successful database operations

## Conclusion

The quickstart now uses TLS both when communicating with the database and the user's web browser. Additionally, only the user `user1` can access the application. Finally, because the quickstart uses a separate PostgreSQL database instead of H2 database, the data is persisted over server restarts.