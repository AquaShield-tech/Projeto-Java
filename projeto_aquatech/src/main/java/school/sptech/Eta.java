package school.sptech;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

public class Eta {
    ArrayList<ArrayList> leituras = new ArrayList<>();

    public void limparTela() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    ArrayList simularValores(Integer qtdVezes){
        ArrayList<Integer>  cpuUse = new ArrayList<>();
        ArrayList<Integer>  discUse = new ArrayList<>();
        ArrayList<Integer>  memoryUse = new ArrayList<>();

        for (int i = 0; i < qtdVezes; i++) {
            cpuUse.add(ThreadLocalRandom.current().nextInt(0,101));
            discUse.add(ThreadLocalRandom.current().nextInt(0,101));
            memoryUse.add(ThreadLocalRandom.current().nextInt(0,101));
        }
        leituras.add(cpuUse);
        leituras.add(discUse);
        leituras.add(memoryUse);

        return leituras;
    }

    void consultarValores(){
        System.out.println("Os valores das leituras:\n" +
                "CPU: "+leituras.get(0)
                +"\nDisco: "+leituras.get(1)
                +"\nMemória: "+leituras.get(2)
        );
    }

    void simularDia(){
        Scanner scan = new Scanner(System.in);
        Integer qtdAlertasNormal = 0;
        Integer qtdAlertasModerado = 0;
        Integer qtdAlertasCritico = 0;

        System.out.println("Iniciando as leituras diárias...");
        System.out.println("Por quantas horas você deseja monitorar? (Em cada hora irá ser realizado 3 leituras)");

        int quantidade = 3 * scan.nextInt();
        simularValores(quantidade);

        ArrayList<Integer> leiturasCPU = leituras.get(0);
        ArrayList<Integer> leiturasDisco = leituras.get(1);
        ArrayList<Integer> leiturasMemoria = leituras.get(2);
        System.out.println("----------MÉTRICAS----------\n" +
                "COMPONENTE     MODERADO     CRÍTICO\n" +
                "CPU               >70        >99\n" +
                "DISCO             >50        >99\n" +
                "MEMÓRIA           >75        >80\n");

        for (int i = 0; i < leiturasCPU.size(); i++) {
            if (leiturasCPU.get(i) > 99) {
                qtdAlertasCritico++;
                System.out.println("FOI IDENTIFICADO UM ALERTA CRÍTICO!! Verifique sua CPU!" +
                        "\nValor lido CPU: " + leiturasCPU.get(i) + "\n");
            } else if (leiturasCPU.get(i) > 70) {
                qtdAlertasModerado++;
            }

            if (leiturasDisco.get(i) > 99) {
                qtdAlertasCritico++;
                System.out.println("FOI IDENTIFICADO UM ALERTA CRÍTICO!! Verifique seu Disco!" +
                        "\nValor lido Disco: " + leiturasDisco.get(i) + "\n");
            } else if (leiturasDisco.get(i) > 50) {
                qtdAlertasModerado++;
            }

            if (leiturasMemoria.get(i) > 90) {
                qtdAlertasCritico++;
                System.out.println("FOI IDENTIFICADO UM ALERTA CRÍTICO!! Verifique sua Memória!" +
                        "\nValor lido Memoria: " + leiturasMemoria.get(i) + "\n");
            } else if (leiturasMemoria.get(i) > 75) {
                qtdAlertasModerado++;
            }
        }

        System.out.println("\nO resumo do seu dia foi: \n"
                + "Quantidade de alertas Moderado: " + qtdAlertasModerado + "\n"
                + "Quantidade de alertas Crítico: " + qtdAlertasCritico + "\n");

        System.out.println("Valores lidos no dia:\n" +
                "CPU: "+leituras.get(0)
                +"\nDisco: "+leituras.get(1)
                +"\nMemória: "+leituras.get(2)
        );

        scan.close();
    }
}
