package school.sptech;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

public class Main {
    static void main() {
        SystemInfo sistema = new SystemInfo();

        for (
                NetworkIF rede : sistema.getHardware().

                getNetworkIFs()) {
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

            System.out.println("Interface: " + rede.getName());
            System.out.printf("Taxa de recebimento: %.2f Mbps%n", recebimento);
            System.out.printf("Taxa de envio: %.2f Mbps%n", envio);

            if (velocidade > 0) {
                Double utilizacao = (recebimento + envio) * 1_000_000 / velocidade * 100;
                System.out.printf("Utilização da rede: %.2f%%%n", utilizacao);
            } else {
                System.out.println("Utilização da rede: indisponível");
            }

            System.out.println("-------------------------");
        }
    }
}