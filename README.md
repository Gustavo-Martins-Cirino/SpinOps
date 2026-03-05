# SpinOps — Gestão de Manutenção Preditiva Industrial
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)
![PWA](https://img.shields.io/badge/PWA-offline--ready-purple)
![License](https://img.shields.io/badge/License-MIT-yellow)
> Projeto desenvolvido para o **Hackathon InovSpin** — solução que cobre os **15 desafios** propostos, com aplicação de Inteligência Artificial em gestão de ativos elétricos industriais.
**SpinOps** é uma plataforma web progressiva (PWA) para gestão inteligente de ativos elétricos industriais, com diagnóstico por IA, ordens de serviço, calendário de manutenção e dashboard analítico com 15 módulos de inteligência artificial.
---
## Sumário
- [Funcionalidades](#funcionalidades)
- [Requisitos](#requisitos)
- [Como Executar](#como-executar)
  - [Opção A — Docker (recomendado)](#opção-a--docker-recomendado)
  - [Opção B — Sem Docker](#opção-b--sem-docker)
- [Configuração de IA](#configuração-de-ia)
- [Testes](#testes)
- [Arquitetura](#arquitetura)
- [Desafios do Hackathon](#desafios-do-hackathon)
---
## Funcionalidades
| Módulo | Descrição |
|--------|-----------|
| Equipamentos | Cadastro, diagnóstico de saúde com IA, simulação de sensores históricos |
| Ordens de Serviço | Criação, priorização, drag-and-drop, filtros por status |
| Dashboard IA | 15 cards analíticos: clustering, classificação explicável, anomalias, previsão de falhas |
| Heatmap de Atividade | Calendário de 14 semanas de OS criadas/concluídas |
| Evolução de Saúde | Gráfico de linha 8 semanas por equipamento com limiares crítico/atenção |
| Calendário | Manutenções programadas por equipamento com visão mensal/semanal |
| Chat IA | Assistente técnico (Groq/Llama 3.1) com fallback local |
| PWA | Funciona offline via Service Worker |
| Push Notifications | Alertas de manutenção vencida (Web Push VAPID) |
---
## Requisitos
### Opção A — Docker (mais simples)
| Ferramenta | Versão mínima | Download |
|-----------|--------------|---------|
| Java JDK | 21 | [adoptium.net](https://adoptium.net/temurin/releases/?version=21) |
| Docker Desktop | qualquer | [docker.com/get-started](https://www.docker.com/get-started/) |
### Opção B — Sem Docker
| Ferramenta | Versão mínima | Download |
|-----------|--------------|---------|
| Java JDK | 21 | [adoptium.net](https://adoptium.net/temurin/releases/?version=21) |
| MySQL Server | 8.0 | [dev.mysql.com/downloads](https://dev.mysql.com/downloads/mysql/) |
> **Maven não precisa ser instalado** — o projeto inclui o wrapper `mvnw` (Linux/Mac) e `mvnw.cmd` (Windows).
---
## Como Executar
### Verificar pré-requisitos
```bash
java -version
# deve exibir: openjdk version "21.x.x" ...
docker --version
# (apenas Opção A) deve exibir: Docker version 24.x.x ...
```
Se `java -version` não funcionar, instale o JDK 21 e adicione `JAVA_HOME` ao PATH do sistema.
---
### Opção A — Docker (recomendado)
> Inicia MySQL + aplicação com um único comando. Não requer configuração manual de banco.
**1. Clone o repositório**
```bash
git clone https://github.com/Gustavo-Martins-Cirino/SpinOps.git
cd SpinOps
```
**2. Suba os containers**
```bash
docker-compose up -d
```
O Docker irá:
- Baixar a imagem `mysql:8.0` e criar o banco `neurotask`
- Compilar e executar a aplicação Spring Boot
**3. Aguarde o startup (~60–90 segundos)**
```bash
docker-compose logs -f app
# Aguarde a linha: Started MydayProductivityApplication in X seconds
```
**4. Acesse no navegador**
```
http://localhost:8080
```
**Parar a aplicação:**
```bash
docker-compose down
```
**Parar e apagar os dados do banco:**
```bash
docker-compose down -v
```
---
### Opção B — Sem Docker
#### 1. Clone o repositório
```bash
git clone <url-do-repositorio>
cd myday-productivity
```
#### 2. Configure o MySQL
Instale o MySQL 8.0, inicie o serviço e crie o banco:
```sql
-- Execute no cliente MySQL (mysql -u root -p):
CREATE DATABASE IF NOT EXISTS neurotask
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```
#### 3. Configure o arquivo de propriedades
```bash
# Linux / macOS
cp src/main/resources/application.properties.example \
   src/main/resources/application.properties
# Windows (PowerShell)
Copy-Item src\main\resources\application.properties.example `
          src\main\resources\application.properties
```
Abra `src/main/resources/application.properties` e ajuste as credenciais:
```properties
# Se o MySQL estiver na porta padrão 3306, troque 3307 por 3306
spring.datasource.url=jdbc:mysql://localhost:3307/neurotask?createDatabaseIfNotExist=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=senha123
```
#### 4. Execute a aplicação
```bash
# Linux / macOS
./mvnw spring-boot:run
# Windows (PowerShell ou Prompt de Comando)
mvnw.cmd spring-boot:run
```
Na primeira execução, o Maven baixa as dependências (~2–3 min). As seguintes são mais rápidas.
Aguarde a mensagem no console:
```
Started MydayProductivityApplication in X.XXX seconds
```
#### 5. Acesse no navegador
```
http://localhost:8080
```
**Porta 8080 já em uso?**
```bash
# Linux / macOS
./mvnw spring-boot:run -Dserver.port=8081
# Windows
mvnw.cmd spring-boot:run -Dserver.port=8081
# Acesse: http://localhost:8081
```
---
## Solução de Problemas
| Sintoma | Causa provável | Solução |
|---------|---------------|---------|
| `java: command not found` | JDK não instalado ou `JAVA_HOME` ausente | Instale o JDK 21 e configure o `PATH` |
| `Connection refused` no MySQL | Serviço MySQL não está rodando | Inicie o MySQL ou use `docker-compose up -d` |
| Erro de credenciais no banco | `username`/`password` errado | Revise `application.properties` |
| Porta 8080 ocupada | Outro processo na mesma porta | Use `-Dserver.port=8081` |
| Container não sobe | Docker Desktop não iniciado | Abra o Docker Desktop e aguarde o daemon iniciar |
| Erro de compilação no Maven | Versão do Java incompatível | Confirme `java -version` = 21+ |
**Ver logs detalhados:**
```bash
# Docker
docker-compose logs app
# Local — filtrar erros
./mvnw spring-boot:run 2>&1 | findstr /i "error"   # Windows
./mvnw spring-boot:run 2>&1 | grep -i "error"       # Linux/macOS
```
---
## Configuração de IA
O SpinOps usa a **API Groq** (gratuita) com o modelo `llama-3.1-8b-instant` para diagnóstico de equipamentos e chat técnico.
**Sem chave de API:** todas as funcionalidades continuam disponíveis usando algoritmos locais de fallback.
**Para ativar a IA com Groq:**
1. Obtenha uma chave gratuita em [console.groq.com](https://console.groq.com)
2. No aplicativo, clique no ícone de engrenagem (Configurações)
3. Cole a chave no campo **"Chave de API (Groq)"**
4. A chave é salva no `localStorage` do navegador — não trafega para o servidor
**Alternativa via variável de ambiente:**
```bash
# Linux / macOS
export OPENAI_API_KEY=gsk_xxxxxxxxxxxxxxxxxxxx
./mvnw spring-boot:run
# Windows (PowerShell)
$env:OPENAI_API_KEY = "gsk_xxxxxxxxxxxxxxxxxxxx"
mvnw.cmd spring-boot:run
```
---
## Testes
### Testes unitários (JUnit 5)
```bash
# Linux / macOS
./mvnw test
# Windows
mvnw.cmd test
```
### Testes E2E com Playwright
```bash
# Pré-requisito: Node.js instalado (https://nodejs.org)
npm install -g playwright
# Com a aplicação rodando em localhost:8080:
cd qa
node playwright-smoke.mjs
# Com URL personalizada:
BASE_URL=http://localhost:8081 node playwright-smoke.mjs
```
Log salvo em `qa/smoke-last.log`.
---
## Arquitetura
```
Navegador (PWA)
  HTML + CSS + Vanilla JS  ·  Service Worker  ·  Chart.js
           |
           |  HTTP / REST
           v
  Spring Boot 3.5 (Java 21)
    controller/ · service/ · model/ · repository/ · dto/
           |
           |  JPA / Hibernate
           v
        MySQL 8.0
           |
           |  HTTPS
           v
  Groq API — llama-3.1-8b-instant  (+ fallback local)
```
### Estrutura de diretórios
```
myday-productivity/
├── src/
│   ├── main/
│   │   ├── java/.../myday_productivity/
│   │   │   ├── MydayProductivityApplication.java
│   │   │   ├── config/          # CORS, cliente HTTP
│   │   │   ├── controller/      # Endpoints REST
│   │   │   ├── dto/             # Request / Response DTOs
│   │   │   ├── exception/       # Handlers globais
│   │   │   ├── model/           # Entidades JPA
│   │   │   ├── repository/      # Spring Data repos
│   │   │   └── service/         # Lógica de negócio + IA
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application.properties.example
│   │       └── static/
│   │           ├── index.html          # SPA completa
│   │           ├── manifest.webmanifest
│   │           ├── sw.js               # Service Worker
│   │           ├── css/
│   │           └── js/
│   └── test/                   # Testes JUnit 5
├── qa/
│   └── playwright-smoke.mjs   # Testes E2E
├── docker-compose.yml
├── pom.xml
└── README.md
```
---
## Tecnologias Utilizadas
| Camada | Tecnologia |
|--------|-----------|
| Backend | Spring Boot 3.5 · Java 21 · Spring Data JPA · HikariCP |
| Banco de dados | MySQL 8.0 |
| Frontend | HTML5 · CSS3 · Vanilla JS ES6+ |
| Gráficos | Chart.js |
| IA | Groq API (llama-3.1-8b-instant) · fallback local |
| PWA | Service Worker · Web App Manifest |
| Build | Maven 3.9 (wrapper incluído — sem instalação) |
| Testes | JUnit 5 · Playwright |
| Containers | Docker · Docker Compose |
---
## Desafios do Hackathon

O SpinOps foi projetado para cobrir todos os 15 desafios do Hackathon InovSpin.

### Desafio 1 — Análise e Exploração de Dados
O dashboard apresenta correlação entre variáveis operacionais, matriz de Pearson interativa, heatmap de atividade de 14 semanas e gráficos de distribuição de OS. Os dados são transformados em insight visual de forma automática a cada carregamento.

### Desafio 2 — Previsão e Forecasting
O card **Previsão de Falhas** aplica regressão linear sobre o histórico de OS para gerar projeções futuras, com intervalo de confiança (IC 90%) desenhado no gráfico e badge de **Ajuste: R²** indicando a qualidade do modelo.

### Desafio 3 — Detecção de Anomalias
O card de **Detecção de Anomalias** identifica OS cujos padrões fogem do comportamento esperado via Z-score e IQR, gera alertas visuais de criticidade e explica o desvio detectado em linguagem natural.

### Desafio 4 — Manutenção Preditiva
O módulo de **Saúde de Equipamentos** analisa tipo do ativo, histórico de manutenções e condição visual com IA, gerando um índice de saúde com sugestões de ação. O gráfico de **Evolução de Saúde** (8 semanas) exibe a trajetória de cada equipamento com limiares de atenção (70%) e crítico (40%).

### Desafio 5 — Classificação e Categorização
O card **Classificação de OS** aplica um modelo de priorização supervisionada que categoriza cada OS em CRÍTICA / ALTA / MÉDIA / BAIXA com percentual de confiança. O botão **"Por quê?"** expande a explicação dos fatores que levaram àquela classificação.

### Desafio 6 — Otimização e Recomendação
O card **"Otimização Operacional — Recomendações por IA"** analisa os dados operacionais e gera recomendações automáticas priorizadas por impacto (ALTO / MÉDIO / BAIXO), visando reduzir gargalos e aumentar a eficiência da equipe de manutenção.

### Desafio 7 — Sistemas de Recomendação
O card **"Recomendação Personalizada — Insights IA"** gera insights personalizados via Groq (Llama 3.1) ou fallback local, justificando cada recomendação com base nos padrões históricos dos equipamentos cadastrados.

### Desafio 8 — Visualização Inteligente de Dados
O dashboard agrega 15 cards de IA com destaque automático de dados críticos, alertas visuais por limiar e resumos gerados dinamicamente. A barra de alertas sinaliza OS vencidas e equipamentos em estado crítico em tempo real.

### Desafio 9 — Aprendizado Supervisionado
O card **Treinamento do Modelo** simula o ciclo completo: inicialização de pesos, gradiente descendente por épocas, curva de Acurácia vs. Erro de Aprendizado, e ao final exibe a **Matriz de Confusão** com os resultados de validação.

### Desafio 10 — Aprendizado Não Supervisionado
O card **Clustering K-Means** agrupa OS por similaridade (tipo, prioridade, tempo de resolução) sem rótulos prévios. O **Gráfico de Cotovelo** auxilia na escolha do K ótimo e os grupos são apresentados com o perfil médio de cada cluster.

### Desafio 11 — Simulação Inteligente
O card **Simulação de Cenários** permite ao usuário ajustar parâmetros (eficiência da equipe, taxa de OS críticas, volume) e comparar visualmente o cenário atual com o simulado, projetando o impacto nas próximas semanas.

### Desafio 12 — Tomada de Decisão Automatizada
O motor de priorização automatiza a decisão de criticidade de cada OS com base em múltiplas variáveis (palavras-chave, histórico, equipamento, prazo). A **Matriz de Risco** cruza probabilidade e impacto para sugerir qual OS atender primeiro.

### Desafio 13 — Segurança e Confiabilidade
A **Matriz de Risco** monitora continuamente os equipamentos e OS, classificando-os em quadrantes de criticidade. Alertas automáticos são gerados para OS vencidas, equipamentos abaixo de 40% de saúde e picos anômalos de volume.

### Desafio 14 — Processamento Inteligente de Dados
O card **Qualidade dos Dados** implementa um pipeline de validação e enriquecimento: detecta campos ausentes, duplicatas, valores fora de faixa e datas inválidas, exibindo o índice de completude e os problemas encontrados antes da análise.

### Desafio 15 — Sustentabilidade e Eficiência
O card **Eficiência e Redução de Desperdício** analisa o índice de OS concluídas no prazo, tempo médio de resolução e taxa de retrabalho, identificando oportunidades de melhoria e apresentando métricas de impacto operacional.

---
*Desenvolvido por Gustavo Cirino para o Hackathon InovSpin.*