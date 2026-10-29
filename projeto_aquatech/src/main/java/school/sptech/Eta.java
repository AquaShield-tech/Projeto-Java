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

        System.out.println("A procurar MAC Address correspondente na base de dados...");

        for (NetworkIF iface : interfaces) {
            String macTemp = iface.getMacaddr();
            if (macTemp != null && !macTemp.isBlank()) {
                
                macTemp = macTemp.replace(":", "").replace("-", "").trim().toLowerCase();
                System.out.println(" -> A analisar interface: " + iface.getName() + " | MAC: " + macTemp);

                try {
                    idMaquina = connection.queryForObject(
                            "SELECT id FROM Maquina WHERE mac_address = ? LIMIT 1",
                            Integer.class, macTemp
                    );
                    if (idMaquina != null) {
                        macAddressAtual = macTemp;
                        interfaceAlvo = iface;
                        System.out.println("    [SUCESSO] Placa encontrada na base de dados!");
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("    [FALHA] Este MAC não está registado.");
                }
            }
        }

        if (idMaquina == null) {
            System.out.println("\nATENCAO: Nenhuma placa de rede desta maquina coincide com a base de dados.");
            System.out.println("Certifique-se de que o Python executou e registou a maquina com sucesso.");
            return;
        }

        System.out.println("\nMaquina reconhecida na base de dados (ID: " + idMaquina + ", MAC: " + macAddressAtual + ")\n");

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
            System.out.println("Erro: Componente de 'Rede' não configurado para esta maquina.");
            return;
        }

        Double limiteFinal = (limiteBanco != null) ? limiteBanco : 100.0;
        Integer idMedidaDownload = 1;
        Integer idMedidaUpload = 2;

        System.out.println("AquaShield: Captura de Rede em andamento (Interface: " + interfaceAlvo.getName() + ")...\n");

        while (true) {
            try {
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