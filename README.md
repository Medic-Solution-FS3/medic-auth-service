# Medic Auth Service

Microservicio de autenticación y gestión de usuarios para la plataforma Medic Solution.

## 🚀 Características

- **Registro de pacientes** con verificación de email
- **Login con JWT** (Access Token + Refresh Token)
- **Gestión de roles**: ADMINISTRADOR, MEDICO, PACIENTE, CLINICA
- **Arquitectura hexagonal** (Ports & Adapters)
- **Transactional Outbox Pattern** para garantizar entrega de eventos a RabbitMQ
- **Refresh tokens** almacenados en base de datos con capacidad de revocación
- **Migraciones con Flyway** para versionado de base de datos
- **Spring Security** con filtros JWT personalizados
- **Docker** ready con multi-stage builds

## 📋 Requisitos Previos

- **Java 21** o superior
- **Maven 3.9+**
- **Docker** y **Docker Compose**
- **MySQL 8.0** (incluido en docker-compose)
- **RabbitMQ 3.12** (incluido en docker-compose)

## 🛠️ Stack Tecnológico

- Spring Boot 3.2.0
- Spring Security
- Spring Data JPA
- Spring AMQP (RabbitMQ)
- Flyway Migration
- MySQL 8.0
- JWT (JJWT 0.12.6)
- Docker

## 📦 Instalación y Configuración

### 1. Clonar el repositorio

```bash
git clone https://github.com/Medic-Solution-FS3/medic-auth-service.git
cd medic-auth-service
git checkout develop
```

### 2. Configurar variables de entorno (Opcional)

Crear archivo `.env` en la raíz del proyecto (opcional, docker-compose ya tiene valores por defecto):

```env
# Database
DB_USERNAME=medic_user
DB_PASSWORD=medic_password

# RabbitMQ
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

# JWT Secret (mínimo 256 bits en base64)
JWT_SECRET=bXktdmVyeS1zZWN1cmUtc2VjcmV0LWtleS1taW5pbXVtLTI1Ni1iaXRzLWxvbmctZm9yLXByb2R1Y3Rpb24=
```

### 3. Levantar servicios con Docker Compose

**Desde la raíz del proyecto medic-solution:**

```bash
cd /Users/amaromontero/Universidad/medic-solution

# Construir imagen del auth-service
docker-compose build auth-service

# Levantar todos los servicios
docker-compose up -d

# Ver logs del auth-service
docker-compose logs -f auth-service
```

### 4. Verificar que el servicio está corriendo

```bash
# Health check
curl http://localhost:8083/actuator/health

# Debería retornar: {"status":"UP"}
```

## 🗄️ Base de Datos

El servicio crea automáticamente la base de datos `db_users` con las siguientes tablas:

- **users**: Usuarios del sistema
- **roles**: Roles disponibles (ADMINISTRADOR, MEDICO, PACIENTE, CLINICA)
- **email_verification_tokens**: Tokens de verificación de email (expiran en 24h)
- **refresh_tokens**: Tokens de refresco con capacidad de revocación
- **outbox_events**: Eventos pendientes de publicación a RabbitMQ (Transactional Outbox Pattern)

### Usuario Seed (Pre-creado)

El sistema incluye un usuario médico de prueba:

- **Email**: `tomateartzzxd@gmail.com`
- **Contraseña**: `password123`
- **Rol**: MEDICO
- **Email verificado**: Sí

## 📡 Endpoints Disponibles

### Autenticación (Públicos)

#### Registro de paciente
```bash
POST /auth/register
Content-Type: application/json

{
  "email": "paciente@test.com",
  "password": "password123",
  "fullName": "Juan Pérez",
  "phone": "+56912345678"
}

# Respuesta 201 Created:
{
  "message": "Registration successful. Please check your email to verify your account."
}
```

#### Verificar email
```bash
POST /auth/verify-email
Content-Type: application/json

{
  "token": "d318eb79-8477-4344-a545-41e2c2159b4c"
}

# Respuesta 200 OK:
{
  "message": "Email verified successfully. You can now login."
}
```

#### Login
```bash
POST /auth/login
Content-Type: application/json

{
  "email": "paciente@test.com",
  "password": "password123"
}

# Respuesta 200 OK:
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
  "refreshToken": "082688a0-4cfe-4b52-8849-a2c3610e1f4b",
  "userId": 3,
  "email": "paciente@test.com"
}
```

#### Refresh token
```bash
POST /auth/refresh
Content-Type: application/json

{
  "refreshToken": "082688a0-4cfe-4b52-8849-a2c3610e1f4b"
}

# Respuesta 200 OK:
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
  "refreshToken": "082688a0-4cfe-4b52-8849-a2c3610e1f4b",
  "userId": 3,
  "email": "paciente@test.com"
}
```

#### Logout (revocar refresh token)
```bash
POST /auth/logout
Content-Type: application/json

{
  "refreshToken": "082688a0-4cfe-4b52-8849-a2c3610e1f4b"
}

# Respuesta 204 No Content
```

### Gestión de Usuarios (Requieren JWT)

#### Obtener usuario actual
```bash
GET /users/me
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9...

# Respuesta 200 OK:
{
  "id": 3,
  "email": "paciente@test.com",
  "fullName": "Juan Pérez",
  "phone": "+56912345678",
  "role": "PACIENTE",
  "active": true,
  "emailVerified": true,
  "createdAt": "2026-06-04T21:27:07"
}
```

#### Obtener usuario por ID (solo ADMINISTRADOR)
```bash
GET /users/{id}
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9...
```

#### Actualizar usuario
```bash
PUT /users/{id}
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9...
Content-Type: application/json

{
  "fullName": "Juan Pérez Actualizado",
  "phone": "+56987654321"
}
```

## 🔑 JWT Structure

