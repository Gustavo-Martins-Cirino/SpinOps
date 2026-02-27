@echo off
echo 🚀 Configurando MyDay Productivity para Hackathon...

REM Verificar pré-requisitos
echo 📋 Verificando pré-requisitos...

java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Java 21+ não encontrado. Por favor, instale Java 21+
    pause
    exit /b 1
)

docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker não encontrado. Por favor, instale Docker
    pause
    exit /b 1
)

echo ✅ Pré-requisitos verificados!

REM Parar e remover container antigo se existir
echo 🗑️ Limpando instalação anterior...
docker stop mysql-neurotask >nul 2>&1
docker rm mysql-neurotask >nul 2>&1

REM Iniciar MySQL
echo 📦 Iniciando MySQL na porta 3307...
docker run --name mysql-neurotask -e MYSQL_ROOT_PASSWORD=senha123 -e MYSQL_DATABASE=neurotask -p 3307:3306 -d mysql:8.0

if %errorlevel% neq 0 (
    echo ❌ Falha ao iniciar MySQL
    pause
    exit /b 1
)

echo ✅ MySQL iniciado!

REM Aguardar MySQL estar pronto
echo ⏳ Aguardando MySQL inicializar...
:wait_mysql
timeout /t 2 >nul
docker exec mysql-neurotask mysqladmin ping -h localhost --silent >nul 2>&1
if %errorlevel% neq 0 (
    echo .
    goto wait_mysql
)
echo ✅ MySQL pronto!

REM Configurar aplicação
echo ⚙️ Configurando aplicação...
if not exist "src\main\resources\application.properties" (
    copy "src\main\resources\application.properties.example" "src\main\resources\application.properties" >nul
    echo ✅ Arquivo application.properties criado!
) else (
    echo ⚠️ application.properties já existe
)

REM Compilar projeto
echo 🔨 Compilando projeto...
mvnw.cmd clean compile -q

if %errorlevel% neq 0 (
    echo ❌ Falha na compilação
    pause
    exit /b 1
)

echo ✅ Projeto compilado!

REM Iniciar aplicação
echo 🚀 Iniciando aplicação...
echo ⚠️ Pressione Ctrl+C para parar a aplicação
echo.

mvnw.cmd spring-boot:run
