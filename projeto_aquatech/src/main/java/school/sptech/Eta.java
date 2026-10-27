package school.sptech;

import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

public class Eta {

    private Double limiteRecebimento;
    private Double limiteEnvio;
    private Double limiteAviso;
    private Double limiteCritico;

    public Eta() {
        this.limiteRecebimento = 80.0;
        this.limiteEnvio = 80.0;
        this.limiteAviso = 80.0;
        this.limiteCritico = 95.0;
    }

    public Eta(Double limiteRecebimento, Double limiteEnvio, Double limiteAviso, Double limiteCritico) {
        this.limiteRecebimento = limiteRecebimento;
        this.limiteEnvio = limiteEnvio;
        this.limiteAviso = limiteAviso;
        this.limiteCritico = limiteCritico;
    }

    public void monitorarRede() {
        SystemInfo sistema = new SystemInfo();

        for (NetworkIF rede : sistema.getHardware().getNetworkIFs()) {
            rede.updateAttributes();

            Long recebidosAntes = rede.getBytesRecv();
            Long enviadosAntes = rede.getBytesSent();
            Long velocidade = rede.getSpeed();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            rede.updateAttributes();

            Double recebimento = (rede.getBytesRecv() - recebidosAntes) * 8.0 / 1_000_000;
            Double envio = (rede.getBytesSent() - enviadosAntes) * 8.0 / 1_000_000;

            System.out.println("\n --Monitoramento de Rede --");
            System.out.println("Interface: " + rede.getName());
            System.out.printf("Taxa de recebimento: %.2f Mbps%n", recebimento);
            System.out.printf("Taxa de envio: %.2f Mbps%n", envio);

            if (velocidade != null && velocidade > 0) {
                Double capacidadeMbps = velocidade / 1_000_000.0;
                Double percentualRecebimento = recebimento / capacidadeMbps * 100;
                Double percentualEnvio = envio / capacidadeMbps * 100;
                Double utilizacao = percentualRecebimento + percentualEnvio;

                if (percentualRecebimento > limiteRecebimento) {
                    System.out.println("ALERTA: recebimento acima do limite!");
                }
                if (percentualEnvio > limiteEnvio) {
                    System.out.println("ALERTA: envio acima do limite!");
                }

                System.out.printf("Utilização da rede: %.2f%%%n", utilizacao);

                if (utilizacao > limiteCritico) {
                    System.out.println("ALERTA CRÍTICO: utilização elevada!");
                } else if (utilizacao > limiteAviso) {
                    System.out.println("AVISO: utilização elevada!");
                }
            } else {
                System.out.println("Utilização da rede: indisponível");
            }

            System.out.println("-----------------------------");
        }
    }
}