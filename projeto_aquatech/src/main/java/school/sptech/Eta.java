package school.sptech;

import oshi.SystemInfo;
import oshi.hardware.NetworkIF;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class Eta {

    private static final String UNIDADE_DOWNLOAD = "Mbps (download)";
    private static final String UNIDADE_UPLOAD = "Mbps (upload)";
    private static final String UNIDADE_LATENCIA = "ms (latencia)";
    private static final String UNIDADE_PERDA = "% (perda pacotes)";
    private static final String UNIDADE_PACOTE = "KB (tamanho pacote)";
    private static final String UNIDADE_VELOCIDADE_LINK = "Mbps (velocidade link)";

    private static final long INTERVALO_MS = 5000;
    private static final double LIMITE_LATENCIA_CRITICO_MS = 100.0;

    private static final String SQL_LEITURA =
            "INSERT INTO leitura (valor, data_hora, fk_medida, configuracao_fk_maquina, configuracao_fk_componente) " +
                    "VALUES (?, ?, ?, ?, ?)";

    public void monitorarRede() {
        SystemInfo sistema = new SystemInfo();
        DBConnectionProvider dbProvider = new DBConnectionProvider();
        JdbcTemplate connection = dbProvider.getConnection();

        System.out.println("AQUASHIELD - INICIANDO MONITORAMENTO DE REDE (OSHI)");

        List<NetworkIF> interfaces = sistema.getHardware().getNetworkIFs();
        Integer idMaquina = null;
        String macAddressAtual = "";
        NetworkIF interfaceAlvo = null;

        for (NetworkIF iface : interfaces) {
            String macTemp = iface.getMacaddr();
            if (macTemp != null && !macTemp.isBlank()) {
                System.out.println("Verificando interface: " + iface.getName() + " | MAC: " + macTemp);
                String macBusca = macTemp.replace(":", "").replace("-", "").trim().toUpperCase();

                List<Integer> maquinas = connection.queryForList(
                        "SELECT id FROM maquina WHERE REPLACE(UPPER(mac_address), ':', '') = ? AND ativo = 1 LIMIT 1",
                        Integer.class, macBusca
                );

                if (!maquinas.isEmpty()) {
                    idMaquina = maquinas.get(0);
                    macAddressAtual = macTemp.trim().toUpperCase();
                    interfaceAlvo = iface;
                    break;
                }
            }
        }

        if (idMaquina == null) {
            System.out.println("ATENCAO: Maquina nao encontrada no banco. Execute o Python primeiro.");
            return;
        }

        System.out.println("Maquina reconhecida (ID: " + idMaquina + ", MAC: " + macAddressAtual + ")");

        Integer idComponenteRede;

        try {
            Integer idCompBusca = connection.queryForObject(
                    "SELECT c.id FROM componente c " +
                            "JOIN tipo_componente tc ON c.fk_tipoComponente = tc.id " +
                            "WHERE tc.tipo LIKE '%Rede%' LIMIT 1",
                    Integer.class
            );

            Integer jaConfigurado = connection.queryForObject(
                    "SELECT COUNT(*) FROM configuracao WHERE fk_maquina = ? AND fk_componente = ?",
                    Integer.class, idMaquina, idCompBusca
            );

            if (jaConfigurado != null && jaConfigurado == 0) {
                connection.update(
                        "INSERT INTO configuracao (fk_maquina, fk_componente, fk_statusMaquina) VALUES (?, ?, 1)",
                        idMaquina, idCompBusca
                );
            }

            idComponenteRede = idCompBusca;
        } catch (Exception e) {
            System.out.println("Erro: Componente de 'Rede' nao configurado para esta maquina.");
            return;
        }

        Integer idMedidaDownload = obterIdMedida(connection, UNIDADE_DOWNLOAD);
        Integer idMedidaUpload = obterIdMedida(connection, UNIDADE_UPLOAD);
        Integer idMedidaLatencia = obterIdMedida(connection, UNIDADE_LATENCIA);
        Integer idMedidaPerda = obterIdMedida(connection, UNIDADE_PERDA);
        Integer idMedidaPacote = obterIdMedida(connection, UNIDADE_PACOTE);
        Integer idMedidaVelocidade = obterIdMedida(connection, UNIDADE_VELOCIDADE_LINK);

        String hostLatencia = System.getenv().getOrDefault("LATENCY_HOST",
                System.getenv().getOrDefault("DB_HOST", "localhost"));
        int portaLatencia = Integer.parseInt(System.getenv().getOrDefault("LATENCY_PORT",
                System.getenv().getOrDefault("DB_PORT", "3306")));

        System.out.println("Captura de Rede em andamento (Interface: " + interfaceAlvo.getName() + ")...\n");

        String hostname = sistema.getOperatingSystem().getNetworkParams().getHostName();
        String so = sistema.getOperatingSystem().getFamily();
        String versaoSO = sistema.getOperatingSystem().getVersionInfo().getVersion();
        String[] ipv4s = interfaceAlvo.getIPv4addr();
        String ipMaquina = (ipv4s != null && ipv4s.length > 0) ? ipv4s[0] : "N/A";

        while (true) {
            try {
                interfaceAlvo.updateAttributes();
                long recebidosAntes = interfaceAlvo.getBytesRecv();
                long enviadosAntes = interfaceAlvo.getBytesSent();
                long pktsAntes = interfaceAlvo.getPacketsRecv() + interfaceAlvo.getPacketsSent();
                long perdasAntes = interfaceAlvo.getInDrops() + interfaceAlvo.getInErrors() + interfaceAlvo.getOutErrors();

                Thread.sleep(INTERVALO_MS);

                interfaceAlvo.updateAttributes();
                long recebidosAgora = interfaceAlvo.getBytesRecv();
                long enviadosAgora = interfaceAlvo.getBytesSent();
                long pktsAgora = interfaceAlvo.getPacketsRecv() + interfaceAlvo.getPacketsSent();
                long perdasAgora = interfaceAlvo.getInDrops() + interfaceAlvo.getInErrors() + interfaceAlvo.getOutErrors();

                double segundos = INTERVALO_MS / 1000.0;
                long deltaBytesRecv = Math.max(0, recebidosAgora - recebidosAntes);
                long deltaBytesSent = Math.max(0, enviadosAgora - enviadosAntes);
                long deltaPkts = Math.max(0, pktsAgora - pktsAntes);
                long deltaPerdas = Math.max(0, perdasAgora - perdasAntes);

                double recebimento = arredondar((deltaBytesRecv * 8.0) / 1_000_000.0 / segundos);
                double envio = arredondar((deltaBytesSent * 8.0) / 1_000_000.0 / segundos);
                double perdaPacotes = deltaPkts > 0 ? arredondar((deltaPerdas * 100.0) / deltaPkts) : 0.0;
                double tamanhoPacoteKB = deltaPkts > 0 ? arredondar(((double) (deltaBytesRecv + deltaBytesSent) / deltaPkts) / 1024.0) : 0.0;
                double velocidadeLinkMbps = interfaceAlvo.getSpeed() > 0 ? arredondar(interfaceAlvo.getSpeed() / 1_000_000.0) : 1000.0;
                double latenciaMs = arredondar(medirLatenciaMs(hostLatencia, portaLatencia));

                Timestamp agora = Timestamp.valueOf(LocalDateTime.now());

                long idLeituraDownload = inserirLeitura(connection, recebimento, agora, idMedidaDownload, idMaquina, idComponenteRede);
                inserirLeitura(connection, envio, agora, idMedidaUpload, idMaquina, idComponenteRede);
                long idLeituraLatencia = inserirLeitura(connection, latenciaMs, agora, idMedidaLatencia, idMaquina, idComponenteRede);
                inserirLeitura(connection, perdaPacotes, agora, idMedidaPerda, idMaquina, idComponenteRede);
                inserirLeitura(connection, tamanhoPacoteKB, agora, idMedidaPacote, idMaquina, idComponenteRede);
                inserirLeitura(connection, velocidadeLinkMbps, agora, idMedidaVelocidade, idMaquina, idComponenteRede);

                Map<String, Object> compBD = connection.queryForMap(
                        "SELECT c.fk_definicaoAlerta AS idAtencao, da.valor_limite AS limiteAtencao, " +
                                "COALESCE(c.fk_definicaoAlertaCritico, 5) AS idCritico, " +
                                "COALESCE(da_crit.valor_limite, 80.0) AS limiteCritico " +
                                "FROM componente c " +
                                "JOIN definicao_alerta da ON c.fk_definicaoAlerta = da.id " +
                                "LEFT JOIN definicao_alerta da_crit ON c.fk_definicaoAlertaCritico = da_crit.id " +
                                "WHERE c.id = ? LIMIT 1",
                        idComponenteRede
                );

                int idAtencao = ((Number) compBD.get("idAtencao")).intValue();
                double limiteAtencao = ((Number) compBD.get("limiteAtencao")).doubleValue();
                int idCritico = ((Number) compBD.get("idCritico")).intValue();
                double limiteCritico = ((Number) compBD.get("limiteCritico")).doubleValue();

                if (recebimento > limiteCritico) {
                    System.out.println("ALERTA CRITICO: Download (" + String.format("%.2f", recebimento) + " Mbps) acima do limite!");
                    Slack.enviarAlerta(
                            connection, hostname, ipMaquina, so, versaoSO,
                            "Rede", "Crítico", recebimento, limiteCritico, " Mbps"
                    );
                    connection.update(
                            "INSERT INTO leitura_alerta (fk_leitura, fk_definicaoAlerta) VALUES (?, ?)",
                            idLeituraDownload, idCritico
                    );
                } else if (recebimento >= limiteAtencao) {
                    System.out.println("ATENCAO: Download (" + String.format("%.2f", recebimento) + " Mbps) acima do limite!");
                    connection.update(
                            "INSERT INTO leitura_alerta (fk_leitura, fk_definicaoAlerta) VALUES (?, ?)",
                            idLeituraDownload, idAtencao
                    );
                }

                if (latenciaMs > LIMITE_LATENCIA_CRITICO_MS) {
                    System.out.println("ALERTA CRITICO: Latencia (" + String.format("%.2f", latenciaMs) + " ms) acima do limite!");
                    Slack.enviarAlerta(
                            connection, hostname, ipMaquina, so, versaoSO,
                            "Rede", "Crítico", latenciaMs, LIMITE_LATENCIA_CRITICO_MS, " ms"
                    );
                    connection.update(
                            "INSERT INTO leitura_alerta (fk_leitura, fk_definicaoAlerta) VALUES (?, ?)",
                            idLeituraLatencia, idCritico
                    );
                }

                System.out.printf("Download: %.2f Mbps | Upload: %.2f Mbps | Latencia: %.2f ms | Perda: %.2f%% | Pacote: %.2f KB%n",
                        recebimento, envio, latenciaMs, perdaPacotes, tamanhoPacoteKB);
                System.out.println("------------------------------------------------------------");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.out.println("Erro no ciclo: " + e.getMessage());
            }
        }
    }

    private double medirLatenciaMs(String host, int porta) {
        long inicio = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, porta), 2000);
            return Math.max(0.1, (System.nanoTime() - inicio) / 1_000_000.0);
        } catch (Exception e) {
            return 2000.0;
        }
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private Integer obterIdMedida(JdbcTemplate connection, String unidade) {
        List<Integer> ids = connection.queryForList(
                "SELECT id FROM medida WHERE unidade_medida = ? LIMIT 1", Integer.class, unidade);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        connection.update("INSERT INTO medida (unidade_medida) VALUES (?)", unidade);
        return connection.queryForObject(
                "SELECT id FROM medida WHERE unidade_medida = ? LIMIT 1", Integer.class, unidade);
    }

    private long inserirLeitura(JdbcTemplate connection, double valor, Timestamp dataHora, int idMedida, int idMaquina, int idComponente) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        connection.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_LEITURA, Statement.RETURN_GENERATED_KEYS);
            ps.setDouble(1, valor);
            ps.setTimestamp(2, dataHora);
            ps.setInt(3, idMedida);
            ps.setInt(4, idMaquina);
            ps.setInt(5, idComponente);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}