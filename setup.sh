#!/bin/bash

echo "🚀 Configurando MyDay Productivity para Hackathon..."

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Função para verificar se comando existe
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Verificar pré-requisitos
echo -e "${BLUE}📋 Verificando pré-requisitos...${NC}"

if ! command_exists java; then
    echo -e "${RED}❌ Java 21+ não encontrado. Por favor, instale Java 21+${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo -e "${RED}❌ Java 21+ necessário. Versão atual: $JAVA_VERSION${NC}"
    exit 1
fi

if ! command_exists docker; then
    echo -e "${RED}❌ Docker não encontrado. Por favor, instale Docker${NC}"
    exit 1
fi

if ! command_exists mvn; then
    echo -e "${YELLOW}⚠️ Maven não encontrado. Usando wrapper...${NC}"
fi

echo -e "${GREEN}✅ Pré-requisitos verificados!${NC}"

# Parar e remover container antigo se existir
echo -e "${BLUE}🗑️ Limpando instalação anterior...${NC}"
docker stop mysql-neurotask 2>/dev/null || true
docker rm mysql-neurotask 2>/dev/null || true

# Iniciar MySQL
echo -e "${BLUE}📦 Iniciando MySQL na porta 3307...${NC}"
docker run --name mysql-neurotask \
    -e MYSQL_ROOT_PASSWORD=senha123 \
    -e MYSQL_DATABASE=neurotask \
    -p 3307:3306 \
    -d mysql:8.0

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Falha ao iniciar MySQL${NC}"
    exit 1
fi

echo -e "${GREEN}✅ MySQL iniciado!${NC}"

# Aguardar MySQL estar pronto
echo -e "${BLUE}⏳ Aguardando MySQL inicializar...${NC}"
for i in {1..30}; do
    if docker exec mysql-neurotask mysqladmin ping -h localhost --silent; then
        echo -e "${GREEN}✅ MySQL pronto!${NC}"
        break
    fi
    echo -n "."
    sleep 1
done

# Configurar aplicação
echo -e "${BLUE}⚙️ Configurando aplicação...${NC}"
if [ ! -f "src/main/resources/application.properties" ]; then
    cp src/main/resources/application.properties.example src/main/resources/application.properties
    echo -e "${GREEN}✅ Arquivo application.properties criado!${NC}"
else
    echo -e "${YELLOW}⚠️ application.properties já existe${NC}"
fi

# Compilar projeto
echo -e "${BLUE}🔨 Compilando projeto...${NC}"
if command_exists mvn; then
    mvn clean compile -q
else
    ./mvnw clean compile -q
fi

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Falha na compilação${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Projeto compilado!${NC}"

# Iniciar aplicação
echo -e "${BLUE}🚀 Iniciando aplicação...${NC}"
echo -e "${YELLOW}⚠️ Pressione Ctrl+C para parar a aplicação${NC}"
echo ""

if command_exists mvn; then
    mvn spring-boot:run
else
    ./mvnw spring-boot:run
fi
