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

        // Testa as placas de rede até achar a que o python cadastrou
        List<NetworkIF> interfaces = sistema.getHardware().getNetworkIFs();
        Integer idMaquina = null;
        String macAddressAtual = "";
        NetworkIF interfaceAlvo = null;

        for (NetworkIF iface : interfaces) {
            String macTemp = iface.getMacaddr();
            if (macTemp != null && !macTemp.isBlank()) {
                macTemp = macTemp.replace(":", "").trim();

                try {
                    idMaquina = connection.queryForObject(
                            "SELECT id FROM Maquina WHERE mac_address = ? LIMIT 1",
                            Integer.class, macTemp
                    );
                    if (idMaquina != null) {
                        macAddressAtual = macTemp;
                        interfaceAlvo = iface;
                        break;
                    }
                } catch (Exception e) {
                    // Se não achar vai pra próxima
                }
            }
        }

        if (idMaquina == null) {
            System.out.println("ATENCAO: Nenhuma placa de rede desta maquina bate com o banco de dados.");
            System.out.println("Certifique-se de que o Python rodou e cadastrou a maquina com sucesso.");
            return;
        }

        System.out.println("Maquina reconhecida no banco de dados (ID: " + idMaquina + ", MAC: " + macAddressAtual + ")\n");

        // Busca a configuração do componente
        Integer idComponenteRede = null;
        Integer fkDefinicaoAlerta = null;
        Double limiteBanco = 100.0;

        try {
            Map<String, Object> compBD = connection.queryForMap(
                    "SELECT c.id as idComp, c.fk_definicaoAlerta as idAlerta, da.valor_limite as limite " +
                            "FROM Configuracao cfg " +
                            "JOIN Componentes c ON cfg.fk_componente = c.id " +
                            "JOIN Definicao_Alerta da ON c.fk_definicaoAlerta = da.id " +
                            "JOIN Tipo_Componente tc ON c.fk_tipoComponente = tc.id " +
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

        Double limiteFinal = (limiteBanco != null) ? limiteBanco : 100.0;
        Integer idMedidaDownload = 1;
        Integer idMedidaUpload = 2;

        System.out.println("AquaShield: Captura de Rede em andamento (Interface: " + interfaceAlvo.getName() + ")...\n");

        // Monitoramento contínuo
        while (true) {
            try {
                // Atualiza os dados em tempo real
                interfaceAlvo.updateAttributes();
                Long recebidosAntes = interfaceAlvo.getBytesRecv();
                Long enviadosAntes = interfaceAlvo.getBytesSent();

                Thread.sleep(5000);

                interfaceAlvo.updateAttributes();
                Long bytesRecebidosAgora = interfaceAlvo.getBytesRecv();
                Long bytesEnviadosAgora = interfaceAlvo.getBytesSent();

                Double recebimento = (bytesRecebidosAgora - recebidosAntes) * 8.0 / 1_000_000.0;
                Double envio = (bytesEnviadosAgora - enviadosAntes) * 8.0 / 1_000_000.0;

                if (recebimento < 0) recebimento = 0.0;
                if (envio < 0) envio = 0.0;

                String sqlLeitura = "INSERT INTO Leitura (valor, data_hora, fk_medida, configuracao_fk_maquina, configuracao_fk_componente) VALUES (?, NOW(), ?, ?, ?)";
                connection.update(sqlLeitura, recebimento, idMedidaDownload, idMaquina, idComponenteRede);
                Long idLeituraDownload = connection.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

                connection.update(sqlLeitura, envio, idMedidaUpload, idMaquina, idComponenteRede);

                if (recebimento > limiteFinal) {
                    System.out.println("ALERTA: Download (" + String.format("%.2f", recebimento) + " Mbps) acima do limite!");
                    connection.update("INSERT INTO Leitura_Alerta (fk_leitura, fk_definicaoAlerta) VALUES (?, ?)", idLeituraDownload, fkDefinicaoAlerta);
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