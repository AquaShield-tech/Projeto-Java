package school.sptech;

import oshi.SystemInfo;
import oshi.hardware.NetworkIF;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Scanner;
import java.util.Map;
import java.math.BigDecimal;

public class Eta {
    public void monitorarRede() {
        Scanner scanner = new Scanner(System.in);
        SystemInfo sistema = new SystemInfo();
        DBConnectionProvider dbProvider = new DBConnectionProvider();
        JdbcTemplate connection = dbProvider.getConnection();

        System.out.println("AQUASHIELD - LOGIN");

        Integer idUsuarioLogado = null;
        Integer idEmpresaUsuario = null;

        while (idUsuarioLogado == null) {
            System.out.print("E-mail: ");
            String emailInput = scanner.nextLine();

            System.out.print("Senha: ");
            String senhaInput = scanner.nextLine();

            try {
                Map<String, Object> usuarioBD = connection.queryForMap("SELECT id, fk_empresa FROM Usuario WHERE email = ? AND senha = ?",
                        emailInput, senhaInput
                );

                idUsuarioLogado = (Integer) usuarioBD.get("id");
                idEmpresaUsuario = (Integer) usuarioBD.get("fk_empresa");
                System.out.println("\nLogin realizado com sucesso! Bem-vindo.");
            } catch (Exception e) {
                System.out.println("\nE-mail ou senha incorretos. Tente novamente.\n");
            }
        }

        String macAddressAtual = "";
        try {
            macAddressAtual = sistema.getHardware().getNetworkIFs().get(0).getMacaddr();

            if (macAddressAtual != null) {
                macAddressAtual = macAddressAtual.replace(":", "").trim();
            }
        } catch (Exception e) {
            macAddressAtual = "000000000000";
        }

        Integer idMaquina = null;
        try {
            idMaquina = connection.queryForObject("SELECT m.id FROM Maquina m " + "JOIN ETA e ON m.fk_eta = e.id " + "JOIN CCO c ON e.fk_cco = c.id " + "WHERE m.mac_address = ? AND c.fk_empresa = ?",
                    Integer.class, macAddressAtual, idEmpresaUsuario
            );
            System.out.println("Maquina reconhecida no sistema (ID: " + idMaquina + ")\n");
        } catch (Exception e) {
            System.out.println("ATENCAO: Esta maquina (MAC: " + macAddressAtual + ") nao esta cadastrada no site para a sua empresa.");
            System.out.println("Cadastre a maquina antes de iniciar o monitoramento.");
            return;
        }

        Integer idComponenteRede = null;
        Integer fkDefinicaoAlerta = null;
        Double limiteBanco = 100.0;

        try {
            Map<String, Object> compBD = connection.queryForMap("SELECT c.id as idComp, c.fk_definicaoAlerta as idAlerta, da.valor_limite as limite " +
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
            System.out.println("Erro detalhado ao buscar componente: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        Double limiteFinal = (limiteBanco != null) ? limiteBanco : 100.0;
        Integer idMedidaDownload = 1;
        Integer idMedidaUpload = 2;

        System.out.println("AquaShield: Monitoramento de Rede iniciado");

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
                    System.out.printf("Download: %.2f Mbps (Limite: %.2f) | Upload: %.2f Mbps%n", recebimento, limiteFinal, envio);
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