### Access Token (30 minutos)

```json
{
  "sub": "3",
  "email": "paciente@test.com",
  "roles": ["PACIENTE"],
  "iat": 1780622845,
  "exp": 1780624645,
  "iss": "medic-auth-service"
}
```

### Refresh Token (7 días)
- UUID almacenado en base de datos
- Soporta revocación
- Expira automáticamente

## 🐰 Eventos RabbitMQ

El servicio publica eventos mediante **Transactional Outbox Pattern**:

### Exchange
- **Nombre**: `user.exchange`
- **Tipo**: Topic

### Eventos publicados

#### UserRegistered
```json
{
  "version": "v1",
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": 3,
  "email": "paciente@test.com",
  "fullName": "Juan Pérez",
  "phone": "+56912345678",
  "role": "PACIENTE",
  "verificationToken": "d318eb79-8477-4344-a545-41e2c2159b4c",
  "occurredAt": "2026-06-04T21:27:05"
}
```
**Routing Key**: `user.registered`

#### EmailVerified
```json
{
  "version": "v1",
  "eventId": "550e8400-e29b-41d4-a716-446655440001",
  "userId": 3,
  "email": "paciente@test.com",
  "fullName": "Juan Pérez",
  "occurredAt": "2026-06-04T21:27:45"
}
```
**Routing Key**: `user.email.verified`

### Consumidores
- **medic-worker-notifier**: Consume eventos para enviar emails de verificación y bienvenida

## 🧪 Testing

### Ejecutar tests unitarios

```bash
# Desde el directorio del proyecto
mvn test

# Con cobertura
mvn clean test jacoco:report
```

### Tests incluidos
- `AuthServiceTest`: Tests del servicio de autenticación
- `OutboxRelaySchedulerTest`: Tests del scheduler de outbox

## 🔍 Verificación del Sistema

### 1. Verificar base de datos

```bash
# Conectar a MySQL
docker exec -it medic-mysql-db mysql -umedic_user -pmedic_password db_users

# Ver usuarios
SELECT id, email, full_name, email_verified FROM users;

# Ver eventos outbox
SELECT id, event_type, routing_key, status FROM outbox_events ORDER BY id DESC LIMIT 5;

# Ver refresh tokens activos
SELECT user_id, expires_at, revoked FROM refresh_tokens WHERE revoked = false;
```

### 2. Verificar RabbitMQ

Acceder a la interfaz web de RabbitMQ:
- **URL**: http://localhost:15672
- **Usuario**: guest
- **Contraseña**: guest

Verificar:
- Exchange `user.exchange` existe
- Eventos se están publicando
- Colas conectadas al exchange

### 3. Ver logs del servicio

```bash
# Logs en tiempo real
docker-compose logs -f auth-service

# Últimas 100 líneas
docker-compose logs --tail=100 auth-service
```

## 🔧 Desarrollo Local (sin Docker)

### 1. Levantar dependencias

```bash
# Solo MySQL y RabbitMQ
docker-compose up -d mysql rabbitmq
```

### 2. Configurar application.yml

Asegurarse de que `spring.profiles.active=local` o usar los valores por defecto.

### 3. Ejecutar la aplicación

```bash
mvn spring-boot:run

# O desde tu IDE (IntelliJ IDEA / Eclipse)
# Run > Run 'AuthServiceApplication'
```

La aplicación estará disponible en `http://localhost:8083`

## 📝 Migraciones de Base de Datos

Las migraciones se ejecutan automáticamente con Flyway al iniciar el servicio.

Ubicación: `src/main/resources/db/migration/common/`

- **V1__init_users_and_roles.sql**: Crea tablas users, roles, email_verification_tokens
- **V2__add_outbox_and_refresh_tokens.sql**: Agrega tablas outbox_events y refresh_tokens
- **V3__seed_roles.sql**: Inserta roles iniciales
- **V4__seed_doctor_user.sql**: Crea usuario médico de prueba

## 🚨 Troubleshooting

### Error: "Schema-validation: wrong column type"
**Solución**: Asegurarse de que `spring.jpa.hibernate.ddl-auto=none` (Flyway maneja el schema)

### Error: "Access denied for user 'medic_user'@'%' to database 'db_users'"
**Solución**: Ejecutar el script de inicialización de base de datos:
```bash
docker exec -it medic-mysql-db mysql -uroot -proot
CREATE DATABASE IF NOT EXISTS db_users;
GRANT ALL PRIVILEGES ON db_users.* TO 'medic_user'@'%';
FLUSH PRIVILEGES;
```

### Error: "Connection refused" al conectar a RabbitMQ
**Solución**: Verificar que RabbitMQ esté corriendo:
```bash
docker-compose ps rabbitmq
docker-compose logs rabbitmq
```

### Error: "JWT secret must be at least 256 bits"
**Solución**: Configurar variable de entorno `JWT_SECRET` con un string base64 de al menos 256 bits.

## 🌐 Integración con API Gateway

El servicio está integrado con `medic-api-gateway` que expone los endpoints bajo `/api/auth/**` y `/api/users/**`.

### Acceso vía Gateway

```bash
# En lugar de http://localhost:8083/auth/login
# Usar:
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"paciente@test.com","password":"password123"}'
```

El gateway automáticamente:
1. Extrae el JWT del header `Authorization: Bearer <token>`
2. Valida el token
3. Inyecta headers `X-User-Id` y `X-User-Role` a los servicios downstream

## 📚 Documentación Adicional

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/3.2.0/reference/html/)
- [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [JJWT Documentation](https://github.com/jwtk/jjwt)
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)

## 👥 Equipo

Desarrollado para la evaluación parcial N°2 de DevOps ISY1101 - DuocUC

## 📄 Licencia

Proyecto académico - DuocUC 2026
