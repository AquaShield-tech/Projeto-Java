package school.sptech;

import oshi.SystemInfo;
import oshi.hardware.NetworkIF;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.math.BigDecimal;

public class Eta {
    public void monitorarRede() {
        SystemInfo sistema = new SystemInfo();
        DBConnectionProvider dbProvider = new DBConnectionProvider();
        JdbcTemplate connection = dbProvider.getConnection();

        System.out.println("AQUASHIELD - INICIANDO MONITORAMENTO AUTOMÁTICO DE REDE");

        // Captura do MAC Address da máquina
        String macAddressAtual = "";
        try {
            macAddressAtual = sistema.getHardware().getNetworkIFs().get(0).getMacaddr();
            if (macAddressAtual != null) {
                macAddressAtual = macAddressAtual.replace(":", "").trim();
            }
        } catch (Exception e) {
            macAddressAtual = "000000000000";
        }

        // Busca o ID da máquina no banco com o MAC Address
        Integer idMaquina = null;
        try {
            idMaquina = connection.queryForObject(
                    "SELECT id FROM Maquina WHERE mac_address = ? LIMIT 1",
                    Integer.class, macAddressAtual
            );
            System.out.println("Maquina reconhecida no banco de dados (ID: " + idMaquina + ", MAC: " + macAddressAtual + ")\n");
        } catch (Exception e) {
            System.out.println("ATENCAO: Esta maquina (MAC: " + macAddressAtual + ") nao esta cadastrada no banco.");
            System.out.println("Certifique-se de rodar o script Python primeiro para que ele cadastre a maquina automaticamente.");
            return; // Para a execução se a máquina não existir
        }

        // Busca a configuração do componente de Rede para esta máquina
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
            System.out.println("Erro: Componente de 'Rede' nao configurado para esta maquina no banco de dados.");
            return;
        }

        Double limiteFinal = (limiteBanco != null) ? limiteBanco : 100.0;
        Integer idMedidaDownload = 1;
        Integer idMedidaUpload = 2;

        System.out.println("AquaShield: Captura de Rede em andamento...\n");

        // Loop infinito ea automático de leitura e inserção
        while (true) {
            try {
                for (NetworkIF rede : sistema.getHardware().getNetworkIFs()) {
                    rede.updateAttributes();
                    Long recebidosAntes = rede.getBytesRecv();
                    Long enviadosAntes = rede.getBytesSent();

                    Thread.sleep(5000);

                    rede.updateAttributes();
                    Long bytesRecebidosAgora = rede.getBytesRecv();
                    Long bytesEnviadosAgora = rede.getBytesSent();

                    Double recebimento = (bytesRecebidosAgora - recebidosAntes) * 8.0 / 1_000_000;
                    Double envio = (bytesEnviadosAgora - enviadosAntes) * 8.0 / 1_000_000;

                    NetworkIF.IfOperStatus operStatus = rede.getIfOperStatus();
                    Boolean ativa = (operStatus == NetworkIF.IfOperStatus.UP);
                    String estadoStr = Boolean.TRUE.equals(ativa) ? "Ativa" : "Inativa";

                    String sqlLeitura = "INSERT INTO Leitura (valor, data_hora, fk_medida, configuracao_fk_maquina, configuracao_fk_componente) VALUES (?, NOW(), ?, ?, ?)";

                    connection.update(sqlLeitura, recebimento, idMedidaDownload, idMaquina, idComponenteRede);
                    Long idLeituraDownload = connection.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

                    connection.update(sqlLeitura, envio, idMedidaUpload, idMaquina, idComponenteRede);

                    if (recebimento > limiteFinal) {
                        String msgAlerta = "ALERTA: Download (" + String.format("%.2f", recebimento) + " Mbps) acima do limite (" + limiteFinal + " Mbps)!";
                        System.out.println(msgAlerta);

                        String sqlAlerta = "INSERT INTO Leitura_Alerta (fk_leitura, fk_definicaoAlerta) VALUES (?, ?)";
                        connection.update(sqlAlerta, idLeituraDownload, fkDefinicaoAlerta);
                    }

                    System.out.println("Interface: " + rede.getName() + " | Estado: " + estadoStr);
                    System.out.printf("Download: %.2f Mbps | Upload: %.2f Mbps%n", recebimento, envio);
                    System.out.println("------------------------------------------------------------");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Monitoramento interrompido.");
                break;
            } catch (Exception e) {
                System.out.println("Erro no ciclo: " + e.getMessage());
            }
        }
    }
}