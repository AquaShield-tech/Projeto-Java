package school.sptech;

import oshi.SystemInfo;
import oshi.hardware.NetworkIF;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

public class Eta {

    private static final String UNIDADE_DOWNLOAD = "Mbps (download)";
    private static final String UNIDADE_UPLOAD = "Mbps (upload)";
    private static final long INTERVALO_MS = 5000;

    private static final String SQL_LEITURA =
            "INSERT INTO leitura (valor, data_hora, fk_medida, configuracao_fk_maquina, configuracao_fk_componente) " +
                    "VALUES (?, NOW(), ?, ?, ?)";

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
                macTemp = macTemp.replace(":", "").replace("-", "").trim().toLowerCase();
                try {
                    idMaquina = connection.queryForObject(
                            "SELECT id FROM maquina WHERE mac_address = ? LIMIT 1",
                            Integer.class, macTemp
                    );
                    if (idMaquina != null) {
                        macAddressAtual = macTemp;
                        interfaceAlvo = iface;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (idMaquina == null) {
            System.out.println("ATENCAO: Maquina nao encontrada no banco. Execute o Python primeiro.");
            return;
        }

        System.out.println("Maquina reconhecida (ID: " + idMaquina + ", MAC: " + macAddressAtual + ")");

        Integer idComponenteRede;
        Integer fkDefinicaoAlerta;
        double limiteBanco = 100.0;

        try {
            // CORRIGIDO: a tabela se chama "componente" (singular), como no Script.sql
            Map<String, Object> compBD = connection.queryForMap(
                    "SELECT c.id AS idComp, c.fk_definicaoAlerta AS idAlerta, da.valor_limite AS limite " +
                            "FROM configuracao cfg " +
                            "JOIN componente c ON cfg.fk_componente = c.id " +
                            "JOIN definicao_alerta da ON c.fk_definicaoAlerta = da.id " +
                            "JOIN tipo_componente tc ON c.fk_tipoComponente = tc.id " +
                            "WHERE cfg.fk_maquina = ? AND tc.tipo LIKE '%Rede%' LIMIT 1",
                    idMaquina
            );

            idComponenteRede = ((Number) compBD.get("idComp")).intValue();
            fkDefinicaoAlerta = ((Number) compBD.get("idAlerta")).intValue();

            Object limiteObj = compBD.get("limite");
            if (limiteObj != null) {
                limiteBanco = ((BigDecimal) limiteObj).doubleValue();
            }
        } catch (Exception e) {
            System.out.println("Erro: Componente de 'Rede' nao configurado para esta maquina.");
            return;
        }

        // CORRIGIDO: ids de medida buscados (ou criados) no banco, sem números fixos
        Integer idMedidaDownload = obterIdMedida(connection, UNIDADE_DOWNLOAD);
        Integer idMedidaUpload = obterIdMedida(connection, UNIDADE_UPLOAD);

        System.out.println("Captura de Rede em andamento (Interface: " + interfaceAlvo.getName() + ")...\n");

        while (true) {
            try {
                interfaceAlvo.updateAttributes();
                long recebidosAntes = interfaceAlvo.getBytesRecv();
                long enviadosAntes = interfaceAlvo.getBytesSent();

                Thread.sleep(INTERVALO_MS);

                interfaceAlvo.updateAttributes();
                long recebidosAgora = interfaceAlvo.getBytesRecv();
                long enviadosAgora = interfaceAlvo.getBytesSent();

                // bytes -> megabits; a média por segundo é calculada abaixo
                double segundos = INTERVALO_MS / 1000.0;
                double recebimento = Math.max(0, (recebidosAgora - recebidosAntes) * 8.0 / 1_000_000.0 / segundos);
                double envio = Math.max(0, (enviadosAgora - enviadosAntes) * 8.0 / 1_000_000.0 / segundos);

                // CORRIGIDO: o id da leitura vem da própria inserção (GeneratedKeyHolder),
                // e não de um LAST_INSERT_ID() em outra consulta, que pode usar outra conexão do pool.
                long idLeituraDownload = inserirLeitura(connection, recebimento, idMedidaDownload, idMaquina, idComponenteRede);
                inserirLeitura(connection, envio, idMedidaUpload, idMaquina, idComponenteRede);

                if (recebimento > limiteBanco) {
                    System.out.println("ALERTA: Download (" + String.format("%.2f", recebimento) + " Mbps) acima do limite!");
                    connection.update(
                            "INSERT INTO leitura_alerta (fk_leitura, fk_definicaoAlerta) VALUES (?, ?)",
                            idLeituraDownload, fkDefinicaoAlerta
                    );
                }

                System.out.printf("Download: %.2f Mbps | Upload: %.2f Mbps%n", recebimento, envio);
                System.out.println("------------------------------------------------------------");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.out.println("Erro no ciclo: " + e.getMessage());
            }
        }
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

    private long inserirLeitura(JdbcTemplate connection, double valor, int idMedida, int idMaquina, int idComponente) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        connection.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_LEITURA, Statement.RETURN_GENERATED_KEYS);
            ps.setDouble(1, valor);
            ps.setInt(2, idMedida);
            ps.setInt(3, idMaquina);
            ps.setInt(4, idComponente);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}