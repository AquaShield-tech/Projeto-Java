
package school.sptech;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class Slack {

    private static final String WEBHOOK_URL = "WEBHOOK_AQUI";

    // Cliente responsável pelo envio das mensagens
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    // Intervalo de 5 minutos entre alertas do mesmo componente
    private static final long INTERVALO_MINIMO = 5 * 60 * 1000;

    // Guarda o horário do último alerta enviado
    private static final Map<String, Long> ultimoAlerta = new HashMap<>();

    // Formato da data e horário
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static String obterIpMaquina() {

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface rede = interfaces.nextElement();

                if (rede.isUp() && !rede.isLoopback()) {
                    Enumeration<InetAddress> enderecos = rede.getInetAddresses();

                    while (enderecos.hasMoreElements()) {
                        InetAddress endereco = enderecos.nextElement();

                        if (endereco instanceof java.net.Inet4Address && !endereco.isLoopbackAddress()) {
                            return endereco.getHostAddress();
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Erro ao buscar IP: " + e.getMessage());
        }
        return "N/A";
    }

    private static Integer buscarIdMaquina(
            JdbcTemplate connection,
            String hostname) {

        try {
            return connection.queryForObject(
                    "SELECT id FROM maquina WHERE hostname = ? AND ativo = 1 LIMIT 1",
                    Integer.class,
                    hostname
            );

        } catch (EmptyResultDataAccessException e) {
            System.out.println("Máquina não encontrada: " + hostname);
            return null;
        }
    }

    private static Integer buscarIdComponente(
            JdbcTemplate connection,
            String componente) {

        try {
            return connection.queryForObject(
                    "SELECT c.id " +
                            "FROM componente c " +
                            "JOIN tipo_componente tc ON c.fk_tipoComponente = tc.id " +
                            "WHERE tc.tipo = ? LIMIT 1",
                    Integer.class,
                    componente
            );

        } catch (EmptyResultDataAccessException e) {
            System.out.println("Componente não encontrado: " + componente);
            return null;
        }
    }


    // Envia o alerta para o Slack
    public static synchronized void enviarAlerta(
            JdbcTemplate connection,
            String hostname,
            String ipMaquina,
            String so,
            String versaoSO,
            String componente,
            String nivelAlerta,
            Double valorIdentificado,
            Double limiteConfigurado,
            String unidade) {

        try {
            if (WEBHOOK_URL == null || WEBHOOK_URL.isBlank() || WEBHOOK_URL.equals("COLE_SEU_WEBHOOK_AQUI")) {
                System.out.println("URL do Slack não configurada.");
                return;
            }

            Long ultimoEnvio = ultimoAlerta.get(componente);

            if (ultimoEnvio != null) {
                long tempoAtual = System.currentTimeMillis();
                long tempoPassado = tempoAtual - ultimoEnvio;

                if (tempoPassado < INTERVALO_MINIMO) {
                    System.out.println("Alerta recente para o componente: " + componente);
                    return;
                }
            }

            if (ipMaquina == null || ipMaquina.isBlank() || ipMaquina.equalsIgnoreCase("N/A")) {
                ipMaquina = obterIpMaquina();
            }

            String dataHora = LocalDateTime.now().format(FORMATTER);

            String mensagem = String.format(
                    Locale.US,
                    ":warning: *ALERTA AQUASHIELD*\n\n" +

                            "*IDENTIFICAÇÃO DA MÁQUINA*\n" +
                            "*Hostname:* %s\n" +
                            "*Endereço IP:* %s\n" +
                            "*Sistema operacional:* %s\n" +
                            "*Versão:* %s\n\n" +

                            "*INFORMAÇÕES DO ALERTA*\n" +
                            "*Componente:* %s\n" +
                            "*Nível:* %s\n" +
                            "*Valor identificado:* %.2f%s\n" +
                            "*Limite configurado:* %.2f%s\n" +
                            "*Data e horário:* %s\n\n" +

                            "*AÇÃO RECOMENDADA*\n" +
                            "Verificar o consumo do componente e identificar " +
                            "possíveis sobrecargas no servidor SCADA.",
                    hostname,
                    ipMaquina,
                    so,
                    versaoSO,
                    componente,
                    nivelAlerta,
                    valorIdentificado,
                    unidade,
                    limiteConfigurado,
                    unidade,
                    dataHora
            );


            // Cria o JSON que será enviado ao Slack
            JSONObject json = new JSONObject();
            json.put("text", mensagem);

            String jsonPayload = json.toString();

            // Configura a requisição HTTP
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WEBHOOK_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            // Envia a mensagem e recebe a resposta
            HttpResponse<String> response = CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            // Verifica se o Slack recebeu a mensagem
            if (response.statusCode() == 200
                    && response.body().trim().equals("ok")) {

                System.out.println("Alerta enviado ao Slack!");

                Integer idMaquina = buscarIdMaquina(connection, hostname);
                Integer idComponente = buscarIdComponente(connection, componente);

                if (idMaquina != null && idComponente != null) {
                    connection.update(
                            "INSERT INTO slack " +
                                    "(fk_maquina, fk_componente, nivelAlerta, " +
                                    "valorIdentificado, limiteConfigurado) " +
                                    "VALUES (?, ?, ?, ?, ?)",
                            idMaquina,
                            idComponente,
                            nivelAlerta,
                            valorIdentificado,
                            limiteConfigurado
                    );
                    System.out.println("Alerta registrado na tabela Slack!");
                } else {
                    System.out.println("Mensagem enviada, mas não foi possível registrar no banco.");
                }

                ultimoAlerta.put(componente, System.currentTimeMillis());
            } else {
                System.out.println("Erro ao enviar mensagem: " + response.body());
            }
        } catch (Exception e) {
            System.out.println("Erro no envio do alerta para o Slack: " + e.getMessage());
        }
    }
}
