package school.sptech;

import oshi.SystemInfo;
import oshi.hardware.NetworkIF;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

public class Eta {
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
                } catch (Exception ignored) { }
            }
        }

        if (idMaquina == null) {
            System.out.println("ATENCAO: Maquina nao encontrada no banco. Execute o Python primeiro.");
            return;
        }

        System.out.println("Maquina reconhecida (ID: " + idMaquina + ", MAC: " + macAddressAtual + ")");

        Integer idComponenteRede = null;
        Integer fkDefinicaoAlerta = null;
        Double limiteBanco = 100.0;

        try {
            Map<String, Object> compBD = connection.queryForMap(
                    "SELECT c.id as idComp, c.fk_definicaoAlerta as idAlerta, da.valor_limite as limite " +
                            "FROM configuracao cfg " +
                            "JOIN componentes c ON cfg.fk_componente = c.id " +
                            "JOIN definicao_alerta da ON c.fk_definicaoAlerta = da.id " +
                            "JOIN tipo_componente tc ON c.fk_tipoComponente = tc.id " +
                            "WHERE cfg.fk_maquina = ? AND tc.tipo LIKE '%Rede%' LIMIT 1", idMaquina
            );

            idComponenteRede = (Integer) compBD.get("idComp");
            fkDefinicaoAlerta = (Integer) compBD.get("idAlerta");

            Object limiteObj = compBD.get("limite");
            if (limiteObj != null) {
                limiteBanco = ((BigDecimal) limiteObj).doubleValue();
            }
        } catch (Exception e) {
            System.out.println("Erro: Componente de 'Rede' nao configurado para esta maquina.");
            return;
        }

        System.out.println("Captura de Rede em andamento (Interface: " + interfaceAlvo.getName() + ")...\n");

        while (true) {
            try {
                interfaceAlvo.updateAttributes();
                Long recebidosAntes = interfaceAlvo.getBytesRecv();
                Long enviadosAntes = interfaceAlvo.getBytesSent();

                Thread.sleep(5000);

                interfaceAlvo.updateAttributes();
                Long bytesRecebidosAgora = interfaceAlvo.getBytesRecv();
                Long bytesEnviadosAgora = interfaceAlvo.getBytesSent();

                Double recebimento = Math.max(0, (bytesRecebidosAgora - recebidosAntes) * 8.0 / 1_000_000.0);
                Double envio = Math.max(0, (bytesEnviadosAgora - enviadosAntes) * 8.0 / 1_000_000.0);

                String sqlLeitura = "INSERT INTO leitura (valor, data_hora, fk_medida, configuracao_fk_maquina, configuracao_fk_componente) VALUES (?, NOW(), ?, ?, ?)";

                connection.update(sqlLeitura, recebimento, 1, idMaquina, idComponenteRede); // 1 = idMedidaDownload
                Long idLeitDown = connection.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

                connection.update(sqlLeitura, envio, 2, idMaquina, idComponenteRede);       // 2 = idMedidaUpload

                if (recebimento > limiteBanco) {
                    System.out.println("ALERTA: Download (" + String.format("%.2f", recebimento) + " Mbps) acima do limite!");
                    connection.update("INSERT INTO leitura_alerta (fk_leitura, fk_definicaoAlerta) VALUES (?, ?)", idLeitDown, fkDefinicaoAlerta);
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
}