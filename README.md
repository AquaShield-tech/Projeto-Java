# AquaShield — Monitoramento de Rede (ETA)

Aplicação Java para monitoramento em tempo real do tráfego de rede em Estações de Tratamento de Água (ETA), integrada ao banco de dados MySQL para registro de métricas e alertas.

---

## Funcionalidades

- **Autenticação no Console**: Validação de acesso por e-mail e senha de usuário cadastrado.
- **Reconhecimento de Máquina**: Identificação automática do dispositivo via endereço MAC (com tratamento prévio de formatação).
- **Leitura de Tráfego de Rede**: Captura contínua de métricas de download e upload (Mbps) utilizando a biblioteca OSHI.
- **Persistência no Banco de Dados**: Gravação automática das leituras na tabela `Leitura` via `JdbcTemplate`.
- **Sistema de Alertas**: Identificação e registro de ultrapassagens de limites de tráfego configurados.

---

## Tecnologias Utilizadas

- **Linguagem**: Java
- **Gerenciador de Dependências**: Maven
- **Biblioteca de Captura**: OSHI (Operating System and Hardware Information)
- **Persistência de Dados**: Spring JDBC (`JdbcTemplate`)
- **Banco de Dados**: MySQL

---

## Pré-requisitos

- Java JDK 17 ou superior
- MySQL Server configurado e rodando
- Maven instalado (ou executado direto pela IDE)
